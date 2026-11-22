package com.amarthaloe.immersion;

import com.amarthaloe.music.MidiPlayerEngine;
import com.amarthaloe.music.RadioPlayerEngine;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;

@EventBusSubscriber(modid = "amarthaloe", bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public class ImmersionClientEvents {

    private static boolean lastTyping = false;
    private static boolean lastMusic = false;
    private static int tickCount = 0;

    // Отслеживаем открытие чата и игру музыки/радио раз в 5 тиков
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) return;

        tickCount++;
        if (tickCount % 5 != 0) return;

        boolean nowTyping = (mc.screen instanceof ChatScreen);
        boolean nowMusic = MidiPlayerEngine.isPlaying || RadioPlayerEngine.isPlaying;

        if (nowTyping != lastTyping || nowMusic != lastMusic) {
            lastTyping = nowTyping;
            lastMusic = nowMusic;
            PacketDistributor.sendToServer(new ImmersionNetworking.PlayerStatePayload(
                    mc.player.getStringUUID(), nowTyping, nowMusic, ""
            ));
        }
    }

    // Отрисовка индикаторов (✎ ..., ♫) и РП-статуса прямо над ником игрока
    @SubscribeEvent
    public static void onRenderNameTag(RenderNameTagEvent event) {
        if (!(event.getEntity() instanceof Player target)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || target.isInvisibleTo(mc.player)) return;

        // Не рисуем статусы слишком далеко (> 24 блоков)
        if (target.distanceToSqr(mc.player) > 576.0) return;

        ImmersionNetworking.PlayerVisualState st = ImmersionNetworking.CLIENT_STATES.get(target.getUUID());
        if (st == null) return;

        StringBuilder icons = new StringBuilder();
        if (st.typing()) {
            int dots = (int) ((System.currentTimeMillis() / 350) % 3) + 1;
            icons.append("§e✎ ").append(".".repeat(dots));
        }
        if (st.music()) {
            if (!icons.isEmpty()) icons.append("  ");
            icons.append("§6♫");
        }

        boolean hasIcons = !icons.isEmpty();
        boolean hasStatus = st.rpStatus() != null && !st.rpStatus().isEmpty();
        if (!hasIcons && !hasStatus) return;

        PoseStack poseStack = event.getPoseStack();
        Font font = mc.font;
        int bgColor = (int) (mc.options.getBackgroundOpacity(0.25F) * 255.0F) << 24;

        poseStack.pushPose();
        // Поднимаем чуть выше стандартного ника
        poseStack.translate(0.0, target.getBbHeight() + 0.78, 0.0);
        poseStack.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        poseStack.scale(0.020F, -0.020F, 0.020F);

        Matrix4f matrix = poseStack.last().pose();
        int yOffset = 0;

        if (hasStatus) {
            Component statusComp = Component.literal("§7§o«" + st.rpStatus() + "»");
            float x = -font.width(statusComp) / 2.0f;
            font.drawInBatch(statusComp, x, yOffset, 0xFFE0E0E0, false, matrix, event.getMultiBufferSource(), Font.DisplayMode.NORMAL, bgColor, event.getPackedLight());
            yOffset -= 11;
        }

        if (hasIcons) {
            Component iconComp = Component.literal(icons.toString());
            float x = -font.width(iconComp) / 2.0f;
            font.drawInBatch(iconComp, x, yOffset, 0xFFFFFFFF, false, matrix, event.getMultiBufferSource(), Font.DisplayMode.NORMAL, bgColor, event.getPackedLight());
        }

        poseStack.popPose();
    }
}
