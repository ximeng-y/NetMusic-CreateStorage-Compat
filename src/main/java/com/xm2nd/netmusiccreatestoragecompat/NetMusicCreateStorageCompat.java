package com.xm2nd.netmusiccreatestoragecompat;

import com.xm2nd.netmusiccreatestoragecompat.event.ClientEvents;
import com.xm2nd.netmusiccreatestoragecompat.event.ServerEvents;
import com.xm2nd.netmusiccreatestoragecompat.network.PlayDiscPacket;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * NetMusic-CreateStorage-Compat：让 NetMusic 的音乐唱片可放入 Create:Storage
 * 背包的唱片机升级槽位并播放（WORN/BLOCK/CONTRAPTION 三种形态）。
 * <p>
 * 架构：双状态机桥接。fxntstorage 原版链路不动，NetMusic 播放走本 mod 的
 * 服务端状态表（NetMusicPlaybackServer）+ 自定义 payload（PlayDiscPacket）+
 * 客户端流式播放（NetMusicPlaybackClient），通过 mixin 挂在 fxntstorage 的
 * 触发点（槽位判定/播放入口/停止/静音/升级移除）上同步两套状态。
 */
@Mod(NetMusicCreateStorageCompat.MOD_ID)
public class NetMusicCreateStorageCompat {

    public static final String MOD_ID = "netmusic_create_storage_compat";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public NetMusicCreateStorageCompat(IEventBus modEventBus) {
        if (!ModList.get().isLoaded("fxntstorage") || !ModList.get().isLoaded("netmusic")) {
            LOGGER.warn("fxntstorage 或 netmusic 未加载，兼容功能不可用（正常情况不应发生，依赖已在 mods.toml 声明 required）");
            return;
        }

        modEventBus.addListener(PlayDiscPacket::registerPayloads);
        NeoForge.EVENT_BUS.register(ServerEvents.class);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            NeoForge.EVENT_BUS.register(ClientEvents.class);
        }
    }
}
