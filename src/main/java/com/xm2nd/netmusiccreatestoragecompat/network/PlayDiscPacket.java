package com.xm2nd.netmusiccreatestoragecompat.network;

import com.github.tartaricacid.netmusic.item.ItemMusicCD;
import com.xm2nd.netmusiccreatestoragecompat.NetMusicCreateStorageCompat;
import com.xm2nd.netmusiccreatestoragecompat.client.NetMusicPlaybackClient;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.Optional;

/**
 * NetMusic 播放控制包（服务端 → 客户端）：
 * PLAY（开始播放）/ STOP（停止）/ MUTE（静音状态变更）。
 * 歌曲播完通知走独立的 {@link PlaybackFinishedPacket}（客户端 → 服务端）。
 * 歌曲信息复用 NetMusic 的 {@link ItemMusicCD.SongInfo}（含 STREAM_CODEC），
 * 无需自定义歌曲字段编码。
 */
public record PlayDiscPacket(Action action, Source source, Optional<BlockPos> pos, Optional<Integer> entityId,
                             Optional<ItemMusicCD.SongInfo> songInfo, boolean muted) implements CustomPacketPayload {

    public enum Action { PLAY, STOP, MUTE }

    public enum Source { PLAYER, BLOCK, ENTITY }

    public static final Type<PlayDiscPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(NetMusicCreateStorageCompat.MOD_ID, "play_disc"));

    public static final StreamCodec<FriendlyByteBuf, PlayDiscPacket> STREAM_CODEC = StreamCodec.composite(
            NeoForgeStreamCodecs.enumCodec(Action.class), PlayDiscPacket::action,
            NeoForgeStreamCodecs.enumCodec(Source.class), PlayDiscPacket::source,
            ByteBufCodecs.optional(BlockPos.STREAM_CODEC), PlayDiscPacket::pos,
            ByteBufCodecs.optional(ByteBufCodecs.INT), PlayDiscPacket::entityId,
            ByteBufCodecs.optional(ItemMusicCD.SongInfo.STREAM_CODEC), PlayDiscPacket::songInfo,
            ByteBufCodecs.BOOL, PlayDiscPacket::muted,
            PlayDiscPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        // 频道版本 = mod 版本：客户端与服务端 mod 版本不一致时，
        // NeoForge 频道协商（NetworkComponentNegotiator）会直接拒绝连接，与本 mod 未安装同款处理。
        PayloadRegistrar registrar = event.registrar(NetMusicCreateStorageCompat.MOD_ID).versioned(modVersion());
        registrar.playToClient(TYPE, STREAM_CODEC, PlayDiscPacket::handleClient);
        PlaybackFinishedPacket.registerPayloads(registrar);
    }

    /** mod 版本取自 mods.toml（发布时需与 build.gradle 同步），两侧一致才允许进入 */
    private static String modVersion() {
        return ModList.get().getModContainerById(NetMusicCreateStorageCompat.MOD_ID)
                .orElseThrow().getModInfo().getVersion().toString();
    }

    private static void handleClient(PlayDiscPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.flow().isClientbound()) {
                NetMusicPlaybackClient.handle(packet);
            }
        });
    }
}
