package com.amarthaloe.events;

import com.amarthaloe.data.OfflineMailStorage;
import com.amarthaloe.data.PassportData;
import com.amarthaloe.network.PassportNetworking;
import com.amarthaloe.rpg.Faction;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = "amarthaloe", bus = EventBusSubscriber.Bus.GAME)
public class ServerEvents {

    public static void syncPlayerDataWithOnlineStatus(ServerPlayer sp) {
        PassportData data = sp.getData(PassportData.PASSPORT);
        for (String uuidStr : data.friends) {
            if (uuidStr.startsWith("bot-")) continue;
            try {
                ServerPlayer friend = sp.server.getPlayerList().getPlayer(UUID.fromString(uuidStr));
                if (friend != null) {
                    PassportData fData = friend.getData(PassportData.PASSPORT);
                    data.knownProfiles.put(uuidStr, fData.toProfileSnapshot(uuidStr, friend.getName().getString(), true));
                } else if (data.knownProfiles.containsKey(uuidStr)) {
                    data.knownProfiles.get(uuidStr).putBoolean("online", false);
                }
            } catch (Exception ignored) {}
        }
        sp.setData(PassportData.PASSPORT, data);
        PacketDistributor.sendToPlayer(sp, new PassportNetworking.PassportPayload(data.serializeNBT(sp.registryAccess())));
    }

