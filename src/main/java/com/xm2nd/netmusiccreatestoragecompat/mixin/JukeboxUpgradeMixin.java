package com.xm2nd.netmusiccreatestoragecompat.mixin;

import com.xm2nd.netmusiccreatestoragecompat.NetMusicDiscHelper;
import net.fxnt.fxntstorage.backpack.upgrade.jukebox.JukeboxUpgrade;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 升级生命周期中的唱片判定扩展：
 * <ul>
 *   <li>onRemoved：移除唱片机升级时，原逻辑只把带 jukebox_playable 组件的唱片移回玩家背包，
 *        NetMusic 唱片会被卡在槽里，此处一并放行</li>
 *   <li>onQuickMove：快捷移动（Shift 点击）时允许把 NetMusic 唱片移入唱片槽</li>
 * </ul>
 */
@Mixin(JukeboxUpgrade.class)
public abstract class JukeboxUpgradeMixin {

    @Redirect(method = "onRemoved",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;has(Lnet/minecraft/core/component/DataComponentType;)Z"))
    private boolean netmusic_create_storage_compat$onRemovedCheckDisc(ItemStack stack, DataComponentType<?> type) {
        return stack.has(type) || NetMusicDiscHelper.isNetMusicDisc(stack);
    }

    @Redirect(method = "onQuickMove",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;has(Lnet/minecraft/core/component/DataComponentType;)Z"))
    private boolean netmusic_create_storage_compat$onQuickMoveCheckDisc(ItemStack stack, DataComponentType<?> type) {
        return stack.has(type) || NetMusicDiscHelper.isNetMusicDisc(stack);
    }
}
