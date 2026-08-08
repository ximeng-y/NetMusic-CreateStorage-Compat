package com.xm2nd.netmusiccreatestoragecompat.mixin;

import com.xm2nd.netmusiccreatestoragecompat.NetMusicDiscHelper;
import com.xm2nd.netmusiccreatestoragecompat.network.PlayDiscPacket;
import com.xm2nd.netmusiccreatestoragecompat.server.NetMusicPlaybackServer;
import net.fxnt.fxntstorage.backpack.client.menu.BackpackMenu;
import net.fxnt.fxntstorage.backpack.inventory.BackpackSlotLayout;
import net.fxnt.fxntstorage.backpack.upgrade.jukebox.JukeboxUpgradeHelper;
import net.fxnt.fxntstorage.network.packet.JukeboxServerPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;

/**
 * 服务端播放入口桥接：当唱片槽内是 NetMusic 唱片时，
 * 改走 NetMusic 播放链路（NetMusicPlaybackServer），不执行原版播放逻辑。
 * 静音切换同步 NetMusic 播放状态。
 */
@Mixin(JukeboxServerPacket.class)
public abstract class JukeboxServerPacketMixin {

    /** 穿戴/方块播放入口（原版逻辑仅读取 jukebox_playable 组件，NetMusic 唱片读不到） */
    @Inject(method = "playMusic", at = @At("HEAD"), cancellable = true)
    private void netmusic_create_storage_compat$playMusic(ServerPlayer player, @Nullable BlockPos blockPos, CallbackInfo ci) {
        JukeboxUpgradeHelper.getMusicDisc(player, player.level(), blockPos).ifPresent(disc -> {
            NetMusicDiscHelper.getSongInfo(disc).ifPresent(songInfo -> {
                if (blockPos == null) {
                    NetMusicPlaybackServer.playForPlayer(player, songInfo);
                } else {
                    NetMusicPlaybackServer.playForBlock(player, blockPos, songInfo);
                }
                ci.cancel();
            });
        });
    }

    /** 装置（contraption）播放入口 */
    @Inject(method = "playMusicForEntity", at = @At("HEAD"), cancellable = true)
    private void netmusic_create_storage_compat$playMusicForEntity(ServerPlayer player, int entityId, CallbackInfo ci) {
        if (!(player.containerMenu instanceof BackpackMenu menu)) return;
        if (menu.getContraptionId() != entityId) return;

        ItemStack disc = menu.container.getItemHandler()
                .getStackInSlot(BackpackSlotLayout.createLayout().jukeboxDiscs().getStartIndex());
        NetMusicDiscHelper.getSongInfo(disc).ifPresent(songInfo -> {
            NetMusicPlaybackServer.playForEntity(player, entityId, songInfo);
            ci.cancel();
        });
    }

    /** 静音切换：同步 NetMusic 播放的静音状态（原版表无 NetMusic 条目，原逻辑照常执行无效果） */
    @Inject(method = "handleToggleMuted", at = @At("HEAD"))
    private void netmusic_create_storage_compat$toggleMuted(ServerPlayer player, CallbackInfo ci) {
        JukeboxServerPacket self = (JukeboxServerPacket) (Object) this;
        PlayDiscPacket.Source source = switch (self.source()) {
            case PLAYER -> PlayDiscPacket.Source.PLAYER;
            case BLOCK -> PlayDiscPacket.Source.BLOCK;
            case ENTITY -> PlayDiscPacket.Source.ENTITY;
        };
        NetMusicPlaybackServer.toggleMuted(player, source, self.pos(), self.entityId());
    }
}
