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

/**
 * 原版 JukeboxSong 声音的"跟随播放者"版（原版 EntitySoundInstance 固定跟随监听者本人）：
 * <ul>
 *   <li>自己的播放：相对声音、不衰减（与原版行为一致）</li>
 *   <li>别人的播放：跟随播放者实体位置、随距离衰减——支撑原版唱片穿戴播放多人可闻</li>
 * </ul>
 * 其余逻辑（pale garden 音量、静音、淡入淡出、存活检查）与原版 EntitySoundInstance 一致。
 */
@OnlyIn(Dist.CLIENT)
public class CompatJukeboxEntitySound extends AbstractTickableSoundInstance {
    private static final float FADE_IN_SPEED = 1f / (10.0f * 20f);
    private static final float FADE_OUT_SPEED = 1f / (2.5f * 20f);

    private final Player followPlayer;
    private boolean muted;
    private float biomeVolumeMultiplier = 1.0f;

    public CompatJukeboxEntitySound(JukeboxSong song, Player followPlayer, boolean muted) {
        super(song.soundEvent().value(), SoundSource.RECORDS, RandomSource.create());
        this.followPlayer = followPlayer;
        this.looping = true;
        this.delay = 0;
        this.muted = muted;
        this.volume = muted ? 0.0F : 1.0F;
        this.pitch = 1.0F;

        if (followPlayer != null && followPlayer.isLocalPlayer()) {
            // 自己的播放：跟随自己、不衰减
            this.attenuation = Attenuation.NONE;
            this.relative = true;
        } else if (followPlayer != null) {
            // 别人的播放：跟随播放者位置、随距离衰减
            this.attenuation = Attenuation.LINEAR;
            this.relative = false;
            updatePos();
        }
    }

    private void updatePos() {
        this.x = (float) followPlayer.getX();
        this.y = (float) followPlayer.getY();
        this.z = (float) followPlayer.getZ();
    }

    public void setMuted(boolean muted) {
        this.muted = muted;
        this.volume = muted ? 0.0f : 1.0f;
    }

    public void setBiomeVolume(float volume) {
        this.biomeVolumeMultiplier = volume;
        applyVolume();
    }

    private void applyVolume() {
        if (!muted) {
            this.volume = 1.0f * biomeVolumeMultiplier;
        }
    }

    @Override
    public void tick() {
        if (isStopped() || followPlayer == null || !followPlayer.isAlive() || followPlayer.isRemoved()) {
            this.stop();
            return;
        }

        // 别人的播放：跟随播放者位置
        if (!followPlayer.isLocalPlayer()) {
            updatePos();
        }

        Player listener = Minecraft.getInstance().player;
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
