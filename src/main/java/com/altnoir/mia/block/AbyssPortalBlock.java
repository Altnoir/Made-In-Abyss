package com.altnoir.mia.block;

import com.altnoir.mia.init.MiaSounds;
import com.altnoir.mia.worldgen.dimension.MiaDimensions;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Portal;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class AbyssPortalBlock extends Block implements Portal {
    public static final MapCodec<AbyssPortalBlock> CODEC = simpleCodec(AbyssPortalBlock::new);
    protected static final VoxelShape SHAPE = Block.box(0.0, 1.0, 0.0, 16.0, 15.0, 16.0);

    public AbyssPortalBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<AbyssPortalBlock> codec() {
        return CODEC;
    }


    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }


    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (entity.canUsePortal(false)
                && Shapes.joinIsNotEmpty(
                Shapes.create(entity.getBoundingBox().move((double) (-pos.getX()), (double) (-pos.getY()), (double) (-pos.getZ()))),
                state.getShape(level, pos),
                BooleanOp.AND
        )) {
//            if (!level.isClientSide && level.dimension() == Level.OVERWORLD && entity instanceof ServerPlayer serverplayer && !serverplayer.seenCredits) {
//                serverplayer.showEndCredits(); // 片尾字幕
//                return;
//            }

            entity.setAsInsidePortal(this, pos);
        }
    }

    @Override
    public @Nullable DimensionTransition getPortalDestination(ServerLevel level, Entity entity, BlockPos pos) {
        ResourceKey<Level> resourcekey = level.dimension() == MiaDimensions.ABYSS_BRINK_LEVEL ? Level.OVERWORLD : MiaDimensions.ABYSS_BRINK_LEVEL;
        ServerLevel serverlevel = level.getServer().getLevel(resourcekey);
        if (serverlevel == null) {
            return null;
        } else {
            boolean flag = resourcekey == MiaDimensions.ABYSS_BRINK_LEVEL;
            BlockPos blockpos = flag ? new BlockPos(0, 301, 633) : serverlevel.getSharedSpawnPos();
            Vec3 vec3 = blockpos.getBottomCenter();
            float f = entity.getYRot();
            if (flag) {
                //AbyssPortalFeature.createAbyssBrinkPlatform(serverlevel, BlockPos.containing(vec3).below(), true);
                BlockPos suitablePos = findSuitablePositionAndCreatePlatform(serverlevel, blockpos);
                vec3 = suitablePos.getBottomCenter();
                f = Direction.WEST.toYRot();
                if (entity instanceof ServerPlayer) {
                    vec3 = vec3.subtract(0.0, 1.0, 0.0);
                }
            } else {
                if (entity instanceof ServerPlayer serverplayer) {
                    return serverplayer.findRespawnPositionAndUseSpawnBlock(false, DimensionTransition.DO_NOTHING);
                }

                vec3 = entity.adjustSpawnLocation(serverlevel, blockpos).getBottomCenter();
            }

            DimensionTransition.PostDimensionTransition PORTAL_SOUND = playerEntity -> {
                if (playerEntity instanceof ServerPlayer serverplayer) {
                    serverplayer.playNotifySound(
                            MiaSounds.ABYSS_PORTAL_TRAVEL.get(),
                            SoundSource.BLOCKS,
                            1.0F,
                            1.0F
                    );
                }
            };

            return new DimensionTransition(
                    serverlevel,
                    vec3,
                    entity.getDeltaMovement(),
                    f,
                    entity.getXRot(),
                    PORTAL_SOUND.then(DimensionTransition.PLACE_PORTAL_TICKET)
            );
        }
    }

    /**
     * 从指定高度开始向下搜索合适的位置
     *
     * @param level     目标世界
     * @param centerPos 中心位置
     * @return 适合站立的位置
     */
    private BlockPos findSuitablePositionAndCreatePlatform(ServerLevel level, BlockPos centerPos) {
        int endY = Math.max(250, level.getMinBuildHeight());

        // 扩大搜索范围
        for (int dxOffset = -8; dxOffset <= 8; dxOffset++) {
            for (int dzOffset = -8; dzOffset <= 8; dzOffset++) {
                BlockPos searchCenter = centerPos.offset(dxOffset * 8, 0, dzOffset * 8);

                // 向下搜索合适的位置
                for (int y = 350; y >= endY; y--) {
                    BlockPos belowPos = new BlockPos(searchCenter.getX(), y, searchCenter.getZ());
                    BlockPos feetPos = belowPos.above(1);
                    BlockPos headPos = belowPos.above(2);

                    boolean hasSpace = level.getBlockState(belowPos).isAir() && level.getBlockState(feetPos).isAir() && level.getBlockState(headPos).isAir();
                    boolean hasSupport = !level.getBlockState(belowPos.below()).isAir();

                    if (hasSpace && hasSupport) {
                        return feetPos;
                    }
                }
            }
        }
        return centerPos;
    }


    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(100) == 0) {
            float[] pitchValues = {0.4F, 0.6F, 1.0F, 1.2F, 1.5F};
            float pitch = pitchValues[random.nextInt(pitchValues.length)];

            level.playLocalSound(
                    (double) pos.getX() + 0.5,
                    (double) pos.getY() + 0.5,
                    (double) pos.getZ() + 0.5,
                    MiaSounds.ABYSS_PORTAL_AMBIENT.get(),
                    SoundSource.BLOCKS,
                    0.5F,
                    pitch,
                    false
            );
        }

        for (int i = 0; i < 4; i++) {
            double d0 = (double) pos.getX() + random.nextDouble();
            double d1 = (double) pos.getY() + random.nextDouble();
            double d2 = (double) pos.getZ() + random.nextDouble();
            double d3 = ((double) random.nextFloat() - 0.5) * 0.5;
            double d4 = ((double) random.nextFloat() - 0.5) * 0.5;
            double d5 = ((double) random.nextFloat() - 0.5) * 0.5;
            int j = random.nextInt(2) * 2 - 1;
            if (!level.getBlockState(pos.west()).is(this) && !level.getBlockState(pos.east()).is(this)) {
                d0 = (double) pos.getX() + 0.5 + 0.25 * (double) j;
                d3 = (double) (random.nextFloat() * 2.0F * (float) j);
            } else {
                d2 = (double) pos.getZ() + 0.5 + 0.25 * (double) j;
                d5 = (double) (random.nextFloat() * 2.0F * (float) j);
            }

            level.addParticle(ParticleTypes.PORTAL, d0, d1, d2, d3, d4, d5);
        }
    }
}
