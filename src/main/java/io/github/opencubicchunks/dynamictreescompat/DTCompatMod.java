package io.github.opencubicchunks.dynamictreescompat;

import com.ferreusveritas.dynamictrees.ModConfigs;
import com.ferreusveritas.dynamictrees.api.TreeHelper;
import com.ferreusveritas.dynamictrees.api.WorldGenRegistry;
import com.ferreusveritas.dynamictrees.api.worldgen.IGroundFinder;
import com.ferreusveritas.dynamictrees.util.SafeChunkBounds;
import com.ferreusveritas.dynamictrees.worldgen.BiomeDataBase;
import com.ferreusveritas.dynamictrees.worldgen.BiomeDataBase.BiomeEntry;
import com.ferreusveritas.dynamictrees.worldgen.TreeGenerator;
import io.github.opencubicchunks.cubicchunks.api.util.Coords;
import io.github.opencubicchunks.cubicchunks.api.util.CubePos;
import io.github.opencubicchunks.cubicchunks.api.world.ICubicWorld;
import io.github.opencubicchunks.cubicchunks.api.worldgen.CubeGeneratorsRegistry;
import io.github.opencubicchunks.cubicchunks.api.worldgen.populator.ICubicPopulator;
import io.github.opencubicchunks.cubicchunks.api.worldgen.populator.event.DecorateCubeBiomeEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.Random;

@Mod(modid = DTCompatMod.MODID, version = DTCompatMod.VERSION, name = DTCompatMod.NAME,
        dependencies = "required:cubicchunks@[0.0.899.0,);required-after:dynamictrees@[0.9.5,)"
)
public class DTCompatMod {

    public static final String MODID = "dynamictreees-cubicaddon";
    public static final String NAME = "CubicChunks DynamicTrees Addon";
    private static final boolean IS_DEV = true; // changed automatically by gradle, don't touch
    public static final String VERSION = IS_DEV ? "1.12.2-9.9.9" : "@VERSION@";

    @Mod.EventHandler public void preInit(FMLPreInitializationEvent event) {
        if (WorldGenRegistry.isWorldGenEnabled()) {
            CubeGeneratorsRegistry.register(new CubicGeneratorTrees(), 20);
            MinecraftForge.TERRAIN_GEN_BUS.register(this);
        }
    }

    @SubscribeEvent(priority = EventPriority.NORMAL, receiveCanceled = true)
    public void onDecorateCubeBiome(DecorateCubeBiomeEvent.Decorate event) {
        int dimensionId = event.getWorld().provider.getDimension();
        BiomeDataBase dbase = TreeGenerator.getTreeGenerator().getBiomeDataBase(dimensionId);
        if (dbase != TreeGenerator.DIMENSIONBLACKLISTED && !ModConfigs.dimensionBlacklist.contains(dimensionId)) {
            Biome biome = event.getWorld().getBiome(event.getCubePos().getCenterBlockPos());
            switch (event.getType()) {
                case CACTUS:
                    if (ModConfigs.vanillaCactusWorldGen) {
                        break;
                    }
                case BIG_SHROOM:
                case TREE:
                    if (dbase.getEntry(biome).shouldCancelVanillaTreeGen()) {
                        event.setResult(Event.Result.DENY);
                    }
                default:
                    break;
            }
        }
    }

    public static class CubicGeneratorTrees implements ICubicPopulator {

        public static class GroundFinder implements IGroundFinder {

            private final int cubeY;

            public GroundFinder(int cubeY) {
                this.cubeY = cubeY;
            }

            @Override
            public BlockPos findGround(BiomeEntry biomeEntry, World world, BlockPos start) {
                BlockPos startWithY = new BlockPos(start.getX(), Coords.cubeToMaxBlock(cubeY + 1), start.getZ());
                BlockPos posNullable = ((ICubicWorld) world).findTopBlock(
                        startWithY, Coords.cubeToCenterBlock(cubeY), Coords.cubeToCenterBlock(cubeY + 1),
                        (pos, state) -> state.getMaterial().blocksMovement() && !TreeHelper.isTreePart(state)
                );
                return posNullable == null ? BlockPos.ORIGIN : posNullable.down();
            }
        }

        @Override
        public void generate(World world, Random random, CubePos pos, Biome biome) {
            TreeGenerator treeGenerator = TreeGenerator.getTreeGenerator();
            BiomeDataBase dbase = treeGenerator.getBiomeDataBase(world);
            if (dbase != TreeGenerator.DIMENSIONBLACKLISTED) {
                SafeChunkBounds safeBounds = new CubicChunkBounds(pos);//Area that is safe to place blocks during worldgen
                treeGenerator.getCircleProvider().getPoissonDiscs(world, pos.getX(), pos.getY(), pos.getZ())
                        .forEach(c -> treeGenerator.makeTree(world, dbase, c, new GroundFinder(pos.getY()), safeBounds));
            }
        }
    }

    public static class CubicChunkBounds extends SafeChunkBounds {

        private final CubePos center;

        public CubicChunkBounds(CubePos center) {
            this.center = center;
        }

        public boolean inBounds(BlockPos pos, boolean gap) {
            // allow cube coordinates between (x, y, z) and (x+1, y+1, z+1)
            int dcx = Coords.blockToCube(pos.getX()) - center.getX();
            int dcy = (Coords.blockToCube(pos.getY()) - center.getY()) >> 1; // give more space for Y
            int dcz = Coords.blockToCube(pos.getZ()) - center.getZ();
            return ((dcx | dcy | dcz) & ~1) == 0;
        }
    }
}
