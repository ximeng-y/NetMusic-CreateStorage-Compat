package com.xm2nd.netmusiccreatestoragecompat.event;

import com.xm2nd.netmusiccreatestoragecompat.client.CompatJukeboxPlaybackClient;
import com.xm2nd.netmusiccreatestoragecompat.client.NetMusicPlaybackClient;
import com.xm2nd.netmusiccreatestoragecompat.client.PendingPlayerPlaybacks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * 客户端事件：每 tick 补播挂起的原版唱片播放（播放者实体加载后），登出时清空全部播放状态。
 */
@OnlyIn(Dist.CLIENT)
public class ClientEvents {

    /** 原版唱片链路：收包时播放者实体未加载的播放挂起在此，实体加载后补播 */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        PendingPlayerPlaybacks.tick();
    }

    @SubscribeEvent
    public static void onClientLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        NetMusicPlaybackClient.stopAll();
        CompatJukeboxPlaybackClient.stopAll();
        PendingPlayerPlaybacks.clearAll();
    }
}
