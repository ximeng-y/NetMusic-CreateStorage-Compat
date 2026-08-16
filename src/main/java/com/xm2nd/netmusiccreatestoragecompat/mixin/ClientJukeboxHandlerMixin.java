package com.xm2nd.netmusiccreatestoragecompat.mixin;

import com.xm2nd.netmusiccreatestoragecompat.client.CompatJukeboxEntitySound;
import com.xm2nd.netmusiccreatestoragecompat.client.CompatJukeboxPlaybackClient;
import com.xm2nd.netmusiccreatestoragecompat.client.NetMusicPlaybackClient;
import net.fxnt.fxntstorage.backpack.upgrade.jukebox.ClientJukeboxHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.JukeboxSong;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 与客户端状态表 ClientJukeboxHandler 同步：
 * 面板播放/停止/静音按钮图标每 tick 读这里的查询方法（JukeboxPanel#getState），
 * NetMusic 播放走本 mod 独立状态表（NetMusicPlaybackClient），从不写原版表，
 * 故合并查询结果使按钮图标对 NetMusic 播放同样正确显示。
 * <p>
 * 注意不能直接写原版私有表：stopPlayer 对 null sound 会 requireNonNull 抛 NPE，
 * playXxx 查不到 JukeboxSong 时连表都不记，查询合并是唯一安全的通路。
 * <p>
 * 多人可闻扩展：playPlayer 创建声音时改为跟随播放者实体（自己的跟随自己，
 * 别人的跟随播放者），并在已播时更新静音状态——配合
 * {@link JukeboxClientPacketMixin}（按播放者实体 id 区分自己的/别人的播放）。
 */
@Mixin(ClientJukeboxHandler.class)
public abstract class ClientJukeboxHandlerMixin {

    @Shadow
    private static Map<UUID, ClientJukeboxHandler.PlayerPlayback> playerSounds;

    // ==================== 原版穿戴播放：声音跟随播放者（多人可闻） ====================

    /**
     * 替换原版 EntitySoundInstance（固定跟随监听者本人）：
     * 自己的播放跟随自己（原行为），别人的播放跟随播放者实体（随距离衰减）。
     * 播放者实体不可见时声音照常创建（按 uuid 每 tick 跟随，实体加载后自动续跟淡入）。
     * 注意：play 调用在合成 lambda 方法 lambda$playPlayer$0 内，注入点指向它；
     * @Redirect handler 参数顺序为「被替换调用参数在前，目标方法参数在后」。
     * <p>
     * 关键：fxntstorage 的 lambda 在 play 之后仍把原版实例存入 playerSounds 表（孤儿），
     * 这里把实际播放的 CompatJukeboxEntitySound 存入并行表 {@link CompatJukeboxPlaybackClient}，
     * 使后续 STOP/静音能作用到真实声音实例。
     */
    @Redirect(method = "lambda$playPlayer$0", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/sounds/SoundManager;play(Lnet/minecraft/client/resources/sounds/SoundInstance;)V"))
    private static void netmusic_create_storage_compat$redirectPlay(SoundManager soundManager, SoundInstance soundInstance,
                                                                    Minecraft mc, ClientJukeboxHandler.PlayerPlayback player,
                                                                    ClientLevel level, SoundManager unusedSoundManager,
                                                                    UUID playerId, Holder.Reference<JukeboxSong> holder) {
        if (mc.level == null) {
            return;
        }
        // 播放者实体可能未加载（远处播放）：声音按 uuid 每 tick 解析跟随，
        // 实体加载（走近）后自动续跟淡入，不再因实体不可见而吞掉播放
        Entity owner = mc.level.getPlayerByUUID(player.playerId());
        CompatJukeboxEntitySound sound = new CompatJukeboxEntitySound(holder.value(),
                owner instanceof Player followPlayer ? followPlayer : null, player.playerId(), player.muted());
        soundManager.play(sound);
        // 存入并行表：确保 stopPlayer/静音切换能作用到实际播放的声音实例
        // （fxntstorage 的 playerSounds 表存的是从未播放的原版实例，无法用于停止/静音）
        CompatJukeboxPlaybackClient.onSoundCreated(player.playerId(), sound);
    }

    // ==================== 原版穿戴播放：已播时更新静音状态 / 换歌重建 ====================

    /**
     * 服务端用 PLAY 包重发表达静音变化（无 MUTE action），原逻辑在已播时直接 return，
     * 这里拦下：查并行表（实际播放的声音实例），有活跃声音时按"同歌→静音更新 / 不同歌→停旧重建"处理。
     * 同歌：更新静音状态（绝对值）+ cancel，不重建声音。
     * 不同歌：停旧 compat 声音，放行原逻辑重建（fxntstorage playPlayer 门控 isActive(孤儿)=false → 新建）。
     */
    @Inject(method = "playPlayer", at = @At("HEAD"), cancellable = true)
    private static void netmusic_create_storage_compat$playPlayerMuted(ClientJukeboxHandler.PlayerPlayback player, CallbackInfo ci) {
        // 查并行表（实际播放的声音实例），而非 fxntstorage 的孤儿表
        CompatJukeboxEntitySound sound = CompatJukeboxPlaybackClient.getSound(player.playerId());
        if (sound == null) {
            // 并行表无活跃声音：放行原逻辑（playPlayer → lambda → @Redirect → 创建并存并行表）
            return;
        }
        // 并行表有活跃声音：判断同歌还是换歌
        ClientJukeboxHandler.PlayerPlayback old = playerSounds.get(player.playerId());
        boolean sameSong = old != null && Objects.equals(old.song(), player.song());
        if (sameSong) {
            // 同歌 → 静音更新
            sound.setMuted(player.muted());
            // 同步 fxntstorage 表 muted 字段，保持面板图标一致
            if (old != null) {
                playerSounds.put(player.playerId(), new ClientJukeboxHandler.PlayerPlayback(player.playerId(), old.sound(), old.song(), player.muted()));
            }
            ci.cancel();
        } else {
            // 不同歌 → 停旧声，放行原逻辑重建
            CompatJukeboxPlaybackClient.stopByUuid(player.playerId());
            // 不 cancel：playPlayer 继续执行（内部 stopPlayer 会触发下面的 inject，并行表已清 → no-op）
        }
    }

    /**
     * 拦截 fxntstorage 的 stopPlayer：同步停止并行表中的实际声音实例。
     * 无论 stopPlayer 从哪里被调用（handleStop、playPlayer 内部清旧、面板停止按钮），
     * 都确保实际播放的 CompatJukeboxEntitySound 被停止。
     */
    @Inject(method = "stopPlayer", at = @At("HEAD"))
    private static void netmusic_create_storage_compat$stopPlayer(UUID id, CallbackInfo ci) {
        CompatJukeboxPlaybackClient.stopByUuid(id);
    }

    // ==================== NetMusic 播放状态合并 ====================

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
