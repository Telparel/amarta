package com.amarthaloe.client.chat;

import com.amarthaloe.client.gui.DarkButton;
import com.amarthaloe.network.PassportNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class AmarthaloeChatScreen extends ChatScreen {

    private int scrollOffsetLines = 0;
    private boolean showConfigPanel = false;

    private String contextTargetName = null;
    private int contextMenuX = 0;
    private int contextMenuY = 0;

    private final List<AmarthaloeChatHud.RenderedLineHitbox> lineHitboxes = new ArrayList<>();

    public AmarthaloeChatScreen(String initial) {
        super(initial);
    }

    @Override
    protected void init() {
        // Сохраняем текущий текст перед перестройкой виджетов (чтобы при клике по вкладкам текст не пропадал)
        String savedText = (this.input != null) ? this.input.getValue() : "";

        super.init();

        if (!savedText.isEmpty() && this.input != null) {
            this.input.setValue(savedText);
        }

        int boxX = AmarthaloeChatHud.BOX_X;
        int boxY = this.height - AmarthaloeChatHud.BOX_HEIGHT - 38;
        int tabY = boxY - 20;

        int curX = boxX;
        for (int i = 0; i < AmarthaloeChatManager.TABS.size(); i++) {
            int idx = i;
            AmarthaloeChatManager.ChatTab tab = AmarthaloeChatManager.TABS.get(i);
            int bw = Math.max(36, this.font.width(tab.name) + 12);

            DarkButton tabBtn = new DarkButton(curX, tabY, bw, 18, Component.literal(tab.name), (b) -> {
                AmarthaloeChatManager.activeTabIndex = idx;
                scrollOffsetLines = 0;
                this.rebuildWidgets();
            });
            tabBtn.setSelected(idx == AmarthaloeChatManager.activeTabIndex);
            this.addRenderableWidget(tabBtn);
            curX += bw + 2;
        }

        if (AmarthaloeChatManager.TABS.size() < 7) {
            DarkButton addTabBtn = new DarkButton(curX, tabY, 18, 18, Component.literal("+"), (b) -> {
                AmarthaloeChatManager.addNewCustomTab();
                showConfigPanel = true;
                this.rebuildWidgets();
            });
            this.addRenderableWidget(addTabBtn);
            curX += 20;
        }

        DarkButton settingsBtn = new DarkButton(boxX + AmarthaloeChatHud.BOX_WIDTH - 22, tabY, 22, 18, Component.literal("⚙"), (b) -> {
            showConfigPanel = !showConfigPanel;
            this.rebuildWidgets();
        });
        settingsBtn.setSelected(showConfigPanel);
        this.addRenderableWidget(settingsBtn);

        if (showConfigPanel) {
            int px = boxX + AmarthaloeChatHud.BOX_WIDTH + 8;
            int py = boxY;
            AmarthaloeChatManager.ChatTab activeTab = AmarthaloeChatManager.getActiveTab();

            int row = 0;
            for (AmarthaloeChatManager.Channel ch : AmarthaloeChatManager.Channel.values()) {
                boolean enabled = activeTab.allowedChannels.contains(ch);
                String label = (enabled ? "§a✔ " : "§8✖ ") + "§f" + ch.displayName;
                DarkButton filterBtn = new DarkButton(px + 6, py + 22 + row * 19, 148, 17, Component.literal(label), (b) -> {
                    if (activeTab.allowedChannels.contains(ch)) {
                        activeTab.allowedChannels.remove(ch);
                    } else {
                        activeTab.allowedChannels.add(ch);
                    }
                    this.rebuildWidgets();
                });
                this.addRenderableWidget(filterBtn);
                row++;
            }

            String scaleStr = Math.round(AmarthaloeChatManager.fontScale * 100) + "%";
            DarkButton scaleBtn = new DarkButton(px + 6, py + 122, 72, 17, Component.literal("Шрифт: §e" + scaleStr), (b) -> {
                if (AmarthaloeChatManager.fontScale < 0.95f) AmarthaloeChatManager.fontScale = 1.0f;
                else if (AmarthaloeChatManager.fontScale < 1.1f) AmarthaloeChatManager.fontScale = 1.15f;
                else AmarthaloeChatManager.fontScale = 0.85f;
                this.rebuildWidgets();
            });
            this.addRenderableWidget(scaleBtn);

            DarkButton spaceBtn = new DarkButton(px + 82, py + 122, 72, 17, Component.literal("Отступ: §e+" + AmarthaloeChatManager.lineSpacing), (b) -> {
                AmarthaloeChatManager.lineSpacing = (AmarthaloeChatManager.lineSpacing % 5) + 1;
                this.rebuildWidgets();
            });
            this.addRenderableWidget(spaceBtn);

            DarkButton timeBtn = new DarkButton(px + 6, py + 141, 72, 17,
                    Component.literal("Время: " + (AmarthaloeChatManager.showTimestamps ? "§aВКЛ" : "§cВЫКЛ")), (b) -> {
                AmarthaloeChatManager.showTimestamps = !AmarthaloeChatManager.showTimestamps;
                this.rebuildWidgets();
            });
            this.addRenderableWidget(timeBtn);

            if (AmarthaloeChatManager.activeTabIndex > 0) {
                DarkButton delTabBtn = new DarkButton(px + 82, py + 141, 72, 17, Component.literal("§cУдалить вкл."), (b) -> {
                    AmarthaloeChatManager.removeCurrentTab();
                    this.rebuildWidgets();
                });
                this.addRenderableWidget(delTabBtn);
            }
        }

        // ВАЖНО: Всегда возвращаем фокус клавиатуры на поле ввода чата после добавления любых кнопок!
        if (this.input != null) {
            this.setFocused(this.input);
            this.input.setFocused(true);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Гарантируем, что поле ввода всегда в фокусе при нажатии любых клавиш
        if (this.input != null && !this.input.isFocused()) {
            this.setFocused(this.input);
            this.input.setFocused(true);
        }

        // Прямая обработка Enter / Numpad Enter, чтобы команды из истории (на стрелку вверх) всегда отправлялись!
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (this.input != null) {
                String text = this.input.getValue();
                this.handleChatInput(text, true);
            }
            if (this.minecraft != null) {
                this.minecraft.setScreen(null);
            }
            return true;
        }

        // Обработка стрелок Вверх / Вниз для навигации по прошлым командам и сообщениям
        if (keyCode == GLFW.GLFW_KEY_UP) {
            this.moveInHistory(-1);
            if (this.input != null) {
                this.setFocused(this.input);
                this.input.setFocused(true);
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DOWN) {
            this.moveInHistory(1);
            if (this.input != null) {
                this.setFocused(this.input);
                this.input.setFocused(true);
            }
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int boxX = AmarthaloeChatHud.BOX_X;
        int boxY = this.height - AmarthaloeChatHud.BOX_HEIGHT - 38;
        int boxW = AmarthaloeChatHud.BOX_WIDTH;
        int boxH = AmarthaloeChatHud.BOX_HEIGHT;

        lineHitboxes.clear();
        List<AmarthaloeChatManager.ChatMessageEntry> filtered = AmarthaloeChatManager.getFilteredMessages();
        AmarthaloeChatHud.renderReliefChatBox(g, boxX, boxY, boxW, boxH, filtered, scrollOffsetLines, true, mouseX, mouseY, lineHitboxes);

        g.drawString(this.font, "§8💡 Нажмите ПКМ по строке игрока в чате для быстрых действий (Друзья / Группа / Гильдия / ЛС)", boxX + 2, boxY + boxH + 3, 0xFFAAAAAA);

        if (showConfigPanel) {
            int px = boxX + boxW + 8;
            int py = boxY;
            int pw = 160;
            int ph = 164;
            g.fillGradient(px, py, px + pw, py + ph, 0xF016120C, 0xF00A0907);
            g.renderOutline(px, py, pw, ph, 0xFFD4AF37);
            g.drawString(this.font, "§6Фильтры: §e" + AmarthaloeChatManager.getActiveTab().name, px + 8, py + 8, 0xFFFFFFFF);
        }

        super.render(g, mouseX, mouseY, partialTick);

        if (contextTargetName != null) {
            renderContextMenu(g, mouseX, mouseY);
        }
    }

    private void renderContextMenu(GuiGraphics g, int mouseX, int mouseY) {
        String[] actions = {
            "✉ Написать в ЛС",
            "✚ Добавить в друзья",
            "⚔ Пригласить в группу",
            "⚑ Пригласить в содружество",
            "📜 Показать свой паспорт"
        };

        int menuW = 148;
        int itemH = 16;
        int menuH = 18 + actions.length * itemH;
        int mx = Math.min(contextMenuX, this.width - menuW - 6);
        int my = Math.min(contextMenuY, this.height - menuH - 6);

        g.pose().pushPose();
        g.pose().translate(0, 0, 300);

        g.fill(mx + 2, my + 2, mx + menuW + 2, my + menuH + 2, 0x88000000);
        g.fillGradient(mx, my, mx + menuW, my + menuH, 0xF51A150E, 0xF50D0B08);
        g.renderOutline(mx, my, menuW, menuH, 0xFFD4AF37);

        g.fill(mx + 1, my + 1, mx + menuW - 1, my + 16, 0xFF282015);
        g.drawCenteredString(this.font, "§6✦ §e" + contextTargetName, mx + menuW / 2, my + 4, 0xFFFFFFFF);

        for (int i = 0; i < actions.length; i++) {
            int iy = my + 17 + i * itemH;
            boolean hov = mouseX >= mx + 2 && mouseX <= mx + menuW - 2 && mouseY >= iy && mouseY < iy + itemH;
            if (hov) {
                g.fill(mx + 2, iy, mx + menuW - 2, iy + itemH, 0x44D4AF37);
            }
            g.drawString(this.font, actions[i], mx + 7, iy + 4, hov ? 0xFFFFD700 : 0xFFE0E0E0);
        }

        g.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (contextTargetName != null) {
            int menuW = 148;
            int itemH = 16;
            int menuH = 18 + 5 * itemH;
            int mx = Math.min(contextMenuX, this.width - menuW - 6);
            int my = Math.min(contextMenuY, this.height - menuH - 6);

            if (mouseX >= mx && mouseX <= mx + menuW && mouseY >= my + 17 && mouseY <= my + menuH) {
                int clickedIdx = (int) ((mouseY - (my + 17)) / itemH);
                executeContextAction(clickedIdx, contextTargetName);
                contextTargetName = null;
                return true;
            }
            contextTargetName = null;
            if (button == 1) return true;
        }

        if (button == 1) {
            int boxX = AmarthaloeChatHud.BOX_X;
            int boxW = AmarthaloeChatHud.BOX_WIDTH;
            if (mouseX >= boxX && mouseX <= boxX + boxW) {
                for (AmarthaloeChatHud.RenderedLineHitbox hb : lineHitboxes) {
                    if (mouseY >= hb.y() && mouseY <= hb.y() + hb.height() && !hb.senderTarget().isEmpty()) {
                        this.contextTargetName = hb.senderTarget();
                        this.contextMenuX = (int) mouseX;
                        this.contextMenuY = (int) mouseY;
                        if (Minecraft.getInstance().player != null) {
                            Minecraft.getInstance().player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.6f, 1.2f);
                        }
                        return true;
                    }
                }
            }
        }

        boolean res = super.mouseClicked(mouseX, mouseY, button);
        // После клика по любой вкладке или кнопке сразу возвращаем фокус на строку ввода чата!
        if (this.input != null) {
            this.setFocused(this.input);
            this.input.setFocused(true);
        }
        return res;
    }

    private void executeContextAction(int actionIdx, String targetName) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.7f, 1.0f);

        switch (actionIdx) {
            case 0 -> {
                this.input.setValue("/dm " + targetName + " ");
                this.input.moveCursorToEnd(false);
                this.setFocused(this.input);
                this.input.setFocused(true);
            }
            case 1 -> PacketDistributor.sendToServer(new PassportNetworking.SocialActionPayload("ADD_FRIEND", targetName, ""));
            case 2 -> PacketDistributor.sendToServer(new PassportNetworking.SocialActionPayload("INVITE_PARTY", targetName, ""));
            case 3 -> PacketDistributor.sendToServer(new PassportNetworking.SocialActionPayload("GUILD_INVITE", targetName, ""));
            case 4 -> {
                if (mc.getConnection() != null) {
                    mc.getConnection().sendCommand("passport show " + targetName);
                }
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scrollOffsetLines = Math.max(0, scrollOffsetLines + (int) (scrollY * 3));
        return true;
    }
}
