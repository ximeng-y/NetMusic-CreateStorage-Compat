package com.xm2nd.netmusiccreatestoragecompat.event;

import com.xm2nd.netmusiccreatestoragecompat.server.NetMusicPlaybackServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * 服务端事件：玩家登录时补发 NetMusic 播放状态
 * （附近方块播放 + 本人穿戴播放，与 fxntstorage syncBlocksToPlayers 语义一致）。
 */
public class ServerEvents {

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            NetMusicPlaybackServer.syncToPlayer(player);
        }
    }
}
