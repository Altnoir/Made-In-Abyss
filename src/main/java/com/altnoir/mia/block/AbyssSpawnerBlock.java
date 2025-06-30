package com.altnoir.mia.block;

import com.altnoir.mia.block.entity.AbyssSpawnerBlockEntity;
import com.altnoir.mia.init.MiaBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.Spawner;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawnerState;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;

import javax.annotation.Nullable;
import java.util.List;

public class AbyssSpawnerBlock extends BaseEntityBlock {
    public static final MapCodec<AbyssSpawnerBlock> CODEC = simpleCodec(AbyssSpawnerBlock::new);
    public static final EnumProperty<TrialSpawnerState> STATE = BlockStateProperties.TRIAL_SPAWNER_STATE;
    public static final BooleanProperty OMINOUS = BlockStateProperties.OMINOUS;

    @Override
    public MapCodec<AbyssSpawnerBlock> codec() {
        return CODEC;
    }

    public AbyssSpawnerBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(STATE, TrialSpawnerState.INACTIVE).setValue(OMINOUS, Boolean.valueOf(false)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> stateBuilder) {
        stateBuilder.add(STATE, OMINOUS);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AbyssSpawnerBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> entityType) {
        return level instanceof ServerLevel serverlevel
                ? createTickerHelper(
                entityType,
                MiaBlockEntities.ABYSS_SPAWNER.get(),
                (blockLevel, blockPos, blockState, e) -> e.getAbyssSpawner()
                        .tickServer(serverlevel, blockPos, blockState.getOptionalValue(BlockStateProperties.OMINOUS).orElse(false))
        )
                : createTickerHelper(
                entityType,
                MiaBlockEntities.ABYSS_SPAWNER.get(),
                (blockLevel, blockPos, blockState, e) -> e.getAbyssSpawner()
                        .tickClient(blockLevel, blockPos, blockState.getOptionalValue(BlockStateProperties.OMINOUS).orElse(false))
        );
    }

    @Override
    public void appendHoverText(ItemStack itemStack, Item.TooltipContext context, List<Component> components, TooltipFlag flag) {
        super.appendHoverText(itemStack, context, components, flag);
        Spawner.appendHoverText(itemStack, components, "spawn_data");
    }
}
