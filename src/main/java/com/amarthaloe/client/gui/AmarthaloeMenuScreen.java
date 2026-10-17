package com.amarthaloe.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class AmarthaloeMenuScreen extends Screen {
    
    // Размеры нашего аккуратного меню
    private final int xSize = 380;
    private final int ySize = 220;
    private int leftPos;
    private int topPos;

    // Вкладки
    private final List<Button> menuButtons = new ArrayList<>();
    private String currentTab = "ИГРОК";

    public AmarthaloeMenuScreen() {
        super(Component.literal("Amarthaloe Menu"));
    }

    @Override
    protected void init() {
        super.init();
        
        // Центрируем интерфейс на экране игрока
        this.leftPos = (this.width - this.xSize) / 2;
        this.topPos = (this.height - this.ySize) / 2;

        menuButtons.clear();
        
        // Левый ряд вкладок (Ширина 90, Высота 18, отступ между ними 4)
        String[] tabs = {
            "ИГРОК", "ТИТУЛЫ", "РЕПУТАЦИЯ", "ДРУЗЬЯ", 
            "ГРУППЫ", "СОДРУЖЕСТВА", "СООБЩЕНИЯ", "НАСТРОЙКИ"
        };

        int btnWidth = 90;
        int btnHeight = 20;
        int startX = leftPos + 10;
        int startY = topPos + 15;

        for (int i = 0; i < tabs.length; i++) {
            String tabName = tabs[i];
            int index = i;
            
            Button btn = Button.builder(Component.literal(tabName), (button) -> {
                this.currentTab = tabName;
                updateButtonStates();
            })
            .bounds(startX, startY + (i * (btnHeight + 4)), btnWidth, btnHeight)
            .build();
            
            menuButtons.add(btn);
            this.addRenderableWidget(btn);
        }
        
        updateButtonStates();
    }

    private void updateButtonStates() {
        // Подсвечиваем активную вкладку, блокируя её от повторного нажатия
        for (Button btn : menuButtons) {
            btn.active = !btn.getMessage().getString().equals(currentTab);
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Затемняем задний игровой фон
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        
        // 1. Отрисовка темных подложек
        // Задний фон основного окна (справа)
        int mainWinX = leftPos + 110;
        int mainWinY = topPos + 10;
        int mainWinW = xSize - 120;
        int mainWinH = ySize - 20;
        
        // Тёмно-серый градиент или заливка для правого "Текущего окна"
        guiGraphics.fill(mainWinX, mainWinY, mainWinX + mainWinW, mainWinY + mainWinH, 0xDD1E1E1E); // Тёмный уголь
        
        // Легкая серебряно-золотая фэнтези рамка для правого окна
        guiGraphics.renderOutline(mainWinX, mainWinY, mainWinW, mainWinH, 0xFF4A4A4A); // Внешняя
        guiGraphics.renderOutline(mainWinX + 1, mainWinY + 1, mainWinW - 2, mainWinH - 2, 0xFF8F7A42); // Внутренняя золотистая

        // Фоновый бортик слева, под кнопками (для глубины)
        guiGraphics.fill(leftPos + 5, topPos + 10, leftPos + 105, topPos + ySize - 10, 0x880C0C0C);

        // 2. Рендерим кнопки и тексты
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        // Отображение текущего открытого окна
        guiGraphics.drawString(this.font, "ВКЛАДКА: " + currentTab, mainWinX + 15, mainWinY + 15, 0xFFE0C068); // Золотой текст
        
        // Здесь мы пока выведем "Заглушку" на будущее
        guiGraphics.drawString(this.font, "Тут будет отображаться интерфейс: " + currentTab.toLowerCase(), mainWinX + 15, mainWinY + 40, 0xFF9F9F9F);
    }

    @Override
    public boolean isPauseScreen() {
        return false; // Чтобы игра не вставала на паузу в одиночном режиме (для онлайна это не важно)
    }
}
