package com.amarthaloe.client.gui;

import com.amarthaloe.network.PassportNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

public class AmarthaloeAuthScreen extends Screen {

    private final boolean isLoginMode; // true = Вход, false = Первичная регистрация
    private EditBox passwordBox;
    private EditBox confirmBox;
    private boolean showPasswordText = false;
    private String errorMessage = "";

    private final int boxW = 280;
    private final int boxH = 175;
    private int leftPos;
    private int topPos;

    public AmarthaloeAuthScreen(boolean isLoginMode) {
        super(Component.literal("Авторизация Амарталоэ"));
        this.isLoginMode = isLoginMode;
    }

    public static void handleServerPrompt(String mode) {
        Minecraft mc = Minecraft.getInstance();
        boolean login = "LOGIN".equalsIgnoreCase(mode);
        if (mc.screen instanceof AmarthaloeAuthScreen existing && existing.isLoginMode == login) {
            return; // Уже открыто
        }
        mc.setScreen(new AmarthaloeAuthScreen(login));
    }

    public static void handleServerResult(String status, String message) {
        Minecraft mc = Minecraft.getInstance();
        if ("SUCCESS".equalsIgnoreCase(status)) {
            if (mc.screen instanceof AmarthaloeAuthScreen) {
                mc.setScreen(null);
            }
        } else if (mc.screen instanceof AmarthaloeAuthScreen authScreen) {
            authScreen.errorMessage = message;
        }
    }

    @Override
    protected void init() {
        super.init();
        this.leftPos = (this.width - this.boxW) / 2;
        this.topPos = (this.height - this.boxH) / 2;

        String savedPass = passwordBox != null ? passwordBox.getValue() : "";
        String savedConf = confirmBox != null ? confirmBox.getValue() : "";

        passwordBox = new EditBox(this.font, leftPos + 25, topPos + 62, 200, 18, Component.empty());
        passwordBox.setMaxLength(32);
        passwordBox.setValue(savedPass);
        passwordBox.setHint(Component.literal("Введите пароль...").withStyle(s -> s.withColor(0x555555)));
        applyPasswordMask(passwordBox);
        this.addRenderableWidget(passwordBox);

        // Кнопка [👁] — показать/скрыть символы пароля
        DarkButton eyeBtn = new DarkButton(leftPos + 230, topPos + 62, 25, 18,
                Component.literal(showPasswordText ? "§e👁" : "§7👁"), (b) -> {
            showPasswordText = !showPasswordText;
            this.rebuildWidgets();
        });
        this.addRenderableWidget(eyeBtn);

        if (!isLoginMode) {
            confirmBox = new EditBox(this.font, leftPos + 25, topPos + 98, 230, 18, Component.empty());
            confirmBox.setMaxLength(32);
            confirmBox.setValue(savedConf);
            confirmBox.setHint(Component.literal("Повторите пароль...").withStyle(s -> s.withColor(0x555555)));
            applyPasswordMask(confirmBox);
            this.addRenderableWidget(confirmBox);
        }

        String btnLabel = isLoginMode ? "ВОЙТИ В ИСОНОМИЮ" : "ЗАРЕГИСТРИРОВАТЬ ПАРОЛЬ";
        DarkButton submitBtn = new DarkButton(leftPos + 25, topPos + boxH - 34, 230, 22, Component.literal(btnLabel), (b) -> {
            submitAuth();
        });
        this.addRenderableWidget(submitBtn);

        this.setFocused(passwordBox);
        passwordBox.setFocused(true);
    }

    private void applyPasswordMask(EditBox box) {
        box.setFormatter((text, cursorPos) -> {
            if (showPasswordText) {
                return FormattedCharSequence.forward(text, Style.EMPTY);
            }
            return FormattedCharSequence.forward("*".repeat(text.length()), Style.EMPTY.withColor(0xFFE0C068));
        });
    }

    private void submitAuth() {
        String p1 = passwordBox.getValue().trim();
        if (p1.length() < 4) {
            errorMessage = "Пароль должен содержать минимум 4 символа!";
            return;
        }
        if (!isLoginMode && confirmBox != null) {
            String p2 = confirmBox.getValue().trim();
            if (!p1.equals(p2)) {
                errorMessage = "Введенные пароли не совпадают!";
                return;
            }
        }
        errorMessage = "";
        PacketDistributor.sendToServer(new PassportNetworking.SocialActionPayload(
                "AUTH_SUBMIT", isLoginMode ? "LOGIN" : "REGISTER", p1
        ));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            submitAuth();
            return true;
        }
        // Запрещаем закрытие окна клавишей Escape
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Глубокое затемнение игрового мира позади окна авторизации
        g.fill(0, 0, this.width, this.height, 0xD8050505);

        // Рельефная золотисто-черная панель
        g.fill(leftPos - 2, topPos - 2, leftPos + boxW + 2, topPos + boxH + 2, 0x88000000);
        g.fillGradient(leftPos, topPos, leftPos + boxW, topPos + boxH, 0xF518140E, 0xF50B0A08);

        for (int ry = topPos + 4; ry < topPos + boxH - 4; ry += 5) {
            g.fill(leftPos + 2, ry, leftPos + boxW - 2, ry + 1, 0x0CD4AF37);
        }

        g.renderOutline(leftPos, topPos, boxW, boxH, 0xFF3A3020);
        g.renderOutline(leftPos + 1, topPos + 1, boxW - 2, boxH - 2, 0xFFD4AF37);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);

        String title = isLoginMode ? "§6§lКОНТРОЛЬ ДОПУСКА ЛИБРЫ" : "§6§lРЕГИСТРАЦИЯ ГРАЖДАНИНА";
        String sub = isLoginMode
                ? "Подтвердите личность для входа на сервер"
                : "Придумайте надежный пароль (от 4 символов)";

        g.drawCenteredString(this.font, title, leftPos + boxW / 2, topPos + 12, 0xFFFFFFFF);
        g.drawCenteredString(this.font, "§7" + sub, leftPos + boxW / 2, topPos + 25, 0xFFAAAAAA);
        g.fill(leftPos + 20, topPos + 38, leftPos + boxW - 20, topPos + 39, 0xFF8F7A42);

        g.drawString(this.font, "Пароль:", leftPos + 25, topPos + 50, 0xFFCCCCCC);
        if (!isLoginMode) {
            g.drawString(this.font, "Подтверждение пароля:", leftPos + 25, topPos + 86, 0xFFCCCCCC);
        } else {
            g.drawCenteredString(this.font, "§8Нажмите [Enter] для быстрого подтверждения", leftPos + boxW / 2, topPos + 96, 0xFF777777);
        }

        if (!errorMessage.isEmpty()) {
            g.drawCenteredString(this.font, "§c⚠ " + errorMessage, leftPos + boxW / 2, topPos + boxH - 48, 0xFFFF5555);
        }
    }
}
