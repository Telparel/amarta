package com.amarthaloe.events;

import com.amarthaloe.data.PassportData;
import com.amarthaloe.network.PassportNetworking;
import com.amarthaloe.rpg.Faction;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Arrays;

@EventBusSubscriber(modid = "amarthaloe", bus = EventBusSubscriber.Bus.GAME)
public class ServerEvents {

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            PassportData data = sp.getData(PassportData.PASSPORT);
            PacketDistributor.sendToPlayer(sp, new PassportNetworking.PassportPayload(data.serializeNBT(sp.registryAccess())));
            sp.refreshDisplayName();
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            PassportData data = sp.getData(PassportData.PASSPORT);
            PacketDistributor.sendToPlayer(sp, new PassportNetworking.PassportPayload(data.serializeNBT(sp.registryAccess())));
            sp.refreshDisplayName();
        }
    }

    // Отображение выбранного Титула (с цветом фракции) + Имени из паспорта в чате
    @SubscribeEvent
    public static void onNameFormat(PlayerEvent.NameFormat event) {
        PassportData data = event.getEntity().getData(PassportData.PASSPORT);
        String baseName = (data.isLocked && !data.rpName.trim().isEmpty())
                ? data.rpName
                : event.getUsername().getString();

        Faction activeFac = Faction.byId(data.activeTitleFaction);
        if (activeFac != null && Math.abs(data.getRep(activeFac)) >= 10) {
            String title = activeFac.getTitle(data.getRep(activeFac), data.isFemale());
            // Если репутация отрицательная — скобки темно-багровые, иначе — в цвет самой фракции
            String color = data.getRep(activeFac) < 0 ? "§4" : activeFac.chatColor;
            event.setDisplayname(Component.literal(color + "[" + title + "] §f" + baseName));
        } else {
            event.setDisplayname(Component.literal(baseName));
        }
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        // 1. Команды паспорта
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
                            PacketDistributor.sendToPlayer(target, new PassportNetworking.PassportPayload(data.serializeNBT(target.registryAccess())));
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

        // 2. Команды репутации Исономии: /rep set|add <игрок> <фракция> <значение>
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
    }

    private static int changeRep(net.minecraft.commands.CommandSourceStack source, ServerPlayer target, String facId, int amount, boolean add) {
        Faction f = Faction.byId(facId);
        if (f == null) {
            source.sendFailure(Component.literal("§cНеизвестная фракция! Доступны: lower_city, middle_class, intelligentsia, government"));
            return 0;
        }
        PassportData data = target.getData(PassportData.PASSPORT);
        int newVal = add ? data.getRep(f) + amount : amount;
        data.setRep(f, newVal);
        target.setData(PassportData.PASSPORT, data);
        target.refreshDisplayName();
        PacketDistributor.sendToPlayer(target, new PassportNetworking.PassportPayload(data.serializeNBT(target.registryAccess())));

        String title = f.getTitle(newVal, data.isFemale());
        source.sendSuccess(() -> Component.literal("§6[Весы Либры] §fРепутация §e" + target.getName().getString() + " §fс фракцией «" + f.displayName + "» теперь: §b" + newVal + " §7(" + title + ")"), true);
        target.sendSystemMessage(Component.literal("§6[Весы Либры] §7Отношение с «" + f.displayName + "» изменилось: §e" + newVal + " §7(Ранг: " + title + ")"));
        return 1;
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
