package org.terraform.v26_3;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.Climate.Sampler;
import net.minecraft.world.level.biome.BiomeSource;
import org.bukkit.craftbukkit.block.CraftBiome;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.terraform.biome.BiomeBank;
import org.terraform.biome.custombiomes.CustomBiomeType;
import org.terraform.data.TerraformWorld;
import org.terraform.main.TerraformGeneratorPlugin;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

public class TerraformWorldProviderBiome extends BiomeSource {
    @SuppressWarnings("unused")
    private static final boolean debug = false;
    private final Set<Holder<Biome>> biomeList;
    private final TerraformWorld tw;
    private BiomeResolver biomeResolver;

    public TerraformWorldProviderBiome(TerraformWorld tw) {
        // super(biomeListToBiomeList(CustomBiomeHandler.getBiomeRegistry()));
        this.tw = tw;
        this.biomeList = CustomBiomeHandler.biomeListToBiomeSet(CustomBiomeHandler.getBiomeRegistry());

    }

    @Override
    public @NonNull Stream<Holder<Biome>> collectPossibleBiomes()
    {
        return this.biomeList.stream();
    }

    @Override // c is getPossibleBiomes
    public @NonNull Set<Holder<Biome>> possibleBiomes()
    {
        return this.biomeList;
    }

    @Override
    public @NonNull BiomeResolver createResolver(@Nullable Sampler sampler) {
        return new TerraformWorldBiomeResolver(tw);
    }
    public @NonNull BiomeResolver getOrCreateBiomeResolver() {
        if(biomeResolver == null) biomeResolver = new TerraformWorldBiomeResolver(tw);
        return biomeResolver;
    }
    @Override
    protected @NonNull MapCodec<? extends BiomeSource> codec() {
        throw new UnsupportedOperationException("Cannot serialize TerraformWorldProviderBiome");
    }

    public static class TerraformWorldBiomeResolver implements BiomeResolver {
        private final TerraformWorld tw;
        private final Registry<Biome> registry;
        public TerraformWorldBiomeResolver(TerraformWorld tw) {
            this.tw =tw;
            this.registry = CustomBiomeHandler.getBiomeRegistry();
        }

        @Override
        public @NonNull Holder<Biome> getNoiseBiome(int x, int y, int z) {
            // Used for biome generation in NMSChunkGenerator.
            // Left shift x and z
            BiomeBank bank = tw.getBiomeBank(x << 2, z << 2);
            if (bank.getHandler().getCustomBiome() == CustomBiomeType.NONE) {
                //NEVER return custom in bank.getHandler().getBiome()
                return Objects.requireNonNull(CraftBiome.bukkitToMinecraftHolder(bank.getHandler().getBiome()));
            }
            else {
                ResourceKey<Biome> rkey = CustomBiomeHandler.terraformGenBiomeRegistry.get(bank.getHandler()
                                                                                                   .getCustomBiome()); // ResourceKey.a(Registry.aP, new MinecraftKey(bank.getHandler().getCustomBiome().getKey()));
                Optional<Holder.Reference<Biome>> holder = registry.get(rkey);
                if (holder.isEmpty()) {
                    TerraformGeneratorPlugin.logger.error("Custom biome was not found in the vanilla registry!");
                }

                if (holder.isPresent()) {
                    return holder.get();
                }
                else {
                    //NEVER return custom in bank.getHandler().getBiome()
                    return Objects.requireNonNull(CraftBiome.bukkitToMinecraftHolder(bank.getHandler().getBiome()));
                }
            }
        }
    }

}