    public static void syncMailAndNotifyUnread(ServerPlayer sp) {
        PassportData data = sp.getData(PassportData.PASSPORT);
        OfflineMailStorage mail = OfflineMailStorage.get(sp.server);
        String myUuid = sp.getStringUUID();

        // Отправляем клиенту сохраненную историю переписок по всем друзьям
        for (String friendUuid : data.friends) {
            PacketDistributor.sendToPlayer(sp, new PassportNetworking.SocialActionPayload("CLEAR_HISTORY", friendUuid, ""));
            List<String> history = mail.getHistory(myUuid, friendUuid);
            for (String line : history) {
                PacketDistributor.sendToPlayer(sp, new PassportNetworking.SocialActionPayload("RECEIVE_DM", friendUuid, line));
            }
        }

        // Синхронизируем счетчики непрочитанных сообщений
        Map<String, Integer> unreadMap = mail.getUnreadForPlayer(myUuid);
        for (Map.Entry<String, Integer> entry : unreadMap.entrySet()) {
            PacketDistributor.sendToPlayer(sp, new PassportNetworking.SocialActionPayload("SET_UNREAD", entry.getKey(), String.valueOf(entry.getValue())));
        }

        int totalUnread = mail.getTotalUnread(myUuid);
        if (totalUnread > 0) {
            sp.sendSystemMessage(Component.literal("§8§m------------------------------------------------------"));
            sp.sendSystemMessage(Component.literal("§6✉ [Почта Исономии] §eУ вас §c§l" + totalUnread + " §eнепрочитанных сообщений, полученных пока вы были не в сети!"));
            sp.sendSystemMessage(Component.literal("§7Откройте меню §b[M] -> СООБЩЕНИЯ§7, чтобы прочитать их."));
            sp.sendSystemMessage(Component.literal("§8§m------------------------------------------------------"));
            sp.playNotifySound(SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.MASTER, 1.0f, 1.1f);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            PassportData data = sp.getData(PassportData.PASSPORT);
            sp.refreshDisplayName();

            if (!data.isLocked) {
                sp.sendSystemMessage(Component.literal("§8§m======================================================"));
                sp.sendSystemMessage(Component.literal("§6§l Добро пожаловать в Амарталоэ и суровую Исономию!"));
                sp.sendSystemMessage(Component.literal("§7 Мир разделен на фракции, законы и контракты Либры."));
                sp.sendSystemMessage(Component.literal("§e➤ Нажмите клавишу §b[M]§e, чтобы открыть главное меню мода."));
                sp.sendSystemMessage(Component.literal("§c⚠ Обязательно заполните паспорт персонажа во вкладке «ИГРОК»."));
                sp.sendSystemMessage(Component.literal("§7 Данные заполняются §lодин раз§7, после чего вступят в силу в чате."));
                sp.sendSystemMessage(Component.literal("§8§m======================================================"));

                sp.connection.send(new ClientboundSetTitlesAnimationPacket(10, 100, 20));
                sp.connection.send(new ClientboundSetTitleTextPacket(Component.literal("§6§lАМАРТАЛОЭ")));
                sp.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("§eНажмите [M] и заполните паспорт!")));
                sp.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 0.9f, 0.8f);
            } else {
                String displayName = data.rpName.isEmpty() ? sp.getName().getString() : data.rpName;
                sp.sendSystemMessage(Component.literal("§6[Амарталоэ] §aДобро пожаловать в Амарталоэ, §e" + displayName + "§a!"));
            }

            syncPlayerDataWithOnlineStatus(sp);
            syncMailAndNotifyUnread(sp);

            String myName = data.rpName.isEmpty() ? sp.getName().getString() : data.rpName;
            for (String friendUuid : data.friends) {
                if (friendUuid.startsWith("bot-")) continue;
                try {
                    ServerPlayer onlineFriend = sp.server.getPlayerList().getPlayer(UUID.fromString(friendUuid));
                    if (onlineFriend != null) {
                        onlineFriend.sendSystemMessage(Component.literal("§6[Друзья] §a● Ваш друг §e" + myName + " §aвошел на сервер!"));
                        onlineFriend.playNotifySound(SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.MASTER, 0.7f, 1.2f);
                        syncPlayerDataWithOnlineStatus(onlineFriend);
                    }
                } catch (Exception ignored) {}
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            PassportData data = sp.getData(PassportData.PASSPORT);
            String myName = data.rpName.isEmpty() ? sp.getName().getString() : data.rpName;
            String myUuid = sp.getStringUUID();

            for (String friendUuid : data.friends) {
                if (friendUuid.startsWith("bot-")) continue;
                try {
                    ServerPlayer onlineFriend = sp.server.getPlayerList().getPlayer(UUID.fromString(friendUuid));
                    if (onlineFriend != null) {
                        onlineFriend.sendSystemMessage(Component.literal("§6[Друзья] §7○ Ваш друг §e" + myName + " §7покинул сервер."));
                        PassportData fData = onlineFriend.getData(PassportData.PASSPORT);
                        if (fData.knownProfiles.containsKey(myUuid)) {
                            fData.knownProfiles.get(myUuid).putBoolean("online", false);
                        }
                        onlineFriend.setData(PassportData.PASSPORT, fData);
                        PacketDistributor.sendToPlayer(onlineFriend, new PassportNetworking.PassportPayload(fData.serializeNBT(onlineFriend.registryAccess())));
                    }
                } catch (Exception ignored) {}
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            syncPlayerDataWithOnlineStatus(sp);
            sp.refreshDisplayName();
        }
    }

    @SubscribeEvent
    public static void onNameFormat(PlayerEvent.NameFormat event) {
        PassportData data = event.getEntity().getData(PassportData.PASSPORT);
        String baseName = (data.isLocked && !data.rpName.trim().isEmpty())
                ? data.rpName
                : event.getUsername().getString();

        Faction activeFac = Faction.byId(data.activeTitleFaction);
        if (activeFac != null && Math.abs(data.getRep(activeFac)) >= 10) {
            String title = activeFac.getTitle(data.getRep(activeFac), data.isFemale());
            String color = data.getRep(activeFac) < 0 ? "§4" : activeFac.chatColor;
            event.setDisplayname(Component.literal(color + "[" + title + "] §f" + baseName));
        } else {
            event.setDisplayname(Component.literal(baseName));
        }
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("passport")
                .then(Commands.literal("reset")
                    .requires(source -> source.hasPermission(2))
                    .then(Commands.argument("target", EntityArgument.player())
                        .executes(ctx -> {
                            ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
                            PassportData data = target.getData(PassportData.PASSPORT);
                            data.isLocked = false;
                            target.setData(PassportData.PASSPORT, data);
                            target.refreshDisplayName();
                            syncPlayerDataWithOnlineStatus(target);
                            ctx.getSource().sendSuccess(() -> Component.literal("§aПаспорт игрока " + target.getName().getString() + " разблокирован!"), true);
                            return 1;
                        })
                    )
                )
                .then(Commands.literal("show")
                    .then(Commands.argument("target", EntityArgument.player())
                        .executes(ctx -> {
                            ServerPlayer sender = ctx.getSource().getPlayerOrException();
                            ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
                            PassportData d = sender.getData(PassportData.PASSPORT);
                            if (!d.isLocked) {
                                sender.sendSystemMessage(Component.literal("§cСначала заполните свой паспорт в меню (M)!"));
                                return 0;
                            }
                            sendPassportCard(sender, target, d);
                            return 1;
                        })
                    )
                )
        );

        event.getDispatcher().register(
            Commands.literal("rep")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("set")
                    .then(Commands.argument("target", EntityArgument.player())
                        .then(Commands.argument("faction", StringArgumentType.word())
                            .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                Arrays.stream(Faction.values()).map(f -> f.id), builder))
                            .then(Commands.argument("amount", IntegerArgumentType.integer(-2000, 2000))
                                .executes(ctx -> changeRep(ctx.getSource(), EntityArgument.getPlayer(ctx, "target"),
                                        StringArgumentType.getString(ctx, "faction"), IntegerArgumentType.getInteger(ctx, "amount"), false))
                            )
                        )
                    )
                )
                .then(Commands.literal("add")
                    .then(Commands.argument("target", EntityArgument.player())
                        .then(Commands.argument("faction", StringArgumentType.word())
                            .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                Arrays.stream(Faction.values()).map(f -> f.id), builder))
                            .then(Commands.argument("amount", IntegerArgumentType.integer(-2000, 2000))
                                .executes(ctx -> changeRep(ctx.getSource(), EntityArgument.getPlayer(ctx, "target"),
                                        StringArgumentType.getString(ctx, "faction"), IntegerArgumentType.getInteger(ctx, "amount"), true))
                            )
                        )
                    )
                )
        );

        event.getDispatcher().register(
            Commands.literal("socialtest")
                .then(Commands.literal("request")
                    .executes(ctx -> {
                        ServerPlayer sp = ctx.getSource().getPlayerOrException();
                        PassportData data = sp.getData(PassportData.PASSPORT);

                        String botId = "bot-viktor";
                        PassportData fakePass = new PassportData();
                        fakePass.isLocked = true;
                        fakePass.rpName = "Виктор Градов";
                        fakePass.gender = "Мужской";
                        fakePass.ageAndBirth = "34 года, 12 ноября";
                        fakePass.race = "Человек";
                        fakePass.heightWeight = "182 см / 79 кг";
                        fakePass.hair = "Русые, с проседью";
                        fakePass.eyes = "Карие, усталые";
                        fakePass.features = "Ожог на левом запястье, очки в роговой оправе";
                        fakePass.voice = "Хриплый тенор";
                        fakePass.optional = "Старший смены теплоузла Блока-7";

                        data.incomingRequests.add(botId);
                        data.knownProfiles.put(botId, fakePass.toProfileSnapshot(botId, "Viktor_B7", true));
                        syncPlayerDataWithOnlineStatus(sp);

                        sp.sendSystemMessage(Component.literal("§6[Друзья] §eВиктор Градов §fхочет добавить вас в друзья! Откройте меню §b[M] -> ДРУЗЬЯ§f, чтобы принять."));
                        sp.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.MASTER, 0.8f, 1.2f);
                        return 1;
                    })
                )
                .then(Commands.literal("addbot")
                    .executes(ctx -> {
                        ServerPlayer sp = ctx.getSource().getPlayerOrException();
                        PassportData data = sp.getData(PassportData.PASSPORT);

                        String bot1 = "bot-marfa";
                        PassportData p1 = new PassportData();
                        p1.isLocked = true;
                        p1.rpName = "Марфа Соколова";
                        p1.gender = "Женский";
                        p1.ageAndBirth = "26 лет, 3 мая";
                        p1.race = "Человек";
                        p1.heightWeight = "168 см / 57 кг";
                        p1.hair = "Рыжие, собраны в пучок";
                        p1.eyes = "Зеленые";
                        p1.features = "Чернильные пятна на пальцах, значок НИИ";
                        p1.voice = "Мягкое сопрано";
                        p1.optional = "Архивариус сектора Интеллигенции";

                        String bot2 = "bot-inspector";
                        PassportData p2 = new PassportData();
                        p2.isLocked = true;
                        p2.rpName = "Август Вейн";
                        p2.gender = "Мужской";
                        p2.ageAndBirth = "41 год, 20 января";
                        p2.race = "Человек";
                        p2.heightWeight = "190 см / 85 кг";
                        p2.hair = "Черные, гладко зачесаны";
                        p2.eyes = "Холодные серые";
                        p2.features = "Безупречная выправка, серебряный перстень Либры";
                        p2.voice = "Сухой, металлический баритон";
                        p2.optional = "Ревизор Контрактов Правительства";

                        data.friends.add(bot1);
                        data.friends.add(bot2);
                        data.knownProfiles.put(bot1, p1.toProfileSnapshot(bot1, "Marfa_NII", true));
                        data.knownProfiles.put(bot2, p2.toProfileSnapshot(bot2, "Inspector_V", false));
                        syncPlayerDataWithOnlineStatus(sp);

                        sp.sendSystemMessage(Component.literal("§6[Тест] §aДобавлены 2 тестовых друга: §eМарфа Соколова §a(В сети) и §eАвгуст Вейн §7(Не в сети). Откройте §b[M] -> ДРУЗЬЯ§a!"));
                        return 1;
                    })
                )
                .then(Commands.literal("msg")
                    .executes(ctx -> {
                        ServerPlayer sp = ctx.getSource().getPlayerOrException();
                        PassportData data = sp.getData(PassportData.PASSPORT);

                        String senderBot = data.friends.isEmpty() ? "bot-marfa" : data.friends.iterator().next();
                        CompoundTag prof = data.knownProfiles.get(senderBot);
                        String botName = prof != null ? prof.getString("rpName") : "Марфа Соколова";

                        String text = "§e" + botName + ": §fТоварищ, ревизоры Либры проверяют пропуска у 4-го энергоблока! Будь осторожнее.";
                        OfflineMailStorage.get(sp.server).addMessage(senderBot, sp.getStringUUID(), text, false);
                        syncMailAndNotifyUnread(sp);
                        return 1;
                    })
                )
                // Симуляция получения оффлайн-сообщений и входа на сервер
                .then(Commands.literal("offlinemsg")
                    .executes(ctx -> {
                        ServerPlayer sp = ctx.getSource().getPlayerOrException();
                        PassportData data = sp.getData(PassportData.PASSPORT);
                        if (!data.friends.contains("bot-inspector")) {
                            sp.sendSystemMessage(Component.literal("§cСначала добавьте тестовых ботов командой /socialtest addbot"));
                            return 0;
                        }
                        OfflineMailStorage mail = OfflineMailStorage.get(sp.server);
                        mail.addMessage("bot-inspector", sp.getStringUUID(), "§eАвгуст Вейн: §fГражданин, вы отсутствовали на проверке.", false);
                        mail.addMessage("bot-inspector", sp.getStringUUID(), "§eАвгуст Вейн: §fЯвитесь в канцелярию Наместников Либры.", false);
                        syncMailAndNotifyUnread(sp);
                        return 1;
                    })
                )
                .then(Commands.literal("toggle")
                    .executes(ctx -> {
                        ServerPlayer sp = ctx.getSource().getPlayerOrException();
                        PassportData data = sp.getData(PassportData.PASSPORT);
                        for (String fUuid : data.friends) {
                            CompoundTag prof = data.knownProfiles.get(fUuid);
                            if (prof != null) {
                                boolean nowOnline = !prof.getBoolean("online");
                                prof.putBoolean("online", nowOnline);
                                String name = prof.getString("rpName");
                                if (nowOnline) {
                                    sp.sendSystemMessage(Component.literal("§6[Друзья] §a● Ваш друг §e" + name + " §aвошел на сервер!"));
                                    sp.playNotifySound(SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.MASTER, 0.7f, 1.2f);
                                } else {
                                    sp.sendSystemMessage(Component.literal("§6[Друзья] §7○ Ваш друг §e" + name + " §7покинул сервер."));
                                }
                            }
                        }
                        syncPlayerDataWithOnlineStatus(sp);
                        return 1;
                    })
                )
                .then(Commands.literal("clear")
                    .executes(ctx -> {
                        ServerPlayer sp = ctx.getSource().getPlayerOrException();
                        PassportData data = sp.getData(PassportData.PASSPORT);
                        data.friends.removeIf(id -> id.startsWith("bot-"));
                        data.incomingRequests.removeIf(id -> id.startsWith("bot-"));
                        syncPlayerDataWithOnlineStatus(sp);
                        sp.sendSystemMessage(Component.literal("§6[Тест] §eВсе тестовые боты удалены из списка друзей."));
                        return 1;
                    })
                )
        );
    }

    private static int changeRep(net.minecraft.commands.CommandSourceStack source, ServerPlayer target, String facId, int amount, boolean add) {
        Faction f = Faction.byId(facId);
        if (f == null) {
            source.sendFailure(Component.literal("§cНеизвестная фракция! Доступны: lower_city, middle_class, intelligentsia, government"));
            return 0;
        }
        PassportData data = target.getData(PassportData.PASSPORT);
        int oldVal = data.getRep(f);
        int oldTier = f.getTierIndex(oldVal);

        int newVal = Math.clamp(add ? oldVal + amount : amount, -2000, 2000);
        int newTier = f.getTierIndex(newVal);

        data.setRep(f, newVal);
        target.setData(PassportData.PASSPORT, data);
        target.refreshDisplayName();
        syncPlayerDataWithOnlineStatus(target);

        String title = f.getTitle(newVal, data.isFemale());
        source.sendSuccess(() -> Component.literal("§6[Весы Либры] §fРепутация §e" + target.getName().getString() + " §fс фракцией «" + f.displayName + "» теперь: §b" + newVal + " §7(" + title + ")"), true);
        target.sendSystemMessage(Component.literal("§6[Весы Либры] §7Отношение с «" + f.displayName + "» изменилось: §e" + (newVal > 0 ? "+" : "") + newVal + " §7(Ранг: " + title + ")"));

        if (newTier != oldTier) {
            boolean isUpgrade = newTier > oldTier;
            triggerTierChangeNotification(target, f, title, newVal, isUpgrade);
        }

        return 1;
    }

    private static void triggerTierChangeNotification(ServerPlayer player, Faction f, String newTitle, int rep, boolean isUpgrade) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));

        String sign = rep > 0 ? "+" : "";
        if (isUpgrade) {
            Component mainTitle = Component.literal(f.chatColor + "§l" + f.displayName);
            Component subTitle = Component.literal("§a▲ Новая ступень: §f" + newTitle + " §7(" + sign + rep + ")");
            player.connection.send(new ClientboundSetTitleTextPacket(mainTitle));
            player.connection.send(new ClientboundSetSubtitleTextPacket(subTitle));

            player.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 0.85f, 1.15f);
            player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.MASTER, 0.6f, 0.9f);
        } else {
            Component mainTitle = Component.literal("§4§l" + f.displayName);
            Component subTitle = Component.literal("§c▼ Падение ранга: §f" + newTitle + " §7(" + sign + rep + ")");
            player.connection.send(new ClientboundSetTitleTextPacket(mainTitle));
            player.connection.send(new ClientboundSetSubtitleTextPacket(subTitle));

            player.playNotifySound(SoundEvents.WITHER_SPAWN, SoundSource.MASTER, 0.5f, 0.55f);
            player.playNotifySound(SoundEvents.ANVIL_LAND, SoundSource.MASTER, 0.4f, 0.6f);
        }
    }

    public static void sendPassportCard(ServerPlayer sender, ServerPlayer target, PassportData d) {
        Component card = Component.literal(
            "§8§m========================================\n" +
            "§6§lПАСПОРТ ИСОНОМИИ: §e" + d.rpName + "\n" +
            "§7Пол: §f" + d.gender + " §8| §7Раса: §f" + d.race + "\n" +
            "§7Возраст / ДР: §f" + d.ageAndBirth + " §8| §7Рост/Вес: §f" + d.heightWeight + "\n" +
            "§7Волосы: §f" + d.hair + " §8| §7Глаза: §f" + d.eyes + "\n" +
            "§7Особенности: §f" + d.features + "\n" +
            "§7Голос: §f" + d.voice + "\n" +
            (d.optional.isEmpty() ? "" : "§7Дополнительно: §f" + d.optional + "\n") +
            "§8§m========================================"
        );
        target.sendSystemMessage(Component.literal("§6[Исономия] §eГражданин предъявил вам паспорт:"));
        target.sendSystemMessage(card);
        sender.sendSystemMessage(Component.literal("§6[Исономия] §aВы показали паспорт игроку " + target.getDisplayName().getString()));
    }
}
