package com.amarthaloe.client.gui;

import com.amarthaloe.data.PassportData;
import com.amarthaloe.network.PassportNetworking;
import com.amarthaloe.rpg.Faction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

public class AmarthaloeMenuScreen extends Screen {

    private final int xSize = 460;
    private final int ySize = 275;
    private int leftPos;
    private int topPos;

    private final List<DarkButton> menuButtons = new ArrayList<>();
    private String currentTab = "ИГРОК";

    // Элементы паспорта
    private EditBox nameBox, ageBirthBox, raceBox, heightWeightBox, hairBox, eyesBox, featuresBox, voiceBox, optionalBox;
    private DarkButton genderBtn, confirmBtn;
    private String selectedGender = "Мужской";

    // Индекс выбранной фракции на большой шкале репутации
    private int selectedFactionIdx = 3; // По умолчанию Наместники Либры (Правительство)

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

        switch (currentTab) {
            case "ИГРОК" -> initPlayerPassportTab();
            case "РЕПУТАЦИЯ" -> initReputationTab();
            case "ТИТУЛЫ" -> initTitlesTab();
        }
    }

    private void initReputationTab() {
        int mainWinX = leftPos + 110;
        int mainWinY = topPos + 8;

        // Стрелочки переключения фракций слева и справа от главной шкалы (как на эскизе)
        DarkButton prevBtn = new DarkButton(mainWinX + 12, mainWinY + 42, 22, 24, Component.literal("◄"), (btn) -> {
            selectedFactionIdx = (selectedFactionIdx - 1 + Faction.values().length) % Faction.values().length;
        });
        DarkButton nextBtn = new DarkButton(mainWinX + 308, mainWinY + 42, 22, 24, Component.literal("►"), (btn) -> {
            selectedFactionIdx = (selectedFactionIdx + 1) % Faction.values().length;
        });

        this.addRenderableWidget(prevBtn);
        this.addRenderableWidget(nextBtn);
    }

    private void initTitlesTab() {
        if (Minecraft.getInstance().player == null) return;
        PassportData data = Minecraft.getInstance().player.getData(PassportData.PASSPORT);

        int mainWinX = leftPos + 110;
        int mainWinY = topPos + 8;
        int startY = mainWinY + 58;
        int stepY = 38;

        Faction[] factions = Faction.values();
        for (int i = 0; i < factions.length; i++) {
            Faction f = factions[i];
            int rep = data.getRep(f);
            boolean unlocked = Math.abs(rep) >= 10; // Титул действует от +10 или -10
            boolean isEquipped = f.id.equals(data.activeTitleFaction);

            String btnLabel = !unlocked ? "Недоступно (нужно ±10)" : (isEquipped ? "СНЯТЬ ТИТУЛ" : "ВЫБРАТЬ");

            DarkButton selectBtn = new DarkButton(mainWinX + 215, startY + i * stepY, 115, 20, Component.literal(btnLabel), (btn) -> {
                String newActive = isEquipped ? "" : f.id;
                data.activeTitleFaction = newActive;
                PacketDistributor.sendToServer(new PassportNetworking.SelectTitlePayload(newActive));
                this.rebuildWidgets();
            });
            selectBtn.active = unlocked;
            selectBtn.setSelected(isEquipped);
            this.addRenderableWidget(selectBtn);
        }
    }

    private void initPlayerPassportTab() {
        if (Minecraft.getInstance().player == null) return;
        PassportData data = Minecraft.getInstance().player.getData(PassportData.PASSPORT);

        if (data.isLocked) return;

        this.selectedGender = data.gender.isEmpty() ? "Мужской" : data.gender;

        int col1X = leftPos + 122;
        int col2X = leftPos + 286;
        int fieldW = 152;
        int fieldH = 16;
        int startY = topPos + 46;
        int stepY = 31;

        nameBox = createField(col1X, startY, fieldW, fieldH, data.rpName, 32, "Например: Артур");
        ageBirthBox = createField(col1X, startY + stepY, fieldW, fieldH, data.ageAndBirth, 40, "25 лет, 14 марта");
        heightWeightBox = createField(col1X, startY + stepY * 2, fieldW, fieldH, data.heightWeight, 32, "185 см / 82 кг");
        eyesBox = createField(col1X, startY + stepY * 3, fieldW, fieldH, data.eyes, 40, "Серые, глубокие");

        genderBtn = new DarkButton(col2X, startY, fieldW, fieldH, Component.literal("Пол: " + selectedGender), (btn) -> {
            selectedGender = selectedGender.equals("Мужской") ? "Женский" : "Мужской";
            btn.setMessage(Component.literal("Пол: " + selectedGender));
        });
        this.addRenderableWidget(genderBtn);

        raceBox = createField(col2X, startY + stepY, fieldW, fieldH, data.race, 32, "Человек...");
        hairBox = createField(col2X, startY + stepY * 2, fieldW, fieldH, data.hair, 40, "Темные, до плеч");
        voiceBox = createField(col2X, startY + stepY * 3, fieldW, fieldH, data.voice, 40, "Низкий баритон");

        int fullW = 316;
        featuresBox = createField(col1X, startY + stepY * 4, fullW, fieldH, data.features, 80, "Шрам над бровью, легкая хромота...");
        optionalBox = createField(col1X, startY + stepY * 5, fullW, fieldH, data.optional, 80, "Любая деталь по желанию (необязательно)");

        confirmBtn = new DarkButton(col1X + 48, topPos + ySize - 32, 220, 20, Component.literal("ПОДТВЕРДИТЬ ПАСПОРТ (НАВСЕГДА)"), (btn) -> {
            if (nameBox.getValue().trim().isEmpty()) {
                nameBox.setSuggestion("Укажите имя!");
                return;
            }
            PassportData current = Minecraft.getInstance().player.getData(PassportData.PASSPORT);
            current.isLocked = true;
            current.rpName = nameBox.getValue().trim();
            current.gender = selectedGender;
            current.ageAndBirth = ageBirthBox.getValue().trim();
            current.race = raceBox.getValue().trim();
            current.heightWeight = heightWeightBox.getValue().trim();
            current.hair = hairBox.getValue().trim();
            current.eyes = eyesBox.getValue().trim();
            current.features = featuresBox.getValue().trim();
            current.voice = voiceBox.getValue().trim();
            current.optional = optionalBox.getValue().trim();

            Minecraft.getInstance().player.setData(PassportData.PASSPORT, current);
            PacketDistributor.sendToServer(new PassportNetworking.PassportPayload(current.serializeNBT(Minecraft.getInstance().player.registryAccess())));
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
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(guiGraphics, mouseX, mouseY, partialTick);

        int mainWinX = leftPos + 110;
        int mainWinY = topPos + 8;
        int mainWinW = xSize - 118;
        int mainWinH = ySize - 16;

        guiGraphics.fill(leftPos + 4, topPos + 8, leftPos + 107, topPos + ySize - 8, 0xCC080808);
        guiGraphics.renderOutline(leftPos + 4, topPos + 8, 103, ySize - 16, 0xFF2A2A2A);

        guiGraphics.fill(mainWinX, mainWinY, mainWinX + mainWinW, mainWinY + mainWinH, 0xF2101010);
        guiGraphics.renderOutline(mainWinX, mainWinY, mainWinW, mainWinH, 0xFF3A3A3A);
        guiGraphics.renderOutline(mainWinX + 1, mainWinY + 1, mainWinW - 2, mainWinH - 2, 0xFF8F7A42);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        int mainWinX = leftPos + 110;
        int mainWinY = topPos + 8;

        switch (currentTab) {
            case "ИГРОК" -> renderPassportContent(guiGraphics, mainWinX, mainWinY);
            case "РЕПУТАЦИЯ" -> renderReputationContent(guiGraphics, mainWinX, mainWinY);
            case "ТИТУЛЫ" -> renderTitlesContent(guiGraphics, mainWinX, mainWinY);
            default -> {
                guiGraphics.drawString(this.font, "РАЗДЕЛ: " + currentTab, mainWinX + 15, mainWinY + 15, 0xFFE0C068);
                guiGraphics.drawString(this.font, "Модуль в разработке...", mainWinX + 15, mainWinY + 35, 0xFF777777);
            }
        }
    }

    private void renderReputationContent(GuiGraphics g, int winX, int winY) {
        if (Minecraft.getInstance().player == null) return;
        PassportData data = Minecraft.getInstance().player.getData(PassportData.PASSPORT);

        g.drawString(this.font, "ВЕСЫ ЛИБРЫ: РЕПУТАЦИЯ В ИСОНОМИИ", winX + 12, winY + 10, 0xFFE0C068);
        g.drawString(this.font, "Пороги рангов: ±10, ±50, ±100, ±500, ±1000", winX + 12, winY + 22, 0xFF888888);

        Faction current = Faction.values()[selectedFactionIdx];
        int rep = data.getRep(current);
        String title = current.getTitle(rep, data.isFemale());
        int[] bounds = current.getBracketBounds(rep);

        // --- 1. ГЛАВНАЯ ШКАЛА ВЫБРАННОЙ ФРАКЦИИ (по эскизу) ---
        int barX = winX + 38;
        int barY = winY + 42;
        int barW = 266;
        int barH = 24;

        // Внешний корпус шкалы с цветом выбранной фракции
        g.fill(barX, barY, barX + barW, barY + barH, 0xFF181818);
        int fillCol = rep < 0 ? 0xFF8B2626 : current.color;

        // Расчет заполнения внутри текущего сегмента
        int minB = bounds[0];
        int maxB = bounds[1];
        float progress = Math.clamp((float)(rep - minB) / (float)(maxB - minB), 0.0f, 1.0f);
        int fillW = (int)(barW * progress);

        // Полупрозрачная заливка прогресса + яркая полоса снизу
        g.fill(barX + 1, barY + 1, barX + fillW, barY + barH - 1, (fillCol & 0x00FFFFFF) | 0x66000000);
        g.fill(barX + 1, barY + barH - 3, barX + fillW, barY + barH - 1, fillCol);

        // Золотисто-фракционная обводка самой капсулы
        g.renderOutline(barX, barY, barW, barH, current.color);
        // Центральная засечка равновесия Либры
        g.fill(barX + barW / 2, barY, barX + barW / 2 + 1, barY + barH, 0x55FFFFFF);

        // Название фракции крупно по центру внутри шкалы
        g.drawCenteredString(this.font, current.displayName, barX + barW / 2, barY + 8, 0xFFFFFFFF);

        // Подписи под шкалой: Левый порог | Текущее число + Титул | Правый порог
        String leftLabel = (minB > 0 ? "+" : "") + minB;
        String rightLabel = (maxB > 0 ? "+" : "") + maxB;
        String centerLabel = (rep > 0 ? "+" : "") + rep + " " + title;

        g.drawString(this.font, leftLabel, barX + 2, barY + barH + 5, 0xFFAAAAAA);
        g.drawCenteredString(this.font, centerLabel, barX + barW / 2, barY + barH + 5, rep < 0 ? 0xFFFF6666 : current.color);
        int rWidth = this.font.width(rightLabel);
        g.drawString(this.font, rightLabel, barX + barW - rWidth - 2, barY + barH + 5, 0xFFAAAAAA);

        // Описание выбранной фракции
        g.drawCenteredString(this.font, current.description, winX + 171, barY + barH + 19, 0xFF777777);

        // Разделительная линия
        g.fill(winX + 12, winY + 102, winX + 330, winY + 103, 0xFF3A3A3A);
        g.drawString(this.font, "СВОДКА ПО ВСЕМ СТОРОНАМ ИСОНОМИИ:", winX + 12, winY + 110, 0xFFE0C068);

        // --- 2. ОБЗОР ВСЕХ 4 ФРАКЦИЙ ОДНОВРЕМЕННО ---
        int listY = winY + 126;
        int rowStep = 31;
        Faction[] all = Faction.values();

        for (int i = 0; i < all.length; i++) {
            Faction f = all[i];
            int fRep = data.getRep(f);
            String fTitle = f.getTitle(fRep, data.isFemale());
            int rowY = listY + i * rowStep;

            // Рамка строки (подсвечивается, если эта фракция сейчас выбрана сверху)
            int boxBg = (i == selectedFactionIdx) ? 0xFF1D1A14 : 0xFF141414;
            int boxBorder = (i == selectedFactionIdx) ? f.color : 0xFF2B2B2B;
            g.fill(winX + 12, rowY, winX + 330, rowY + 26, boxBg);
            g.renderOutline(winX + 12, rowY, 318, 26, boxBorder);

            // Цветной маркер фракции слева
            g.fill(winX + 14, rowY + 2, winX + 18, rowY + 24, f.color);

            // Название и текущий ранг
            g.drawString(this.font, f.displayName, winX + 24, rowY + 4, 0xFFFFFFFF);
            String rankStr = (fRep > 0 ? "+" : "") + fRep + " • " + fTitle;
            int rankW = this.font.width(rankStr);
            g.drawString(this.font, rankStr, winX + 324 - rankW, rowY + 4, fRep < 0 ? 0xFFFF5555 : f.color);

            // Мини-шкала от -1000 до +1000 с центром в 0
            int miniX = winX + 24;
            int miniY = rowY + 17;
            int miniW = 300;
            g.fill(miniX, miniY, miniX + miniW, miniY + 4, 0xFF090909);
            int centerMini = miniX + miniW / 2;

            float norm = Math.clamp(fRep / 1000.0f, -1.0f, 1.0f);
            int barLen = (int)((miniW / 2) * Math.abs(norm));
            if (fRep >= 0) {
                g.fill(centerMini, miniY, centerMini + barLen, miniY + 4, f.color);
            } else {
                g.fill(centerMini - barLen, miniY, centerMini, miniY + 4, 0xFFAA2222);
            }
            // Нулевая отметка посередине
            g.fill(centerMini, miniY - 1, centerMini + 1, miniY + 5, 0xFFFFFFFF);
        }
    }

    private void renderTitlesContent(GuiGraphics g, int winX, int winY) {
        if (Minecraft.getInstance().player == null) return;
        PassportData data = Minecraft.getInstance().player.getData(PassportData.PASSPORT);

        g.drawString(this.font, "ДЕЙСТВУЮЩИЕ ТИТУЛЫ ИСОНОМИИ", winX + 12, winY + 10, 0xFFE0C068);
        g.drawString(this.font, "Пол в паспорте: " + data.gender + " (титулы адаптированы)", winX + 12, winY + 23, 0xFFAAAAAA);

        Faction activeFac = Faction.byId(data.activeTitleFaction);
        String previewName = data.rpName.isEmpty() ? Minecraft.getInstance().player.getName().getString() : data.rpName;
        String previewChat;
        if (activeFac != null && Math.abs(data.getRep(activeFac)) >= 10) {
            String t = activeFac.getTitle(data.getRep(activeFac), data.isFemale());
            String col = data.getRep(activeFac) < 0 ? "§4" : activeFac.chatColor;
            previewChat = col + "[" + t + "] §f" + previewName;
        } else {
            previewChat = "§7(Без титула) §f" + previewName;
        }
        g.drawString(this.font, "Предпросмотр в чате: " + previewChat, winX + 12, winY + 37, 0xFFE0C068);
        g.fill(winX + 12, winY + 50, winX + 330, winY + 51, 0xFF8F7A42);

        int startY = winY + 58;
        int stepY = 38;
        Faction[] factions = Faction.values();

        for (int i = 0; i < factions.length; i++) {
            Faction f = factions[i];
            int rep = data.getRep(f);
            String title = f.getTitle(rep, data.isFemale());
            int rowY = startY + i * stepY;

            g.fill(winX + 12, rowY - 4, winX + 210, rowY + 24, 0xFF151515);
            g.renderOutline(winX + 12, rowY - 4, 198, 28, f.color);

            g.drawString(this.font, f.displayName + " (" + (rep > 0 ? "+" : "") + rep + ")", winX + 18, rowY, 0xFFAAAAAA);
            g.drawString(this.font, "Титул: " + title, winX + 18, rowY + 11, rep < 0 ? 0xFFFF5555 : f.color);
        }
    }

    private void renderPassportContent(GuiGraphics g, int winX, int winY) {
        if (Minecraft.getInstance().player == null) return;
        PassportData data = Minecraft.getInstance().player.getData(PassportData.PASSPORT);

        g.drawString(this.font, "ПАСПОРТ ПЕРСОНАЖА", winX + 12, winY + 8, 0xFFE0C068);

        if (!data.isLocked) {
            g.drawString(this.font, "ВНИМАНИЕ: Заполняется ОДИН РАЗ! Смена — через админов.", winX + 12, winY + 20, 0xFFFF5555);

            int col1X = leftPos + 122;
            int col2X = leftPos + 286;
            int labelY = topPos + 36;
            int stepY = 31;

            g.drawString(this.font, "Имя персонажа (для чата):", col1X, labelY, 0xFFBBBBBB);
            g.drawString(this.font, "Пол персонажа:", col2X, labelY, 0xFFBBBBBB);

            g.drawString(this.font, "Возраст и ДР (без года):", col1X, labelY + stepY, 0xFFBBBBBB);
            g.drawString(this.font, "Раса:", col2X, labelY + stepY, 0xFFBBBBBB);

            g.drawString(this.font, "Рост и вес:", col1X, labelY + stepY * 2, 0xFFBBBBBB);
            g.drawString(this.font, "Волосы:", col2X, labelY + stepY * 2, 0xFFBBBBBB);

            g.drawString(this.font, "Глаза:", col1X, labelY + stepY * 3, 0xFFBBBBBB);
            g.drawString(this.font, "Голос:", col2X, labelY + stepY * 3, 0xFFBBBBBB);

            g.drawString(this.font, "Особенности внешности:", col1X, labelY + stepY * 4, 0xFFBBBBBB);
            g.drawString(this.font, "Дополнительно (по желанию):", col1X, labelY + stepY * 5, 0xFFBBBBBB);
        } else {
            g.drawString(this.font, "Статус: УТВЕРЖДЕН (Смена через администрацию)", winX + 12, winY + 20, 0xFF55FF55);
            g.fill(winX + 12, winY + 33, winX + 330, winY + 34, 0xFF8F7A42);

            int y = winY + 44;
            int step = 20;

            drawPassportRow(g, "Имя персонажа:", data.rpName, winX + 15, y);
            drawPassportRow(g, "Пол:", data.gender, winX + 190, y);

            drawPassportRow(g, "Возраст / ДР:", data.ageAndBirth, winX + 15, y + step);
            drawPassportRow(g, "Раса:", data.race, winX + 190, y + step);

            drawPassportRow(g, "Рост / Вес:", data.heightWeight, winX + 15, y + step * 2);
            drawPassportRow(g, "Волосы:", data.hair, winX + 190, y + step * 2);

            drawPassportRow(g, "Глаза:", data.eyes, winX + 15, y + step * 3);
            drawPassportRow(g, "Голос:", data.voice, winX + 190, y + step * 3);

            drawPassportRow(g, "Особенности внешности:", data.features, winX + 15, y + step * 4 + 4);
            drawPassportRow(g, "Дополнительно:", data.optional.isEmpty() ? "—" : data.optional, winX + 15, y + step * 5 + 8);

            g.fill(winX + 12, winY + 198, winX + 330, winY + 199, 0xFF3A3A3A);
            g.drawString(this.font, "Показать паспорт: /passport show <ник>", winX + 15, winY + 208, 0xFFE0C068);
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
