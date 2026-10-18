package com.amarthaloe.events;

import com.amarthaloe.data.PassportData;
import com.amarthaloe.network.PassportNetworking;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.ServerChatEvent;

import java.util.*;

@EventBusSubscriber(modid = "amarthaloe", bus = EventBusSubscriber.Bus.GAME)
public class RpChatAndCommands {

    public static final double LOCAL_RADIUS = 100.0;
    public static final double WHISPER_RADIUS = 5.0;
    public static final double INTERACTION_RADIUS = 6.0;

    private static final Random RANDOM = new Random();

    public record PendingInteraction(String id, UUID senderUuid, UUID targetUuid, String type, long timestamp) {}
    private static final Map<String, PendingInteraction> PENDING_INTERACTIONS = new HashMap<>();

    // --- 1. РАЗДЕЛЕНИЕ ЧАТА НА ЛОКАЛЬНЫЙ (100 БЛОКОВ) И ГЛОБАЛЬНЫЙ (!) ---
    @SubscribeEvent
    public static void onServerChat(ServerChatEvent event) {
        ServerPlayer sender = event.getPlayer();
        String rawMsg = event.getRawText().trim();
        if (rawMsg.isEmpty()) return;

        event.setCanceled(true);

        Component formattedAuthor = ServerEvents.buildFormattedDisplayName(sender, sender.getName().getString());

        if (rawMsg.startsWith("!")) {
            String globalText = rawMsg.substring(1).trim();
            if (globalText.isEmpty()) return;

            MutableComponent msg = Component.literal("§6[Глобал] ")
                    .append(formattedAuthor)
                    .append(Component.literal(" §6» §e" + globalText));

            for (ServerPlayer p : sender.server.getPlayerList().getPlayers()) {
                p.sendSystemMessage(msg);
            }
        } else {
            MutableComponent msg = Component.literal("§7[Локал] ")
                    .append(formattedAuthor)
                    .append(Component.literal(" §8» §f" + rawMsg));

            int listeners = sendInRadius(sender, LOCAL_RADIUS, msg);
            if (listeners <= 1 && sender.server.getPlayerList().getPlayers().size() > 1) {
                sender.sendSystemMessage(Component.literal("§8§o* В радиусе 100 блоков никого нет. Чтобы написать в глобальный чат, начните сообщение с «!»"));
            }
        }
    }

    // Дополнительный перехватчик на случай, если клиент отправляет /me или /w как подписанный пакет
    @SubscribeEvent
    public static void onCommandPreprocess(CommandEvent event) {
        CommandSourceStack source = event.getParseResults().getContext().getSource();
        if (!(source.getEntity() instanceof ServerPlayer sender)) return;

        String input = event.getParseResults().getReader().getString();
        if (input.startsWith("/")) input = input.substring(1);

        if (input.regionMatches(true, 0, "me ", 0, 3)) {
            event.setCanceled(true);
            String action = input.substring(3).trim();
            if (!action.isEmpty()) {
                String name = getRpName(sender);
                sendInRadius(sender, LOCAL_RADIUS, Component.literal("§d* " + name + " " + action));
            }
        } else if (input.regionMatches(true, 0, "w ", 0, 2)) {
            event.setCanceled(true);
            String text = input.substring(2).trim();
            if (!text.isEmpty()) {
                String name = getRpName(sender);
                Component whisperMsg = Component.literal("§3[Шепот] §b" + name + " §7тихо шепчет: §7§o" + text);
                int heard = sendInRadius(sender, WHISPER_RADIUS, whisperMsg);
                if (heard <= 1) {
                    sender.sendSystemMessage(Component.literal("§8§o* Рядом (в радиусе 5 блоков) нет никого, кто услышал бы ваш шепот."));
                }
            }
        }
    }

    public static int sendInRadius(ServerPlayer source, double radius, Component message) {
        double maxDistSq = radius * radius;
        int count = 0;
        for (ServerPlayer player : source.server.getPlayerList().getPlayers()) {
            if (player.level() == source.level() && player.distanceToSqr(source) <= maxDistSq) {
                player.sendSystemMessage(message);
                count++;
            }
        }
        return count;
    }

