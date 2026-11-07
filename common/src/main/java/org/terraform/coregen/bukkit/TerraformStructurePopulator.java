package org.terraform.coregen.bukkit;

import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;
import org.terraform.biome.BiomeBank;
import org.terraform.coregen.populatordata.PopulatorDataAbstract;
import org.terraform.coregen.populatordata.PopulatorDataSpigotAPI;
import org.terraform.data.*;
import org.terraform.main.TerraformGeneratorPlugin;
import org.terraform.main.config.TConfig;
import org.terraform.structure.JigsawState;
import org.terraform.structure.JigsawStructurePopulator;
import org.terraform.structure.SingleMegaChunkStructurePopulator;
import org.terraform.structure.StructureRegistry;
import org.terraform.structure.room.CubeRoom;
import org.terraform.structure.room.PathPopulatorData;
import org.terraform.structure.room.RoomLayoutGenerator;
import org.terraform.structure.room.path.PathState;
import org.terraform.structure.stronghold.StrongholdPopulator;
import org.terraform.utils.datastructs.ConcurrentLRUCache;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Random;
import java.util.function.Consumer;

public class TerraformStructurePopulator extends BlockPopulator {

    private final ConcurrentLRUCache<MegaChunk, JigsawState> jigsawCache;
    private final TerraformWorld tw;

    public TerraformStructurePopulator(TerraformWorld tw) {
        this.tw = tw;
        this.jigsawCache = new ConcurrentLRUCache<>("jigsawCache",20, (mc)->{
            BiomeBank biome = mc.getCenterBiomeSection(tw).getBiomeBank();
            SingleMegaChunkStructurePopulator spop = getMegachunkStructure(mc, tw, biome);
            if (spop == null) {
                return null;
            }
            if (!(spop instanceof JigsawStructurePopulator jsp)) {
                return null;
            }
            return jsp.calculateRoomPopulators(tw, mc);
        });
    }

    // NEW BLOCK POPULATOR API
    // Used to generate small paths and small rooms
    @Override
    public void populate(@NotNull WorldInfo worldInfo,
                         @NotNull Random random,
                         int chunkX,
                         int chunkZ,
                         @NotNull LimitedRegion lr)
    {
        // Check if the nearest structure in this megachunk is close enough
        MegaChunk mc = new MegaChunk(chunkX, chunkZ);

        JigsawState state = jigsawCache.get(mc);
        if (state == null) return;
        synchronized (state) {
        // Check if the room will be in range
        if (!state.isInRange(chunkX, chunkZ)) {
            return;
        }

        PopulatorDataAbstract data = new PopulatorDataSpigotAPI(lr, tw, chunkX, chunkZ);

        // Carve each path
        ArrayList<HashSet<PathState.PathNode>> seenNodes = new ArrayList<>();
        for(int i = 0; i < state.roomPopulatorStates.size(); i++)
        {
            RoomLayoutGenerator roomLayoutGenerator = state.roomPopulatorStates.get(i);
            HashSet<PathState.PathNode> nodes = new HashSet<>();
            final PathState pathState = roomLayoutGenerator.getOrCalculatePathState(tw);
            pathState.nodes.stream()
               // Filter to only those inside this chunk
               .filter(node -> node.center.getX() >> 4 == chunkX && node.center.getZ() >> 4 == chunkZ)
               .forEach(node -> {
                   pathState.writer.apply(data, tw, node);
                   nodes.add(node);
               });
            seenNodes.add(nodes);
        }

        // Carve each room
        ArrayList<CubeRoom> seenRooms = new ArrayList<>();
        state.roomPopulatorStates.forEach(roomLayoutGenerator -> roomLayoutGenerator.getRooms()
            .stream()
            .filter(room -> room.canLRCarve(chunkX,chunkZ,lr))
            .forEach(room -> {
                seenRooms.add(room);
                if(roomLayoutGenerator.roomCarver != null)
                    roomLayoutGenerator.roomCarver.carveRoom(
                            data,
                            room,
                            roomLayoutGenerator.wallMaterials
                    );
            }));

        // Populate the paths
        seenNodes.forEach((nodes)->nodes.forEach((node) -> {
            // If the path has a direction of up, it is a crossway.
            if (node.populator != null) {
                node.populator.populate(new PathPopulatorData(new Wall(
                        new SimpleBlock(data, node.center),
                        node.connected.size() == 1 ? node.connected.stream().findAny().get() : BlockFace.UP
                ), node.pathRadius));
            }
        }));

        // Populate the rooms
        seenRooms.stream()
                .filter(room -> room.getPop() != null)
                .forEach(room -> room.getPop().populate(data, room));
        }
    }

