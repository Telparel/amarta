package com.amarthaloe.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public class DarkButton extends Button {
    private boolean selected = false;

    public DarkButton(int x, int y, int width, int height, Component message, OnPress onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    @Override
    protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Глубокий черный цвет кнопки (чуть светлее при наведении или выборе)
        int bgColor = this.selected ? 0xFF221D14 : (this.isHoveredOrFocused() && this.active ? 0xFF1A1A1A : 0xFF0D0D0D);
        // Цвет рамки: золотой для выбранной/наведенной, темно-серый для обычной
        int borderColor = this.selected ? 0xFFD4AF37 : (this.isHoveredOrFocused() && this.active ? 0xFF8F7A42 : 0xFF2E2E2E);
        // Цвет текста
        int textColor = !this.active ? 0xFF666666 : (this.selected ? 0xFFFFD700 : (this.isHoveredOrFocused() ? 0xFFFFFFFF : 0xFFCCCCCC));

        guiGraphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, bgColor);
        guiGraphics.renderOutline(this.getX(), this.getY(), this.width, this.height, borderColor);

        guiGraphics.drawCenteredString(
                Minecraft.getInstance().font,
                this.getMessage(),
                this.getX() + this.width / 2,
                this.getY() + (this.height - 8) / 2,
                textColor
        );
    }
}
