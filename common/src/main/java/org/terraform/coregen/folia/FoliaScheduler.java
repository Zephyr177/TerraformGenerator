package org.terraform.coregen.folia;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.terraform.main.TerraformGeneratorPlugin;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
/**
 * This is called FoliaScheduler, but Paper also has the region scheduler
 * methods
 */
public class FoliaScheduler implements AbstractScheduler {
    public CompletableFuture<Void> execAsyncRegion(@NotNull World world,
                                                  int chunkX,
                                                  int chunkZ,
                                                  @NotNull Runnable run)
    {
        return callAsyncRegion(world, chunkX, chunkZ, () -> {
            run.run();
            return null;
        });
    }

    @Override
    public <T> CompletableFuture<T> callAsyncRegion(@NotNull World world,
                                                    int chunkX,
                                                    int chunkZ,
                                                    @NotNull Supplier<T> action)
    {
        CompletableFuture<T> result = new CompletableFuture<>();
        try {
            world.getChunkAtAsync(chunkX, chunkZ).whenComplete((chunk, loadFailure) -> {
                if (loadFailure != null) {
                    result.completeExceptionally(loadFailure);
                    return;
                }
                if (chunk == null) {
                    result.completeExceptionally(new IllegalStateException("Chunk load returned null"));
                    return;
                }
                try {
                    Bukkit.getRegionScheduler().execute(TerraformGeneratorPlugin.get(), world, chunkX, chunkZ, () -> {
                        if (result.isDone()) {
                            return;
                        }
                        try {
                            result.complete(action.get());
                        }
                        catch (Throwable failure) {
                            result.completeExceptionally(failure);
                        }
                    });
                }
                catch (Throwable failure) {
                    result.completeExceptionally(failure);
                }
            });
        }
        catch (Throwable failure) {
            result.completeExceptionally(failure);
        }
        return result.orTimeout(5, TimeUnit.MINUTES);
    }

    @Override
    public <T> CompletableFuture<T> callSyncGlobal(@NotNull Supplier<T> action) {
        CompletableFuture<T> result = new CompletableFuture<>();
        try {
            Bukkit.getGlobalRegionScheduler().execute(TerraformGeneratorPlugin.get(), () -> {
                if (result.isDone()) {
                    return;
                }
                try {
                    result.complete(action.get());
                }
                catch (Throwable failure) {
                    result.completeExceptionally(failure);
                }
            });
        }
        catch (Throwable failure) {
            result.completeExceptionally(failure);
        }
        return result.orTimeout(5, TimeUnit.MINUTES);
    }

    @Override
    public void execSyncGlobal(@NotNull Runnable run) {
        Bukkit.getGlobalRegionScheduler().execute(TerraformGeneratorPlugin.get(), run);
    }

    @Override
    public void execAsync(@NotNull Runnable run) {
        Bukkit.getAsyncScheduler().runNow(TerraformGeneratorPlugin.get(), (task)->{
            run.run();
        });
    }
}
