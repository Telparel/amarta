package com.amarthaloe.client.chat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = "amarthaloe", bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public class AmarthaloeChatHud {

    public static final int BOX_X = 6;
    public static final int BOX_WIDTH = 330;
    public static final int BOX_HEIGHT = 145;

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onChatReceived(ClientChatReceivedEvent event) {
        if (event.isSystem() && event.getMessage().getString().isEmpty()) return;
        if (event instanceof ClientChatReceivedEvent.System sys && sys.isOverlay()) return;

        Minecraft mc = Minecraft.getInstance();
        long ticks = mc.gui != null ? mc.gui.getGuiTicks() : 0;
        AmarthaloeChatManager.recordIncomingMessage(event.getMessage(), ticks);

        // Блокируем попадание сообщения в ванильный HUD, чтобы он оставался чистым
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (event.getNewScreen() instanceof ChatScreen orig && !(event.getNewScreen() instanceof AmarthaloeChatScreen)) {
            // Если чат открыли клавишей "/" (команда), сохраняем начальный слэш!
            String initialText = "";
            if (Minecraft.getInstance().options.keyCommand.isDown()) {
                initialText = "/";
            }
            event.setNewScreen(new AmarthaloeChatScreen(initialText));
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRenderChatLayer(RenderGuiLayerEvent.Pre event) {
        if (!event.getName().equals(VanillaGuiLayers.CHAT)) return;
        event.setCanceled(true);

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        if (mc.screen instanceof AmarthaloeChatScreen) return;

        long currentTicks = mc.gui.getGuiTicks();
        List<AmarthaloeChatManager.ChatMessageEntry> all = AmarthaloeChatManager.getFilteredMessages();
        List<AmarthaloeChatManager.ChatMessageEntry> recent = new ArrayList<>();

        for (int i = all.size() - 1; i >= 0 && recent.size() < 12; i--) {
            AmarthaloeChatManager.ChatMessageEntry entry = all.get(i);
            if (currentTicks - entry.timestampTicks() < 200) {
                recent.add(0, entry);
            }
        }

        if (recent.isEmpty()) return;

        int screenH = mc.getWindow().getGuiScaledHeight();
        int boxY = screenH - BOX_HEIGHT - 38;
        renderReliefChatBox(event.getGuiGraphics(), BOX_X, boxY, BOX_WIDTH, BOX_HEIGHT, recent, 0, false, -1, -1, null);
    }

    public record RenderedLineHitbox(int y, int height, String senderTarget) {}

    public static void renderReliefChatBox(
            GuiGraphics g, int x, int y, int w, int h,
            List<AmarthaloeChatManager.ChatMessageEntry> messages,
            int scrollOffsetLines, boolean interactive,
            int mouseX, int mouseY, List<RenderedLineHitbox> outHitboxes
    ) {
        Minecraft mc = Minecraft.getInstance();
        float scale = AmarthaloeChatManager.fontScale;
        int lineStep = (int) (9 * scale) + AmarthaloeChatManager.lineSpacing;

        int alphaBg = interactive ? 0xEA0B0A08 : 0xB80B0A08;
        int alphaTop = interactive ? 0xEA17130D : 0xB817130D;
        g.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0x55000000);
        g.fillGradient(x, y, x + w, y + h, alphaTop, alphaBg);

        for (int ry = y + 3; ry < y + h - 2; ry += 4) {
            g.fill(x + 2, ry, x + w - 2, ry + 1, 0x10D4AF37);
        }
        for (int rx = x + 6; rx < x + w - 4; rx += 18) {
            g.fill(rx, y + 2, rx + 1, y + h - 2, 0x08FFFFFF);
        }

        g.renderOutline(x, y, w, h, 0xFF2B2418);
        g.renderOutline(x + 1, y + 1, w - 2, h - 2, interactive ? 0xFFC5A049 : 0x998F7A42);

        int cornerCol = interactive ? 0xFFFFD700 : 0xAA8F7A42;
        g.fill(x + 1, y + 1, x + 4, y + 4, cornerCol);
        g.fill(x + w - 4, y + 1, x + w - 1, y + 4, cornerCol);
        g.fill(x + 1, y + h - 4, x + 4, y + h - 1, cornerCol);
        g.fill(x + w - 4, y + h - 4, x + w - 1, y + h - 1, cornerCol);

        int wrapWidth = (int) ((w - 16) / scale);
        record WrappedLine(FormattedCharSequence seq, String senderTarget) {}
        List<WrappedLine> allLines = new ArrayList<>();

        for (AmarthaloeChatManager.ChatMessageEntry entry : messages) {
            Component fullComp = AmarthaloeChatManager.showTimestamps
                    ? Component.literal("§8[" + entry.timePrefix() + "] ").append(entry.message())
                    : entry.message();

            List<FormattedCharSequence> split = mc.font.split(fullComp, wrapWidth);
            for (FormattedCharSequence s : split) {
                allLines.add(new WrappedLine(s, entry.senderTarget()));
            }
        }

        int maxVisibleLines = Math.max(1, (h - 10) / lineStep);
        int endIdx = Math.max(0, allLines.size() - scrollOffsetLines);
        int startIdx = Math.max(0, endIdx - maxVisibleLines);

        g.enableScissor(x + 3, y + 3, x + w - 3, y + h - 3);

        int drawY = y + h - 6 - (endIdx - startIdx) * lineStep;
        for (int i = startIdx; i < endIdx; i++) {
            WrappedLine wl = allLines.get(i);

            if (interactive && !wl.senderTarget.isEmpty()
                    && mouseX >= x + 3 && mouseX <= x + w - 8
                    && mouseY >= drawY - 1 && mouseY < drawY + lineStep - 1) {
                g.fill(x + 3, drawY - 1, x + w - 8, drawY + lineStep - 1, 0x28D4AF37);
            }

            if (outHitboxes != null) {
                outHitboxes.add(new RenderedLineHitbox(drawY - 1, lineStep, wl.senderTarget));
            }

            g.pose().pushPose();
            g.pose().translate(x + 6, drawY, 0);
            g.pose().scale(scale, scale, 1.0f);
            g.drawString(mc.font, wl.seq, 0, 0, 0xFFF5EFE0, AmarthaloeChatManager.cozyShadow);
            g.pose().popPose();

            drawY += lineStep;
        }

        g.disableScissor();

        if (interactive && allLines.size() > maxVisibleLines) {
            int sbX = x + w - 5;
            int sbY = y + 3;
            int sbH = h - 6;
            g.fill(sbX, sbY, sbX + 2, sbY + sbH, 0x55000000);

            float ratio = (float) maxVisibleLines / allLines.size();
            int thumbH = Math.max(14, (int) (sbH * ratio));
            float scrollNorm = (float) scrollOffsetLines / (allLines.size() - maxVisibleLines);
            int thumbY = sbY + (int) ((1.0f - scrollNorm) * (sbH - thumbH));
            g.fill(sbX, thumbY, sbX + 2, thumbY + thumbH, 0xFFD4AF37);
        }
    }
}
