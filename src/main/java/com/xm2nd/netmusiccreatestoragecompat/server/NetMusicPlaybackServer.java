package com.xm2nd.netmusiccreatestoragecompat.server;

import com.github.tartaricacid.netmusic.api.resolver.MusicPlayResolverManager;
import com.github.tartaricacid.netmusic.item.ItemMusicCD;
import com.xm2nd.netmusiccreatestoragecompat.network.PlayDiscPacket;
import com.xm2nd.netmusiccreatestoragecompat.network.PlaybackFinishedPacket;
import net.fxnt.fxntstorage.backpack.upgrade.jukebox.JukeboxHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * NetMusic 播放的服务端状态机（与 fxntstorage 的 {@link JukeboxHandler} 平行）。
 * <p>
 * 由 mixin 挂在 fxntstorage 触发点上：
 * <ul>
 *   <li>播放入口（JukeboxServerPacketMixin）：记状态表 → 服务端异步解析 URL → 发 PLAY 包</li>
 *   <li>停止入口（JukeboxHandlerMixin）：清状态表 + 广播 STOP 包（换碟/取碟/登出/重生/换装备自动覆盖）</li>
 *   <li>静音入口（JukeboxServerPacketMixin）：翻转 muted + 广播 MUTE 包</li>
 *   <li>状态查询（JukeboxHandlerMixin）：合并本表，供面板显示/TOGGLE 判定/音符粒子复用</li>
 *   <li>玩家登录（ServerEvents）：补发全部在播状态</li>
 * </ul>
 * 所有 S2C 包全局广播（sendToAllPlayers），可闻距离由客户端声音实例决定
 * （32 格 = 音量 2.0 × 16 格衰减基准），远处玩家收包但衰减到无声。
 */
public final class NetMusicPlaybackServer {

    /** 方块播放：维度+坐标 → 播放信息 */
    private static final Map<JukeboxHandler.BlockKey, BlockPlayback> BLOCK_PLAYBACKS = new ConcurrentHashMap<>();
    /** 穿戴播放：玩家 UUID → 播放信息 */
    private static final Map<UUID, PlayerPlayback> PLAYER_PLAYBACKS = new ConcurrentHashMap<>();
    /** 装置播放：contraption 实体 id → 播放信息 */
    private static final Map<Integer, EntityPlayback> ENTITY_PLAYBACKS = new ConcurrentHashMap<>();

    private record BlockPlayback(ItemMusicCD.SongInfo songInfo, boolean muted) {
    }

    /**
     * 记录播放时的 entityId：玩家重生后 entity id 会变，
     * STOP/MUTE 必须用播放时的旧 id 广播，客户端表（按 entityId 建键）才能匹配到旧声音实例。
     */
    private record PlayerPlayback(ItemMusicCD.SongInfo songInfo, int entityId, boolean muted) {
    }

    private record EntityPlayback(ItemMusicCD.SongInfo songInfo, boolean muted) {
    }

    private NetMusicPlaybackServer() {
    }

    // ==================== 播放入口 ====================

    /**
     * 穿戴（WORN）：全局广播（所有玩家都收到包，客户端按距离衰减——"背着音响"效果）。
     * 与原版 playPlayer 只发本人的语义不同，这是本 mod 的扩展行为。
     */
    public static void playForPlayer(ServerPlayer player, ItemMusicCD.SongInfo songInfo) {
        UUID uuid = player.getUUID();
        stopPlayer(player); // 同键替换：先停旧的

        PLAYER_PLAYBACKS.put(uuid, new PlayerPlayback(songInfo, player.getId(), false));
        sendResolved(player.serverLevel().getServer(), songInfo, resolved -> {
            // resolve 完成时播放可能已停止或换歌，校验仍是同一首才发送
            PlayerPlayback current = PLAYER_PLAYBACKS.get(uuid);
            if (current != null && current.songInfo().equals(songInfo)) {
                PacketDistributor.sendToAllPlayers(
                        playPacket(PlayDiscPacket.Source.PLAYER, Optional.empty(), Optional.of(player.getId()), resolved, false));
            }
        });
    }

    /** 方块（BLOCK）：全局广播（客户端按方块位置距离衰减，32 格内可闻） */
    public static void playForBlock(ServerPlayer player, BlockPos pos, ItemMusicCD.SongInfo songInfo) {
        ServerLevel level = player.serverLevel();
        JukeboxHandler.BlockKey key = JukeboxHandler.BlockKey.of(level, pos);
        stopBlock(level, pos); // 同键替换：先停旧的

        BLOCK_PLAYBACKS.put(key, new BlockPlayback(songInfo, false));
        sendResolved(level.getServer(), songInfo, resolved -> {
            BlockPlayback current = BLOCK_PLAYBACKS.get(key);
            if (current != null && current.songInfo().equals(songInfo)) {
                PacketDistributor.sendToAllPlayers(
                        playPacket(PlayDiscPacket.Source.BLOCK, Optional.of(pos), Optional.empty(), resolved, false));
            }
        });
    }

