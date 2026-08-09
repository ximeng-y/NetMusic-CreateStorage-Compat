package com.xm2nd.netmusiccreatestoragecompat.mixin;

import com.xm2nd.netmusiccreatestoragecompat.client.CompatJukeboxEntitySound;
import com.xm2nd.netmusiccreatestoragecompat.client.NetMusicPlaybackClient;
import net.fxnt.fxntstorage.backpack.upgrade.jukebox.ClientJukeboxHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
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
     * 播放者实体不可见时不播（出视野即无声，与原版声音存活语义一致）。
     * 注意：play 调用在合成 lambda 方法 lambda$playPlayer$0 内，注入点指向它；
     * @Redirect handler 参数顺序为「被替换调用参数在前，目标方法参数在后」。
     */
    @Redirect(method = "lambda$playPlayer$0", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/sounds/SoundManager;play(Lnet/minecraft/client/resources/sounds/SoundInstance;)V"))
    private static void netmusic_create_storage_compat$redirectPlay(SoundManager soundManager, SoundInstance soundInstance,
                                                                    Minecraft mc, ClientJukeboxHandler.PlayerPlayback player,
                                                                    ClientLevel level, SoundManager unusedSoundManager,
                                                                    UUID playerId, Holder.Reference<JukeboxSong> holder) {
        if (mc.level == null) {
            return;
        }
        Entity owner = mc.level.getPlayerByUUID(player.playerId());
        if (!(owner instanceof Player followPlayer)) {
            return;
        }
        soundManager.play(new CompatJukeboxEntitySound(holder.value(), followPlayer, player.muted()));
    }

    // ==================== 原版穿戴播放：已播时更新静音状态 ====================

    /**
     * 服务端用 PLAY 包重发表达静音变化（无 MUTE action），原逻辑在已播时直接 return，
     * 这里拦下：表里已有该播放者 → 只更新静音状态（绝对值），不重建声音。
     * 声音实例是本 mod 的 {@link CompatJukeboxEntitySound}（public），可直接调用。
     */
    @Inject(method = "playPlayer", at = @At("HEAD"), cancellable = true)
    private static void netmusic_create_storage_compat$playPlayerMuted(ClientJukeboxHandler.PlayerPlayback player, CallbackInfo ci) {
        ClientJukeboxHandler.PlayerPlayback old = playerSounds.get(player.playerId());
        if (old == null) {
            return;
        }
        // 声音实例静态类型是原版 private 嵌套类，先提升到公共父类型再判断
        AbstractTickableSoundInstance sound = old.sound();
        if (sound instanceof CompatJukeboxEntitySound compatSound) {
            compatSound.setMuted(player.muted());
        }
        playerSounds.put(player.playerId(), new ClientJukeboxHandler.PlayerPlayback(player.playerId(), old.sound(), old.song(), player.muted()));
        ci.cancel();
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
