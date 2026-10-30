package com.altnoir.mementoinabyss.mixin.accessor;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LivingEntity.class)
public interface VerticalLivingAnimationAccessor {
    @Accessor("itemSwapTicker")
    int mia$getItemSwapTicker();

    @Accessor("itemSwapTicker")
    void mia$setItemSwapTicker(int value);

    @Accessor("attackStrengthTicker")
    int mia$getAttackStrengthTicker();

    @Accessor("attackStrengthTicker")
    void mia$setAttackStrengthTicker(int value);
}
