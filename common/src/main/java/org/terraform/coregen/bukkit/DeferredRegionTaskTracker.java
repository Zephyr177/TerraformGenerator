package org.terraform.coregen.bukkit;

import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.terraform.main.TerraformGeneratorPlugin;

import java.util.Collections;
import java.util.WeakHashMap;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Tracks deferred owner-region operations so world unload and shutdown can cancel them. */
public final class DeferredRegionTaskTracker {
    private static final ConcurrentMap<World, Set<TaskControl>> TASKS = new ConcurrentHashMap<>();
    private static final Set<World> UNLOADING_WORLDS = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<World> RELOADED_WORLDS = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Object LIFECYCLE_LOCK = new Object();
    private static boolean acceptingTasks = true;

    private DeferredRegionTaskTracker() {
    }

    public static void start() {
        synchronized (LIFECYCLE_LOCK) {
            acceptingTasks = true;
        }
    }

    public static CompletableFuture<Void> submit(@NotNull World world,
                                                  int chunkX,
                                                  int chunkZ,
                                                  @NotNull Consumer<Runnable> action)
    {
        TaskControl control = new TaskControl();
        synchronized (LIFECYCLE_LOCK) {
            if (!acceptingTasks || UNLOADING_WORLDS.contains(world)) {
                CompletableFuture<Void> cancelled = new CompletableFuture<>();
                cancelled.cancel(false);
                return cancelled;
            }
            TASKS.compute(world, (ignored, tasks) -> {
                Set<TaskControl> updated = tasks == null ? ConcurrentHashMap.newKeySet() : tasks;
                updated.add(control);
                return updated;
            });
        }

        CompletableFuture<Void> scheduled;
        try {
            scheduled = TerraformGeneratorPlugin.taskScheduler.execAsyncRegion(world, chunkX, chunkZ, () -> {
                if (!control.start(world)) {
                    throw new CancellationException("Deferred region operation cancelled before execution");
                }
                try {
                    control.checkActive();
                    action.accept(control::checkActive);
                }
                finally {
                    control.finish(world);
                }
            });
        }
        catch (Throwable failure) {
            control.schedulerFinished(world, failure);
            return control.completion;
        }

        control.future = scheduled;
        scheduled.whenComplete((ignored, failure) -> control.schedulerFinished(world, failure));
        if (control.cancelled.get()) {
            scheduled.cancel(false);
        }
        return control.completion;
    }

    public static void allowWorld(@NotNull World world) {
        synchronized (LIFECYCLE_LOCK) {
            if (TASKS.containsKey(world)) {
                RELOADED_WORLDS.add(world);
            }
            else {
                UNLOADING_WORLDS.remove(world);
                RELOADED_WORLDS.remove(world);
            }
        }
    }

    public static void cancelWorld(@NotNull World world) {
        Set<TaskControl> tasks;
        synchronized (LIFECYCLE_LOCK) {
            UNLOADING_WORLDS.add(world);
            RELOADED_WORLDS.remove(world);
            tasks = TASKS.get(world);
        }
        if (tasks != null) {
            tasks.forEach(TaskControl::cancel);
        }
    }

    public static void cancelAll() {
        ArrayList<TaskControl> tasks = new ArrayList<>();
        synchronized (LIFECYCLE_LOCK) {
            acceptingTasks = false;
            TASKS.values().forEach(tasks::addAll);
        }
        tasks.forEach(TaskControl::cancel);
    }


    private static void remove(World world, TaskControl control) {
        TASKS.computeIfPresent(world, (ignored, tasks) -> {
            tasks.remove(control);
            return tasks.isEmpty() ? null : tasks;
        });
        synchronized (LIFECYCLE_LOCK) {
            if (!TASKS.containsKey(world) && RELOADED_WORLDS.remove(world)) {
                UNLOADING_WORLDS.remove(world);
            }
        }
    }

    private static final class TaskControl {
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final AtomicBoolean running = new AtomicBoolean();
        private final AtomicBoolean schedulerFinished = new AtomicBoolean();
        private final CompletableFuture<Void> completion = new CompletableFuture<>();
        private volatile CompletableFuture<Void> future;
        private volatile Throwable schedulerFailure;

        private boolean start(World world) {
            if (cancelled.get()) {
                completeIfReady(world);
                return false;
            }
            running.set(true);
            if (cancelled.get()) {
                running.set(false);
                completeIfReady(world);
                return false;
            }
            return true;
        }

        private void finish(World world) {
            running.set(false);
            completeIfReady(world);
        }

        private void schedulerFinished(World world, Throwable failure) {
            if (failure != null) {
                schedulerFailure = failure;
                cancelled.set(true);
            }
            schedulerFinished.set(true);
            completeIfReady(world);
        }

        private void completeIfReady(World world) {
            if (!schedulerFinished.get() || running.get()) {
                return;
            }
            remove(world, this);
            Throwable failure = schedulerFailure;
            if (failure == null) {
                completion.complete(null);
            }
            else {
                completion.completeExceptionally(failure);
            }
        }

        private void cancel() {
            cancelled.set(true);
            CompletableFuture<Void> current = future;
            if (current != null) {
                current.cancel(false);
            }
        }

        private void checkActive() {
            if (cancelled.get()) {
                throw new CancellationException("Deferred region operation cancelled");
            }
        }
    }
}
