package org.terraform.utils.version;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.PointedDripstone;
import org.bukkit.entity.EntityType;
import org.jetbrains.annotations.NotNull;
import org.terraform.data.SimpleBlock;
import org.terraform.main.config.TConfig;
import org.terraform.utils.BlockUtils;

/**
 * You're almost certainly going to have to drop old version support in 26.2 to
 * accommodate API breakages.
 */
public class V_26_3 {
    public static Material POPLAR_WOOD = Version.VERSION.isAtLeast(Version.v26_3) ?
                                         Material.valueOf("POPLAR_WOOD") : Material.BIRCH_WOOD;
    public static Material POPLAR_LOG = Version.VERSION.isAtLeast(Version.v26_3) ?
                                        Material.valueOf("POPLAR_LOG") : Material.BIRCH_LOG;
    public static Material RED_SHRUB = Version.VERSION.isAtLeast(Version.v26_3) ?
                                        Material.valueOf("RED_SHRUB") : Material.GRASS;
    public static Material RED_POPLAR_LEAVES = Version.VERSION.isAtLeast(Version.v26_3) ?
                                       Material.valueOf("RED_POPLAR_LEAVES") : Material.BIRCH_LEAVES;
    public static Material ORANGE_POPLAR_LEAVES = Version.VERSION.isAtLeast(Version.v26_3) ?
                                       Material.valueOf("ORANGE_POPLAR_LEAVES") : Material.BIRCH_LEAVES;
    public static Material YELLOW_POPLAR_LEAVES = Version.VERSION.isAtLeast(Version.v26_3) ?
                                       Material.valueOf("YELLOW_POPLAR_LEAVES") : Material.BIRCH_LEAVES;
    public static Material[] WOOL_STAIRS = Version.VERSION.isAtLeast(Version.v26_3) ?
                                           new Material[] {
                                            Material.valueOf("WHITE_WOOL_STAIRS"),
                                            Material.valueOf("BLACK_WOOL_STAIRS"),
                                            Material.valueOf("BLUE_WOOL_STAIRS"),
                                            Material.valueOf("BROWN_WOOL_STAIRS"),
                                            Material.valueOf("CYAN_WOOL_STAIRS"),
                                            Material.valueOf("GRAY_WOOL_STAIRS"),
                                            Material.valueOf("GREEN_WOOL_STAIRS"),
                                            Material.valueOf("LIGHT_BLUE_WOOL_STAIRS"),
                                            Material.valueOf("LIGHT_GRAY_WOOL_STAIRS"),
                                            Material.valueOf("LIME_WOOL_STAIRS"),
                                            Material.valueOf("MAGENTA_WOOL_STAIRS"),
                                            Material.valueOf("ORANGE_WOOL_STAIRS"),
                                            Material.valueOf("PINK_WOOL_STAIRS"),
                                            Material.valueOf("PURPLE_WOOL_STAIRS"),
                                            Material.valueOf("RED_WOOL_STAIRS"),
                                            Material.valueOf("YELLOW_WOOL_STAIRS")
                                    } : new Material[] {Material.BIRCH_STAIRS};
    //Cocoa's blockdata also implements Directional and Ageable, so use that as a substitute
    public static Material SHELF_MUSHROOM = Version.VERSION.isAtLeast(Version.v26_3) ?
                                       Material.valueOf("SHELF_MUSHROOM") : Material.COCOA;
    public static Biome DAPPLED_FOREST = Version.VERSION.isAtLeast(Version.v26_3) ?
                                       Biome.valueOf("DAPPLED_FOREST") : Biome.BIRCH_FOREST;
    public static EntityType CUSHION = Version.VERSION.isAtLeast(Version.v26_3) ? EntityType.valueOf("CUSHION") : EntityType.SNOWBALL;
}