    /**
     * 装置（CONTRAPTION）：全局广播（客户端按 contraption 实体位置距离衰减，声音跟随装置）。
     * 与原版 playEntity 只发 listener 的语义不同，这是本 mod 的扩展行为。
     */
    public static void playForEntity(ServerPlayer listener, int entityId, ItemMusicCD.SongInfo songInfo) {
        stopEntity(listener, entityId); // 同键替换：先停旧的

        ENTITY_PLAYBACKS.put(entityId, new EntityPlayback(songInfo, false));
        sendResolved(listener.serverLevel().getServer(), songInfo, resolved -> {
            EntityPlayback current = ENTITY_PLAYBACKS.get(entityId);
            if (current != null && current.songInfo().equals(songInfo)) {
                PacketDistributor.sendToAllPlayers(
                        playPacket(PlayDiscPacket.Source.ENTITY, Optional.empty(), Optional.of(entityId), resolved, false));
            }
        });
    }

    // ==================== 停止入口 ====================

    public static void stopPlayer(ServerPlayer player) {
        UUID uuid = player.getUUID();
        PlayerPlayback pb = PLAYER_PLAYBACKS.remove(uuid);
        if (pb != null) {
            // 用播放时记录的 entityId 广播 STOP：玩家重生后 id 会变，
            // 客户端表键是 PLAY 时的旧 id，用旧 id 才能匹配
            PacketDistributor.sendToAllPlayers(
                    stopPacket(PlayDiscPacket.Source.PLAYER, Optional.empty(), Optional.of(pb.entityId())));
        }
    }

    public static void stopBlock(Level level, BlockPos pos) {
        JukeboxHandler.BlockKey key = JukeboxHandler.BlockKey.of(level, pos);
        if (BLOCK_PLAYBACKS.remove(key) != null) {
            PacketDistributor.sendToAllPlayers(
                    stopPacket(PlayDiscPacket.Source.BLOCK, Optional.of(pos), Optional.empty()));
        }
    }

    public static void stopEntity(ServerPlayer listener, int entityId) {
        if (ENTITY_PLAYBACKS.remove(entityId) != null) {
            PacketDistributor.sendToAllPlayers(
                    stopPacket(PlayDiscPacket.Source.ENTITY, Optional.empty(), Optional.of(entityId)));
        }
    }

    // ==================== 静音切换 ====================

    /** 由 JukeboxServerPacket.handleToggleMuted 同步调用（三种 source 共用） */
    public static void toggleMuted(ServerPlayer player, PlayDiscPacket.Source source, Optional<BlockPos> pos, Optional<Integer> entityId) {
        switch (source) {
            case PLAYER -> {
                PLAYER_PLAYBACKS.computeIfPresent(player.getUUID(), (uuid, pb) -> {
                    boolean newMuted = !pb.muted();
                    PacketDistributor.sendToAllPlayers(
                            mutePacket(PlayDiscPacket.Source.PLAYER, Optional.empty(), Optional.of(pb.entityId()), newMuted));
                    return new PlayerPlayback(pb.songInfo(), pb.entityId(), newMuted);
                });
            }
            case BLOCK -> pos.ifPresent(blockPos -> {
                ServerLevel level = player.serverLevel();
                JukeboxHandler.BlockKey key = JukeboxHandler.BlockKey.of(level, blockPos);
                BLOCK_PLAYBACKS.computeIfPresent(key, (k, pb) -> {
                    boolean newMuted = !pb.muted();
                    PacketDistributor.sendToAllPlayers(
                            mutePacket(PlayDiscPacket.Source.BLOCK, Optional.of(blockPos), Optional.empty(), newMuted));
                    return new BlockPlayback(pb.songInfo(), newMuted);
                });
            });
            case ENTITY -> entityId.ifPresent(id -> {
                ENTITY_PLAYBACKS.computeIfPresent(id, (k, pb) -> {
                    boolean newMuted = !pb.muted();
                    PacketDistributor.sendToAllPlayers(
                            mutePacket(PlayDiscPacket.Source.ENTITY, Optional.empty(), Optional.of(id), newMuted));
                    return new EntityPlayback(pb.songInfo(), newMuted);
                });
            });
        }
    }

    // ==================== 状态查询（供 mixin 合并） ====================

