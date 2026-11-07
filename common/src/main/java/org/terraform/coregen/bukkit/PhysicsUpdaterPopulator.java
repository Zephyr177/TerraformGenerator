package org.terraform.coregen.bukkit;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.generator.BlockPopulator;
import org.jetbrains.annotations.NotNull;
import org.terraform.data.SimpleChunkLocation;
import org.terraform.data.SimpleLocation;
import org.terraform.main.TerraformGeneratorPlugin;
import org.terraform.main.config.TConfig;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.*;

public class PhysicsUpdaterPopulator extends BlockPopulator implements Listener {

    // SimpleChunkLocation to a collection of simplelocations
    public static final @NotNull Map<SimpleChunkLocation, Collection<SimpleLocation>> cache = new HashMap<>();
    // private final TerraformWorld tw;

    public PhysicsUpdaterPopulator() {
        // this.tw = tw;
        Bukkit.getPluginManager().registerEvents(this, TerraformGeneratorPlugin.get());
    }

    public static void pushChange(String world, @NotNull SimpleLocation loc) {
        synchronized (cache){
            if (cache.size() > TConfig.c.DEVSTUFF_FLUSH_PATCHER_CACHE_FREQUENCY) {
                flushChanges();
            }

            SimpleChunkLocation scl = new SimpleChunkLocation(world, loc.getX(), loc.getY(), loc.getZ());
            if (!cache.containsKey(scl)) {
                cache.put(scl, new ArrayList<>());
            }

            cache.get(scl).add(loc);
        }
    }

    public static void syncAndFlush(){
        synchronized (cache){
            flushChanges();
        }
    }
    private static void flushChanges() {

        if (cache.isEmpty()) {
            return;
        }
        TerraformGeneratorPlugin.logger.info("[PhysicsUpdaterPopulator] Flushing repairs ("
                                             + cache.size()
                                             + " chunks)");
        ArrayList<SimpleChunkLocation> locs = new ArrayList<>(cache.keySet());
        for (SimpleChunkLocation scl : locs) {
            World w = Bukkit.getWorld(scl.getWorld());
            if (w == null) {
                continue;
            }
            Collection<SimpleLocation> changes = cache.remove(scl);
            if (changes != null) {
                scheduleChanges(w, scl, changes);
            }
        }
    }

    private static CompletableFuture<Void> scheduleChanges(@NotNull World world,
                                                           @NotNull SimpleChunkLocation location,
                                                           @NotNull Collection<SimpleLocation> changes)
    {
        return DeferredRegionTaskTracker.submit(world,
                location.getX(),
                location.getZ(),
                checkActive -> {
                    for (SimpleLocation entry : changes) {
                        checkActive.run();
                        Block target = world.getBlockAt(entry.getX(), entry.getY(), entry.getZ());
                        BlockData old = target.getBlockData();
                        target.setType(Material.AIR);
                        target.setBlockData(old, true);
                    }
                }).whenComplete((ignored, failure) -> {
                    if (failure != null) {
                        synchronized (cache) {
                            cache.computeIfAbsent(location, unused -> new ArrayList<>()).addAll(changes);
                        }
                        if (!(failure instanceof CancellationException)) {
                            TerraformGeneratorPlugin.logger.error("Failed to apply deferred physics updates for " + location);
                            TerraformGeneratorPlugin.logger.stackTrace(failure);
                        }
                    }
                });
    }

    @Override
    public void populate(@NotNull World world, @NotNull Random random, @NotNull Chunk chunk) {
        populateAsync(world, chunk);
    }

    public CompletableFuture<Void> populateAsync(@NotNull World world, @NotNull Chunk chunk) {
        SimpleChunkLocation scl = new SimpleChunkLocation(chunk);
        Collection<SimpleLocation> changes;
        synchronized (cache) {
            changes = cache.remove(scl);
        }
        if (changes == null) {
            return CompletableFuture.completedFuture(null);
        }
        return scheduleChanges(world, scl, changes);
    }

    @EventHandler
    public void onWorldUnload(@NotNull WorldUnloadEvent event) {
        TerraformGeneratorPlugin.logger.info("[PhysicsUpdaterPopulator] Flushing repairs for "
                                             + event.getWorld()
                                                    .getName()
                                             + " ("
                                             + cache.size()
                                             + " chunks in cache)");
        syncAndFlush();

    }

}
