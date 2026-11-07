package org.terraform.coregen.folia;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.terraform.main.TerraformGeneratorPlugin;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.concurrent.TimeUnit;
public class SpigotScheduler implements AbstractScheduler{
    //Dumbass name tbh
    public CompletableFuture<Void> execAsyncRegion(@NotNull World world,
                                                   int chunkX,
                                                   int chunkZ,
                                                   @NotNull Runnable run)
    {
        return callSyncGlobal(() -> {
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
        return callSyncGlobal(action);
    }

    @Override
    public <T> CompletableFuture<T> callSyncGlobal(@NotNull Supplier<T> action) {
        CompletableFuture<T> result = new CompletableFuture<>();
        try {
            Bukkit.getScheduler().runTask(TerraformGeneratorPlugin.get(), () -> {
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
        Bukkit.getScheduler().runTask(TerraformGeneratorPlugin.get(), run);
    }

    @Override
    public void execAsync(@NotNull Runnable run) {
        Bukkit.getScheduler().runTaskAsynchronously(TerraformGeneratorPlugin.get(), run);
    }
}