    public static String getRpName(ServerPlayer player) {
        PassportData d = player.getData(PassportData.PASSPORT);
        return (d.isLocked && !d.rpName.trim().isEmpty()) ? d.rpName : player.getName().getString();
    }

    // --- 2. РЕГИСТРАЦИЯ РП-КОМАНД И ИНТЕРАКТИВНЫХ ДЕЙСТВИЙ ---
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        // ВАЖНО: Удаляем стандартные ванильные команды /me и /w из дерева Brigadier,
        // чтобы они не конфликтовали своими аргументами подписи чата (MessageArgument) с нашими!
        dispatcher.getRoot().getChildren().removeIf(node ->
                node.getName().equals("me") || node.getName().equals("w")
        );

        // /w <текст> — Шепот в радиусе 5 блоков
        dispatcher.register(
            Commands.literal("w")
                .then(Commands.argument("message", StringArgumentType.greedyString())
                    .executes(ctx -> {
                        ServerPlayer sender = ctx.getSource().getPlayerOrException();
                        String text = StringArgumentType.getString(ctx, "message");
                        String name = getRpName(sender);

                        Component whisperMsg = Component.literal("§3[Шепот] §b" + name + " §7тихо шепчет: §7§o" + text);
                        int heard = sendInRadius(sender, WHISPER_RADIUS, whisperMsg);
                        if (heard <= 1) {
                            sender.sendSystemMessage(Component.literal("§8§o* Рядом (в радиусе 5 блоков) нет никого, кто услышал бы ваш шепот."));
                        }
                        return 1;
                    })
                )
        );

        // /me <действие> — Отпись действия от 1-го лица персонажа
        dispatcher.register(
            Commands.literal("me")
                .then(Commands.argument("action", StringArgumentType.greedyString())
                    .executes(ctx -> {
                        ServerPlayer sender = ctx.getSource().getPlayerOrException();
                        String action = StringArgumentType.getString(ctx, "action");
                        String name = getRpName(sender);

                        Component rpMsg = Component.literal("§d* " + name + " " + action);
                        sendInRadius(sender, LOCAL_RADIUS, rpMsg);
                        return 1;
                    })
                )
        );

        // /do <описание> — Отпись окружения и событий от 3-го лица
        dispatcher.register(
            Commands.literal("do")
                .then(Commands.argument("description", StringArgumentType.greedyString())
                    .executes(ctx -> {
                        ServerPlayer sender = ctx.getSource().getPlayerOrException();
                        String desc = StringArgumentType.getString(ctx, "description");
                        String name = getRpName(sender);

                        Component doMsg = Component.literal("§b(( " + desc + " )) §8— §7" + name);
                        sendInRadius(sender, LOCAL_RADIUS, doMsg);
                        return 1;
                    })
                )
        );

        // /try <попытка> — Попытка действия (Вышло / Не вышло)
        dispatcher.register(
            Commands.literal("try")
                .then(Commands.argument("attempt", StringArgumentType.greedyString())
                    .executes(ctx -> {
                        ServerPlayer sender = ctx.getSource().getPlayerOrException();
                        String attempt = StringArgumentType.getString(ctx, "attempt");
                        String name = getRpName(sender);
                        boolean success = RANDOM.nextBoolean();

                        String outcome = success ? "§8[§a§lВышло§8]" : "§8[§c§lНе вышло§8]";
                        Component tryMsg = Component.literal("§d* " + name + " " + attempt + " " + outcome);
                        sendInRadius(sender, LOCAL_RADIUS, tryMsg);

                        sender.level().playSound(null, sender.blockPosition(),
                                success ? SoundEvents.EXPERIENCE_ORB_PICKUP : SoundEvents.NOTE_BLOCK_BASS.value(),
                                SoundSource.PLAYERS, 0.5f, success ? 1.3f : 0.7f);
                        return 1;
                    })
                )
        );

