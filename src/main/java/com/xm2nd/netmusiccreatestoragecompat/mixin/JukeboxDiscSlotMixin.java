package com.xm2nd.netmusiccreatestoragecompat.mixin;

import com.xm2nd.netmusiccreatestoragecompat.NetMusicDiscHelper;
import net.fxnt.fxntstorage.backpack.client.menu.slot.JukeboxDiscSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 放行 NetMusic 唱片进入唱片机升级槽位。
 * 原逻辑 {@code mayPlace} 只接受带 {@code jukebox_playable} 组件的原版唱片，
 * NetMusic 唱片只有自定义 song_info 组件，此处额外放行。
 */
@Mixin(JukeboxDiscSlot.class)
public abstract class JukeboxDiscSlotMixin {

    @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
    private void netmusic_create_storage_compat$allowNetMusicDisc(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (NetMusicDiscHelper.isNetMusicDisc(stack)) {
            cir.setReturnValue(true);
        }
    }
}
