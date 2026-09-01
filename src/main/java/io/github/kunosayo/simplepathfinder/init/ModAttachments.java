package io.github.kunosayo.simplepathfinder.init;

import io.github.kunosayo.simplepathfinder.SimplePathFinder;
import io.github.kunosayo.simplepathfinder.data.LocatorDataAttachment;
import io.github.kunosayo.simplepathfinder.data.PlayerBlockDistanceAttachment;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;


public class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, SimplePathFinder.MOD_ID);

    /**
     * 方块实体的定位器数据附件
     * 用于 PathFinderBlockEntity 存储定位器数据
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<LocatorDataAttachment>> LOCATOR_DATA =
            ATTACHMENT_TYPES.register("locator_data",
                    () -> AttachmentType.serializable(() -> new LocatorDataAttachment())
                            .sync(LocatorDataAttachment.STREAM_CODEC)
                            .build());

    /**
     * 玩家的方块距离配置附件
     * 用于存储玩家自定义的方块距离配置
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<PlayerBlockDistanceAttachment>> PLAYER_BLOCK_DISTANCE =
            ATTACHMENT_TYPES.register("player_block_distance",
                    () -> AttachmentType.serializable(() -> new PlayerBlockDistanceAttachment())
                            .sync(PlayerBlockDistanceAttachment.STREAM_CODEC)
                            .copyOnDeath()
                            .build());
}
