package com.xm2nd.netmusiccreatestoragecompat.mixin;

import com.xm2nd.netmusiccreatestoragecompat.client.CompatJukeboxPlaybackClient;
import com.xm2nd.netmusiccreatestoragecompat.client.PendingPlayerPlaybacks;
import net.fxnt.fxntstorage.backpack.upgrade.jukebox.ClientJukeboxHandler;
import net.fxnt.fxntstorage.network.packet.JukeboxClientPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;
import java.util.UUID;

/**
 * 原版播放广播多人化的客户端配套：PLAYER 播放包现在会携带播放者实体 id
 * （服务端广播发出），收包时按该 id 区分"自己的"（走原逻辑，跟随自己）与
 * "别人的"（跟随播放者实体）。
 * <p>
 * "已在播时更新静音状态"（服务端用 PLAY 包重发表达静音变化）由
 * ClientJukeboxHandlerMixin 对 playPlayer 的注入统一处理：这里只负责把
 * 他人的播放以"播放者 uuid"构造 PlayerPlayback 交给原版播放入口。
 */
@Mixin(JukeboxClientPacket.class)
public abstract class JukeboxClientPacketMixin {

    // record 组件访问器（编译期目标类不可见，声明为 shadow）
    @Shadow
    abstract JukeboxClientPacket.Source source();

    @Shadow
    abstract Optional<Integer> entityId();

    @Shadow
    abstract Optional<ResourceLocation> song();

    @Shadow
    abstract boolean muted();

    @Inject(method = "handlePlay", at = @At("HEAD"), cancellable = true)
    private void netmusic_create_storage_compat$handlePlay(Player player, CallbackInfo ci) {
        if (this.source() != JukeboxClientPacket.Source.PLAYER) return;

        Minecraft mc = Minecraft.getInstance();
        int selfId = mc.player.getId();
        int ownerId = this.entityId().orElse(selfId);

        if (ownerId == selfId) {
            // 自己的播放：已播时原逻辑会直接 return，拦下改走 playPlayer 更新静音状态
            if (ClientJukeboxHandler.isPlayerPlaying(player)) {
                this.song().ifPresent(song -> ClientJukeboxHandler.playPlayer(
                        new ClientJukeboxHandler.PlayerPlayback(player.getUUID(), null, song, this.muted())));
                ci.cancel();
            }
            // 未播：走原逻辑（key=自己 uuid、跟随自己）
            return;
        }

        // 别人的播放：key=播放者 uuid，声音跟随播放者实体
        Entity owner = mc.level == null ? null : mc.level.getEntity(ownerId);
        if (owner instanceof Player ownerPlayer) {
            UUID ownerUuid = ownerPlayer.getUUID();
            // 记录 entityId→UUID 映射：STOP 时播放者实体可能已卸载，用此映射解析 UUID
            CompatJukeboxPlaybackClient.recordUuid(ownerId, ownerUuid);
            this.song().ifPresent(song -> ClientJukeboxHandler.playPlayer(
                    new ClientJukeboxHandler.PlayerPlayback(ownerUuid, null, song, this.muted())));
            ci.cancel();
            return;
        }
        // 播放者实体未加载（远处/跨维度/刚登录）：挂起待补播，实体加载后由客户端 tick 补播
        this.song().ifPresent(song -> PendingPlayerPlaybacks.pendPlayer(ownerId, song, this.muted()));
        ci.cancel();
    }

    @Inject(method = "handleStop", at = @At("HEAD"), cancellable = true)
    private void netmusic_create_storage_compat$handleStop(Player player, CallbackInfo ci) {
        if (this.source() != JukeboxClientPacket.Source.PLAYER) return;

        Minecraft mc = Minecraft.getInstance();
        int selfId = mc.player.getId();
        int ownerId = this.entityId().orElse(selfId);

        if (ownerId == selfId) {
            return; // 自己：走原逻辑
        }

        // 挂起待补播的播放一并清理
        PendingPlayerPlaybacks.removePlayer(ownerId);
        // 解析播放者 UUID：优先从实体，实体未加载时从映射查（使远处 STOP 也能停止）
        UUID ownerUuid = null;
        Entity owner = mc.level == null ? null : mc.level.getEntity(ownerId);
        if (owner instanceof Player ownerPlayer) {
            ownerUuid = ownerPlayer.getUUID();
        } else {
            ownerUuid = CompatJukeboxPlaybackClient.getUuid(ownerId);
        }
        if (ownerUuid != null) {
            // stopPlayer 会触发 stopPlayer inject → 停并行表 compat 声音 + 清 fxntstorage 孤儿表
            ClientJukeboxHandler.stopPlayer(ownerUuid);
        }
        CompatJukeboxPlaybackClient.removeMapping(ownerId);
        ci.cancel();
    }
}
