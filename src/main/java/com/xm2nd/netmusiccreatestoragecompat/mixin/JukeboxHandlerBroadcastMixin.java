package com.xm2nd.netmusiccreatestoragecompat.mixin;

import net.fxnt.fxntstorage.backpack.upgrade.jukebox.JukeboxBuffHandler;
import net.fxnt.fxntstorage.backpack.upgrade.jukebox.JukeboxHandler;
import net.fxnt.fxntstorage.backpack.upgrade.jukebox.JukeboxHandler.EntityPlayback;
import net.fxnt.fxntstorage.backpack.upgrade.jukebox.JukeboxHandler.PlayerPlayback;
import net.fxnt.fxntstorage.config.ConfigManager;
import net.fxnt.fxntstorage.network.packet.JukeboxClientPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 原版播放广播多人化：把 JukeboxHandler 穿戴/方块/装置播放的"只发本人/操作者"改为
 * 全局广播（所有玩家收包，可闻距离由客户端声音实例的 32 格衰减决定），
 * 实现原版唱片穿戴/方块/装置全局可闻。
 * <p>
 * 播放/停止包复用 entityId 字段携带播放者实体 id，供客户端区分"自己的"与"别人的"播放。
 * 静音没有独立 action，用 PLAY 包携带新 muted 值重发（客户端对已在播的播放只更新静音状态）。
 */
@Mixin(JukeboxHandler.class)
public abstract class JukeboxHandlerBroadcastMixin {

    @Shadow
    private static Map<UUID, PlayerPlayback> playerSounds;

    @Shadow
    private static Map<JukeboxHandler.BlockKey, JukeboxHandler.BlockPlayback> blockSounds;

    @Shadow
    private static Map<Integer, EntityPlayback> entitySounds;

    // ==================== 穿戴播放 ====================

    @Inject(method = "playPlayer", at = @At("HEAD"), cancellable = true)
    private static void netmusic_create_storage_compat$playPlayer(ServerPlayer player, ResourceLocation song, CallbackInfo ci) {
        UUID uuid = player.getUUID();

        // 先停旧的（广播 STOP，替换原版只发本人的行为）
        PlayerPlayback removed = playerSounds.remove(uuid);
        if (removed != null) {
            JukeboxBuffHandler.removePlayerBuff(player, removed.song());
            PacketDistributor.sendToAllPlayers(new JukeboxClientPacket(JukeboxClientPacket.Action.STOP, JukeboxClientPacket.Source.PLAYER,
                    Optional.empty(), Optional.empty(), false, Optional.of(player.getId())));
        }

        playerSounds.put(uuid, new PlayerPlayback(uuid, song, false));
        JukeboxBuffHandler.applyMusicBuffsFromPlayer(player, song);
        PacketDistributor.sendToAllPlayers(new JukeboxClientPacket(JukeboxClientPacket.Action.PLAY, JukeboxClientPacket.Source.PLAYER,
                Optional.empty(), Optional.of(song), false, Optional.of(player.getId())));
        ci.cancel();
    }

    @Inject(method = "stopPlayer", at = @At("HEAD"), cancellable = true)
    private static void netmusic_create_storage_compat$stopPlayer(ServerPlayer player, CallbackInfo ci) {
        UUID uuid = player.getUUID();
        PlayerPlayback removed = playerSounds.remove(uuid);
        if (removed != null) {
            JukeboxBuffHandler.removePlayerBuff(player, removed.song());
            PacketDistributor.sendToAllPlayers(new JukeboxClientPacket(JukeboxClientPacket.Action.STOP, JukeboxClientPacket.Source.PLAYER,
                    Optional.empty(), Optional.empty(), false, Optional.of(player.getId())));
        }
        ci.cancel();
    }

    @Inject(method = "togglePlayerMuted", at = @At("HEAD"), cancellable = true)
    private static void netmusic_create_storage_compat$togglePlayerMuted(ServerPlayer player, CallbackInfo ci) {
        playerSounds.computeIfPresent(player.getUUID(), (uuid, pb) -> {
            PlayerPlayback newPb = pb.toggleMuted();
            PacketDistributor.sendToAllPlayers(new JukeboxClientPacket(JukeboxClientPacket.Action.PLAY, JukeboxClientPacket.Source.PLAYER,
                    Optional.empty(), Optional.of(newPb.song()), newPb.muted(), Optional.of(player.getId())));
            return newPb;
        });
        ci.cancel();
    }

    // ==================== 方块播放 ====================

