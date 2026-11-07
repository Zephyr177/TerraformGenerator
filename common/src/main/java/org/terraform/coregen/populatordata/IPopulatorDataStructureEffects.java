package org.terraform.coregen.populatordata;

import org.bukkit.block.Biome;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.terraform.coregen.NaturalSpawnType;

import java.util.function.Consumer;

/** Operations whose live Bukkit state must be created and consumed on the owning region. */
public interface IPopulatorDataStructureEffects {
    void editBlockState(int x,
                        int y,
                        int z,
                        boolean force,
                        boolean applyPhysics,
                        Consumer<BlockState> editor);

    void spawnEntity(double worldX, double worldY, double worldZ, EntityType type, Consumer<Entity> configure);

    void registerNaturalSpawns(NaturalSpawnType type,
                               int targetChunkX,
                               int targetChunkZ,
                               int x0,
                               int y0,
                               int z0,
                               int x1,
                               int y1,
                               int z1);
}