        // /roll — Бросок кубика 1d20
        dispatcher.register(
            Commands.literal("roll")
                .executes(ctx -> {
                    ServerPlayer sender = ctx.getSource().getPlayerOrException();
                    String name = getRpName(sender);
                    int roll = RANDOM.nextInt(20) + 1;

                    String rollFormat;
                    if (roll == 20) {
                        rollFormat = "§6§l20 §e(КРИТИЧЕСКИЙ УСПЕХ!)";
                    } else if (roll == 1) {
                        rollFormat = "§4§l1 §c(КРИТИЧЕСКИЙ ПРОВАЛ!)";
                    } else if (roll >= 15) {
                        rollFormat = "§a§l" + roll;
                    } else if (roll <= 5) {
                        rollFormat = "§c§l" + roll;
                    } else {
                        rollFormat = "§e§l" + roll;
                    }

                    Component rollMsg = Component.literal("§6🎲 §e" + name + " §7бросает кубик §f(1d20)§7: выпало " + rollFormat);
                    sendInRadius(sender, LOCAL_RADIUS, rollMsg);

                    sender.level().playSound(null, sender.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 0.8f + (roll / 20.0f) * 0.6f);
                    return 1;
                })
        );

        registerSocialEmotionCommand(dispatcher, "hug", "обнять", "обнял(а)", "крепко обнимает");
        registerSocialEmotionCommand(dispatcher, "kiss", "поцеловать", "поцеловал(а)", "нежно целует");
        registerSocialEmotionCommand(dispatcher, "pat", "погладить по голове", "погладил(а) по голове", "ласково гладит по голове");

