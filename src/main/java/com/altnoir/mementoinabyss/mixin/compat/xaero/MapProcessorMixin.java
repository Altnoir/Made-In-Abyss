package com.altnoir.mementoinabyss.mixin.compat.xaero;

import com.altnoir.mementoinabyss.impl.vertical.client.VerticalResidentWorlds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "xaero.map.MapProcessor", remap = false)
abstract class MapProcessorMixin {
    @Shadow @Final private Object loadingSync;
    @Shadow private boolean isLoading;
    @Shadow public @Nullable ClientLevel mainWorld;

    @Inject(method = "setMainValues", at = @At("HEAD"), cancellable = true)
    private void mia$deferResidentMapSwitch(CallbackInfo ci) {
        var level = Minecraft.getInstance().level;
        if (level == null
                || mainWorld == null
                || level == mainWorld
                || !VerticalResidentWorlds.owns(level)
                || !VerticalResidentWorlds.owns(mainWorld)) return;
        synchronized (loadingSync) {
            // Keep all old map state together; the next frame retries after its loader finishes.
            if (isLoading) ci.cancel();
        }
    }
}
