package com.amarthaloe.music;

import com.amarthaloe.data.PassportData;
import com.amarthaloe.network.PassportNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.*;

@EventBusSubscriber(modid = "amarthaloe", bus = EventBusSubscriber.Bus.MOD)
public class MusicNetworking {

    private static final Map<UUID, Set<UUID>> ENSEMBLES = new HashMap<>();
    private static final Map<UUID, UUID> PENDING_INVITES = new HashMap<>();

    public static List<String> CLIENT_SERVER_ARCHIVE = new ArrayList<>();
    public static String CLIENT_PENDING_INVITE_FROM = "";
    public static String CLIENT_ENSEMBLE_PARTNER = "";

    // 1. Пакетная отправка нот (1 легкий пакет за тик вместо десятков)
    public record PlayNotesBatchPayload(int instrumentId, byte[] notes, float volume) implements CustomPacketPayload {
        public static final Type<PlayNotesBatchPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("amarthaloe", "music_notes_batch"));
        public static final StreamCodec<FriendlyByteBuf, PlayNotesBatchPayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, PlayNotesBatchPayload::instrumentId,
                ByteBufCodecs.BYTE_ARRAY, PlayNotesBatchPayload::notes,
                ByteBufCodecs.FLOAT, PlayNotesBatchPayload::volume,
                PlayNotesBatchPayload::new
        );
        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // 2. Пакет передачи MIDI-файла
    public record MidiFilePayload(String trackName, byte[] data) implements CustomPacketPayload {
        public static final Type<MidiFilePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("amarthaloe", "music_midi"));
        public static final StreamCodec<FriendlyByteBuf, MidiFilePayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, MidiFilePayload::trackName,
                ByteBufCodecs.BYTE_ARRAY, MidiFilePayload::data,
                MidiFilePayload::new
        );
        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // 3. Пакет действий архива и ансамбля
    public record MusicActionPayload(String action, String arg1, String arg2) implements CustomPacketPayload {
        public static final Type<MusicActionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("amarthaloe", "music_action"));
        public static final StreamCodec<FriendlyByteBuf, MusicActionPayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, MusicActionPayload::action,
                ByteBufCodecs.STRING_UTF8, MusicActionPayload::arg1,
                ByteBufCodecs.STRING_UTF8, MusicActionPayload::arg2,
                MusicActionPayload::new
        );
        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1");

        registrar.playToServer(PlayNotesBatchPayload.TYPE, PlayNotesBatchPayload.STREAM_CODEC, (payload, ctx) -> {
            ctx.enqueueWork(() -> {
                if (ctx.player() instanceof ServerPlayer sp) {
                    playNotesInWorld(sp, payload.instrumentId(), payload.notes(), payload.volume());
                }
            });
        });

        registrar.playBidirectional(MidiFilePayload.TYPE, MidiFilePayload.STREAM_CODEC, (payload, ctx) -> {
            ctx.enqueueWork(() -> {
                if (ctx.flow().isServerbound() && ctx.player() instanceof ServerPlayer sp) {
                    MusicStorage storage = MusicStorage.get(sp.server);
                    if (storage.saveTrack(payload.trackName(), payload.data())) {
                        sp.sendSystemMessage(Component.literal("§6[Музыка] §aМелодия «§e" + payload.trackName() + "§a» загружена в общий архив сервера!"));
                        broadcastArchiveList(sp.server);
                    } else {
                        sp.sendSystemMessage(Component.literal("§c[Музыка] Ошибка загрузки: файл пуст или превышает 256 КБ."));
                    }
                } else if (ctx.flow().isClientbound()) {
                    MidiPlayerEngine.saveDownloadedTrack(payload.trackName(), payload.data());
                }
            });
        });

        registrar.playBidirectional(MusicActionPayload.TYPE, MusicActionPayload.STREAM_CODEC, (payload, ctx) -> {
            ctx.enqueueWork(() -> {
                if (ctx.flow().isServerbound() && ctx.player() instanceof ServerPlayer sp) {
                    handleServerAction(sp, payload.action(), payload.arg1(), payload.arg2());
                } else if (ctx.flow().isClientbound()) {
                    handleClientAction(payload.action(), payload.arg1(), payload.arg2());
                }
            });
        });
    }

    private static void playNotesInWorld(ServerPlayer sp, int instId, byte[] notes, float volume) {
        if (notes == null || notes.length == 0) return;

        SoundEvent sound = getInstrumentSound(instId);
        ServerLevel level = sp.serverLevel();
        float vol = Math.clamp(volume, 0.1f, 1.5f);

        int count = Math.min(notes.length, 4); // Не больше 4 одновременных нот, чтобы не забивать каналы OpenAL
        for (int i = 0; i < count; i++) {
            int clampedNote = Math.clamp(notes[i], 0, 24);
            float pitch = (float) Math.pow(2.0, (clampedNote - 12.0) / 12.0);
            // Передаем sp первым аргументом: звук слышат все вокруг, КРОМЕ самого музыканта (он слышит его локально без пинга!)
            level.playSound(sp, sp.getX(), sp.getY(), sp.getZ(), sound, SoundSource.RECORDS, vol * 2.0f, pitch);
        }

        // Одна частица ноты на всю пачку — минимальная нагрузка на сервер
        double noteColor = Math.clamp(notes[0], 0, 24) / 24.0;
        level.sendParticles(ParticleTypes.NOTE, sp.getX(), sp.getY() + 2.2, sp.getZ(), 0, noteColor, 0.0, 0.0, 1.0);
    }

    public static SoundEvent getInstrumentSound(int id) {
        return switch (id) {
            case 1 -> SoundEvents.NOTE_BLOCK_GUITAR.value();
            case 2 -> SoundEvents.NOTE_BLOCK_FLUTE.value();
            case 3 -> SoundEvents.NOTE_BLOCK_BASS.value();
            case 4 -> SoundEvents.NOTE_BLOCK_BELL.value();
            case 5 -> SoundEvents.NOTE_BLOCK_BANJO.value();
            case 6 -> SoundEvents.NOTE_BLOCK_CHIME.value();
            case 7 -> SoundEvents.NOTE_BLOCK_PLING.value();
            case 8 -> SoundEvents.NOTE_BLOCK_SNARE.value();
            default -> SoundEvents.NOTE_BLOCK_HARP.value();
        };
    }

    private static void broadcastArchiveList(net.minecraft.server.MinecraftServer server) {
        String joined = String.join("|", MusicStorage.get(server).getTrackNames());
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(p, new MusicActionPayload("ARCHIVE_LIST", joined, ""));
        }
    }

    private static void handleServerAction(ServerPlayer sender, String action, String arg1, String arg2) {
        MusicStorage storage = MusicStorage.get(sender.server);
        PassportData sData = sender.getData(PassportData.PASSPORT);
        String senderName = sData.rpName.isEmpty() ? sender.getName().getString() : sData.rpName;

        switch (action) {
            case "REQ_ARCHIVE" -> {
                String joined = String.join("|", storage.getTrackNames());
                PacketDistributor.sendToPlayer(sender, new MusicActionPayload("ARCHIVE_LIST", joined, ""));
            }
            case "DOWNLOAD_TRACK" -> {
                byte[] bytes = storage.getTrack(arg1);
                if (bytes != null) {
                    PacketDistributor.sendToPlayer(sender, new MidiFilePayload(arg1, bytes));
                    sender.sendSystemMessage(Component.literal("§6[Музыка] §aТрек «§e" + arg1 + "§a» скачан из архива сервера!"));
                }
            }
            case "ENSEMBLE_INVITE" -> {
                ServerPlayer target = PassportNetworking.findPlayerByNameOrRpName(sender, arg1);
                if (target == null || target.getUUID().equals(sender.getUUID())) {
                    sender.sendSystemMessage(Component.literal("§c[Ансамбль] Игрок «" + arg1 + "» не найден в сети."));
                    return;
                }
                PENDING_INVITES.put(target.getUUID(), sender.getUUID());
                sender.sendSystemMessage(Component.literal("§6[Ансамбль] §aПриглашение сыграть вместе отправлено игроку §e" + target.getDisplayName().getString() + "§a!"));
                target.sendSystemMessage(Component.literal("§6[Ансамбль] §e" + senderName + " §fприглашает вас сыграть вместе! Откройте проигрыватель на §b[N]§f."));
                target.playNotifySound(SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.MASTER, 0.9f, 1.2f);
                PacketDistributor.sendToPlayer(target, new MusicActionPayload("INVITE_RECEIVED", senderName, ""));
            }
            case "ENSEMBLE_ACCEPT" -> {
                UUID leaderId = PENDING_INVITES.remove(sender.getUUID());
                if (leaderId == null) return;
                ServerPlayer leader = sender.server.getPlayerList().getPlayer(leaderId);
                if (leader == null) return;

                Set<UUID> group = ENSEMBLES.computeIfAbsent(leaderId, k -> new HashSet<>());
                group.add(leaderId);
                group.add(sender.getUUID());
                ENSEMBLES.put(sender.getUUID(), group);

                PassportData lData = leader.getData(PassportData.PASSPORT);
                String leaderName = lData.rpName.isEmpty() ? leader.getName().getString() : lData.rpName;

                PacketDistributor.sendToPlayer(leader, new MusicActionPayload("ENSEMBLE_JOINED", senderName, ""));
                PacketDistributor.sendToPlayer(sender, new MusicActionPayload("ENSEMBLE_JOINED", leaderName, ""));
                leader.sendSystemMessage(Component.literal("§6[Ансамбль] §a" + senderName + " присоединился к вашему ансамблю! Нажмите «Играть» для синхронного старта."));
                sender.sendSystemMessage(Component.literal("§6[Ансамбль] §aВы в ансамбле с §e" + leaderName + "§a! Выберите свой инструмент и дорожку."));
            }
            case "ENSEMBLE_LEAVE" -> {
                Set<UUID> group = ENSEMBLES.remove(sender.getUUID());
                if (group != null) {
                    group.remove(sender.getUUID());
                    for (UUID uid : new ArrayList<>(group)) {
                        ServerPlayer member = sender.server.getPlayerList().getPlayer(uid);
                        if (member != null) {
                            if (group.size() <= 1) ENSEMBLES.remove(uid);
                            PacketDistributor.sendToPlayer(member, new MusicActionPayload("ENSEMBLE_LEFT", "", ""));
                        }
                    }
                }
                PacketDistributor.sendToPlayer(sender, new MusicActionPayload("ENSEMBLE_LEFT", "", ""));
            }
            case "ENSEMBLE_START" -> {
                Set<UUID> group = ENSEMBLES.get(sender.getUUID());
                if (group != null && !group.isEmpty()) {
                    for (UUID uid : group) {
                        ServerPlayer member = sender.server.getPlayerList().getPlayer(uid);
                        if (member != null) {
                            PacketDistributor.sendToPlayer(member, new MusicActionPayload("SYNC_START", arg1, ""));
                        }
                    }
                } else {
                    PacketDistributor.sendToPlayer(sender, new MusicActionPayload("SYNC_START", arg1, ""));
                }
            }
            case "ENSEMBLE_STOP" -> {
                Set<UUID> group = ENSEMBLES.get(sender.getUUID());
                if (group != null) {
                    for (UUID uid : group) {
                        ServerPlayer member = sender.server.getPlayerList().getPlayer(uid);
                        if (member != null) {
                            PacketDistributor.sendToPlayer(member, new MusicActionPayload("SYNC_STOP", "", ""));
                        }
                    }
                } else {
                    PacketDistributor.sendToPlayer(sender, new MusicActionPayload("SYNC_STOP", "", ""));
                }
            }
        }
    }

    private static void handleClientAction(String action, String arg1, String arg2) {
        switch (action) {
            case "ARCHIVE_LIST" -> {
                CLIENT_SERVER_ARCHIVE.clear();
                if (!arg1.isEmpty()) {
                    CLIENT_SERVER_ARCHIVE.addAll(Arrays.asList(arg1.split("\\|")));
                }
            }
            case "INVITE_RECEIVED" -> CLIENT_PENDING_INVITE_FROM = arg1;
            case "ENSEMBLE_JOINED" -> {
                CLIENT_PENDING_INVITE_FROM = "";
                CLIENT_ENSEMBLE_PARTNER = arg1;
            }
            case "ENSEMBLE_LEFT" -> CLIENT_ENSEMBLE_PARTNER = "";
            case "SYNC_START" -> MidiPlayerEngine.startSyncedPlayback();
            case "SYNC_STOP" -> MidiPlayerEngine.stopPlayback();
        }
    }
}
