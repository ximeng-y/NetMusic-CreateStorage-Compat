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
        PayloadRegistrar registrar = event.registrar(NetMusicCreateStorageCompat.MOD_ID).versioned("1.0");
        registrar.playToClient(TYPE, STREAM_CODEC, PlayDiscPacket::handleClient);
        PlaybackFinishedPacket.registerPayloads(registrar);
    }

    private static void handleClient(PlayDiscPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.flow().isClientbound()) {
                NetMusicPlaybackClient.handle(packet);
            }
        });
    }
}
