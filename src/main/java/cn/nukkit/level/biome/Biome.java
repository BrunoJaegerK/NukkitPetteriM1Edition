package cn.nukkit.level.biome;

import cn.nukkit.block.BlockID;
import cn.nukkit.level.ChunkManager;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.generator.populator.type.Populator;
import cn.nukkit.math.NukkitRandom;
import cn.nukkit.utils.Utils;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
public abstract class Biome implements BlockID {

    public static final Biome[] biomes = new Biome[256];
    public static final List<Biome> unorderedBiomes = new ObjectArrayList<>();
    private static final QuickLookupTable runtimeId2Identifier = new QuickLookupTable();
    private static final Map<String, BiomeClimate> climateByName = new HashMap<>();

    private final ArrayList<Populator> populators = new ArrayList<>();
    private int id;
    private float baseHeight = 0.1f;
    private float heightVariation = 0.3f;

    static {
        JsonObject json = Utils.loadJsonResource("biome_id_map.json").getAsJsonObject();
        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            int biomeId = entry.getValue().getAsInt();
            runtimeId2Identifier.put(biomeId, entry.getKey());
        }

        JsonObject climateDefinitions = Utils.loadJsonResource("stripped_biome_definitions.json").getAsJsonObject();
        for (Map.Entry<String, JsonElement> entry : climateDefinitions.entrySet()) {
            JsonObject climate = entry.getValue().getAsJsonObject();
            climateByName.put(entry.getKey(), new BiomeClimate(
                    climate.get("temperature").getAsFloat(),
                    climate.get("downfall").getAsFloat(),
                    climate.get("rain").getAsBoolean()
            ));
        }
    }

    private static final class BiomeClimate {
        private final float temperature;
        private final float downfall;
        private final boolean precipitation;

        private BiomeClimate(float temperature, float downfall, boolean precipitation) {
            this.temperature = temperature;
            this.downfall = downfall;
            this.precipitation = precipitation;
        }
    }

    private static class QuickLookupTable {

        private String[] array = new String[256];

        private void put(int k, String v) {
            if (k < 0) {
                throw new IllegalArgumentException();
            }

            if (array.length <= k) {
                array = Arrays.copyOf(array, k + 1);
            }

            array[k] = v;
        }

        private String get(int k) {
            if (k < 0 || array == null || k >= array.length) {
                return null;
            }

            return array[k];
        }
    }

    public static String getBiomeNameFromId(int biomeId) {
        return runtimeId2Identifier.get(biomeId);
    }

    private static BiomeClimate getClimate(int biomeId) {
        String biomeName = runtimeId2Identifier.get(biomeId);
        return biomeName == null ? null : climateByName.get(biomeName);
    }

    /**
     * Gets the Bedrock biome temperature at a world height. Temperature is fixed
     * through Y=80 and decreases by 1/800 per block above it. The per-column
     * transition noise used by the client is not represented by this height-only API.
     */
    public static float getTemperatureAt(int biomeId, int y) {
        BiomeClimate climate = getClimate(biomeId);
        float baseTemperature = climate != null
                ? climate.temperature
                : (getBiome(biomeId).isFreezing() ? 0.0f : 0.5f);
        return baseTemperature - Math.max(0, y - 80) / 800.0f;
    }

    /**
     * Gets the biome's precipitation strength (downfall), clamped to Bedrock's [0, 1] range.
     */
    public static float getDownfall(int biomeId) {
        BiomeClimate climate = getClimate(biomeId);
        if (climate == null) {
            Biome biome = getBiome(biomeId);
            return biome.canRain() || biome.isFreezing() ? 0.5f : 0.0f;
        }
        return Math.max(0.0f, Math.min(1.0f, climate.downfall));
    }

    /**
     * Whether this biome permits precipitation at all.
     */
    public static boolean hasPrecipitation(int biomeId) {
        BiomeClimate climate = getClimate(biomeId);
        if (climate != null) {
            return climate.precipitation && getDownfall(biomeId) > 0.0f;
        }
        Biome biome = getBiome(biomeId);
        return biome.canRain() || biome.isFreezing();
    }

    /**
     * Whether snowfall, rather than rain, occurs at the supplied world height.
     */
    public static boolean isSnowingAt(int biomeId, int y) {
        return hasPrecipitation(biomeId) && getTemperatureAt(biomeId, y) <= 0.15f;
    }

    /**
     * Whether rain, rather than snowfall, occurs at the supplied world height.
     */
    public static boolean isRainingAt(int biomeId, int y) {
        return hasPrecipitation(biomeId) && getTemperatureAt(biomeId, y) > 0.15f;
    }

    public static int getBiomeIdOrCorrect(int biomeId) {
        if (runtimeId2Identifier.get(biomeId) == null) {
            return EnumBiome.OCEAN.id;
        }
        return biomeId;
    }

    protected static void register(int id, Biome biome) {
        biome.setId(id);
        biomes[id] = biome;
        unorderedBiomes.add(biome);
    }

    public static Biome getBiome(int id) {
        Biome biome = biomes[id];
        return biome != null ? biome : EnumBiome.OCEAN.biome;
    }

    /**
     * Get Biome by name.
     *
     * @param name Name of biome. Name could contain symbol "_" instead of space
     * @return Biome. Null - when biome was not found
     */
    public static Biome getBiome(String name) {
        for (Biome biome : biomes) {
            if (biome != null) {
                if (biome.getName().equalsIgnoreCase(name.replace("_", " "))) return biome;
            }
        }
        return null;
    }

    public void clearPopulators() {
        this.populators.clear();
    }

    public void addPopulator(Populator populator) {
        this.populators.add(populator);
    }

    public void populateChunk(ChunkManager level, int chunkX, int chunkZ, NukkitRandom random) {
        FullChunk chunk = level.getChunk(chunkX, chunkZ);
        for (Populator populator : populators) {
            populator.populate(level, chunkX, chunkZ, random, chunk);
        }
    }

    public ArrayList<Populator> getPopulators() {
        return populators;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public abstract String getName();

    public void setBaseHeight(float baseHeight) {
        this.baseHeight = baseHeight;
    }

    public void setHeightVariation(float heightVariation) {
        this.heightVariation = heightVariation;
    }

    public float getBaseHeight() {
        return baseHeight;
    }

    public float getHeightVariation() {
        return heightVariation;
    }

    @Override
    public int hashCode() {
        return id;
    }

    @Override
    public boolean equals(Object obj) {
        return hashCode() == obj.hashCode();
    }

    /**
     * Whether or not water should freeze into ice on generation
     *
     * @return overhang
     */
    public boolean isFreezing() {
        return false;
    }

    /**
     * Whether or not overhangs should generate in this biome (places where solid blocks generate over air)
     * <p>
     * This should probably be used with a custom max elevation or things can look stupid
     *
     * @return overhang
     */
    public boolean doesOverhang() {
        return false;
    }

    /**
     * How much offset should be added to the min/max heights at this position
     *
     * @param x x
     * @param z z
     * @return height offset
     */
    public int getHeightOffset(int x, int z) {
        return 0;
    }

    public boolean canRain() {
        return true;
    }
}
