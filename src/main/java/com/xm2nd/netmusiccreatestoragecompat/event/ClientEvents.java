package com.xm2nd.netmusiccreatestoragecompat.event;

import com.xm2nd.netmusiccreatestoragecompat.client.NetMusicPlaybackClient;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/**
 * 客户端事件：登出时清空所有 NetMusic 播放声音。
 */
@OnlyIn(Dist.CLIENT)
public class ClientEvents {

    @SubscribeEvent
    public static void onClientLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        NetMusicPlaybackClient.stopAll();
    }
}
