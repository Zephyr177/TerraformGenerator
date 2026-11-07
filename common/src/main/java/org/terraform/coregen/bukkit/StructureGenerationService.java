package org.terraform.coregen.bukkit;

import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.terraform.coregen.folia.SpigotScheduler;
import org.terraform.coregen.populatordata.PopulatorDataPostGen;
import org.terraform.coregen.populatordata.PopulatorDataRegion;
import org.terraform.coregen.populatordata.PopulatorDataAbstract;
import org.terraform.data.TerraformWorld;
import org.terraform.event.TerraformStructureSpawnEvent;
import org.terraform.main.TerraformGeneratorPlugin;
import org.terraform.structure.SingleMegaChunkStructurePopulator;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/** Runs large structures outside region callbacks and routes each live operation to its owning region. */
public final class StructureGenerationService {
    private static final Map<SingleMegaChunkStructurePopulator, ReentrantLock> POPULATOR_LOCKS = new WeakHashMap<>();
    private static final ConcurrentMap<JobKey, JobControl> PENDING = new ConcurrentHashMap<>();
    private static final Object LIFECYCLE_LOCK = new Object();
    private static final Map<World, Boolean> UNLOADING_WORLDS = new WeakHashMap<>();
    private static volatile boolean acceptingJobs = true;

    private StructureGenerationService() {
    }

    public static void start() {
        synchronized (LIFECYCLE_LOCK) {
            acceptingJobs = true;
        }
    }

    public static void allowWorld(@NotNull World world) {
        synchronized (LIFECYCLE_LOCK) {
            UNLOADING_WORLDS.remove(world);
        }
    }


