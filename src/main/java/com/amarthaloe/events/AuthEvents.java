package com.amarthaloe.events;

import com.amarthaloe.data.AuthStorage;
import com.amarthaloe.network.PassportNetworking;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;

@EventBusSubscriber(modid = "amarthaloe", bus = EventBusSubscriber.Bus.GAME)
public class AuthEvents {

    public static class PendingSession {
        public final Vec3 loginPos;
        public final float yRot;
        public final float xRot;
        public int ticksWaiting = 0;
        public int failedAttempts = 0;
        public final boolean isRegistered;

        public PendingSession(ServerPlayer sp, boolean isRegistered) {
            this.loginPos = sp.position();
            this.yRot = sp.getYRot();
            this.xRot = sp.getXRot();
            this.isRegistered = isRegistered;
        }
    }

    // Игроки, которые прямо сейчас находятся на этапе ввода пароля
    private static final Map<UUID, PendingSession> UNAUTHENTICATED = new HashMap<>();

    public static boolean isUnauthenticated(ServerPlayer player) {
        return UNAUTHENTICATED.containsKey(player.getUUID());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            AuthStorage auth = AuthStorage.get(sp.server);
            boolean registered = auth.isRegistered(sp.getStringUUID());
            UNAUTHENTICATED.put(sp.getUUID(), new PendingSession(sp, registered));

            // Отправляем клиенту приказ открыть окно Регистрации или Входа
            PacketDistributor.sendToPlayer(sp, new PassportNetworking.SocialActionPayload(
                    "AUTH_PROMPT", registered ? "LOGIN" : "REGISTER", ""
            ));
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            UNAUTHENTICATED.remove(sp.getUUID());
        }
    }

    // Обработка пакета с паролем от клиента
    public static void handleClientAuthSubmit(ServerPlayer sp, String mode, String password) {
        PendingSession session = UNAUTHENTICATED.get(sp.getUUID());
        if (session == null) return; // Уже авторизован

        String pass = password.trim();
        if (pass.length() < 4 || pass.length() > 32) {
            PacketDistributor.sendToPlayer(sp, new PassportNetworking.SocialActionPayload(
                    "AUTH_RESULT", "ERROR", "Длина пароля должна быть от 4 до 32 символов!"
            ));
            sp.playNotifySound(SoundEvents.VILLAGER_NO, SoundSource.MASTER, 0.8f, 1.0f);
            return;
        }

        AuthStorage auth = AuthStorage.get(sp.server);

        if (!session.isRegistered) {
            // Регистрация нового пароля
            auth.registerPassword(sp.getStringUUID(), pass);
            UNAUTHENTICATED.remove(sp.getUUID());

            PacketDistributor.sendToPlayer(sp, new PassportNetworking.SocialActionPayload("AUTH_RESULT", "SUCCESS", ""));
            sp.sendSystemMessage(Component.literal("§6[Безопасность Либры] §aВаш пароль успешно зарегистрирован! Приятной игры в Исономии."));
            sp.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.MASTER, 0.8f, 1.2f);
        } else {
            // Проверка существующего пароля
            if (auth.verifyPassword(sp.getStringUUID(), pass)) {
                UNAUTHENTICATED.remove(sp.getUUID());
                PacketDistributor.sendToPlayer(sp, new PassportNetworking.SocialActionPayload("AUTH_RESULT", "SUCCESS", ""));
                sp.sendSystemMessage(Component.literal("§6[Безопасность Либры] §aЛичность подтверждена. Доступ к Исономии открыт!"));
                sp.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.MASTER, 0.8f, 1.3f);
            } else {
                session.failedAttempts++;
                if (session.failedAttempts >= 3) {
                    UNAUTHENTICATED.remove(sp.getUUID());
                    sp.connection.disconnect(Component.literal("§c[Безопасность Либры]\n§fПревышено число попыток ввода пароля (3/3)."));
                } else {
                    int left = 3 - session.failedAttempts;
                    PacketDistributor.sendToPlayer(sp, new PassportNetworking.SocialActionPayload(
                            "AUTH_RESULT", "ERROR", "Неверный пароль! Осталось попыток: " + left
                    ));
                    sp.playNotifySound(SoundEvents.VILLAGER_NO, SoundSource.MASTER, 0.9f, 0.9f);
                }
            }
        }
    }

    // Удержание неавторизованных игроков на месте и тайм-аут 60 секунд
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (UNAUTHENTICATED.isEmpty()) return;

        for (ServerPlayer sp : new ArrayList<>(event.getServer().getPlayerList().getPlayers())) {
            PendingSession session = UNAUTHENTICATED.get(sp.getUUID());
            if (session == null) continue;

            session.ticksWaiting++;

            // Каждые 10 тиков возвращаем игрока на точку входа, если он сдвинулся, и переоткрываем окно, если оно закрылось
            if (sp.position().distanceToSqr(session.loginPos) > 0.04) {
                sp.teleportTo(session.loginPos.x, session.loginPos.y, session.loginPos.z);
            }
            if (session.ticksWaiting % 30 == 0) {
                PacketDistributor.sendToPlayer(sp, new PassportNetworking.SocialActionPayload(
                        "AUTH_PROMPT", session.isRegistered ? "LOGIN" : "REGISTER", ""
                ));
            }

            // Если за 60 секунд (1200 тиков) пароль не введен — кикаем
            if (session.ticksWaiting > 1200) {
                UNAUTHENTICATED.remove(sp.getUUID());
                sp.connection.disconnect(Component.literal("§c[Безопасность Либры]\n§fВремя ожидания авторизации истекло (60 сек)."));
            }
        }
    }

    // --- ПОЛНАЯ ЗАЩИТА НЕАВТОРИЗОВАННОГО ИГРОКА ---
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp && isUnauthenticated(sp)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onChat(ServerChatEvent event) {
        if (isUnauthenticated(event.getPlayer())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onCommand(CommandEvent event) {
        if (event.getParseResults().getContext().getSource().getEntity() instanceof ServerPlayer sp && isUnauthenticated(sp)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onItemToss(ItemTossEvent event) {
        if (event.getPlayer() instanceof ServerPlayer sp && isUnauthenticated(sp)) {
            event.setCanceled(true);
            sp.getInventory().add(event.getEntity().getItem());
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onInteractBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer sp && isUnauthenticated(sp)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer sp && isUnauthenticated(sp)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onInteractItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getEntity() instanceof ServerPlayer sp && isUnauthenticated(sp)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttackEntity(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp && isUnauthenticated(sp)) {
            event.setCanceled(true);
        }
    }

    // Команда для администрации: /auth reset <игрок>
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("auth")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("reset")
                    .then(Commands.argument("target", EntityArgument.player())
                        .executes(ctx -> {
                            ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
                            AuthStorage auth = AuthStorage.get(target.server);
                            auth.resetPassword(target.getStringUUID());
                            UNAUTHENTICATED.put(target.getUUID(), new PendingSession(target, false));
                            PacketDistributor.sendToPlayer(target, new PassportNetworking.SocialActionPayload("AUTH_PROMPT", "REGISTER", ""));
                            ctx.getSource().sendSuccess(() -> Component.literal("§aПароль игрока " + target.getName().getString() + " сброшен! Ему открыто окно новой регистрации."), true);
                            return 1;
                        })
                    )
                )
        );
    }
}
