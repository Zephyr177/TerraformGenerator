package org.terraform.coregen.populatordata;

import org.bukkit.Material;

import java.util.List;

/** Batches conditional writes while preserving sequence and target-region ownership. */
public interface IPopulatorDataBatchBlockWriter {
    record Placement(int x, int y, int z, Material material) {
    }

    void setTypeIfNotSolid(int x, int y, int z, Material material, boolean updatePhysics);

    void setTypeIfNotSolidBatch(List<Placement> placements, boolean updatePhysics);

    void flushBlockWrites();
}
