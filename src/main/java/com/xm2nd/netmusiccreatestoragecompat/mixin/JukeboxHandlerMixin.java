package com.xm2nd.netmusiccreatestoragecompat.mixin;

import com.xm2nd.netmusiccreatestoragecompat.server.NetMusicPlaybackServer;
import net.fxnt.fxntstorage.backpack.upgrade.jukebox.JukeboxHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 与原版 JukeboxHandler 状态机同步：
 * <ul>
 *   <li>停止入口：换碟/取碟/登出/重生/换装备等场景都会调到这里，NetMusic 状态随之自动清理</li>
 *   <li>状态查询：合并 NetMusic 播放状态，使面板显示、播放/停止切换判定、
 *       音符粒子（tickActive 依赖 isBlockPlaying）等原版逻辑对 NetMusic 播放同样生效</li>
 * </ul>
 */
@Mixin(JukeboxHandler.class)
public abstract class JukeboxHandlerMixin {

    // ==================== 停止入口：同步清理 ====================

    @Inject(method = "stopPlayer", at = @At("HEAD"))
    private static void netmusic_create_storage_compat$stopPlayer(ServerPlayer player, CallbackInfo ci) {
        NetMusicPlaybackServer.stopPlayer(player);
    }

    @Inject(method = "stopBlock", at = @At("HEAD"))
    private static void netmusic_create_storage_compat$stopBlock(Level level, BlockPos pos, CallbackInfo ci) {
        NetMusicPlaybackServer.stopBlock(level, pos);
    }

    @Inject(method = "stopEntity", at = @At("HEAD"))
    private static void netmusic_create_storage_compat$stopEntity(ServerPlayer listener, int entityId, CallbackInfo ci) {
        NetMusicPlaybackServer.stopEntity(listener, entityId);
    }

    // ==================== 状态查询：合并 NetMusic 播放状态 ====================

    @Inject(method = "isPlayerPlaying", at = @At("HEAD"), cancellable = true)
    private static void netmusic_create_storage_compat$isPlayerPlaying(ServerPlayer player, CallbackInfoReturnable<Boolean> cir) {
        if (NetMusicPlaybackServer.isPlayerPlaying(player)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "isBlockPlaying", at = @At("HEAD"), cancellable = true)
    private static void netmusic_create_storage_compat$isBlockPlaying(Level level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (NetMusicPlaybackServer.isBlockPlaying(level, pos)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "isEntityPlaying", at = @At("HEAD"), cancellable = true)
    private static void netmusic_create_storage_compat$isEntityPlaying(int entityId, CallbackInfoReturnable<Boolean> cir) {
        if (NetMusicPlaybackServer.isEntityPlaying(entityId)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "isPlayerMuted", at = @At("HEAD"), cancellable = true)
    private static void netmusic_create_storage_compat$isPlayerMuted(ServerPlayer player, CallbackInfoReturnable<Boolean> cir) {
        if (NetMusicPlaybackServer.isPlayerMuted(player)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "isBlockMuted", at = @At("HEAD"), cancellable = true)
    private static void netmusic_create_storage_compat$isBlockMuted(Level level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (NetMusicPlaybackServer.isBlockMuted(level, pos)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "isEntityMuted", at = @At("HEAD"), cancellable = true)
    private static void netmusic_create_storage_compat$isEntityMuted(int entityId, CallbackInfoReturnable<Boolean> cir) {
        if (NetMusicPlaybackServer.isEntityMuted(entityId)) {
            cir.setReturnValue(true);
        }
    }
}
