package org.terraform.coregen.populatordata;

import org.bukkit.Material;

import java.util.function.Predicate;

/** Provides a highest-ground scan performed on the owning world region. */
public interface IPopulatorDataGroundHeight {
    int getHighestGround(int x, int z, int minY, int maxY, Predicate<Material> groundPredicate);

    int[] getHighestGrounds(int[] x,
                            int[] z,
                            int minY,
                            int maxY,
                            Predicate<Material> groundPredicate);
}
