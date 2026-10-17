package com.amarthaloe.network;

import com.amarthaloe.data.OfflineMailStorage;
import com.amarthaloe.data.PassportData;
import com.amarthaloe.events.ServerEvents;
import com.amarthaloe.rpg.Faction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.*;

@EventBusSubscriber(modid = "amarthaloe", bus = EventBusSubscriber.Bus.MOD)
public class PassportNetworking {

    // Клиентский кэш истории диалогов и непрочитанных сообщений
    public static final Map<String, List<String>> CLIENT_DM_HISTORY = new HashMap<>();
    public static final Map<String, Integer> CLIENT_UNREAD_COUNTS = new HashMap<>();

    public static void addClientMessage(String partnerUuid, String formattedLine) {
        CLIENT_DM_HISTORY.computeIfAbsent(partnerUuid, k -> new ArrayList<>()).add(formattedLine);
    }

    public static int getTotalClientUnread() {
        int total = 0;
        for (int c : CLIENT_UNREAD_COUNTS.values()) total += c;
        return total;
    }

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

    public record SocialActionPayload(String action, String target, String extra) implements CustomPacketPayload {
        public static final Type<SocialActionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("amarthaloe", "social_action"));
        public static final StreamCodec<FriendlyByteBuf, SocialActionPayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, SocialActionPayload::action,
                ByteBufCodecs.STRING_UTF8, SocialActionPayload::target,
                ByteBufCodecs.STRING_UTF8, SocialActionPayload::extra,
                SocialActionPayload::new
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