    public static void submit(@NotNull World world,
                              @NotNull TerraformWorld terraformWorld,
                              int originChunkX,
                              int originChunkZ,
                              @NotNull List<StructureJob> jobs,
                              @NotNull Consumer<PopulatorDataAbstract> afterGeneration)
    {
        submit(world, terraformWorld, originChunkX, originChunkZ, jobs, afterGeneration, () -> {});
    }
    public static void submit(@NotNull World world,
                              @NotNull TerraformWorld terraformWorld,
                              int originChunkX,
                              int originChunkZ,
                              @NotNull List<StructureJob> jobs,
                              @NotNull Consumer<PopulatorDataAbstract> afterGeneration,
                              @NotNull Runnable afterSuccess)
    {
        JobKey key = new JobKey(world, originChunkX, originChunkZ);
        JobControl control = new JobControl();
        synchronized (LIFECYCLE_LOCK) {
            if (!acceptingJobs || UNLOADING_WORLDS.containsKey(world)) {
                return;
            }
            if (PENDING.putIfAbsent(key, control) != null) {
                return;
            }
        }

        try {
            TerraformGeneratorPlugin.taskScheduler.execAsync(() -> {
                control.worker = Thread.currentThread();
                try {
                    control.checkActive();
                    if (TerraformGeneratorPlugin.taskScheduler instanceof SpigotScheduler) {
                        TerraformGeneratorPlugin.taskScheduler.callSyncGlobal(() -> {
                            control.checkActive();
                            PopulatorDataPostGen data = new PopulatorDataPostGen(
                                    world.getChunkAt(originChunkX, originChunkZ),
                                    control::checkActive);
                            for (StructureJob job : jobs) {
                                control.checkActive();
                                if (job.emitSpawnEvent) {
                                    org.bukkit.Bukkit.getPluginManager().callEvent(
                                            new TerraformStructureSpawnEvent(job.eventX,
                                                    job.eventZ,
                                                    job.populator.getClass().getName())
                                    );
                                }
                                runSerialized(control, job, terraformWorld, data);
                            }
                            control.checkActive();
                            afterGeneration.accept(data);
                            control.checkActive();
                            afterSuccess.run();
                            return null;
                        }).get();
                        return;
                    }

                    PopulatorDataRegion data = new PopulatorDataRegion(terraformWorld,
                            world,
                            originChunkX,
                            originChunkZ,
                            control::checkActive,
                            control::cancel);
                    for (StructureJob job : jobs) {
                        control.checkActive();
                        if (job.emitSpawnEvent) {
                            try {
                                TerraformGeneratorPlugin.taskScheduler.callSyncGlobal(() -> {
                                    control.checkActive();
                                    org.bukkit.Bukkit.getPluginManager().callEvent(
                                            new TerraformStructureSpawnEvent(job.eventX,
                                                    job.eventZ,
                                                    job.populator.getClass().getName())
                                    );
                                    return null;
                                }).get();
                            }
                            catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                control.cancel();
                                throw new CancellationException("Interrupted before structure generation");
                            }
                            catch (ExecutionException e) {
                                throw new IllegalStateException("Structure spawn event failed", e.getCause());
                            }
                        }

                        runSerialized(control, job, terraformWorld, data);
                        data.flush();
                    }
                    control.checkActive();
                    afterGeneration.accept(data);
                    data.flush();
                    control.checkActive();
                    afterSuccess.run();
                }
                catch (CancellationException ignored) {
                    // World unload or plugin shutdown cancelled the job before its next owner-region operation.
                }
                catch (Throwable failure) {
                    boolean cancelled = control.isCancelled();
                    control.cancel();
                    if (!cancelled) {
                        TerraformGeneratorPlugin.logger.error("Failed to generate structures at chunk "
                                                              + originChunkX
                                                              + ","
                                                              + originChunkZ);
                        TerraformGeneratorPlugin.logger.stackTrace(failure);
                    }
                }
                finally {
                    control.worker = null;
                    PENDING.remove(key, control);
                }
            });
        }
        catch (Throwable failure) {
            PENDING.remove(key, control);
            TerraformGeneratorPlugin.logger.error("Failed to schedule structures at chunk "
                                                  + originChunkX
                                                  + ","
                                                  + originChunkZ);
            TerraformGeneratorPlugin.logger.stackTrace(failure);
        }
    }

    private static void runSerialized(JobControl control,
                                      StructureJob job,
                                      TerraformWorld terraformWorld,
                                      PopulatorDataAbstract data)
    {
        ReentrantLock lock;
        synchronized (POPULATOR_LOCKS) {
            lock = POPULATOR_LOCKS.computeIfAbsent(job.populator, ignored -> new ReentrantLock());
        }
        try {
            while (!lock.tryLock(100, TimeUnit.MILLISECONDS)) {
                control.checkActive();
            }
            try {
                control.checkActive();
                job.populator.populate(terraformWorld, data);
            }
            finally {
                lock.unlock();
            }
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            control.cancel();
            throw new CancellationException("Interrupted while waiting for structure execution lock");
        }
    }

    public static void cancelWorld(@NotNull World world) {
        synchronized (LIFECYCLE_LOCK) {
            UNLOADING_WORLDS.put(world, Boolean.TRUE);
            PENDING.forEach((key, control) -> {
                if (key.world == world) {
                    control.cancel();
                }
            });
        }
    }

    public static void cancelAll() {
        synchronized (LIFECYCLE_LOCK) {
            acceptingJobs = false;
            PENDING.forEach((key, control) -> {
                control.cancel();
            });
        }
    }

    public record StructureJob(@NotNull SingleMegaChunkStructurePopulator populator,
                               boolean emitSpawnEvent,
                               int eventX,
                               int eventZ)
    {
    }

    private static final class JobControl {
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private volatile Thread worker;

        private boolean isCancelled() {
            return cancelled.get();
        }

        private void checkActive() {
            if (cancelled.get() || Thread.currentThread().isInterrupted()) {
                throw new CancellationException("Structure generation job is no longer active");
            }
        }

        private void cancel() {
            cancelled.set(true);
            Thread running = worker;
            if (running != null) {
                running.interrupt();
            }
        }
    }

    private static final class JobKey {
        private final World world;
        private final int chunkX;
        private final int chunkZ;

        private JobKey(World world, int chunkX, int chunkZ) {
            this.world = world;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof JobKey key
                   && world == key.world
                   && chunkX == key.chunkX
                   && chunkZ == key.chunkZ;
        }

        @Override
        public int hashCode() {
            int result = System.identityHashCode(world);
            result = 31 * result + chunkX;
            result = 31 * result + chunkZ;
            return result;
        }
    }
}
