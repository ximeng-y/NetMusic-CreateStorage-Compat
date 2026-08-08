package com.xm2nd.netmusiccreatestoragecompat.server;

import com.github.tartaricacid.netmusic.api.resolver.MusicPlayResolverManager;
import com.github.tartaricacid.netmusic.item.ItemMusicCD;
import com.xm2nd.netmusiccreatestoragecompat.network.PlayDiscPacket;
import com.xm2nd.netmusiccreatestoragecompat.network.PlaybackFinishedPacket;
import net.fxnt.fxntstorage.backpack.upgrade.jukebox.JukeboxHandler;
import net.fxnt.fxntstorage.config.ConfigManager;
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
 *   <li>玩家登录（ServerEvents）：补发附近方块播放与本人穿戴播放</li>
 * </ul>
 * 广播半径与 fxntstorage 保持一致（JUKEBOX_BUFFS_RANGE），保证两套状态机行为统一。
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

    private record PlayerPlayback(ItemMusicCD.SongInfo songInfo, boolean muted) {
    }

    private record EntityPlayback(ItemMusicCD.SongInfo songInfo, boolean muted) {
    }

    private NetMusicPlaybackServer() {
    }

    // ==================== 播放入口 ====================

    /** 穿戴（WORN）：只发给玩家本人（与 fxntstorage playPlayer 语义一致） */
    public static void playForPlayer(ServerPlayer player, ItemMusicCD.SongInfo songInfo) {
        UUID uuid = player.getUUID();
        stopPlayer(player); // 同键替换：先停旧的

        PLAYER_PLAYBACKS.put(uuid, new PlayerPlayback(songInfo, false));
        sendResolved(player.serverLevel().getServer(), songInfo, resolved -> {
            // resolve 完成时播放可能已停止或换歌，校验仍是同一首才发送
            PlayerPlayback current = PLAYER_PLAYBACKS.get(uuid);
            if (current != null && current.songInfo().equals(songInfo)) {
                PacketDistributor.sendToPlayer(player, playPacket(PlayDiscPacket.Source.PLAYER, Optional.empty(), Optional.empty(), resolved, false));
            }
        });
    }

    /** 方块（BLOCK）：广播给附近玩家（与 fxntstorage playBlock 语义一致） */
    public static void playForBlock(ServerPlayer player, BlockPos pos, ItemMusicCD.SongInfo songInfo) {
        ServerLevel level = player.serverLevel();
        JukeboxHandler.BlockKey key = JukeboxHandler.BlockKey.of(level, pos);
        stopBlock(level, pos); // 同键替换：先停旧的

        BLOCK_PLAYBACKS.put(key, new BlockPlayback(songInfo, false));
        sendResolved(level.getServer(), songInfo, resolved -> {
            BlockPlayback current = BLOCK_PLAYBACKS.get(key);
            if (current != null && current.songInfo().equals(songInfo)) {
                sendToNearby(level, pos, playPacket(PlayDiscPacket.Source.BLOCK, Optional.of(pos), Optional.empty(), resolved, false));
            }
        });
    }

    /** 装置（CONTRAPTION）：只发给打开背包菜单的 listener（与 fxntstorage playEntity 语义一致） */
    public static void playForEntity(ServerPlayer listener, int entityId, ItemMusicCD.SongInfo songInfo) {
        stopEntity(listener, entityId); // 同键替换：先停旧的

        ENTITY_PLAYBACKS.put(entityId, new EntityPlayback(songInfo, false));
        sendResolved(listener.serverLevel().getServer(), songInfo, resolved -> {
            EntityPlayback current = ENTITY_PLAYBACKS.get(entityId);
            if (current != null && current.songInfo().equals(songInfo)) {
                PacketDistributor.sendToPlayer(listener, playPacket(PlayDiscPacket.Source.ENTITY, Optional.empty(), Optional.of(entityId), resolved, false));
            }
        });
    }

    // ==================== 停止入口 ====================

    public static void stopPlayer(ServerPlayer player) {
        UUID uuid = player.getUUID();
        if (PLAYER_PLAYBACKS.remove(uuid) != null) {
            PacketDistributor.sendToPlayer(player, stopPacket(PlayDiscPacket.Source.PLAYER, Optional.empty(), Optional.empty()));
        }
    }

    public static void stopBlock(Level level, BlockPos pos) {
        JukeboxHandler.BlockKey key = JukeboxHandler.BlockKey.of(level, pos);
        if (BLOCK_PLAYBACKS.remove(key) != null && level instanceof ServerLevel serverLevel) {
            sendToNearby(serverLevel, pos, stopPacket(PlayDiscPacket.Source.BLOCK, Optional.of(pos), Optional.empty()));
        }
    }

    public static void stopEntity(ServerPlayer listener, int entityId) {
        if (ENTITY_PLAYBACKS.remove(entityId) != null) {
            PacketDistributor.sendToPlayer(listener, stopPacket(PlayDiscPacket.Source.ENTITY, Optional.empty(), Optional.of(entityId)));
        }
    }

    // ==================== 静音切换 ====================

    /** 由 JukeboxServerPacket.handleToggleMuted 同步调用（三种 source 共用） */
    public static void toggleMuted(ServerPlayer player, PlayDiscPacket.Source source, Optional<BlockPos> pos, Optional<Integer> entityId) {
        switch (source) {
            case PLAYER -> {
                PLAYER_PLAYBACKS.computeIfPresent(player.getUUID(), (uuid, pb) -> {
                    boolean newMuted = !pb.muted();
                    PacketDistributor.sendToPlayer(player, mutePacket(PlayDiscPacket.Source.PLAYER, Optional.empty(), Optional.empty(), newMuted));
                    return new PlayerPlayback(pb.songInfo(), newMuted);
                });
            }
            case BLOCK -> pos.ifPresent(blockPos -> {
                ServerLevel level = player.serverLevel();
                JukeboxHandler.BlockKey key = JukeboxHandler.BlockKey.of(level, blockPos);
                BLOCK_PLAYBACKS.computeIfPresent(key, (k, pb) -> {
                    boolean newMuted = !pb.muted();
                    sendToNearby(level, blockPos, mutePacket(PlayDiscPacket.Source.BLOCK, Optional.of(blockPos), Optional.empty(), newMuted));
                    return new BlockPlayback(pb.songInfo(), newMuted);
                });
            });
            case ENTITY -> entityId.ifPresent(id -> {
                ENTITY_PLAYBACKS.computeIfPresent(id, (k, pb) -> {
                    boolean newMuted = !pb.muted();
                    PacketDistributor.sendToPlayer(player, mutePacket(PlayDiscPacket.Source.ENTITY, Optional.empty(), Optional.of(id), newMuted));
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
     * 玩家登录时补发：本人穿戴播放 + 附近方块播放（与 fxntstorage syncBlocksToPlayers 语义一致）。
     */
    public static void syncToPlayer(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        int range = broadcastRange();

        for (Map.Entry<JukeboxHandler.BlockKey, BlockPlayback> entry : BLOCK_PLAYBACKS.entrySet()) {
            JukeboxHandler.BlockKey key = entry.getKey();
            if (!key.dimension().equals(level.dimension())) continue;

            BlockPos pos = key.pos();
            double maxDist = (double) range * range;
            if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > maxDist) continue;

            BlockPlayback pb = entry.getValue();
            PacketDistributor.sendToPlayer(player, playPacket(PlayDiscPacket.Source.BLOCK, Optional.of(pos), Optional.empty(), pb.songInfo(), pb.muted()));
        }

        PlayerPlayback own = PLAYER_PLAYBACKS.get(player.getUUID());
        if (own != null) {
            PacketDistributor.sendToPlayer(player, playPacket(PlayDiscPacket.Source.PLAYER, Optional.empty(), Optional.empty(), own.songInfo(), own.muted()));
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

    private static int broadcastRange() {
        return ConfigManager.ServerConfig.JUKEBOX_BUFFS_RANGE.get();
    }

    private static void sendToNearby(ServerLevel level, BlockPos pos, PlayDiscPacket packet) {
        PacketDistributor.sendToPlayersNear(level, null, pos.getX(), pos.getY(), pos.getZ(), broadcastRange(), packet);
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
