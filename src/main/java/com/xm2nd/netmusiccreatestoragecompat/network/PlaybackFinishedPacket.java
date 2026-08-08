package com.xm2nd.netmusiccreatestoragecompat.network;

import com.xm2nd.netmusiccreatestoragecompat.NetMusicCreateStorageCompat;
import com.xm2nd.netmusiccreatestoragecompat.server.NetMusicPlaybackServer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.Optional;

/**
 * 歌曲播完通知包（客户端 → 服务端）：
 * NetMusic 单曲在客户端自然播完后发送，服务端据此清理播放状态，
 * 使面板播放状态恢复诚实（不再显示"播放中"）。
 * <p>
 * 与 {@link PlayDiscPacket} 分离注册（NeoForge 不允许同一 payload type 双向注册）。
 */
public record PlaybackFinishedPacket(PlayDiscPacket.Source source, Optional<BlockPos> pos, Optional<Integer> entityId)
        implements CustomPacketPayload {

    public static final Type<PlaybackFinishedPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(NetMusicCreateStorageCompat.MOD_ID, "play_disc_finished"));

    public static final StreamCodec<FriendlyByteBuf, PlaybackFinishedPacket> STREAM_CODEC = StreamCodec.composite(
            NeoForgeStreamCodecs.enumCodec(PlayDiscPacket.Source.class), PlaybackFinishedPacket::source,
            ByteBufCodecs.optional(BlockPos.STREAM_CODEC), PlaybackFinishedPacket::pos,
            ByteBufCodecs.optional(ByteBufCodecs.INT), PlaybackFinishedPacket::entityId,
            PlaybackFinishedPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void registerPayloads(PayloadRegistrar registrar) {
        registrar.playToServer(TYPE, STREAM_CODEC, PlaybackFinishedPacket::handleServer);
    }

    private static void handleServer(PlaybackFinishedPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                NetMusicPlaybackServer.onFinished(player, packet);
            }
        });
    }
}
