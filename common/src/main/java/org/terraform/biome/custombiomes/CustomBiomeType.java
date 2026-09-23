package org.terraform.biome.custombiomes;

import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.terraform.utils.version.Version;

import java.util.Locale;

public enum CustomBiomeType {
    NONE,
    MUDDY_BOG("b8ad49", "9c8046", "b8ad49", "d9cd62", "ad8445", "ad8445"),
    CHERRY_GROVE("", "69faff", "", "87fffb", "ffa1fc", "acff96"),
    SCARLET_FOREST("", "", "", "", "fc3103", "ff7700"),
    CRYSTALLINE_CLUSTER("e54fff", "c599ff", "e54fff", "", "", ""),
    ;

    private final @NotNull String key;
    private final String fogColor;
    private final String waterColor;
    private final String waterFogColor;
    private final String skyColor;
    private final String grassColor;
    private String foliageColor;
    //Store both for compat reasons
    private final Vector3fc fogColorVec;
    private final Vector3fc waterColorVec;
    private final Vector3fc waterFogColorVec;

    private final Vector3fc skyColorVec;
    private final Vector3fc grassColorVec;
    private final Vector3fc foliageColorVec;
    private float rainFall = 0.8f;
    private boolean isCold = false;

    CustomBiomeType() {
        this.key = "terraformgenerator:" + this.toString().toLowerCase(Locale.ENGLISH);
        this.fogColor = "";
        this.waterColor = "";
        this.waterFogColor = "";
        this.skyColor = "";
        this.foliageColor = "";
        this.grassColor = "";

        this.fogColorVec = parseHexColor(fogColor);
        this.waterColorVec = parseHexColor(waterColor);
        this.waterFogColorVec = parseHexColor(waterFogColor);
        this.skyColorVec = parseHexColor(skyColor);
        this.foliageColorVec = parseHexColor(foliageColor);
        this.grassColorVec = parseHexColor(grassColor);
    }

    CustomBiomeType(String fogColor,
                    String waterColor,
                    String waterFogColor,
                    String skyColor,
                    String foliageColor,
                    String grassColor)
    {
        this.key = "terraformgenerator:" + this.toString().toLowerCase(Locale.ENGLISH);
        this.fogColor = fogColor;
        this.waterColor = waterColor;
        this.waterFogColor = waterFogColor;
        this.skyColor = skyColor;
        this.foliageColor = foliageColor;
        this.grassColor = grassColor;

        //this.rainFall = 0.8f;
        this.isCold = false;
        // In 1.20, cherry trees no longer need the pink.
        if (Version.VERSION.isAtLeast(Version.v1_20) && this.foliageColor.equals("ffa1fc")) {
            this.foliageColor = "acff96";
        }

        this.fogColorVec = parseHexColor(fogColor);
        this.waterColorVec = parseHexColor(waterColor);
        this.waterFogColorVec = parseHexColor(waterFogColor);
        this.skyColorVec = parseHexColor(skyColor);
        this.foliageColorVec = parseHexColor(foliageColor);
        this.grassColorVec = parseHexColor(grassColor);
    }

    public @NotNull String getKey() {
        return key;
    }

    private Vector3fc parseHexColor(String col){
        try{
            float r = Integer.parseInt(col.substring(0,2), 16)/255f;
            float g = Integer.parseInt(col.substring(2,4), 16)/255f;
            float b = Integer.parseInt(col.substring(4,6), 16)/255f;
            return new Vector3f(r,g,b);
        }catch(Exception e){
            return new Vector3f(0,0,0);
        }
    }

    public Vector3fc getFogColorVec() {
        return fogColorVec;
    }

    public Vector3fc getWaterColorVec() {
        return waterColorVec;
    }

    public Vector3fc getSkyColorVec() {
        return skyColorVec;
    }

    public Vector3fc getWaterFogColorVec() {
        return waterFogColorVec;
    }
    //Grass colour override used by implementation doesn't use the vector.
    // Keep this around for now.
    public Vector3fc getGrassColorVec() {
        return grassColorVec;
    }

    public Vector3fc getFoliageColorVec() {
        return foliageColorVec;
    }
    public String getFogColor() {
        return fogColor;
    }

    public String getWaterColor() {
        return waterColor;
    }

    public String getWaterFogColor() {
        return waterFogColor;
    }

    public String getSkyColor() {
        return skyColor;
    }

    public String getFoliageColor() {
        return foliageColor;
    }

    public String getGrassColor() {
        return grassColor;
    }

    public float getRainFall() {
        return rainFall;
    }

    public boolean isCold() {
        return isCold;
    }
}