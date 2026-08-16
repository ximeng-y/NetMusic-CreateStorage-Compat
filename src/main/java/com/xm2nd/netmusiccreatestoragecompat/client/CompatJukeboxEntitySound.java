package com.xm2nd.netmusiccreatestoragecompat.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.JukeboxSong;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * 原版 JukeboxSong 声音的"跟随播放者"版（原版 EntitySoundInstance 固定跟随监听者本人）：
 * <ul>
 *   <li>自己的播放：相对声音、不衰减，音量 1.0（与原版行为一致）</li>
 *   <li>别人的播放：按播放者 uuid 每 tick 解析跟随、随距离衰减，音量 2.0 ——
 *       可闻距离 = 音量 × 16 = 32 格；播放者实体未加载（远处/跨维度）时声音照常
 *       创建并保持上次位置，实体加载（走近）后自动续跟淡入</li>
 * </ul>
 * 其余逻辑（pale garden 音量、静音、淡入淡出）与原版 EntitySoundInstance 一致。
 */
@OnlyIn(Dist.CLIENT)
public class CompatJukeboxEntitySound extends AbstractTickableSoundInstance {
    private static final float FADE_IN_SPEED = 1f / (10.0f * 20f);
    private static final float FADE_OUT_SPEED = 1f / (2.5f * 20f);

    /** 构造时的播放者实体引用：仅用于本地玩家判定与初始位置，跟随靠 followUuid 每 tick 解析 */
    private final Player followPlayer;
    /** 播放者 uuid：实体未加载时按 uuid 每 tick 重新解析跟随 */
    private final UUID followUuid;
    private final float baseVolume;
    private boolean muted;
    private float biomeVolumeMultiplier = 1.0f;

    public CompatJukeboxEntitySound(JukeboxSong song, @Nullable Player followPlayer, UUID followUuid, boolean muted) {
        super(song.soundEvent().value(), SoundSource.RECORDS, RandomSource.create());
        this.followPlayer = followPlayer;
        this.followUuid = followUuid;
        this.looping = true;
        this.delay = 0;
        this.muted = muted;
        this.baseVolume = followPlayer != null && followPlayer.isLocalPlayer() ? 1.0F : 2.0F;
        this.volume = muted ? 0.0F : baseVolume;
        this.pitch = 1.0F;

        if (followPlayer != null && followPlayer.isLocalPlayer()) {
            // 自己的播放：跟随自己、不衰减
            this.attenuation = Attenuation.NONE;
            this.relative = true;
        } else {
            // 别人的播放：跟随播放者位置、随距离衰减（音量 2.0 → 32 格可闻）
            this.attenuation = Attenuation.LINEAR;
            this.relative = false;
            if (followPlayer != null) {
                updatePos();
            }
        }
    }

    private void updatePos() {
        this.x = (float) followPlayer.getX();
        this.y = (float) followPlayer.getY();
        this.z = (float) followPlayer.getZ();
    }

    public void setMuted(boolean muted) {
        this.muted = muted;
        this.volume = muted ? 0.0f : baseVolume;
    }

    public boolean isMuted() {
        return muted;
    }

    public void setBiomeVolume(float volume) {
        this.biomeVolumeMultiplier = volume;
        applyVolume();
    }

    private void applyVolume() {
        if (!muted) {
            this.volume = baseVolume * biomeVolumeMultiplier;
        }
    }

    @Override
    public void tick() {
        if (isStopped()) {
            return;
        }

        // 别人的播放：按 uuid 每 tick 解析播放者实体（未加载时保持上次位置，加载后自动续跟）
        Minecraft mc = Minecraft.getInstance();
        if (followUuid != null && !relative) {
            Player owner = mc.level == null ? null : mc.level.getPlayerByUUID(followUuid);
            if (owner != null && !owner.isLocalPlayer()) {
                this.x = (float) owner.getX();
                this.y = (float) owner.getY();
                this.z = (float) owner.getZ();
            }
        }

        Player listener = mc.player;
        boolean inPaleGarden = listener != null && isInPaleGarden(listener.level(), listener.blockPosition());

        if (!muted) {
            float target = inPaleGarden ? 0.0f : 1.0f;

            if (biomeVolumeMultiplier < target) {
                biomeVolumeMultiplier = Math.min(target, biomeVolumeMultiplier + FADE_IN_SPEED);
            } else if (biomeVolumeMultiplier > target) {
                biomeVolumeMultiplier = Math.max(target, biomeVolumeMultiplier - FADE_OUT_SPEED);
            }

            applyVolume();
        }
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    private static boolean isInPaleGarden(Level level, net.minecraft.core.BlockPos pos) {
        return level.getBiome(pos)
                .is(ResourceKey.create(Registries.BIOME,
                        ResourceLocation.withDefaultNamespace("pale_garden")));
    }
}
