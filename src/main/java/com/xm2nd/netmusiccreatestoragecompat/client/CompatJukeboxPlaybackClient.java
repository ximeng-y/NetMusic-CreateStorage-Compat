package com.xm2nd.netmusiccreatestoragecompat.client;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * fxntstorage 原版唱片链路的客户端并行状态表。
 * <p>
 * 根因：fxntstorage {@code ClientJukeboxHandler.playPlayer} 的 lambda 内先构造原版
 * {@code EntitySoundInstance}，再调 {@code SoundManager.play}（被本 mod @Redirect 替换为播放
 * {@link CompatJukeboxEntitySound}），最后把<strong>原版实例</strong>存入 {@code playerSounds} 表。
 * 后果：表里存的是从未播放的"孤儿"实例，{@code stopPlayer}/{@code playPlayerMuted} 全部打在孤儿上——
 * STOP 停不掉实际声音、静音切换无效、换歌时旧声音变僵尸（looping 永不自停）。
 * <p>
 * 修复：本表按播放者 UUID 维护实际播放的 {@link CompatJukeboxEntitySound} 实例，
 * STOP/静音/查询走本表而非 fxntstorage 的孤儿表。同时维护 entityId→UUID 映射，
 * 使 STOP 在播放者实体未加载时也能按 UUID 停止（远处停止场景）。
 */
@OnlyIn(Dist.CLIENT)
public final class CompatJukeboxPlaybackClient {

    /** 播放者 UUID → 实际播放的声音实例 */
    private static final Map<UUID, CompatJukeboxEntitySound> SOUND_BY_UUID = new ConcurrentHashMap<>();
    /** 播放者实体 id → UUID（STOP 时实体可能未加载，用此映射解析 UUID） */
    private static final Map<Integer, UUID> UUID_BY_ENTITY_ID = new ConcurrentHashMap<>();

    private CompatJukeboxPlaybackClient() {
    }

    /** @Redirect handler 创建声音后调用：存入并行表 */
    public static void onSoundCreated(UUID playerId, CompatJukeboxEntitySound sound) {
        SOUND_BY_UUID.put(playerId, sound);
    }

    /** handlePlay 实体可见时调用：记录 entityId→UUID 映射 */
    public static void recordUuid(int entityId, UUID uuid) {
        UUID_BY_ENTITY_ID.put(entityId, uuid);
    }

    /** 查询实际播放的声音实例（供 playPlayerMuted inject 判定"已播"用） */
    public static CompatJukeboxEntitySound getSound(UUID playerId) {
        return SOUND_BY_UUID.get(playerId);
    }

    /** 按 entityId 查 UUID（实体未加载时用） */
    public static UUID getUuid(int entityId) {
        return UUID_BY_ENTITY_ID.get(entityId);
    }

    /** 按 UUID 停止实际声音并清表（由 stopPlayer inject 调用） */
    public static void stopByUuid(UUID playerId) {
        CompatJukeboxEntitySound sound = SOUND_BY_UUID.remove(playerId);
        if (sound != null) {
            Minecraft.getInstance().getSoundManager().stop(sound);
        }
    }

    /** 清除 entityId→UUID 映射（handleStop 调用） */
    public static void removeMapping(int entityId) {
        UUID_BY_ENTITY_ID.remove(entityId);
    }

    /** 登出时清空全部 */
    public static void stopAll() {
        Minecraft mc = Minecraft.getInstance();
        for (CompatJukeboxEntitySound sound : SOUND_BY_UUID.values()) {
            mc.getSoundManager().stop(sound);
        }
        SOUND_BY_UUID.clear();
        UUID_BY_ENTITY_ID.clear();
    }
}
