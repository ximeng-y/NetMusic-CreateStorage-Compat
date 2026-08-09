package com.xm2nd.netmusiccreatestoragecompat.client;

import com.github.tartaricacid.netmusic.compat.sbackpack.NetMusicBackpackSound;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.net.URL;

/**
 * 兼容播放声音：复用 NetMusic 的流式实现（NetMusicBackpackSound，从 URL 拉流，
 * 不依赖 TileEntityMusicPlayer 方块），仅补充：
 * <ul>
 *   <li>公开构造器（原类构造器为 protected）</li>
 *   <li>静音支持（音量 0，与 fxntstorage 原版 muted 语义一致）</li>
 *   <li>歌曲自然播完后的回调（通知客户端清理状态并告知服务端）</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
public class CompatNetMusicSound extends NetMusicBackpackSound {

    private final Runnable onFinished;
    private final float baseVolume;
    private boolean finishedNotified = false;
    private boolean muted = false;

    public CompatNetMusicSound(BlockPos pos, @Nullable Entity entity, URL url, int timeSecond, Runnable onFinished) {
        super(pos, entity, url, timeSecond);
        this.onFinished = onFinished;
        // 统一 32 格可闻距离：原版衰减公式 可闻距离 = 音量 × 16，音量 2.0 → 32 格。
        // 父类构造逻辑：跟随实体 2.0F、静态方块 4.0F（64 格），静态分支在此覆盖为 2.0F。
        this.baseVolume = 2.0F;
        this.volume = 2.0F;
    }

    @Override
    public void tick() {
        super.tick();
        // 播完/出错/实体消失自停后触发一次回调
        if (this.isStopped() && !finishedNotified) {
            finishedNotified = true;
            onFinished.run();
        }
    }

    public void setMuted(boolean muted) {
        this.muted = muted;
        this.volume = muted ? 0.0F : this.baseVolume;
    }

    public boolean isMuted() {
        return muted;
    }
}
