package com.xm2nd.netmusiccreatestoragecompat.client;

import com.github.tartaricacid.netmusic.compat.sbackpack.NetMusicBackpackSound;
import net.minecraft.client.Minecraft;
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
 *   <li>按实体 id 每 tick 解析跟随：播放者实体未加载（远处/跨维度/刚登录）时
 *       声音照常创建并保持上次位置，实体加载（走近）后自动续跟，随距离自然淡入——
 *       不再因"收包时实体不可见"而整段丢弃（多人"背着音响"语义）</li>
 * </ul>
 * 构造时对"他人/装置"传入 entity=null + followEntityId，父类 tick 里的
 * updatePositionFromEntity(null) 为空操作，因此不会触发"实体移除即 stop"；
 * 播完/出错自停仍由父类 tick 计数处理。
 */
@OnlyIn(Dist.CLIENT)
public class CompatNetMusicSound extends NetMusicBackpackSound {

    private final Runnable onFinished;
    private final float baseVolume;
    /** 跟随目标实体 id；-1 = 不跟随（静态方块 / 本地玩家自身） */
    private final int followEntityId;
    private boolean finishedNotified = false;
    private boolean muted = false;
    /** 实体是否已加载过一次（首次加载前静默，避免从世界原点 (0,0,0) 发声的"幽灵音乐"） */
    private boolean entityLocated = false;

    public CompatNetMusicSound(BlockPos pos, @Nullable Entity entity, int followEntityId, URL url, int timeSecond, Runnable onFinished) {
        super(pos, entity, url, timeSecond);
        this.onFinished = onFinished;
        this.followEntityId = followEntityId;
        // 统一 32 格可闻距离：原版衰减公式 可闻距离 = 音量 × 16，音量 2.0 → 32 格。
        // 父类构造逻辑：跟随实体 2.0F、静态方块 4.0F（64 格），静态分支在此覆盖为 2.0F。
        this.baseVolume = 2.0F;
        // 需跟随实体但首次未加载时，先静默避免从世界原点 (0,0,0) 发声；
        // 实体加载后 tick 里恢复 baseVolume。静态/本地玩家分支不受影响。
        if (followEntityId >= 0 && entity == null) {
            this.volume = 0.0F;
            this.entityLocated = false;
        } else {
            this.volume = 2.0F;
            this.entityLocated = true;
        }
    }

    @Override
    public void tick() {
        // 按实体 id 每 tick 解析跟随：实体未加载时保持上次位置，加载后自动续跟
        if (followEntityId >= 0) {
            Minecraft mc = Minecraft.getInstance();
            Entity entity = mc.level == null ? null : mc.level.getEntity(followEntityId);
            if (entity != null) {
                this.x = (float) entity.getX();
                this.y = (float) entity.getY();
                this.z = (float) entity.getZ();
                // 首次定位到实体后恢复音量（之前静默避免从原点发声）
                if (!entityLocated && !muted) {
                    this.volume = this.baseVolume;
                }
                entityLocated = true;
            }
        }
        // 父类 tick：播完/出错自停计数
        super.tick();
        // 播完/出错自停后触发一次回调
        if (this.isStopped() && !finishedNotified) {
            finishedNotified = true;
            onFinished.run();
        }
    }

    /** 静音（音量 0）启动时也允许播放：vanilla SoundEngine 会跳过启动音量即 0 的声音 */
    @Override
    public boolean canStartSilent() {
        return true;
    }

    public void setMuted(boolean muted) {
        this.muted = muted;
        // 未定位到实体时保持静默（避免幽灵音乐）；定位后按 muted/baseVolume
        if (!muted && !entityLocated && followEntityId >= 0) {
            this.volume = 0.0F;
        } else {
            this.volume = muted ? 0.0F : this.baseVolume;
        }
    }

    public boolean isMuted() {
        return muted;
    }
}