    // Large structures are generated asynchronously; PopulatorDataRegion routes each operation to its owner.
    // This callback only selects work and returns without crossing region ownership boundaries.
    @Override
    public void populate(@NotNull World world, @NotNull Random random, @NotNull Chunk chunk) {
        populate(world, random, chunk, ignored -> {});
    }

    public void populate(@NotNull World world,
                         @NotNull Random random,
                         @NotNull Chunk chunk,
                         @NotNull Consumer<PopulatorDataAbstract> afterGeneration) {
        TerraformGeneratorPlugin.watchdogSuppressant.tickWatchdog();
        int chunkX = chunk.getX();
        int chunkZ = chunk.getZ();
        if (!TerraformGeneratorPlugin.INJECTED_WORLDS.contains(world.getName())) {
            return;
        }

        MegaChunk mc = new MegaChunk(chunkX, chunkZ);
        BiomeBank biome = mc.getCenterBiomeSection(tw).getBiomeBank();
        ArrayList<StructureGenerationService.StructureJob> jobs = new ArrayList<>(2);

        StrongholdPopulator stronghold = new StrongholdPopulator();
        if (TConfig.areStructuresEnabled() && stronghold.canSpawn(tw, chunkX, chunkZ, biome)) {
            TerraformGeneratorPlugin.logger.info("Generating Stronghold at chunk: " + chunkX + "," + chunkZ);
            jobs.add(new StructureGenerationService.StructureJob(stronghold, false, 0, 0));
        }

        CoordPair chunkCoords = mc.getCenterBiomeSectionChunkCoords();
        if (chunkCoords.x() == chunkX && chunkCoords.z() == chunkZ) {
            CoordPair blockCoords = mc.getCenterBiomeSectionBlockCoords();
            for (SingleMegaChunkStructurePopulator spop : StructureRegistry.getLargeStructureForMegaChunk(tw, mc)) {
                if (spop == null || !spop.isEnabled() || spop instanceof StrongholdPopulator) {
                    continue;
                }
                if (TConfig.areStructuresEnabled() && spop.canSpawn(tw, chunkX, chunkZ, biome)) {
                    TerraformGeneratorPlugin.logger.info("Generating "
                                                         + spop.getClass().getName()
                                                         + " at chunk: "
                                                         + chunkX
                                                         + ","
                                                         + chunkZ);
                    jobs.add(new StructureGenerationService.StructureJob(spop,
                            true,
                            blockCoords.x(),
                            blockCoords.z()));
                    break;
                }
            }
        }

        StructureGenerationService.submit(world, tw, chunkX, chunkZ, jobs, afterGeneration);
    }


    public @Nullable SingleMegaChunkStructurePopulator getMegachunkStructure(@NotNull MegaChunk mc,
                                                                             @NotNull TerraformWorld tw,
                                                                             BiomeBank biome)
    {
        CoordPair chunkCoords = mc.getCenterBiomeSectionChunkCoords();
        for (SingleMegaChunkStructurePopulator spop : StructureRegistry.getLargeStructureForMegaChunk(tw, mc)) {
            if (spop == null) {
                continue;
            }
            if (!spop.isEnabled()) {
                continue;
            }
            if (spop instanceof StrongholdPopulator) {
                continue;
            }
            // TerraformGeneratorPlugin.logger.info("[v]       MC(" + mc.getX() + "," + mc.getZ() + ") - Checking " + spop.getClass().getName());
            if (TConfig.areStructuresEnabled() && spop.canSpawn(tw, chunkCoords.x(), chunkCoords.z(), biome)) {
                return spop;
            }
        }
        return null;
    }

}
