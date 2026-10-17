package com.amarthaloe.client.gui;

import com.amarthaloe.data.PassportData;
import com.amarthaloe.network.PassportNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

public class AmarthaloeMenuScreen extends Screen {

    // Увеличили размер окна, чтобы все 10 полей паспорта стояли просторно и красиво
    private final int xSize = 450;
    private final int ySize = 250;
    private int leftPos;
    private int topPos;

    private final List<DarkButton> menuButtons = new ArrayList<>();
    private String currentTab = "ИГРОК";

    // Элементы паспорта
    private EditBox nameBox, ageBirthBox, raceBox, heightWeightBox, hairBox, eyesBox, featuresBox, voiceBox, optionalBox;
    private DarkButton genderBtn, confirmBtn;
    private String selectedGender = "Мужской";

    public AmarthaloeMenuScreen() {
        super(Component.literal("Amarthaloe Menu"));
    }

    @Override
    protected void init() {
        super.init();
        this.leftPos = (this.width - this.xSize) / 2;
        this.topPos = (this.height - this.ySize) / 2;

        menuButtons.clear();

        String[] tabs = {
            "ИГРОК", "ТИТУЛЫ", "РЕПУТАЦИЯ", "ДРУЗЬЯ",
            "ГРУППЫ", "СОДРУЖЕСТВА", "СООБЩЕНИЯ", "НАСТРОЙКИ"
        };

        int btnWidth = 95;
        int btnHeight = 20;
        int startX = leftPos + 8;
        int startY = topPos + 14;

        for (int i = 0; i < tabs.length; i++) {
            String tabName = tabs[i];
            DarkButton btn = new DarkButton(startX, startY + (i * (btnHeight + 4)), btnWidth, btnHeight, Component.literal(tabName), (button) -> {
                this.currentTab = tabName;
                this.rebuildWidgets();
            });
            btn.setSelected(tabName.equals(currentTab));
            menuButtons.add(btn);
            this.addRenderableWidget(btn);
        }

        if (currentTab.equals("ИГРОК")) {
            initPlayerPassportTab();
        }
    }

