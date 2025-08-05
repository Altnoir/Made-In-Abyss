package com.altnoir.mia.init;

import com.altnoir.mia.MIA;
import com.altnoir.mia.block.entity.AbyssSpawnerBlockEntity;
import com.altnoir.mia.block.entity.EndlessCupBlockEntity;
import com.altnoir.mia.block.entity.PedestalBlockEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class MiaBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, MIA.MOD_ID);

    public static final Supplier<BlockEntityType<PedestalBlockEntity>> PEDESTAL = BLOCK_ENTITY_TYPES.register("pedestal", () ->
            BlockEntityType.Builder.of(
                    PedestalBlockEntity::new, MiaBlocks.PEDESTAL.get()).build(null)
    );
    public static final Supplier<BlockEntityType<AbyssSpawnerBlockEntity>> ABYSS_SPAWNER = BLOCK_ENTITY_TYPES.register("abyss_spawner", () ->
            BlockEntityType.Builder.of(
                    AbyssSpawnerBlockEntity::new, MiaBlocks.ABYSS_SPAWNER.get()).build(null)
    );

    public static final Supplier<BlockEntityType<EndlessCupBlockEntity>> ENDLESS_CUP_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register("endless_cup_block_entity", () ->
            BlockEntityType.Builder.of(
                    EndlessCupBlockEntity::new, MiaBlocks.ENDLESS_CUP.get()).build(null)
    );

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITY_TYPES.register(eventBus);
    }
}
