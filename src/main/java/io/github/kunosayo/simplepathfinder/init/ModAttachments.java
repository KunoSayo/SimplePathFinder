package io.github.kunosayo.simplepathfinder.init;

import io.github.kunosayo.simplepathfinder.SimplePathFinder;
import io.github.kunosayo.simplepathfinder.data.LocatorDataAttachment;
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
}
