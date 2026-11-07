package org.terraform.coregen.populatordata;

import org.bukkit.block.Biome;
import org.jetbrains.annotations.NotNull;
import org.terraform.biome.BiomeBank;
import org.terraform.biome.custombiomes.CustomBiomeType;

public interface IPopulatorDataBiomeWriter {
    void setBiome(int rawX, int rawY, int rawZ, CustomBiomeType customBiome, Biome fallback);

    void setBiome(int rawX, int rawY, int rawZ, Biome biome);

    default void setBiome(int rawX, int rawY, int rawZ, @NotNull BiomeBank biomeBank) {
        setBiome(rawX,
                rawY,
                rawZ,
                biomeBank.getHandler().getCustomBiome(),
                biomeBank.getHandler().getBiome());
    }
}