        dispatcher.register(
            Commands.literal("rpinteract")
                .then(Commands.argument("id", StringArgumentType.word())
                    .then(Commands.argument("decision", StringArgumentType.word())
                        .executes(ctx -> {
                            ServerPlayer clicker = ctx.getSource().getPlayerOrException();
                            String id = StringArgumentType.getString(ctx, "id");
                            String decision = StringArgumentType.getString(ctx, "decision");
                            handleInteractionResponse(clicker, id, "accept".equalsIgnoreCase(decision));
                            return 1;
                        })
                    )
                )
        );
    }

    private static void registerSocialEmotionCommand(CommandDispatcher<CommandSourceStack> dispatcher, String cmd, String infinitive, String pastVerb, String presentVerb) {
        dispatcher.register(
            Commands.literal(cmd)
                .then(Commands.argument("target", StringArgumentType.greedyString())
                    .suggests((ctx, builder) -> {
                        ServerPlayer sp = ctx.getSource().getPlayer();
                        if (sp == null) return builder.buildFuture();
                        List<String> names = new ArrayList<>();
                        for (ServerPlayer p : sp.server.getPlayerList().getPlayers()) {
                            if (!p.getUUID().equals(sp.getUUID())) {
                                names.add(getRpName(p));
                                names.add(p.getName().getString());
                            }
                        }
                        return SharedSuggestionProvider.suggest(names, builder);
                    })
                    .executes(ctx -> {
                        ServerPlayer sender = ctx.getSource().getPlayerOrException();
                        String query = StringArgumentType.getString(ctx, "target");
                        ServerPlayer target = PassportNetworking.findPlayerByNameOrRpName(sender, query);

                        if (target == null) {
                            sender.sendSystemMessage(Component.literal("§c[Действие] Игрок «" + query + "» не найден в сети."));
                            return 0;
                        }
                        if (target.getUUID().equals(sender.getUUID())) {
                            sender.sendSystemMessage(Component.literal("§c[Действие] Вы не можете применить это действие к самому себе!"));
                            return 0;
                        }
                        if (sender.level() != target.level() || sender.distanceToSqr(target) > INTERACTION_RADIUS * INTERACTION_RADIUS) {
                            sender.sendSystemMessage(Component.literal("§c[Действие] Вы слишком далеко! Подойдите к персонажу ближе (до 6 блоков)."));
                            return 0;
                        }

                        String reqId = UUID.randomUUID().toString().substring(0, 8);
                        PENDING_INTERACTIONS.put(reqId, new PendingInteraction(reqId, sender.getUUID(), target.getUUID(), cmd, System.currentTimeMillis()));

                        String senderName = getRpName(sender);
                        String targetName = getRpName(target);

                        sender.sendSystemMessage(Component.literal("§d[РП] §7Вы предложили персонажу §e" + targetName + " §7действие: §f" + infinitive + "§7. Ожидание ответа..."));

                        MutableComponent acceptBtn = Component.literal("§a§l[✔ Принять]")
                                .withStyle(Style.EMPTY
                                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/rpinteract " + reqId + " accept"))
                                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("§aНажмите, чтобы разрешить действие («" + infinitive + "»)"))));

                        MutableComponent declineBtn = Component.literal("§c§l[✖ Отказать]")
                                .withStyle(Style.EMPTY
                                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/rpinteract " + reqId + " decline"))
                                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("§cНажмите, чтобы отстраниться и отказать"))));

                        MutableComponent prompt = Component.literal("§d♥ [РП-Действие] §e" + senderName + " §fхочет §d" + infinitive + " §fвас! ")
                                .append(acceptBtn)
                                .append(Component.literal("  "))
                                .append(declineBtn);

                        target.sendSystemMessage(prompt);
                        target.playNotifySound(SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.MASTER, 0.9f, 1.3f);
                        return 1;
                    })
                )
        );
    }

    private static void handleInteractionResponse(ServerPlayer target, String reqId, boolean accepted) {
        PendingInteraction pending = PENDING_INTERACTIONS.remove(reqId);
        if (pending == null) {
            target.sendSystemMessage(Component.literal("§c[РП] Это предложение уже истекло или было использовано."));
            return;
        }
        if (!pending.targetUuid().equals(target.getUUID())) return;

        if (System.currentTimeMillis() - pending.timestamp() > 60000) {
            target.sendSystemMessage(Component.literal("§c[РП] Время ожидания ответа истекло."));
            return;
        }

        ServerPlayer sender = target.server.getPlayerList().getPlayer(pending.senderUuid());
        if (sender == null || sender.level() != target.level() || sender.distanceToSqr(target) > (INTERACTION_RADIUS + 2) * (INTERACTION_RADIUS + 2)) {
            target.sendSystemMessage(Component.literal("§c[РП] Игрок уже отошел слишком далеко."));
            return;
        }

        String senderName = getRpName(sender);
        String targetName = getRpName(target);
        ServerLevel sLevel = sender.serverLevel();

        if (accepted) {
            String actionLine = switch (pending.type()) {
                case "hug" -> "§d♥ * " + senderName + " крепко и тепло обнимает " + targetName + ".";
                case "kiss" -> "§d♥ * " + senderName + " нежно целует " + targetName + ".";
                case "pat" -> "§d♥ * " + senderName + " ласково гладит " + targetName + " по голове.";
                default -> "§d* " + senderName + " взаимодействует с " + targetName + ".";
            };

            sendInRadius(sender, LOCAL_RADIUS, Component.literal(actionLine));

            sLevel.sendParticles(ParticleTypes.HEART, sender.getX(), sender.getY() + 2.0, sender.getZ(), 5, 0.3, 0.2, 0.3, 0.02);
            sLevel.sendParticles(ParticleTypes.HEART, target.getX(), target.getY() + 2.0, target.getZ(), 5, 0.3, 0.2, 0.3, 0.02);
            sLevel.playSound(null, target.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.8f, 1.3f);
        } else {
            String refuseLine = switch (pending.type()) {
                case "hug" -> "§7* " + senderName + " попытался(ась) обнять " + targetName + ", но получил(а) отказ.";
                case "kiss" -> "§7* " + targetName + " уклоняется от поцелуя " + senderName + ".";
                case "pat" -> "§7* " + targetName + " отстраняется, не позволяя " + senderName + " погладить себя по голове.";
                default -> "§7* " + targetName + " отклоняет действие от " + senderName + ".";
            };

            sendInRadius(sender, LOCAL_RADIUS, Component.literal(refuseLine));
            sLevel.sendParticles(ParticleTypes.SMOKE, target.getX(), target.getY() + 1.8, target.getZ(), 6, 0.2, 0.2, 0.2, 0.01);
            sLevel.playSound(null, target.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 0.5f, 1.0f);
        }
    }
}
