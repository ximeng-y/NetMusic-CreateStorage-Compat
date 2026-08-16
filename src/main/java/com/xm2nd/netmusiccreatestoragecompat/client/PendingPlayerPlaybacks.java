package com.xm2nd.netmusiccreatestoragecompat.client;

import net.fxnt.fxntstorage.backpack.upgrade.jukebox.ClientJukeboxHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 原版唱片链路（fxntstorage）的挂起播放表：
 * 收到 PLAY 包时播放者实体尚未加载（远处/跨维度/刚登录），拿不到播放者 uuid 建表，
 * 先按实体 id 挂起；播放者实体加载后（走近/进入追踪范围）由客户端 tick 补走
 * {@link ClientJukeboxHandler#playPlayer} 正常播放入口，保持状态表一致。
 */
@OnlyIn(Dist.CLIENT)
public final class PendingPlayerPlaybacks {

    private record Pending(ResourceLocation song, boolean muted) {
    }

    /** 播放者实体 id → 待补播信息 */
    private static final Map<Integer, Pending> PENDING = new ConcurrentHashMap<>();

    private PendingPlayerPlaybacks() {
    }

    public static void pendPlayer(int entityId, ResourceLocation song, boolean muted) {
        PENDING.put(entityId, new Pending(song, muted));
    }

    public static void removePlayer(int entityId) {
        PENDING.remove(entityId);
    }

    /** 客户端每 tick 调用：播放者实体加载后补播（重发 PLAY 会覆盖挂起信息，最后的状态生效） */
    public static void tick() {
        if (PENDING.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        PENDING.entrySet().removeIf(entry -> {
            Entity entity = level.getEntity(entry.getKey());
            if (!(entity instanceof Player player)) {
                return false;
            }
            // 记录 entityId→UUID 映射，STOP 时实体可能已卸载，用此映射解析
            CompatJukeboxPlaybackClient.recordUuid(entry.getKey(), player.getUUID());
            Pending pending = entry.getValue();
            ClientJukeboxHandler.playPlayer(
                    new ClientJukeboxHandler.PlayerPlayback(player.getUUID(), null, pending.song(), pending.muted()));
            return true;
        });
    }

    /** 登出时清空 */
    public static void clearAll() {
        PENDING.clear();
    }
}