    public static boolean isPlayerPlaying(ServerPlayer player) {
        return PLAYER_PLAYBACKS.containsKey(player.getUUID());
    }

    public static boolean isBlockPlaying(Level level, BlockPos pos) {
        return BLOCK_PLAYBACKS.containsKey(JukeboxHandler.BlockKey.of(level, pos));
    }

    public static boolean isEntityPlaying(int entityId) {
        return ENTITY_PLAYBACKS.containsKey(entityId);
    }

    public static boolean isPlayerMuted(ServerPlayer player) {
        PlayerPlayback pb = PLAYER_PLAYBACKS.get(player.getUUID());
        return pb != null && pb.muted();
    }

    public static boolean isBlockMuted(Level level, BlockPos pos) {
        BlockPlayback pb = BLOCK_PLAYBACKS.get(JukeboxHandler.BlockKey.of(level, pos));
        return pb != null && pb.muted();
    }

    public static boolean isEntityMuted(int entityId) {
        EntityPlayback pb = ENTITY_PLAYBACKS.get(entityId);
        return pb != null && pb.muted();
    }

    // ==================== 玩家登录补发 ====================

    /**
     * 玩家登录时补发所有在播状态（与全局广播一致，不做距离/维度过滤）：
     * 附近方块播放、所有穿戴播放（含本人），跨维度收包后声音因位置距离衰减不可闻。
     */
    public static void syncToPlayer(ServerPlayer player) {
        MinecraftServer server = player.serverLevel().getServer();

        for (Map.Entry<JukeboxHandler.BlockKey, BlockPlayback> entry : BLOCK_PLAYBACKS.entrySet()) {
            BlockPos pos = entry.getKey().pos();
            BlockPlayback pb = entry.getValue();
            PacketDistributor.sendToPlayer(player, playPacket(PlayDiscPacket.Source.BLOCK, Optional.of(pos), Optional.empty(), pb.songInfo(), pb.muted()));
        }

        for (Map.Entry<UUID, PlayerPlayback> entry : PLAYER_PLAYBACKS.entrySet()) {
            ServerPlayer owner = server.getPlayerList().getPlayer(entry.getKey());
            if (owner == null) continue;
            PlayerPlayback pb = entry.getValue();
            // 新登录的客户端用 owner 当前 id 建表（owner 在线，id 为当前有效值）；
            // 该 id 与后续 PLAY/STOP/MUTE 广播携带的 id 一致，客户端表可正确匹配
            PacketDistributor.sendToPlayer(player, playPacket(PlayDiscPacket.Source.PLAYER, Optional.empty(), Optional.of(owner.getId()), pb.songInfo(), pb.muted()));
        }
    }

    // ==================== 歌曲播完回调（C2S FINISHED） ====================

    public static void onFinished(ServerPlayer player, PlaybackFinishedPacket packet) {
        switch (packet.source()) {
            case PLAYER -> PLAYER_PLAYBACKS.remove(player.getUUID());
            case BLOCK -> packet.pos().ifPresent(pos -> BLOCK_PLAYBACKS.remove(JukeboxHandler.BlockKey.of(player.serverLevel(), pos)));
            case ENTITY -> packet.entityId().ifPresent(ENTITY_PLAYBACKS::remove);
        }
    }

    // ==================== 内部工具 ====================

    /** 服务端异步解析最终 URL（NetMusic 公开 API），完成后再发送 */
    private static void sendResolved(MinecraftServer server, ItemMusicCD.SongInfo songInfo, Consumer<ItemMusicCD.SongInfo> onResolved) {
        MusicPlayResolverManager.resolve(songInfo.clone()).thenAcceptAsync(onResolved, server);
    }

    private static PlayDiscPacket playPacket(PlayDiscPacket.Source source, Optional<BlockPos> pos, Optional<Integer> entityId,
                                             ItemMusicCD.SongInfo songInfo, boolean muted) {
        return new PlayDiscPacket(PlayDiscPacket.Action.PLAY, source, pos, entityId, Optional.of(songInfo), muted);
    }

    private static PlayDiscPacket stopPacket(PlayDiscPacket.Source source, Optional<BlockPos> pos, Optional<Integer> entityId) {
        return new PlayDiscPacket(PlayDiscPacket.Action.STOP, source, pos, entityId, Optional.empty(), false);
    }

    private static PlayDiscPacket mutePacket(PlayDiscPacket.Source source, Optional<BlockPos> pos, Optional<Integer> entityId, boolean muted) {
        return new PlayDiscPacket(PlayDiscPacket.Action.MUTE, source, pos, entityId, Optional.empty(), muted);
    }
}
