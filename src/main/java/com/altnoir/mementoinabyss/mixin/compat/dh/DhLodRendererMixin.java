package com.altnoir.mementoinabyss.mixin.compat.dh;

import com.altnoir.mementoinabyss.compat.dh.DhCrossDimensionLod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.seibel.distanthorizons.core.render.renderer.LodRenderer", remap = false)
abstract class DhLodRendererMixin {
    @Inject(
            method =
                    "renderTerrain(Lcom/seibel/distanthorizons/core/wrapperInterfaces/render/renderPass/IDhTerrainRenderer;Lcom/seibel/distanthorizons/core/render/RenderBufferHandler;Lcom/seibel/distanthorizons/core/render/RenderParams;ZLcom/seibel/distanthorizons/core/wrapperInterfaces/minecraft/IProfilerWrapper;)V",
            at = @At("TAIL"),
            require = 0)
    private void mia$renderNeighbor(
            @Coerce Object renderer,
            @Coerce Object buffers,
            @Coerce Object params,
            boolean opaque,
            @Coerce Object profiler,
            CallbackInfo ci) {
        if (opaque) DhCrossDimensionLod.render(renderer, params, true, profiler);
    }

    @Inject(
            method =
                    "renderTerrain(Lcom/seibel/distanthorizons/core/wrapperInterfaces/render/renderPass/IDhTerrainRenderer;Lcom/seibel/distanthorizons/core/render/RenderBufferHandler;Lcom/seibel/distanthorizons/core/render/RenderParams;ZLcom/seibel/distanthorizons/core/wrapperInterfaces/minecraft/IProfilerWrapper;)V",
            at = @At("HEAD"),
            require = 0)
    private void mia$renderNeighborTranslucent(
            @Coerce Object renderer,
            @Coerce Object buffers,
            @Coerce Object params,
            boolean opaque,
            @Coerce Object profiler,
            CallbackInfo ci) {
        if (!opaque) DhCrossDimensionLod.render(renderer, params, false, profiler);
    }

    @Inject(
            method =
                    "renderTerrain(Lcom/seibel/distanthorizons/core/render/RenderParams;Lcom/seibel/distanthorizons/core/wrapperInterfaces/minecraft/IProfilerWrapper;Z)V",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/seibel/distanthorizons/core/wrapperInterfaces/render/renderPass/IDhMetaRenderer;copyToMcTexture(Lcom/seibel/distanthorizons/core/render/RenderParams;)V",
                            shift = At.Shift.AFTER),
            require = 0)
    private void mia$confirmComposite(CallbackInfo ci) {
        DhCrossDimensionLod.composited();
    }
}
