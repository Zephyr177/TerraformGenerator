package org.terraform.coregen.populatordata;

import org.bukkit.World;
import org.bukkit.Material;
import org.terraform.biome.custombiomes.CustomBiomeType;
import org.bukkit.block.Biome;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.minecart.StorageMinecart;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.terraform.coregen.NaturalSpawnType;
import org.terraform.coregen.TerraLootTable;
import org.terraform.coregen.bukkit.TerraformGenerator;
import org.terraform.data.TerraformWorld;
import org.terraform.main.TerraformGeneratorPlugin;
import org.terraform.main.config.TConfig;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Blocking procedural facade over operations scheduled on the owning world region.
 * Never call this from a world region, global-region, or chunk-generation callback.
 */
public final class PopulatorDataRegion extends PopulatorDataAbstract
        implements IPopulatorDataPhysicsCapable,
        IPopulatorDataMinecartSpawner,
        IPopulatorDataBeehiveEditor,
        IPopulatorDataBiomeWriter,
        IPopulatorDataGroundHeight,
        IPopulatorDataStructureEffects,
        IPopulatorDataBatchBlockWriter,
        IPopulatorDataGroundInvalidation
{
    private static final int MAX_PENDING_BLOCK_WRITES = 1024;

    private final World world;
    private final TerraformWorld terraformWorld;
    private final int originChunkX;
    private final int originChunkZ;
    private final Runnable cancellationCheck;
    private final Runnable cancellationAction;
    private final List<BlockWrite> pendingWrites = new ArrayList<>();
    private final java.util.Set<Long> dirtyGroundColumns = new java.util.HashSet<>();

    public PopulatorDataRegion(@NotNull TerraformWorld terraformWorld,
                               @NotNull World world,
                               int originChunkX,
                               int originChunkZ,
                               @NotNull Runnable cancellationCheck,
                               @NotNull Runnable cancellationAction)
    {
        this.terraformWorld = terraformWorld;
        this.world = world;
        this.originChunkX = originChunkX;
        this.originChunkZ = originChunkZ;
        this.cancellationCheck = cancellationCheck;
        this.cancellationAction = cancellationAction;
    }

    private void markGroundDirty(int x, int z) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                long key = ((long) (x + dx) << 32) | ((z + dz) & 0xffffffffL);
                dirtyGroundColumns.add(key);
            }
        }
    }

    private void queueWrite(BlockWrite write) {
        cancellationCheck.run();
        if (pendingWrites.size() >= MAX_PENDING_BLOCK_WRITES) {
            flush();
        }
        markGroundDirty(write.x, write.z);
        pendingWrites.add(write);
    }

    public void flush() {
        cancellationCheck.run();
        int start = 0;
        try {
            while (start < pendingWrites.size()) {
                cancellationCheck.run();
                BlockWrite first = pendingWrites.get(start);
                int chunkX = first.x >> 4;
                int chunkZ = first.z >> 4;
                int end = start + 1;
                while (end < pendingWrites.size()) {
                    BlockWrite next = pendingWrites.get(end);
                    if ((next.x >> 4) != chunkX || (next.z >> 4) != chunkZ) {
                        break;
                    }
                    end++;
                }

                final int batchStart = start;
                final int batchEnd = end;
                onRegion(chunkX << 4, chunkZ << 4, () -> {
                    for (int i = batchStart; i < batchEnd; i++) {
                        cancellationCheck.run();
                        pendingWrites.get(i).apply(world);
                    }
                    return null;
                });
                start = end;
            }
            pendingWrites.clear();
        }
        catch (RuntimeException | Error failure) {
            pendingWrites.clear();
            throw failure;
        }
    }

    @Override
    public void flushBlockWrites() {
        flush();
    }

    @Override
    public void setTypeIfNotSolid(int x, int y, int z, Material material, boolean updatePhysics) {
        flush();
        onRegion(x, z, () -> {
            var block = world.getBlockAt(x, y, z);
            if (!block.getType().isSolid()) {
                block.setType(material, updatePhysics);
                markGroundDirty(x, z);
            }
            return null;
        });
    }
    @Override
    public void setTypeIfNotSolidBatch(List<IPopulatorDataBatchBlockWriter.Placement> placements,
                                       boolean updatePhysics)
    {
        flush();
        int start = 0;
        while (start < placements.size()) {
            IPopulatorDataBatchBlockWriter.Placement first = placements.get(start);
            int chunkX = first.x() >> 4;
            int chunkZ = first.z() >> 4;
            int end = start + 1;
            while (end < placements.size()) {
                IPopulatorDataBatchBlockWriter.Placement next = placements.get(end);
                if ((next.x() >> 4) != chunkX || (next.z() >> 4) != chunkZ) {
                    break;
                }
                end++;
            }

            int batchStart = start;
            int batchEnd = end;
            onRegion(chunkX << 4, chunkZ << 4, () -> {
                for (int i = batchStart; i < batchEnd; i++) {
                    cancellationCheck.run();
                    IPopulatorDataBatchBlockWriter.Placement placement = placements.get(i);
                    var block = world.getBlockAt(placement.x(), placement.y(), placement.z());
                    if (!block.getType().isSolid()) {
                        block.setType(placement.material(), updatePhysics);
                        markGroundDirty(placement.x(), placement.z());
                    }
                }
                return null;
            });
            start = end;
        }
    }

    private <T> T onRegion(int x, int z, java.util.function.Supplier<T> action) {
        assertNotRegionThread();
        cancellationCheck.run();
        try {
            return TerraformGeneratorPlugin.taskScheduler.callAsyncRegion(world, x >> 4, z >> 4, () -> {
                cancellationCheck.run();
                return action.get();
            }).get();
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            cancellationAction.run();
            throw new IllegalStateException("Interrupted while accessing world region", e);
        }
        catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof java.util.concurrent.TimeoutException) {
                cancellationAction.run();
            }
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("World region operation failed", cause);
        }
    }

    private static void assertNotRegionThread() {
        if (org.bukkit.Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Region-backed structure generation cannot block the primary thread");
        }
        try {
            Class<?> tickThread = Class.forName("ca.spottedleaf.moonrise.common.util.TickThread");
            if ((boolean) tickThread.getMethod("isTickThread").invoke(null)) {
                throw new IllegalStateException("Region-backed structure generation cannot block a tick thread");
            }
        }
        catch (ClassNotFoundException ignored) {
            // Standard Paper/Spigot does not expose the regionized tick-thread marker.
        }
        catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to determine whether this is a region tick thread", e);
        }
    }

    @Override
    public @NotNull Material getType(int x, int y, int z) {
        flush();
        return onRegion(x, z, () -> world.getBlockAt(x, y, z).getType());
    }

    @Override
    public @Nullable BlockData getBlockData(int x, int y, int z) {
        flush();
        return onRegion(x, z, () -> world.getBlockAt(x, y, z).getBlockData());
    }

    @Override
    public void setType(int x, int y, int z, Material type) {
        boolean physics = !org.bukkit.Tag.DOORS.isTagged(type)
                          && !org.bukkit.Tag.CARPETS.isTagged(type)
                          && type != Material.FARMLAND
                          && type != Material.WATER;
        setType(x, y, z, type, physics);
    }

    @Override
    public void setBlockData(int x, int y, int z, @NotNull BlockData data) {
        boolean physics = !org.bukkit.Tag.DOORS.isTagged(data.getMaterial())
                          && !org.bukkit.Tag.CARPETS.isTagged(data.getMaterial())
                          && data.getMaterial() != Material.FARMLAND
                          && data.getMaterial() != Material.WATER;
        setBlockData(x, y, z, data, physics);
    }

    @Override
    public void setType(int x, int y, int z, Material type, boolean updatePhysics) {
        queueWrite(new BlockWrite(x, y, z, type, null, updatePhysics));
    }

    @Override
    public void setBlockData(int x, int y, int z, @NotNull BlockData data, boolean updatePhysics) {
        queueWrite(new BlockWrite(x, y, z, null, data.clone(), updatePhysics));
    }

    @Override
    public @Nullable Biome getBiome(int rawX, int rawZ) {
        flush();
        return onRegion(rawX, rawZ,
                () -> world.getBlockAt(rawX, TerraformGenerator.seaLevel, rawZ).getBiome());
    }

    @Override
    public void setBiome(int rawX, int rawY, int rawZ, CustomBiomeType customBiome, Biome fallback) {
        flush();
        onRegion(rawX, rawZ, () -> {
            var nativeData = TerraformGeneratorPlugin.injector.getICAData(world.getChunkAt(rawX >> 4, rawZ >> 4));
            if (nativeData instanceof IPopulatorDataBiomeWriter biomeWriter) {
                biomeWriter.setBiome(rawX, rawY, rawZ, customBiome, fallback);
            }
            return null;
        });
    }

    @Override
    public void setBiome(int rawX, int rawY, int rawZ, Biome biome) {
        flush();
        onRegion(rawX, rawZ, () -> {
            var nativeData = TerraformGeneratorPlugin.injector.getICAData(world.getChunkAt(rawX >> 4, rawZ >> 4));
            if (nativeData instanceof IPopulatorDataBiomeWriter biomeWriter) {
                biomeWriter.setBiome(rawX, rawY, rawZ, biome);
            }
            return null;
        });
    }

    @Override
    public int getChunkX() {
        return originChunkX;
    }

    @Override
    public int getChunkZ() {
        return originChunkZ;
    }

    @Override
    public void addEntity(float rawX, float rawY, float rawZ, EntityType type) {
        spawnEntity((double) rawX + 0.5,
                (double) rawY + 0.3,
                (double) rawZ + 0.5,
                type,
                entity -> {
                    entity.setPersistent(true);
                    if (entity instanceof LivingEntity livingEntity) {
                        livingEntity.setRemoveWhenFarAway(false);
                    }
                });
    }

    private static int blockCoordinate(double coordinate) {
        return (int) Math.floor(coordinate);
    }

    @Override
    public void spawnEntity(double worldX, double worldY, double worldZ, EntityType type, Consumer<Entity> configure) {
        flush();
        onRegion(blockCoordinate(worldX), blockCoordinate(worldZ), () -> {
            cancellationCheck.run();
            if (world.getDifficulty() == org.bukkit.Difficulty.PEACEFUL && isHostileMob(type)) {
                return null;
            }
            Entity entity = world.spawnEntity(new org.bukkit.Location(world, worldX, worldY, worldZ), type);
            configure.accept(entity);
            return null;
        });
    }

    @Override
    public void setSpawner(int rawX, int rawY, int rawZ, EntityType type) {
        if (!TConfig.c.FEATURE_SPAWNERS_ENABLED) {
            return;
        }
        flush();
        onRegion(rawX, rawZ, () -> {
            var block = world.getBlockAt(rawX, rawY, rawZ);
            block.setType(Material.SPAWNER, false);
            CreatureSpawner spawner = (CreatureSpawner) block.getState();
            spawner.setSpawnedType(type);
            spawner.update();
            return null;
        });
    }

    @Override
    public void lootTableChest(int x, int y, int z, TerraLootTable table) {
        flush();
        onRegion(x, z, () -> {
            TerraformGeneratorPlugin.injector.getICAData(world.getChunkAt(x >> 4, z >> 4))
                    .lootTableChest(x, y, z, table);
            return null;
        });
    }

    @Override
    public void registerNaturalSpawns(NaturalSpawnType type,
                                      int targetChunkX,
                                      int targetChunkZ,
                                      int x0,
                                      int y0,
                                      int z0,
                                      int x1,
                                      int y1,
                                      int z1)
    {
        flush();
        onRegion(targetChunkX << 4, targetChunkZ << 4, () -> {
            TerraformGeneratorPlugin.injector.getICAData(world.getChunkAt(targetChunkX, targetChunkZ))
                    .registerNaturalSpawns(type, x0, y0, z0, x1, y1, z1);
            return null;
        });
    }

    @Override
    public void editBlockState(int x,
                               int y,
                               int z,
                               boolean force,
                               boolean applyPhysics,
                               Consumer<BlockState> editor)
    {
        flush();
        onRegion(x, z, () -> {
            BlockState state = world.getBlockAt(x, y, z).getState();
            editor.accept(state);
            state.update(force, applyPhysics);
            return null;
        });
    }

    @Override
    public void setBeehiveWithBee(int rawX, int rawY, int rawZ) {
        if (!TConfig.areAnimalsEnabled()) {
            return;
        }
        flush();
        onRegion(rawX, rawZ, () -> {
            var block = world.getBlockAt(rawX, rawY, rawZ);
            block.setType(Material.BEE_NEST, false);
            var hive = (org.bukkit.block.Beehive) block.getState();
            TerraformGeneratorPlugin.injector.storeBee(hive);
            hive.update();
            return null;
        });
    }

    @Override
    public void spawnMinecartWithChest(int x, int y, int z, TerraLootTable table, Random random) {
        flush();
        long lootSeed = random.nextLong();
        onRegion(x, z, () -> {
            StorageMinecart minecart = (StorageMinecart) world.spawnEntity(
                    new org.bukkit.Location(world, x + 0.5f, y + 0.5f, z + 0.5f),
                    EntityType.MINECART_CHEST
            );
            minecart.setLootTable(table.bukkit(), lootSeed);
            return null;
        });
    }

    @Override
    public int getHighestGround(int x, int z, int minY, int maxY, Predicate<Material> groundPredicate) {
        flush();
        return onRegion(x, z, () -> {
            for (int y = maxY - 1; y > minY; y--) {
                cancellationCheck.run();
                if (groundPredicate.test(world.getBlockAt(x, y, z).getType())) {
                    return y;
                }
            }
            return minY;
        });
    }

    @Override
    public int[] getHighestGrounds(int[] x, int[] z, int minY, int maxY, Predicate<Material> groundPredicate) {
        if (x.length != z.length) {
            throw new IllegalArgumentException("x and z coordinate arrays must have equal lengths");
        }
        flush();
        int[] heights = new int[x.length];
        Map<Long, List<Integer>> indexesByChunk = new HashMap<>();
        for (int i = 0; i < x.length; i++) {
            long key = ((long) (x[i] >> 4) << 32) | ((z[i] >> 4) & 0xffffffffL);
            indexesByChunk.computeIfAbsent(key, ignored -> new ArrayList<>()).add(i);
        }

        for (Map.Entry<Long, List<Integer>> entry : indexesByChunk.entrySet()) {
            int chunkX = (int) (entry.getKey() >> 32);
            int chunkZ = (int) (long) entry.getKey();
            List<Integer> indexes = entry.getValue();
            for (int start = 0; start < indexes.size(); start += 32) {
                int batchStart = start;
                int batchEnd = Math.min(start + 32, indexes.size());
                onRegion(chunkX << 4, chunkZ << 4, () -> {
                    for (int i = batchStart; i < batchEnd; i++) {
                        cancellationCheck.run();
                        int index = indexes.get(i);
                        int height = minY;
                        for (int y = maxY - 1; y > minY; y--) {
                            if (groundPredicate.test(world.getBlockAt(x[index], y, z[index]).getType())) {
                                height = y;
                                break;
                            }
                        }
                        heights[index] = height;
                    }
                    return null;
                });
            }
        }
        return heights;
    }
    @Override
    public boolean isGroundColumnDirty(int x, int z) {
        long key = ((long) x << 32) | (z & 0xffffffffL);
        return dirtyGroundColumns.contains(key);
    }
    private record BlockWrite(int x,
                              int y,
                              int z,
                              Material material,
                              BlockData blockData,
                              boolean updatePhysics)
    {
        private void apply(World world) {
            var block = world.getBlockAt(x, y, z);
            if (material != null) {
                block.setType(material, updatePhysics);
            }
            else {
                block.setBlockData(blockData, updatePhysics);
            }
        }
    }

    @Override
    public @NotNull TerraformWorld getTerraformWorld() {
        return terraformWorld;
    }

    @Override
    public boolean isInBound(int x, int y, int z) {
        return true;
    }
}
