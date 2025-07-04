package com.altnoir.mia.init;

import com.altnoir.mia.MIA;
import com.altnoir.mia.block.*;
import com.altnoir.mia.worldgen.tree.MiaTreeGrowers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;
import java.util.function.ToIntFunction;

public class MiaBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MIA.MOD_ID);

    public static final DeferredBlock<Block> ABYSS_ANDESITE = registerBlock("abyss_andesite", () ->
            new AbyssAndesiteBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .instrument(NoteBlockInstrument.BASEDRUM)
                    .requiresCorrectToolForDrops()
                    .strength(3.0F, 6.0F)
                    .sound(SoundType.DEEPSLATE)
            )
    );
    public static final DeferredBlock<Block> ABYSS_COBBLED_ANDESITE = registerBlock("abyss_cobbled_andesite", () ->
            new Block(BlockBehaviour.Properties.ofFullCopy(ABYSS_ANDESITE.get())
                    .strength(3.5F, 6.0F)
            )
    );
    public static final DeferredBlock<Block> ABYSS_GRASS_BLOCK = registerBlock("abyss_grass_block", () ->
            new AbyssGrassBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.GRASS)
                    .randomTicks()
                    .strength(0.6F)
                    .sound(SoundType.GRASS)
            )
    );
    public static final DeferredBlock<Block> COVERGRASS_ABYSS_ANDESITE = registerBlock("covergrass_abyss_andesite", () ->
            new CoverGrassBlock(ABYSS_ANDESITE.get(), BlockBehaviour.Properties.of()
                    .mapColor(MapColor.GRASS)
                    .randomTicks()
                    .instrument(NoteBlockInstrument.BASEDRUM)
                    .requiresCorrectToolForDrops()
                    .strength(3.0F, 6.0F)
                    .sound(SoundType.DEEPSLATE)
            )
    );

    public static final DeferredBlock<Block> SKYFOG_LOG = registerBlock("skyfog_log", () ->
            log(MapColor.WOOD, MapColor.PODZOL)
    );
    public static final DeferredBlock<Block> SKYFOG_WOOD = registerBlock("skyfog_wood", () ->
            wood(MapColor.WOOD)
    );
    public static final DeferredBlock<Block> STRIPPED_SKYFOG_LOG = registerBlock("stripped_skyfog_log", () ->
            log(MapColor.WOOD, MapColor.WOOD)
    );
    public static final DeferredBlock<Block> STRIPPED_SKYFOG_WOOD = registerBlock("stripped_skyfog_wood", () ->
            wood(MapColor.WOOD)
    );
    public static final DeferredBlock<Block> SKYFOG_PLANKS = registerBlock("skyfog_planks", () ->
            planks(MapColor.COLOR_GREEN)
    );
    public static final DeferredBlock<Block> SKYFOG_LEAVES = registerBlock("skyfog_leaves", () ->
            leaves(SoundType.CHERRY_LEAVES)
    );
    public static final DeferredBlock<Block> SKYFOG_SAPLING = registerBlock("skyfog_sapling", () ->
            new SaplingBlock(
                    MiaTreeGrowers.SKYFOG_TREE,
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.PLANT)
                            .noCollission()
                            .randomTicks()
                            .instabreak()
                            .sound(SoundType.CHERRY_SAPLING)
                            .pushReaction(PushReaction.DESTROY)
            )
    );

    public static final DeferredBlock<Block> FORTITUDE_FLOWER = registerBlock("fortitude_flower", () ->
            new FlowerBlock(
                    MobEffects.WEAKNESS,
                    9.0F,
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.PLANT)
                            .noCollission()
                            .instabreak()
                            .sound(SoundType.GRASS)
                            .offsetType(BlockBehaviour.OffsetType.XZ)
                            .pushReaction(PushReaction.DESTROY)
            )
    );
    public static final DeferredBlock<Block> LAMP_TUBE = registerBlock("lamp_tube", () ->
            new LampTubeBlock(
                    BlockBehaviour.Properties.of()
                            .forceSolidOff()
                            .instabreak()
                            .lightLevel(litBlockEmission(BlockStateProperties.POWERED, 10))
                            .sound(SoundType.AMETHYST)
                            .noOcclusion()
            )
    );

    public static final DeferredBlock<Block> ABYSS_PORTAL = registerBlock("abyss_portal", () ->
            new AbyssPortalBlock(
                    BlockBehaviour.Properties.of()
                            .noCollission()
                            .strength(-1.0F)
                            .lightLevel(state -> 15)
                            .sound(SoundType.GLASS)
                            .noLootTable()
                            .pushReaction(PushReaction.BLOCK)
            )
    );

    public static final DeferredBlock<Block> ABYSS_SPAWNER = registerBlock("abyss_spawner", () ->
            new AbyssSpawnerBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.STONE)
                            .instrument(NoteBlockInstrument.BASEDRUM)
                            .lightLevel(state -> state.getValue(AbyssSpawnerBlock.STATE).lightLevel())
                            .strength(50.0F)
                            .sound(SoundType.TRIAL_SPAWNER)
                            .isViewBlocking(MiaBlocks::never)
                            .noOcclusion()
            )
    );
    public static final DeferredBlock<Block> ENDLESS_CUP = registerBlock("endless_cup", () ->
            new EndlessCupBlock(
                    BlockBehaviour.Properties.ofFullCopy(ABYSS_ANDESITE.get())
            )
    );

    private static boolean never(BlockState state, BlockGetter blockGetter, BlockPos pos) {
        return false;
    }

    private static Block log(MapColor topMapColor, MapColor sideMapColor) {
        return new MiaWoodBlock(
                BlockBehaviour.Properties.of()
                        .mapColor(state -> state.getValue(RotatedPillarBlock.AXIS) == Direction.Axis.Y ? topMapColor : sideMapColor)
                        .instrument(NoteBlockInstrument.BASS)
                        .strength(2.0F)
                        .sound(SoundType.WOOD)
                        .ignitedByLava()
        );
    }

    private static Block wood(MapColor mapColor) {
        return new MiaWoodBlock(
                BlockBehaviour.Properties.of()
                        .mapColor(mapColor)
                        .instrument(NoteBlockInstrument.BASS)
                        .strength(2.0F)
                        .sound(SoundType.WOOD)
                        .ignitedByLava()
        );
    }

    private static Block planks(MapColor mapColor) {
        return new MiaPlankBlock(
                BlockBehaviour.Properties.of()
                        .mapColor(mapColor)
                        .instrument(NoteBlockInstrument.BASS)
                        .strength(2.0F, 3.0F)
                        .sound(SoundType.WOOD)
                        .ignitedByLava()
        );
    }

    private static Block leaves(SoundType soundType) {
        return new MiaLeavesBlock(
                BlockBehaviour.Properties.of()
                        .mapColor(MapColor.PLANT)
                        .strength(0.2F)
                        .randomTicks()
                        .sound(soundType)
                        .noOcclusion()
                        .isValidSpawn(Blocks::ocelotOrParrot)
                        .isSuffocating(MiaBlocks::never)
                        .isViewBlocking(MiaBlocks::never)
                        .ignitedByLava()
                        .pushReaction(PushReaction.DESTROY)
                        .isRedstoneConductor(MiaBlocks::never)
        );
    }

    private static ToIntFunction<BlockState> litBlockEmission(BooleanProperty property, int lightValue) {
        return state -> state.getValue(property) ? lightValue : 0;
    }

    private static <T extends Block> DeferredBlock<T> registerBlock(String name, Supplier<T> block) {
        DeferredBlock<T> toReturn = BLOCKS.register(name, block);
        registerBlockItem(name, toReturn);
        return toReturn;
    }

    private static <T extends Block> void registerBlockItem(String name, DeferredBlock<T> block) {
        MiaItems.ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
    }

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}
