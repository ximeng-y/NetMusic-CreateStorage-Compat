package com.xm2nd.netmusiccreatestoragecompat.mixin;

import com.xm2nd.netmusiccreatestoragecompat.client.NetMusicPlaybackClient;
import net.fxnt.fxntstorage.backpack.upgrade.jukebox.ClientJukeboxHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 与客户端状态表 ClientJukeboxHandler 同步：
 * 面板播放/停止/静音按钮图标每 tick 读这里的查询方法（JukeboxPanel#getState），
 * NetMusic 播放走本 mod 独立状态表（NetMusicPlaybackClient），从不写原版表，
 * 故合并查询结果使按钮图标对 NetMusic 播放同样正确显示。
 * <p>
 * 注意不能直接写原版私有表：stopPlayer 对 null sound 会 requireNonNull 抛 NPE，
 * playXxx 查不到 JukeboxSong 时连表都不记，查询合并是唯一安全的通路。
 */
@Mixin(ClientJukeboxHandler.class)
public abstract class ClientJukeboxHandlerMixin {

    @Inject(method = "isPlayerPlaying", at = @At("HEAD"), cancellable = true)
    private static void netmusic_create_storage_compat$isPlayerPlaying(Player player, CallbackInfoReturnable<Boolean> cir) {
        if (NetMusicPlaybackClient.isPlayerPlaying()) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "isPlayerMuted", at = @At("HEAD"), cancellable = true)
    private static void netmusic_create_storage_compat$isPlayerMuted(Player player, CallbackInfoReturnable<Boolean> cir) {
        if (NetMusicPlaybackClient.isPlayerMuted()) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "isBlockPlaying", at = @At("HEAD"), cancellable = true)
    private static void netmusic_create_storage_compat$isBlockPlaying(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (NetMusicPlaybackClient.isBlockPlaying(pos)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "isBlockMuted", at = @At("HEAD"), cancellable = true)
    private static void netmusic_create_storage_compat$isBlockMuted(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (NetMusicPlaybackClient.isBlockMuted(pos)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "isEntityPlaying", at = @At("HEAD"), cancellable = true)
    private static void netmusic_create_storage_compat$isEntityPlaying(int entityId, CallbackInfoReturnable<Boolean> cir) {
        if (NetMusicPlaybackClient.isEntityPlaying(entityId)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "isEntityMuted", at = @At("HEAD"), cancellable = true)
    private static void netmusic_create_storage_compat$isEntityMuted(int entityId, CallbackInfoReturnable<Boolean> cir) {
        if (NetMusicPlaybackClient.isEntityMuted(entityId)) {
            cir.setReturnValue(true);
        }
    }
}
