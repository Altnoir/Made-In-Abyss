package com.altnoir.mementoinabyss.mixin.accessor;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Player.class)
public interface VerticalPlayerAnimationAccessor {
    @Accessor("lastItemInMainHand")
    ItemStack mia$getLastItemInMainHand();

    @Accessor("lastItemInMainHand")
    void mia$setLastItemInMainHand(ItemStack stack);
}
