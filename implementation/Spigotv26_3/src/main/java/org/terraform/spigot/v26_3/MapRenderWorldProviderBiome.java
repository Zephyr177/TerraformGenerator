package org.terraform.spigot.v26_3;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.Climate.Sampler;
import net.minecraft.world.level.biome.BiomeSource;
import org.bukkit.craftbukkit.block.CraftBiome;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.terraform.coregen.HeightMap;
import org.terraform.data.TerraformWorld;
import org.terraform.main.config.TConfig;

import java.util.Set;
import java.util.stream.Stream;

public class MapRenderWorldProviderBiome extends BiomeSource {
    @SuppressWarnings("unused")
    private static final boolean debug = false;
    private final TerraformWorld tw;
    private final Set<Holder<Biome>> biomeList;

    public MapRenderWorldProviderBiome(TerraformWorld tw) {
        this.biomeList = CustomBiomeHandler.biomeListToBiomeSet(CustomBiomeHandler.getBiomeRegistry());
        this.tw = tw;
    }

    @Override
    public @NonNull Stream<Holder<Biome>> collectPossibleBiomes()
    {
        return this.biomeList.stream();
    }

    @Override // c is possibleBiomes
    public @NonNull Set<Holder<Biome>> possibleBiomes()
    {
        return this.biomeList;
    }

    @Override
    public @NonNull BiomeResolver createResolver(@Nullable Sampler sampler) {
        return new MapRendererResolver(tw);
    }

    @Override
    protected @NonNull MapCodec<? extends BiomeSource> codec() {
        throw new UnsupportedOperationException("Cannot serialize MapRenderWorldProviderBiome");
    }

    public static class MapRendererResolver implements BiomeResolver {
        private final TerraformWorld tw;
        private final Holder<Biome> river;
        private final Holder<Biome> plains;
        public MapRendererResolver(TerraformWorld tw) {
            this.tw =tw;
            this.river = CraftBiome.bukkitToMinecraftHolder(org.bukkit.block.Biome.RIVER);
            this.plains = CraftBiome.bukkitToMinecraftHolder(org.bukkit.block.Biome.PLAINS);
        }
        @Override
        public @NonNull Holder<Biome> getNoiseBiome(int x, int y, int z) {
            // Used to be attempted for cave gen. That didn't work, so now, this is
            // for optimising cartographers and buried treasure.
            // This will return river or plains depending on whether
            // the area is submerged.

            return HeightMap.getBlockHeight(tw, x, z) <= TConfig.c.HEIGHT_MAP_SEA_LEVEL ? river : plains;
        }
    }

}
