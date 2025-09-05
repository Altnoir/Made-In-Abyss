package com.altnoir.mia.block.entity.renderer;

import com.altnoir.mia.block.EndlessCupBlock;
import com.altnoir.mia.block.entity.EndlessCupBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;

public class EndlessCupBlockRenderer implements BlockEntityRenderer<EndlessCupBlockEntity> {
    public EndlessCupBlockRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(EndlessCupBlockEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (blockEntity.getBlockState().getValue(EndlessCupBlock.WATERLOGGED)) return;
        // 获取水的渲染属性
        IClientFluidTypeExtensions water = IClientFluidTypeExtensions.of(Fluids.WATER);
        ResourceLocation stillTexture = water.getStillTexture();
        TextureAtlasSprite sprite = net.minecraft.client.Minecraft.getInstance()
                .getTextureAtlas(TextureAtlas.LOCATION_BLOCKS)
                .apply(stillTexture);

        float u0 = sprite.getU0();
        float u1 = sprite.getU1();
        float v0 = sprite.getV0();
        float v1 = sprite.getV1();

        // 获取群系颜色
        BlockPos blockPos = blockEntity.getBlockPos();
        LevelReader levelReader = blockEntity.getLevel();
        int biomeColor = BiomeColors.getAverageWaterColor(levelReader, blockPos);

        float red = ((biomeColor >> 16) & 0xFF) / 255.0F;
        float green = ((biomeColor >> 8) & 0xFF) / 255.0F;
        float blue = (biomeColor & 0xFF) / 255.0F;

        VertexConsumer buffer = bufferSource.getBuffer(RenderType.translucent());

        poseStack.pushPose();
        poseStack.translate(0.0F, 1.00F, 0.0F);

        float size1 = 0.85F;
        float size2 = 1.0F - size1;

        buffer.addVertex(poseStack.last().pose(), size2, -0.11F, size1)
                .setColor(red, green, blue, 1.0F)
                .setUv(u0, v1)
                .setLight(packedLight)
                .setOverlay(packedOverlay)
                .setNormal(0.0F, 1.0F, 0.0F);

        buffer.addVertex(poseStack.last().pose(), size1, -0.11F, size1)
                .setColor(red, green, blue, 1.0F)
                .setUv(u1, v1)
                .setLight(packedLight)
                .setOverlay(packedOverlay)
                .setNormal(0.0F, 1.0F, 0.0F);

        buffer.addVertex(poseStack.last().pose(), size1, -0.11F, size2)
                .setColor(red, green, blue, 1.0F)
                .setUv(u1, v0)
                .setLight(packedLight)
                .setOverlay(packedOverlay)
                .setNormal(0.0F, 1.0F, 0.0F);

        buffer.addVertex(poseStack.last().pose(), size2, -0.11F, size2)
                .setColor(red, green, blue, 1.0F)
                .setUv(u0, v0)
                .setLight(packedLight)
                .setOverlay(packedOverlay)
                .setNormal(0.0F, 1.0F, 0.0F);

        poseStack.popPose();
    }
}
