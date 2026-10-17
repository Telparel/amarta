package com.amarthaloe.network;

import com.amarthaloe.data.PassportData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = "amarthaloe", bus = EventBusSubscriber.Bus.MOD)
public class PassportNetworking {

    public record PassportPayload(CompoundTag tag) implements CustomPacketPayload {
        public static final Type<PassportPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("amarthaloe", "passport_sync"));
        public static final StreamCodec<FriendlyByteBuf, PassportPayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.COMPOUND_TAG, PassportPayload::tag,
                PassportPayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1");

        registrar.playBidirectional(
                PassportPayload.TYPE,
                PassportPayload.STREAM_CODEC,
                (payload, context) -> {
                    context.enqueueWork(() -> {
                        if (context.flow().isServerbound() && context.player() instanceof ServerPlayer serverPlayer) {
                            PassportData current = serverPlayer.getData(PassportData.PASSPORT);
                            // Защита: разрешаем запись ТОЛЬКО если паспорт еще не был сохранен
                            if (!current.isLocked) {
                                current.deserializeNBT(serverPlayer.registryAccess(), payload.tag());
                                current.isLocked = true;
                                serverPlayer.setData(PassportData.PASSPORT, current);
                                serverPlayer.refreshDisplayName();
                                
                                // Отправляем подтверждение обратно клиенту
                                PacketDistributor.sendToPlayer(serverPlayer, new PassportPayload(current.serializeNBT(serverPlayer.registryAccess())));
                                serverPlayer.sendSystemMessage(Component.literal("§6[Amarthaloe] §aВаш паспорт успешно утвержден! Изменить данные теперь может только администрация."));
                            }
                        } else if (context.flow().isClientbound()) {
                            // Клиент получает актуальные данные с сервера
                            context.player().getData(PassportData.PASSPORT).deserializeNBT(context.player().registryAccess(), payload.tag());
                        }
                    });
                }
        );
    }
}
