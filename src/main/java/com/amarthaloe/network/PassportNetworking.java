package com.amarthaloe.network;

import com.amarthaloe.data.PassportData;
import com.amarthaloe.rpg.Faction;
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

    // Пакет смены активного титула в чате
    public record SelectTitlePayload(String factionId) implements CustomPacketPayload {
        public static final Type<SelectTitlePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("amarthaloe", "select_title"));
        public static final StreamCodec<FriendlyByteBuf, SelectTitlePayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, SelectTitlePayload::factionId,
                SelectTitlePayload::new
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
                            if (!current.isLocked) {
                                // Сохраняем текущую репутацию перед записью анкеты
                                CompoundTag incoming = payload.tag();
                                current.rpName = incoming.getString("rpName");
                                current.gender = incoming.getString("gender");
                                current.ageAndBirth = incoming.getString("ageAndBirth");
                                current.race = incoming.getString("race");
                                current.heightWeight = incoming.getString("heightWeight");
                                current.hair = incoming.getString("hair");
                                current.eyes = incoming.getString("eyes");
                                current.features = incoming.getString("features");
                                current.voice = incoming.getString("voice");
                                current.optional = incoming.getString("optional");
                                current.isLocked = true;

                                serverPlayer.setData(PassportData.PASSPORT, current);
                                serverPlayer.refreshDisplayName();

                                PacketDistributor.sendToPlayer(serverPlayer, new PassportPayload(current.serializeNBT(serverPlayer.registryAccess())));
                                serverPlayer.sendSystemMessage(Component.literal("§6[Исономия] §aВаш паспорт зарегистрирован в реестре Либры!"));
                            }
                        } else if (context.flow().isClientbound()) {
                            context.player().getData(PassportData.PASSPORT).deserializeNBT(context.player().registryAccess(), payload.tag());
                        }
                    });
                }
        );

        registrar.playToServer(
                SelectTitlePayload.TYPE,
                SelectTitlePayload.STREAM_CODEC,
                (payload, context) -> {
                    context.enqueueWork(() -> {
                        if (context.player() instanceof ServerPlayer sp) {
                            PassportData data = sp.getData(PassportData.PASSPORT);
                            Faction f = Faction.byId(payload.factionId());
                            if (f == null || Math.abs(data.getRep(f)) < 10) {
                                data.activeTitleFaction = "";
                            } else {
                                data.activeTitleFaction = f.id;
                            }
                            sp.setData(PassportData.PASSPORT, data);
                            sp.refreshDisplayName();
                            PacketDistributor.sendToPlayer(sp, new PassportPayload(data.serializeNBT(sp.registryAccess())));
                        }
                    });
                }
        );
    }
}