    private void initPlayerPassportTab() {
        if (Minecraft.getInstance().player == null) return;
        PassportData data = Minecraft.getInstance().player.getData(PassportData.PASSPORT);

        // Если паспорт уже сохранен, мы не создаем поля ввода, а рисуем красивый лист паспорта в методе render()
        if (data.isLocked) {
            return;
        }

        this.selectedGender = data.gender.isEmpty() ? "Мужской" : data.gender;

        int col1X = leftPos + 120;
        int col2X = leftPos + 280;
        int fieldW = 148;
        int fieldH = 16;
        int startY = topPos + 45;
        int stepY = 34;

        // Левая колонка
        nameBox = createField(col1X, startY, fieldW, fieldH, data.rpName, 32, "Например: Арагорн");
        ageBirthBox = createField(col1X, startY + stepY, fieldW, fieldH, data.ageAndBirth, 40, "25 лет, 14 марта");
        heightWeightBox = createField(col1X, startY + stepY * 2, fieldW, fieldH, data.heightWeight, 32, "185 см / 82 кг");
        eyesBox = createField(col1X, startY + stepY * 3, fieldW, fieldH, data.eyes, 40, "Серые, глубокие");

        // Правая колонка
        genderBtn = new DarkButton(col2X, startY, fieldW, fieldH, Component.literal("Пол: " + selectedGender), (btn) -> {
            selectedGender = selectedGender.equals("Мужской") ? "Женский" : "Мужской";
            btn.setMessage(Component.literal("Пол: " + selectedGender));
        });
        this.addRenderableWidget(genderBtn);

        raceBox = createField(col2X, startY + stepY, fieldW, fieldH, data.race, 32, "Человек / Эльф...");
        hairBox = createField(col2X, startY + stepY * 2, fieldW, fieldH, data.hair, 40, "Темные, до плеч");
        voiceBox = createField(col2X, startY + stepY * 3, fieldW, fieldH, data.voice, 40, "Низкий баритон");

        // Нижние широкие строки (во всю ширину окна)
        int fullW = 308;
        featuresBox = createField(col1X, startY + stepY * 4, fullW, fieldH, data.features, 80, "Шрам над бровью, легкая хромота...");
        optionalBox = createField(col1X, startY + stepY * 4 + 30, fullW, fieldH, data.optional, 80, "Любая деталь по желанию (необязательно)");

        // Кнопка подтверждения (Один раз)
        confirmBtn = new DarkButton(col1X + 54, topPos + ySize - 28, 200, 18, Component.literal("ПОДТВЕРДИТЬ ПАСПОРТ (НАВСЕГДА)"), (btn) -> {
            if (nameBox.getValue().trim().isEmpty()) {
                nameBox.setSuggestion("Укажите имя!");
                return;
            }
            PassportData newData = new PassportData();
            newData.isLocked = true;
            newData.rpName = nameBox.getValue().trim();
            newData.gender = selectedGender;
            newData.ageAndBirth = ageBirthBox.getValue().trim();
            newData.race = raceBox.getValue().trim();
            newData.heightWeight = heightWeightBox.getValue().trim();
            newData.hair = hairBox.getValue().trim();
            newData.eyes = eyesBox.getValue().trim();
            newData.features = featuresBox.getValue().trim();
            newData.voice = voiceBox.getValue().trim();
            newData.optional = optionalBox.getValue().trim();

            // Сохраняем локально и отправляем на сервер
            Minecraft.getInstance().player.setData(PassportData.PASSPORT, newData);
            PacketDistributor.sendToServer(new PassportNetworking.PassportPayload(newData.serializeNBT(Minecraft.getInstance().player.registryAccess())));
            this.rebuildWidgets();
        });
        this.addRenderableWidget(confirmBtn);
    }

    private EditBox createField(int x, int y, int w, int h, String initialVal, int maxLen, String hint) {
        EditBox box = new EditBox(this.font, x, y, w, h, Component.empty());
        box.setMaxLength(maxLen);
        box.setValue(initialVal);
        box.setHint(Component.literal(hint).withStyle(style -> style.withColor(0x555555)));
        this.addRenderableWidget(box);
        return box;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);

        int mainWinX = leftPos + 110;
        int mainWinY = topPos + 10;
        int mainWinW = xSize - 120;
        int mainWinH = ySize - 20;

        // Подложка левой панели кнопок
        guiGraphics.fill(leftPos + 4, topPos + 10, leftPos + 107, topPos + ySize - 10, 0xCC080808);
        guiGraphics.renderOutline(leftPos + 4, topPos + 10, 103, ySize - 20, 0xFF2A2A2A);

        // Подложка основного окна справа (глубокий темный цвет + двойная рамка)
        guiGraphics.fill(mainWinX, mainWinY, mainWinX + mainWinW, mainWinY + mainWinH, 0xEE121212);
        guiGraphics.renderOutline(mainWinX, mainWinY, mainWinW, mainWinH, 0xFF3A3A3A);
        guiGraphics.renderOutline(mainWinX + 1, mainWinY + 1, mainWinW - 2, mainWinH - 2, 0xFF8F7A42);