    @Inject(method = "playBlock", at = @At("HEAD"), cancellable = true)
    private static void netmusic_create_storage_compat$playBlock(ServerPlayer player, BlockPos pos, ResourceLocation song, CallbackInfo ci) {
        ServerLevel level = player.serverLevel();
        JukeboxHandler.stopBlock(level, pos); // 触发 stopBlock 注入 → 全员 STOP

        JukeboxHandler.BlockPlayback playback = new JukeboxHandler.BlockPlayback(pos, level.dimension(), song, false, new HashSet<>());
        blockSounds.put(JukeboxHandler.BlockKey.of(level, pos), playback);

        JukeboxBuffHandler.applyMusicBuffsFromBlock(player, song);
        PacketDistributor.sendToAllPlayers(new JukeboxClientPacket(JukeboxClientPacket.Action.PLAY, JukeboxClientPacket.Source.BLOCK,
                Optional.of(pos), Optional.of(song), false, Optional.empty()));
        ci.cancel();
    }

    @Inject(method = "stopBlock", at = @At("HEAD"), cancellable = true)
    private static void netmusic_create_storage_compat$stopBlock(Level level, BlockPos pos, CallbackInfo ci) {
        JukeboxHandler.BlockKey key = JukeboxHandler.BlockKey.of(level, pos);
        JukeboxHandler.BlockPlayback removed = blockSounds.remove(key);
        if (removed != null) {
            PacketDistributor.sendToAllPlayers(new JukeboxClientPacket(JukeboxClientPacket.Action.STOP, JukeboxClientPacket.Source.BLOCK,
                    Optional.of(pos), Optional.empty(), false, Optional.empty()));
        }
        ci.cancel();
    }

    // ==================== 装置播放 ====================

    @Inject(method = "playEntity", at = @At("HEAD"), cancellable = true)
    private static void netmusic_create_storage_compat$playEntity(ServerPlayer listener, int entityId, ResourceLocation song, CallbackInfo ci) {
        EntityPlayback removed = entitySounds.remove(entityId);
        if (removed != null) {
            PacketDistributor.sendToAllPlayers(new JukeboxClientPacket(JukeboxClientPacket.Action.STOP, JukeboxClientPacket.Source.ENTITY,
                    Optional.empty(), Optional.empty(), false, Optional.of(entityId)));
        }
        entitySounds.put(entityId, new EntityPlayback(entityId, song, false));
        PacketDistributor.sendToAllPlayers(new JukeboxClientPacket(JukeboxClientPacket.Action.PLAY, JukeboxClientPacket.Source.ENTITY,
                Optional.empty(), Optional.of(song), false, Optional.of(entityId)));
        ci.cancel();
    }

    @Inject(method = "stopEntity", at = @At("HEAD"), cancellable = true)
    private static void netmusic_create_storage_compat$stopEntity(ServerPlayer listener, int entityId, CallbackInfo ci) {
        entitySounds.remove(entityId);
        PacketDistributor.sendToAllPlayers(new JukeboxClientPacket(JukeboxClientPacket.Action.STOP, JukeboxClientPacket.Source.ENTITY,
                Optional.empty(), Optional.empty(), false, Optional.of(entityId)));
        ci.cancel();
    }

    // toggleEntityMuted 无 level/位置参数，无法定位 contraption 广播，保持原版行为（仅操作者本地静音）

    // ==================== 登录补发（追加穿戴/方块播放） ====================

    @Inject(method = "syncBlocksToPlayers", at = @At("HEAD"), cancellable = true)
    private static void netmusic_create_storage_compat$syncBlocksToPlayers(ServerPlayer player, CallbackInfo ci) {
        ServerLevel level = player.serverLevel();

        // 方块：全量补发（与全局广播一致）；buff 保持原距离语义（仅同维度范围内玩家获得，不改变 fxntstorage 行为）
        int range = ConfigManager.ServerConfig.JUKEBOX_BUFFS_RANGE.get();
        double maxDist = (double) range * range;
        for (Map.Entry<JukeboxHandler.BlockKey, JukeboxHandler.BlockPlayback> entry : blockSounds.entrySet()) {
            JukeboxHandler.BlockPlayback pb = entry.getValue();
            BlockPos pos = pb.pos();
            if (pb.dimension().equals(level.dimension())
                    && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= maxDist) {
                JukeboxBuffHandler.applyMusicBuffsFromBlock(player, pb.song());
            }
            PacketDistributor.sendToPlayer(player, new JukeboxClientPacket(JukeboxClientPacket.Action.PLAY, JukeboxClientPacket.Source.BLOCK,
                    Optional.of(pos), Optional.of(pb.song()), pb.muted(), Optional.empty()));
        }

        // 穿戴：全量补发（含本人，与全局广播一致）
        for (Map.Entry<UUID, PlayerPlayback> entry : playerSounds.entrySet()) {
            ServerPlayer owner = level.getServer().getPlayerList().getPlayer(entry.getKey());
            if (owner == null) continue;
            PlayerPlayback pb = entry.getValue();
            PacketDistributor.sendToPlayer(player, new JukeboxClientPacket(JukeboxClientPacket.Action.PLAY, JukeboxClientPacket.Source.PLAYER,
                    Optional.empty(), Optional.of(pb.song()), pb.muted(), Optional.of(owner.getId())));
        }
        ci.cancel();
    }
}
