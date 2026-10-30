package com.altnoir.mementoinabyss.impl.vertical.client;

import com.altnoir.mementoinabyss.impl.vertical.network.VerticalSectionPayload;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

final class VerticalSectionView implements CollisionGetter {
    private final Map<VerticalClientSections.Key, VerticalSectionPayload> sections;
    private final VerticalSectionPayload anchor;
    private final VerticalSectionPayload[] neighbours = new VerticalSectionPayload[27];

    VerticalSectionView(
            Map<VerticalClientSections.Key, VerticalSectionPayload> sections,
            VerticalSectionPayload anchor) {
        this.sections = sections;
        this.anchor = anchor;
        for (int y = -1; y <= 1; y++)
            for (int z = -1; z <= 1; z++)
                for (int x = -1; x <= 1; x++)
                    neighbours[(y + 1) * 9 + (z + 1) * 3 + x + 1] =
                            sections.get(
                                    new VerticalClientSections.Key(
                                            anchor.source(),
                                            anchor.chunkX() + x,
                                            anchor.sectionY() + y,
                                            anchor.chunkZ() + z));
    }

    private VerticalSectionPayload section(BlockPos pos) {
        int x = Math.floorDiv(pos.getX(), 16) - anchor.chunkX();
        int y = Math.floorDiv(pos.getY(), 16) - anchor.sectionY();
        int z = Math.floorDiv(pos.getZ(), 16) - anchor.chunkZ();
        if (Math.abs(x) <= 1 && Math.abs(y) <= 1 && Math.abs(z) <= 1)
            return neighbours[(y + 1) * 9 + (z + 1) * 3 + x + 1];
        return sections.get(
                new VerticalClientSections.Key(
                        anchor.source(),
                        Math.floorDiv(pos.getX(), 16),
                        Math.floorDiv(pos.getY(), 16),
                        Math.floorDiv(pos.getZ(), 16)));
    }

    private static int index(BlockPos pos) {
        return (pos.getY() & 15) << 8 | (pos.getZ() & 15) << 4 | (pos.getX() & 15);
    }

    @Override
    public WorldBorder getWorldBorder() {
        return Minecraft.getInstance().level.getWorldBorder();
    }

    @Override
    public BlockGetter getChunkForCollisions(int chunkX, int chunkZ) {
        return this;
    }

    @Override
    public List<VoxelShape> getEntityCollisions(Entity source, AABB testArea) {
        return List.of();
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        var s = section(pos);
        return s == null
                ? Blocks.AIR.defaultBlockState()
                : Block.stateById(s.palette()[Short.toUnsignedInt(s.cells()[index(pos)])]);
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return getBlockState(pos).getFluidState();
    }

    @Override
    public BlockEntity getBlockEntity(BlockPos pos) {
        return null;
    }

    @Override
    public int getHeight() {
        return anchor.height();
    }

    @Override
    public int getMinY() {
        return anchor.minY();
    }
}
