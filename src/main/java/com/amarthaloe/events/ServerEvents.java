package com.amarthaloe.events;

import com.amarthaloe.data.PassportData;
import com.amarthaloe.network.PassportNetworking;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = "amarthaloe", bus = EventBusSubscriber.Bus.GAME)
public class ServerEvents {

    // 1. Синхронизация паспорта при входе на сервер
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            PassportData data = sp.getData(PassportData.PASSPORT);
            PacketDistributor.sendToPlayer(sp, new PassportNetworking.PassportPayload(data.serializeNBT(sp.registryAccess())));
            sp.refreshDisplayName();
        }
    }

    // 2. Синхронизация после респавна
    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            PassportData data = sp.getData(PassportData.PASSPORT);
            PacketDistributor.sendToPlayer(sp, new PassportNetworking.PassportPayload(data.serializeNBT(sp.registryAccess())));
            sp.refreshDisplayName();
        }
    }

    // 3. Замена ника в чате на имя из паспорта
    @SubscribeEvent
    public static void onNameFormat(PlayerEvent.NameFormat event) {
        PassportData data = event.getEntity().getData(PassportData.PASSPORT);
        if (data.isLocked && !data.rpName.trim().isEmpty()) {
            event.setDisplayname(Component.literal(data.rpName));
        }
    }

    // 4. Команды для демонстрации паспорта и сброса администрацией
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("passport")
                // Команда для админов: /passport reset <игрок>
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
                            ctx.getSource().sendSuccess(() -> Component.literal("§aПаспорт игрока " + target.getName().getString() + " разблокирован для повторного редактирования!"), true);
                            target.sendSystemMessage(Component.literal("§6[Amarthaloe] §eАдминистрация разблокировала ваш паспорт. Откройте меню (M), чтобы внести изменения."));
                            return 1;
                        })
                    )
                )
                // Команда демонстрации паспорта другому игроку: /passport show <игрок>
                .then(Commands.literal("show")
                    .then(Commands.argument("target", EntityArgument.player())
                        .executes(ctx -> {
                            ServerPlayer sender = ctx.getSource().getPlayerOrException();
                            ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
                            PassportData d = sender.getData(PassportData.PASSPORT);
                            if (!d.isLocked) {
                                sender.sendSystemMessage(Component.literal("§cСначала заполните и сохраните свой паспорт в меню (M)!"));
                                return 0;
                            }
                            sendPassportCard(sender, target, d);
                            return 1;
                        })
                    )
                )
        );
    }

    public static void sendPassportCard(ServerPlayer sender, ServerPlayer target, PassportData d) {
        Component card = Component.literal(
            "§8§m========================================\n" +
            "§6§lПАСПОРТ ПЕРСОНАЖА: §e" + d.rpName + "\n" +
            "§7Пол: §f" + d.gender + " §8| §7Раса: §f" + d.race + "\n" +
            "§7Возраст / ДР: §f" + d.ageAndBirth + " §8| §7Рост/Вес: §f" + d.heightWeight + "\n" +
            "§7Волосы: §f" + d.hair + " §8| §7Глаза: §f" + d.eyes + "\n" +
            "§7Особенности: §f" + d.features + "\n" +
            "§7Голос: §f" + d.voice + "\n" +
            (d.optional.isEmpty() ? "" : "§7Дополнительно: §f" + d.optional + "\n") +
            "§8§m========================================"
        );
        target.sendSystemMessage(Component.literal("§6[Amarthaloe] §eИгрок показал вам свой паспорт:"));
        target.sendSystemMessage(card);
        sender.sendSystemMessage(Component.literal("§6[Amarthaloe] §aВы показали паспорт игроку " + target.getDisplayName().getString()));
    }
}