                                ServerEvents.syncPlayerDataWithOnlineStatus(serverPlayer);
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
                            ServerEvents.syncPlayerDataWithOnlineStatus(sp);
                        }
                    });
                }
        );

        registrar.playBidirectional(
                SocialActionPayload.TYPE,
                SocialActionPayload.STREAM_CODEC,
                (payload, context) -> {
                    context.enqueueWork(() -> {
                        if (context.flow().isServerbound() && context.player() instanceof ServerPlayer sender) {
                            handleServerSocialAction(sender, payload.action(), payload.target(), payload.extra());
                        } else if (context.flow().isClientbound()) {
                            switch (payload.action()) {
                                case "RECEIVE_DM" -> addClientMessage(payload.target(), payload.extra());
                                case "CLEAR_HISTORY" -> CLIENT_DM_HISTORY.remove(payload.target());
                                case "SET_UNREAD" -> {
                                    try {
                                        int count = Integer.parseInt(payload.extra());
                                        if (count <= 0) CLIENT_UNREAD_COUNTS.remove(payload.target());
                                        else CLIENT_UNREAD_COUNTS.put(payload.target(), count);
                                    } catch (NumberFormatException ignored) {}
                                }
                            }
                        }
                    });
                }
        );
    }

    private static void handleServerSocialAction(ServerPlayer sender, String action, String targetStr, String extra) {
        PassportData senderData = sender.getData(PassportData.PASSPORT);
        String senderUuid = sender.getStringUUID();
        String senderName = senderData.rpName.isEmpty() ? sender.getName().getString() : senderData.rpName;
        OfflineMailStorage mail = OfflineMailStorage.get(sender.server);

        switch (action) {
            case "ADD_FRIEND" -> {
                ServerPlayer found = findPlayerByNameOrRpName(sender, targetStr);
                if (found == null) {
                    sender.sendSystemMessage(Component.literal("§c[Друзья] Игрок с ником или именем «" + targetStr + "» сейчас не найден в сети."));
                    return;
                }
                if (found.getUUID().equals(sender.getUUID())) {
                    sender.sendSystemMessage(Component.literal("§c[Друзья] Нельзя добавить самого себя! (Для теста используй /socialtest request)"));
                    return;
                }
                String targetUuid = found.getStringUUID();
                if (senderData.friends.contains(targetUuid)) {
                    sender.sendSystemMessage(Component.literal("§e[Друзья] Этот человек уже есть в вашем списке друзей."));
                    return;
                }

                PassportData targetData = found.getData(PassportData.PASSPORT);
                targetData.incomingRequests.add(senderUuid);
                targetData.knownProfiles.put(senderUuid, senderData.toProfileSnapshot(senderUuid, sender.getName().getString(), true));
                senderData.knownProfiles.put(targetUuid, targetData.toProfileSnapshot(targetUuid, found.getName().getString(), true));

                found.setData(PassportData.PASSPORT, targetData);
                sender.setData(PassportData.PASSPORT, senderData);

                String targetDisplayName = targetData.rpName.isEmpty() ? found.getName().getString() : targetData.rpName;
                sender.sendSystemMessage(Component.literal("§6[Друзья] §aЗапрос в друзья отправлен игроку §e" + targetDisplayName + "§a!"));
                found.sendSystemMessage(Component.literal("§6[Друзья] §e" + senderName + " §fхочет добавить вас в друзья! Откройте меню §b[M] -> ДРУЗЬЯ§f, чтобы принять."));
                found.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.MASTER, 0.8f, 1.2f);

                ServerEvents.syncPlayerDataWithOnlineStatus(sender);
                ServerEvents.syncPlayerDataWithOnlineStatus(found);
            }
            case "ACCEPT_FRIEND" -> {
                if (!senderData.incomingRequests.remove(targetStr)) return;
                senderData.friends.add(targetStr);

                if (!targetStr.startsWith("bot-")) {
                    ServerPlayer other = sender.server.getPlayerList().getPlayer(UUID.fromString(targetStr));
                    if (other != null) {
                        PassportData otherData = other.getData(PassportData.PASSPORT);
                        otherData.friends.add(senderUuid);
                        otherData.incomingRequests.remove(senderUuid);
                        otherData.knownProfiles.put(senderUuid, senderData.toProfileSnapshot(senderUuid, sender.getName().getString(), true));
                        senderData.knownProfiles.put(targetStr, otherData.toProfileSnapshot(targetStr, other.getName().getString(), true));
                        other.setData(PassportData.PASSPORT, otherData);
                        other.sendSystemMessage(Component.literal("§6[Друзья] §a" + senderName + " принял(а) ваш запрос в друзья!"));
                        other.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.MASTER, 0.6f, 1.3f);
                        ServerEvents.syncPlayerDataWithOnlineStatus(other);
                    }
                }
                sender.setData(PassportData.PASSPORT, senderData);
                sender.sendSystemMessage(Component.literal("§6[Друзья] §aЗаявка в друзья принята!"));
                ServerEvents.syncPlayerDataWithOnlineStatus(sender);
            }
            case "DECLINE_FRIEND" -> {
                senderData.incomingRequests.remove(targetStr);
                sender.setData(PassportData.PASSPORT, senderData);
                ServerEvents.syncPlayerDataWithOnlineStatus(sender);
            }
            case "REMOVE_FRIEND" -> {
                senderData.friends.remove(targetStr);
                if (!targetStr.startsWith("bot-")) {
                    ServerPlayer other = sender.server.getPlayerList().getPlayer(UUID.fromString(targetStr));
                    if (other != null) {
                        PassportData otherData = other.getData(PassportData.PASSPORT);
                        otherData.friends.remove(senderUuid);
                        other.setData(PassportData.PASSPORT, otherData);
                        ServerEvents.syncPlayerDataWithOnlineStatus(other);
                    }
                }
                sender.setData(PassportData.PASSPORT, senderData);
                sender.sendSystemMessage(Component.literal("§6[Друзья] §7Игрок удален из списка друзей."));
                ServerEvents.syncPlayerDataWithOnlineStatus(sender);
            }
            case "INVITE_PARTY" -> {
                if (targetStr.startsWith("bot-")) {
                    CompoundTag botProf = senderData.knownProfiles.get(targetStr);
                    String botName = botProf != null ? botProf.getString("rpName") : "Бот";
                    sender.sendSystemMessage(Component.literal("§6[Группа] §aВы отправили приглашение в группу персонажу §e" + botName + "§a!"));
                    sender.playNotifySound(SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.MASTER, 1.0f, 1.2f);
                    return;
                }
                ServerPlayer other = sender.server.getPlayerList().getPlayer(UUID.fromString(targetStr));
                if (other == null) {
                    sender.sendSystemMessage(Component.literal("§c[Группа] Игрок сейчас не в сети!"));
                    return;
                }
                PassportData otherData = other.getData(PassportData.PASSPORT);
                String otherName = otherData.rpName.isEmpty() ? other.getName().getString() : otherData.rpName;
                sender.sendSystemMessage(Component.literal("§6[Группа] §aВы отправили приглашение в группу игроку §e" + otherName + "§a."));
                other.sendSystemMessage(Component.literal("§6[Группа] §e" + senderName + " §fприглашает вас в группу!"));
                other.playNotifySound(SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.MASTER, 1.0f, 1.2f);
            }
            case "MARK_READ" -> {
                mail.markAsRead(senderUuid, targetStr);
                PacketDistributor.sendToPlayer(sender, new SocialActionPayload("SET_UNREAD", targetStr, "0"));
            }
            case "SEND_DM" -> {
                String sharedLine = "§e" + senderName + ": §f" + extra;

                // Если это тестовый бот
                if (targetStr.startsWith("bot-")) {
                    CompoundTag botProf = senderData.knownProfiles.get(targetStr);
                    String botName = botProf != null ? botProf.getString("rpName") : "Житель Исономии";
                    boolean botOnline = botProf != null && botProf.getBoolean("online");

                    mail.addMessage(senderUuid, targetStr, sharedLine, true);
                    PacketDistributor.sendToPlayer(sender, new SocialActionPayload("RECEIVE_DM", targetStr, sharedLine));

                    if (botOnline) {
                        String reply = "§e" + botName + ": §fПринято к сведению, гражданин! Норма пайка выполнена.";
                        mail.addMessage(targetStr, senderUuid, reply, true);
                        PacketDistributor.sendToPlayer(sender, new SocialActionPayload("RECEIVE_DM", targetStr, reply));
                        sender.sendSystemMessage(Component.literal("§6[ЛС от §e" + botName + "§6] §fПринято к сведению, гражданин! Норма пайка выполнена."));
                        sender.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.MASTER, 0.6f, 1.5f);
                    } else {
                        sender.sendSystemMessage(Component.literal("§6[Почта Исономии] §7" + botName + " сейчас не в сети. Ваше сообщение сохранено и будет доставлено при входе!"));
                        sender.playNotifySound(SoundEvents.BOOK_PAGE_TURN, SoundSource.MASTER, 0.8f, 1.2f);
                    }
                    return;
                }

                ServerPlayer other = sender.server.getPlayerList().getPlayer(UUID.fromString(targetStr));
                boolean isOnline = (other != null);

                // Сохраняем сообщение в серверную базу данных (и для онлайна, и для оффлайна!)
                mail.addMessage(senderUuid, targetStr, sharedLine, isOnline);
                PacketDistributor.sendToPlayer(sender, new SocialActionPayload("RECEIVE_DM", targetStr, sharedLine));

                if (isOnline) {
                    PacketDistributor.sendToPlayer(other, new SocialActionPayload("RECEIVE_DM", senderUuid, sharedLine));
                    int unread = mail.getUnreadForPlayer(targetStr).getOrDefault(senderUuid, 0) + 1;
                    PacketDistributor.sendToPlayer(other, new SocialActionPayload("SET_UNREAD", senderUuid, String.valueOf(unread)));
                    other.sendSystemMessage(Component.literal("§6[ЛС от §e" + senderName + "§6] §f" + extra));
                    other.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.MASTER, 0.6f, 1.5f);
                } else {
                    CompoundTag targetProf = senderData.knownProfiles.get(targetStr);
                    String targetName = targetProf != null ? targetProf.getString("rpName") : "Собеседник";
                    sender.sendSystemMessage(Component.literal("§6[Почта Исономии] §7" + targetName + " не в сети. Сообщение оставлено в почтовом ящике и дойдет при входе!"));
                    sender.playNotifySound(SoundEvents.BOOK_PAGE_TURN, SoundSource.MASTER, 0.8f, 1.2f);
                }
            }
        }
    }

    private static ServerPlayer findPlayerByNameOrRpName(ServerPlayer requester, String query) {
        String q = query.trim();
        for (ServerPlayer sp : requester.server.getPlayerList().getPlayers()) {
            if (sp.getName().getString().equalsIgnoreCase(q)) return sp;
            PassportData d = sp.getData(PassportData.PASSPORT);
            if (!d.rpName.isEmpty() && d.rpName.equalsIgnoreCase(q)) return sp;
        }
        return null;
    }
}
