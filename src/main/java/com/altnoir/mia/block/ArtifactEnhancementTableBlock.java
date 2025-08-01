package com.altnoir.mia.block;

import com.altnoir.mia.inventory.ArtifactEnhancementTableMenu;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class ArtifactEnhancementTableBlock extends CraftingTableBlock {
    public static final MapCodec<ArtifactEnhancementTableBlock> CODEC = simpleCodec(ArtifactEnhancementTableBlock::new);

    private static final Component CONTAINER_TITLE = Component
            .translatable("containier.mia.artifact.enhancement.title");

    public MapCodec<ArtifactEnhancementTableBlock> codec() {
        return CODEC;
    }

    public ArtifactEnhancementTableBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        return new SimpleMenuProvider((containerId, playerInventory, player) -> {
            return new ArtifactEnhancementTableMenu(containerId, playerInventory,
                    ContainerLevelAccess.create(level, pos));
        }, CONTAINER_TITLE);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hitResult) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        } else {
            player.openMenu(state.getMenuProvider(level, pos));
            player.awardStat(Stats.INTERACT_WITH_SMITHING_TABLE);
            return InteractionResult.CONSUME;
        }
    }

}