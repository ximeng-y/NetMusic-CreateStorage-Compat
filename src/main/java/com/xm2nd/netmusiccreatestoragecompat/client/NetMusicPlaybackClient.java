package com.xm2nd.netmusiccreatestoragecompat.client;

import com.github.tartaricacid.netmusic.client.audio.MusicPlayManager;
import com.github.tartaricacid.netmusic.item.ItemMusicCD;
import com.xm2nd.netmusiccreatestoragecompat.network.PlayDiscPacket;
import com.xm2nd.netmusiccreatestoragecompat.network.PlaybackFinishedPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * NetMusic 播放的客户端管理器：接收服务端 PLAY/STOP/MUTE 包，
 * 用 NetMusic 的流式播放 API 播放/停止/静音，并维护声音实例表。
 * <p>
 * 三种 source 各维护一份表（与 fxntstorage 客户端状态对应）：
 * <ul>
 *   <li>PLAYER：本玩家穿戴背包（单例，跟随玩家）</li>
 *   <li>BLOCK：方块背包（按坐标静态定位）</li>
 *   <li>ENTITY：装置背包（跟随 contraption 实体）</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
public final class NetMusicPlaybackClient {

    private static CompatNetMusicSound playerSound;
    private static final Map<BlockPos, CompatNetMusicSound> BLOCK_SOUNDS = new ConcurrentHashMap<>();
    private static final Map<Integer, CompatNetMusicSound> ENTITY_SOUNDS = new ConcurrentHashMap<>();

    private NetMusicPlaybackClient() {
    }

    public static void handle(PlayDiscPacket packet) {
        switch (packet.action()) {
            case PLAY -> play(packet);
            case STOP -> stop(packet);
            case MUTE -> mute(packet);
        }
    }

    // ==================== 播放 ====================

    private static void play(PlayDiscPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;

        ItemMusicCD.SongInfo info = packet.songInfo().orElse(null);
        if (info == null) return;

        URL url = toUrl(MusicPlayManager.getFinalUrl(info.songUrl).orElse(info.songUrl));
        if (url == null) return;

        CompatNetMusicSound sound = switch (packet.source()) {
            case PLAYER -> {
                stopPlayer();
                yield new CompatNetMusicSound(BlockPos.ZERO, mc.player, url, info.songTime, NetMusicPlaybackClient::onPlayerFinished);
            }
            case BLOCK -> {
                BlockPos pos = packet.pos().orElse(null);
                if (pos == null) yield null;
                stopBlock(pos);
                yield new CompatNetMusicSound(pos, null, url, info.songTime, () -> onBlockFinished(pos));
            }
            case ENTITY -> {
                int entityId = packet.entityId().orElse(-1);
                Entity entity = level.getEntity(entityId);
                if (entity == null) yield null;
                stopEntity(entityId);
                yield new CompatNetMusicSound(BlockPos.ZERO, entity, url, info.songTime, () -> onEntityFinished(entityId));
            }
        };
        if (sound == null) return;

        if (packet.muted()) {
            sound.setMuted(true);
        }

        mc.getSoundManager().play(sound);
        mc.gui.setNowPlaying(Component.literal(info.songName));

        switch (packet.source()) {
            case PLAYER -> playerSound = sound;
            case BLOCK -> packet.pos().ifPresent(pos -> BLOCK_SOUNDS.put(pos, sound));
            case ENTITY -> packet.entityId().ifPresent(id -> ENTITY_SOUNDS.put(id, sound));
        }
    }

    // ==================== 停止 ====================

    private static void stop(PlayDiscPacket packet) {
        switch (packet.source()) {
            case PLAYER -> stopPlayer();
            case BLOCK -> packet.pos().ifPresent(NetMusicPlaybackClient::stopBlock);
            case ENTITY -> packet.entityId().ifPresent(NetMusicPlaybackClient::stopEntity);
        }
    }

    private static void stopPlayer() {
        if (playerSound != null) {
            Minecraft.getInstance().getSoundManager().stop(playerSound);
            playerSound = null;
        }
    }

    private static void stopBlock(BlockPos pos) {
        CompatNetMusicSound sound = BLOCK_SOUNDS.remove(pos);
        if (sound != null) {
            Minecraft.getInstance().getSoundManager().stop(sound);
        }
    }

    private static void stopEntity(int entityId) {
        CompatNetMusicSound sound = ENTITY_SOUNDS.remove(entityId);
        if (sound != null) {
            Minecraft.getInstance().getSoundManager().stop(sound);
        }
    }

    // ==================== 静音 ====================

    private static void mute(PlayDiscPacket packet) {
        CompatNetMusicSound sound = switch (packet.source()) {
            case PLAYER -> playerSound;
            case BLOCK -> packet.pos().map(BLOCK_SOUNDS::get).orElse(null);
            case ENTITY -> packet.entityId().map(ENTITY_SOUNDS::get).orElse(null);
        };
        if (sound != null) {
            sound.setMuted(packet.muted());
        }
    }

    // ==================== 歌曲播完回调 ====================

    private static void onPlayerFinished() {
        playerSound = null;
        sendFinished(PlayDiscPacket.Source.PLAYER, Optional.empty(), Optional.empty());
    }

    private static void onBlockFinished(BlockPos pos) {
        BLOCK_SOUNDS.remove(pos);
        sendFinished(PlayDiscPacket.Source.BLOCK, Optional.of(pos), Optional.empty());
    }

    private static void onEntityFinished(int entityId) {
        ENTITY_SOUNDS.remove(entityId);
        sendFinished(PlayDiscPacket.Source.ENTITY, Optional.empty(), Optional.of(entityId));
    }

    private static void sendFinished(PlayDiscPacket.Source source, Optional<BlockPos> pos, Optional<Integer> entityId) {
        PacketDistributor.sendToServer(new PlaybackFinishedPacket(source, pos, entityId));
    }

    // ==================== 清理 ====================

    /** 登出时清空所有播放 */
    public static void stopAll() {
        stopPlayer();
        BLOCK_SOUNDS.keySet().forEach(NetMusicPlaybackClient::stopBlock);
        ENTITY_SOUNDS.keySet().forEach(NetMusicPlaybackClient::stopEntity);
    }

    // ==================== 内部工具 ====================

    private static URL toUrl(String urlString) {
        try {
            return URI.create(urlString).toURL();
        } catch (MalformedURLException | IllegalArgumentException e) {
            return null;
        }
    }
}
