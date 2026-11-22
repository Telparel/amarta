package com.amarthaloe.immersion;

import net.minecraft.network.FriendlyByteBuf;
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

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = "amarthaloe", bus = EventBusSubscriber.Bus.MOD)
public class ImmersionNetworking {

    public record PlayerVisualState(boolean typing, boolean music, String rpStatus) {}

    // Кэш состояний всех игроков на клиенте (для отрисовки над головой)
    public static final Map<UUID, PlayerVisualState> CLIENT_STATES = new ConcurrentHashMap<>();

    public record PlayerStatePayload(String uuid, boolean typing, boolean music, String rpStatus) implements CustomPacketPayload {
        public static final Type<PlayerStatePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("amarthaloe", "immersion_state"));
        public static final StreamCodec<FriendlyByteBuf, PlayerStatePayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, PlayerStatePayload::uuid,
                ByteBufCodecs.BOOL, PlayerStatePayload::typing,
                ByteBufCodecs.BOOL, PlayerStatePayload::music,
                ByteBufCodecs.STRING_UTF8, PlayerStatePayload::rpStatus,
                PlayerStatePayload::new
        );
        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1");

        registrar.playBidirectional(PlayerStatePayload.TYPE, PlayerStatePayload.STREAM_CODEC, (payload, ctx) -> {
            ctx.enqueueWork(() -> {
                if (ctx.flow().isServerbound() && ctx.player() instanceof ServerPlayer sp) {
                    // Клиент прислал обновление своего статуса набора текста / музыки
                    ImmersionServerEvents.updateClientFlags(sp, payload.typing(), payload.music());
                } else if (ctx.flow().isClientbound()) {
                    try {
                        UUID id = UUID.fromString(payload.uuid());
                        CLIENT_STATES.put(id, new PlayerVisualState(payload.typing(), payload.music(), payload.rpStatus()));
                    } catch (Exception ignored) {}
                }
            });
        });
    }

    public static void broadcastState(ServerPlayer source, boolean typing, boolean music, String rpStatus) {
        PlayerStatePayload pkt = new PlayerStatePayload(source.getStringUUID(), typing, music, rpStatus);
        PacketDistributor.sendToAllPlayers(pkt);
    }
}