        if (currentTab.equals("ИГРОК")) {
            renderPassportContent(guiGraphics, mainWinX, mainWinY);
        } else {
            guiGraphics.drawString(this.font, "РАЗДЕЛ: " + currentTab, mainWinX + 15, mainWinY + 15, 0xFFE0C068);
            guiGraphics.drawString(this.font, "Модуль в разработке...", mainWinX + 15, mainWinY + 35, 0xFF777777);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void renderPassportContent(GuiGraphics g, int winX, int winY) {
        if (Minecraft.getInstance().player == null) return;
        PassportData data = Minecraft.getInstance().player.getData(PassportData.PASSPORT);

        g.drawString(this.font, "ПАСПОРТ ПЕРСОНАЖА", winX + 12, winY + 8, 0xFFE0C068);

        if (!data.isLocked) {
            // Предупреждение об однократном заполнении
            g.drawString(this.font, "ВНИМАНИЕ: Внести данные можно ТОЛЬКО ОДИН РАЗ! Смена — через администрацию.", winX + 12, winY + 20, 0xFFFF5555);

            int col1X = leftPos + 120;
            int col2X = leftPos + 280;
            int startY = topPos + 35;
            int stepY = 34;

            // Подписи к полям
            g.drawString(this.font, "Имя персонажа (для чата):", col1X, startY, 0xFFAAAAAA);
            g.drawString(this.font, "Пол персонажа:", col2X, startY, 0xFFAAAAAA);

            g.drawString(this.font, "Возраст и ДР (без года):", col1X, startY + stepY, 0xFFAAAAAA);
            g.drawString(this.font, "Раса:", col2X, startY + stepY, 0xFFAAAAAA);

            g.drawString(this.font, "Рост и вес:", col1X, startY + stepY * 2, 0xFFAAAAAA);
            g.drawString(this.font, "Волосы:", col2X, startY + stepY * 2, 0xFFAAAAAA);

            g.drawString(this.font, "Глаза:", col1X, startY + stepY * 3, 0xFFAAAAAA);
            g.drawString(this.font, "Голос:", col2X, startY + stepY * 3, 0xFFAAAAAA);

            g.drawString(this.font, "Особенности внешности:", col1X, startY + stepY * 4, 0xFFAAAAAA);
            g.drawString(this.font, "Дополнительно (по желанию):", col1X, startY + stepY * 4 + 30, 0xFFAAAAAA);
        } else {
            // РЕЖИМ ПРОСМОТРА УТВЕРЖДЕННОГО ПАСПОРТА
            g.drawString(this.font, "Статус: УТВЕРЖДЕН (Изменения только через администрацию сервера)", winX + 12, winY + 20, 0xFF55FF55);

            // Декоративная золотая линия
            g.fill(winX + 12, winY + 33, winX + 318, winY + 34, 0xFF8F7A42);

            int y = winY + 42;
            int step = 18;

            drawPassportRow(g, "Имя персонажа:", data.rpName, winX + 15, y);
            drawPassportRow(g, "Пол:", data.gender, winX + 185, y);

            drawPassportRow(g, "Возраст / ДР:", data.ageAndBirth, winX + 15, y + step);
            drawPassportRow(g, "Раса:", data.race, winX + 185, y + step);

            drawPassportRow(g, "Рост / Вес:", data.heightWeight, winX + 15, y + step * 2);
            drawPassportRow(g, "Волосы:", data.hair, winX + 185, y + step * 2);

            drawPassportRow(g, "Глаза:", data.eyes, winX + 15, y + step * 3);
            drawPassportRow(g, "Голос:", data.voice, winX + 185, y + step * 3);

            drawPassportRow(g, "Особенности внешности:", data.features, winX + 15, y + step * 4 + 4);
            drawPassportRow(g, "Дополнительно:", data.optional.isEmpty() ? "—" : data.optional, winX + 15, y + step * 5 + 8);

            g.fill(winX + 12, winY + 185, winX + 318, winY + 186, 0xFF3A3A3A);
            g.drawString(this.font, "Показать паспорт игроку: команда /passport show <ник>", winX + 15, winY + 195, 0xFFE0C068);
        }
    }

    private void drawPassportRow(GuiGraphics g, String label, String value, int x, int y) {
        g.drawString(this.font, label, x, y, 0xFF888888);
        int labelWidth = this.font.width(label) + 5;
        g.drawString(this.font, value.isEmpty() ? "Не указано" : value, x + labelWidth, y, 0xFFFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
