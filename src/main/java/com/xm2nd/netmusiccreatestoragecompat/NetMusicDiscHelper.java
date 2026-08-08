package com.xm2nd.netmusiccreatestoragecompat;

import com.github.tartaricacid.netmusic.item.ItemMusicCD;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * NetMusic 唱片的统一判定工具。
 * 所有 mixin 注入点通过本类判断物品是否为"带歌曲信息的 NetMusic 唱片"。
 */
public final class NetMusicDiscHelper {

    private NetMusicDiscHelper() {
    }

    /** 是否为带歌曲信息的 NetMusic 唱片（无歌曲信息的空 CD 不放行） */
    public static boolean isNetMusicDisc(ItemStack stack) {
        return getSongInfo(stack).isPresent();
    }

    /** 读取 NetMusic 唱片的歌曲信息（ItemMusicCD.getSongInfo 内部已校验物品类型） */
    public static Optional<ItemMusicCD.SongInfo> getSongInfo(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(ItemMusicCD.getSongInfo(stack));
    }
}
