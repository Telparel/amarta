package com.amarthaloe.immersion;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = "amarthaloe", bus = EventBusSubscriber.Bus.GAME)
public class ImmersionServerEvents {

    private static class ServerState {
        boolean typing = false;
        boolean music = false;
        String rpStatus = "";
    }

    private static final Map<UUID, ServerState> SERVER_STATES = new ConcurrentHashMap<>();
    private static final Map<UUID, ArmorStand> ACTIVE_SEATS = new ConcurrentHashMap<>();

    public static void updateClientFlags(ServerPlayer sp, boolean typing, boolean music) {
        ServerState st = SERVER_STATES.computeIfAbsent(sp.getUUID(), k -> new ServerState());
        st.typing = typing;
        st.music = music;
        ImmersionNetworking.broadcastState(sp, st.typing, st.music, st.rpStatus);
    }

    // --- 1. СИДЕНИЕ НА СТУПЕНЬКАХ И ПОЛУБЛОКАХ ПО ПКМ ПУСТОЙ РУКОЙ ---
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide() || event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        if (sp.isShiftKeyDown() || sp.isPassenger() || !sp.getMainHandItem().isEmpty()) return;

        BlockPos pos = event.getPos();
        BlockState state = event.getLevel().getBlockState(pos);

        // Проверяем, что над блоком свободно
        if (!event.getLevel().getBlockState(pos.above()).isAir()) return;

        double sitY = -1;
        if (state.getBlock() instanceof StairBlock && state.getValue(StairBlock.HALF) == Half.BOTTOM) {
            sitY = pos.getY() + 0.5;
        } else if (state.getBlock() instanceof SlabBlock && state.getValue(SlabBlock.TYPE) == SlabType.BOTTOM) {
            sitY = pos.getY() + 0.5;
        }

        if (sitY > 0) {
            sitPlayerAt(sp, pos.getX() + 0.5, sitY, pos.getZ() + 0.5);
            event.setCanceled(true);
        }
    }

    public static void sitPlayerAt(ServerPlayer sp, double x, double y, double z) {
        standUp(sp);
        ServerLevel level = sp.serverLevel();

        ArmorStand seat = new ArmorStand(level, x, y - 0.15, z);
        seat.setInvisible(true);
        seat.setNoGravity(true);
        seat.setInvulnerable(true);
        seat.setSilent(true);
        seat.addTag("amarthaloe_seat");

        // Делаем стойку нематериальной (Marker: 1b), чтобы не мешала кликам и блокам
        CompoundTag tag = new CompoundTag();
        seat.saveWithoutId(tag);
        tag.putBoolean("Marker", true);
        seat.load(tag);

        level.addFreshEntity(seat);
        ACTIVE_SEATS.put(sp.getUUID(), seat);
        sp.startRiding(seat, true);
    }

    public static void standUp(ServerPlayer sp) {
        sp.setForcedPose(null);
        if (sp.isPassenger() && sp.getVehicle() != null && sp.getVehicle().getTags().contains("amarthaloe_seat")) {
            Entity seat = sp.getVehicle();
            sp.stopRiding();
            seat.discard();
        }
        ArmorStand old = ACTIVE_SEATS.remove(sp.getUUID());
        if (old != null && !old.isRemoved()) {
            old.discard();
        }
    }

    // --- 2. ОЧИСТКА ПУСТЫХ СИДЕНИЙ И ВСТАВАНИЕ НА SHIFT ---
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        // Удаляем сиденья, с которых игроки уже слезли
        ACTIVE_SEATS.entrySet().removeIf(entry -> {
            ArmorStand seat = entry.getValue();
            if (seat.isRemoved() || !seat.isVehicle()) {
                if (!seat.isRemoved()) seat.discard();
                return true;
            }
            return false;
        });

        // Если игрок лежит (/lay или /crawl) и нажал Shift — автоматически встает
        for (ServerPlayer sp : event.getServer().getPlayerList().getPlayers()) {
            if (sp.getForcedPose() != null && sp.isShiftKeyDown()) {
                sp.setForcedPose(null);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            // Отправляем новому игроку статусы всех, кто уже на сервере
            for (Map.Entry<UUID, ServerState> entry : SERVER_STATES.entrySet()) {
                ServerState st = entry.getValue();
                PacketDistributor.sendToPlayer(sp, new ImmersionNetworking.PlayerStatePayload(
                        entry.getKey().toString(), st.typing, st.music, st.rpStatus
                ));
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            standUp(sp);
            SERVER_STATES.remove(sp.getUUID());
        }
    }

    // --- 3. КОМАНДЫ /status, /sit, /lay, /crawl, /stand ---
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("status")
                .then(Commands.literal("clear")
                    .executes(ctx -> {
                        ServerPlayer sp = ctx.getSource().getPlayerOrException();
                        ServerState st = SERVER_STATES.computeIfAbsent(sp.getUUID(), k -> new ServerState());
                        st.rpStatus = "";
                        ImmersionNetworking.broadcastState(sp, st.typing, st.music, st.rpStatus);
                        sp.sendSystemMessage(Component.literal("§6[Статус] §7Ваш РП-статус очищен."));
                        return 1;
                    })
                )
                .then(Commands.argument("text", StringArgumentType.greedyString())
                    .executes(ctx -> {
                        ServerPlayer sp = ctx.getSource().getPlayerOrException();
                        String text = StringArgumentType.getString(ctx, "text").trim();
                        if (text.length() > 48) text = text.substring(0, 48);

                        ServerState st = SERVER_STATES.computeIfAbsent(sp.getUUID(), k -> new ServerState());
                        st.rpStatus = text;
                        ImmersionNetworking.broadcastState(sp, st.typing, st.music, st.rpStatus);
                        sp.sendSystemMessage(Component.literal("§6[Статус] §aУстановлен РП-статус: §f«" + text + "»"));
                        return 1;
                    })
                )
        );

        event.getDispatcher().register(
            Commands.literal("sit")
                .executes(ctx -> {
                    ServerPlayer sp = ctx.getSource().getPlayerOrException();
                    if (!sp.onGround()) {
                        sp.sendSystemMessage(Component.literal("§c[Поза] Нельзя сесть в воздухе!"));
                        return 0;
                    }
                    sitPlayerAt(sp, sp.getX(), sp.getY(), sp.getZ());
                    return 1;
                })
        );

        event.getDispatcher().register(
            Commands.literal("lay")
                .executes(ctx -> {
                    ServerPlayer sp = ctx.getSource().getPlayerOrException();
                    standUp(sp);
                    sp.setForcedPose(Pose.SLEEPING);
                    sp.sendSystemMessage(Component.literal("§6[Поза] §7Вы прилегли. Нажмите §e[Shift]§7 или §e/stand§7, чтобы встать."));
                    return 1;
                })
        );

        event.getDispatcher().register(
            Commands.literal("crawl")
                .executes(ctx -> {
                    ServerPlayer sp = ctx.getSource().getPlayerOrException();
                    standUp(sp);
                    sp.setForcedPose(Pose.SWIMMING);
                    sp.sendSystemMessage(Component.literal("§6[Поза] §7Вы легли на живот. Нажмите §e[Shift]§7 или §e/stand§7, чтобы встать."));
                    return 1;
                })
        );

        event.getDispatcher().register(
            Commands.literal("stand")
                .executes(ctx -> {
                    ServerPlayer sp = ctx.getSource().getPlayerOrException();
                    standUp(sp);
                    return 1;
                })
        );
    }
}
