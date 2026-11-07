package org.terraform.coregen.folia;

import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
public interface AbstractScheduler {

    CompletableFuture<Void> execAsyncRegion(@NotNull World world, int chunkX, int chunkZ, @NotNull Runnable run);

    <T> CompletableFuture<T> callAsyncRegion(@NotNull World world,
                                             int chunkX,
                                             int chunkZ,
                                             @NotNull Supplier<T> action);

    <T> CompletableFuture<T> callSyncGlobal(@NotNull Supplier<T> action);

    void execSyncGlobal(@NotNull Runnable run);

    void execAsync(@NotNull Runnable run);
}
