package com.xm2nd.netmusiccreatestoragecompat.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 装置播放声音（fxntstorage 内部类 EntityContraptionSoundInstance）可闻距离 16 → 32 格。
 * <p>
 * 原版衰减公式：可闻距离 = max(1, 音量) × 16。该类音量基准为 1.0（16 格），改为 2.0（32 格）：
 * <ul>
 *   <li>构造器：biomeVolumeMultiplier 基准与初始音量置 2.0（muted 时保持 0）</li>
 *   <li>tick：pale garden 淡入淡出的 target 同步为 2.0，否则渐变会把音量拉回 1.0</li>
 * </ul>
 * 该类的 setMuted/applyVolume 直接恢复 biomeVolumeMultiplier（已被置 2.0），无需额外注入。
 */
@Mixin(targets = "net.fxnt.fxntstorage.backpack.upgrade.jukebox.ClientJukeboxHandler$EntityContraptionSoundInstance")
public abstract class StorageContraptionSoundRangeMixin {

    @Shadow
    private boolean muted;

    @Shadow
    private float biomeVolumeMultiplier;

    @Shadow
    protected float volume;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void netmusic_create_storage_compat$initVolume(CallbackInfo ci) {
        this.biomeVolumeMultiplier = 2.0F;
        if (!this.muted) {
            this.volume = 2.0F;
        }
    }

    @ModifyConstant(method = "tick", constant = @Constant(floatValue = 1.0F))
    private static float netmusic_create_storage_compat$tickTarget(float original) {
        return 2.0F;
    }
}
