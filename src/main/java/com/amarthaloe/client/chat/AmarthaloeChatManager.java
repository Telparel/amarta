package com.amarthaloe.client.chat;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class AmarthaloeChatManager {

    public enum Channel {
        LOCAL("Локальный"),
        GLOBAL("Глобальный"),
        WHISPER("Шепот"),
        RP("РП (/me, /do, /try, /roll)"),
        SYSTEM("Система и Уведомления");

        public final String displayName;
        Channel(String displayName) { this.displayName = displayName; }
    }

    public record ChatMessageEntry(
            Component message,
            String timePrefix,
            Channel channel,
            String senderTarget,
            long timestampTicks
    ) {}

    public static class ChatTab {
        public String name;
        public final EnumSet<Channel> allowedChannels;

        public ChatTab(String name, EnumSet<Channel> allowedChannels) {
            this.name = name;
            this.allowedChannels = allowedChannels;
        }
    }

    public static final List<ChatMessageEntry> ALL_MESSAGES = new ArrayList<>();
    public static final List<ChatTab> TABS = new ArrayList<>();
    public static int activeTabIndex = 0;

    // Настройки уютного шрифта (меняются прямо в чате)
    public static float fontScale = 1.0f;       // 0.85f, 1.0f, 1.15f
    public static int lineSpacing = 2;          // Дополнительный воздух между строками (1..5 пкс)
    public static boolean showTimestamps = true;
    public static boolean cozyShadow = true;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    static {
        // Базовые вкладки по умолчанию
        TABS.add(new ChatTab("Все", EnumSet.allOf(Channel.class)));
        TABS.add(new ChatTab("Локал", EnumSet.of(Channel.LOCAL, Channel.RP, Channel.WHISPER)));
        TABS.add(new ChatTab("Глобал", EnumSet.of(Channel.GLOBAL, Channel.SYSTEM)));
        TABS.add(new ChatTab("РП", EnumSet.of(Channel.RP, Channel.WHISPER)));
    }

    public static ChatTab getActiveTab() {
        if (TABS.isEmpty()) {
            TABS.add(new ChatTab("Все", EnumSet.allOf(Channel.class)));
        }
        activeTabIndex = Math.clamp(activeTabIndex, 0, TABS.size() - 1);
        return TABS.get(activeTabIndex);
    }

    public static void addNewCustomTab() {
        if (TABS.size() >= 7) return;
        int num = TABS.size() + 1;
        TABS.add(new ChatTab("Вкл. " + num, EnumSet.allOf(Channel.class)));
        activeTabIndex = TABS.size() - 1;
    }

    public static void removeCurrentTab() {
        if (TABS.size() <= 1 || activeTabIndex == 0) return;
        TABS.remove(activeTabIndex);
        activeTabIndex = Math.max(0, activeTabIndex - 1);
    }

    public static void recordIncomingMessage(Component msg, long gameTicks) {
        String plain = msg.getString();
        Channel ch = detectChannel(plain);
        String senderTarget = extractSenderFromStyle(msg);
        if (senderTarget.isEmpty()) {
            senderTarget = extractSenderFromPlain(plain);
        }

        String timeStr = LocalTime.now().format(TIME_FMT);
        ALL_MESSAGES.add(new ChatMessageEntry(msg, timeStr, ch, senderTarget, gameTicks));
        if (ALL_MESSAGES.size() > 300) {
            ALL_MESSAGES.remove(0);
        }
    }

    public static List<ChatMessageEntry> getFilteredMessages() {
        ChatTab tab = getActiveTab();
        List<ChatMessageEntry> res = new ArrayList<>();
        for (ChatMessageEntry e : ALL_MESSAGES) {
            if (tab.allowedChannels.contains(e.channel)) {
                res.add(e);
            }
        }
        return res;
    }

    private static Channel detectChannel(String plain) {
        if (plain.startsWith("[Локал]")) return Channel.LOCAL;
        if (plain.startsWith("[Глобал]")) return Channel.GLOBAL;
        if (plain.startsWith("[Шепот]")) return Channel.WHISPER;
        if (plain.startsWith("* ") || plain.startsWith("(( ") || plain.startsWith("🎲") || plain.startsWith("♥")) {
            return Channel.RP;
        }
        return Channel.SYSTEM;
    }

    // Ищет прикрепленный в стиле тег insertion ("amarthaloe_player:<Имя>")
    public static String extractSenderFromStyle(Component root) {
        if (root.getStyle() != null && root.getStyle().getInsertion() != null) {
            String ins = root.getStyle().getInsertion();
            if (ins.startsWith("amarthaloe_player:")) {
                return ins.substring("amarthaloe_player:".length());
            }
        }
        for (Component sibling : root.getSiblings()) {
            String found = extractSenderFromStyle(sibling);
            if (!found.isEmpty()) return found;
        }
        return "";
    }

    // Резервное распознавание имени отправителя прямо из текста строки
    private static String extractSenderFromPlain(String plain) {
        try {
            if (plain.contains(" » ")) {
                String left = plain.substring(0, plain.indexOf(" » ")).trim();
                int lastBracket = left.lastIndexOf(']');
                if (lastBracket != -1 && lastBracket + 1 < left.length()) {
                    left = left.substring(lastBracket + 1).trim();
                }
                int lastAngle = left.lastIndexOf('>');
                if (lastAngle != -1 && lastAngle + 1 < left.length()) {
                    left = left.substring(lastAngle + 1).trim();
                }
                return left.replaceAll("§[0-9a-fk-or]", "").trim();
            }
            if (plain.startsWith("[Шепот] ") && plain.contains(" тихо шепчет:")) {
                return plain.substring(8, plain.indexOf(" тихо шепчет:")).trim();
            }
            if (plain.startsWith("* ")) {
                String rest = plain.substring(2).trim();
                int sp = rest.indexOf(' ');
                if (sp > 0) return rest.substring(0, sp).trim();
            }
        } catch (Exception ignored) {}
        return "";
    }
}
