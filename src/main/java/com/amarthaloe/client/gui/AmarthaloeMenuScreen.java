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

    // Увеличили высоту до 275 и ширину до 460 для идеальных отступов
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

        if (data.isLocked) {
            return;
        }

        this.selectedGender = data.gender.isEmpty() ? "Мужской" : data.gender;

        int col1X = leftPos + 122;
        int col2X = leftPos + 286;
        int fieldW = 152;
        int fieldH = 16;
        int startY = topPos + 46;
        int stepY = 31; // Аккуратный шаг по вертикали

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
        int fullW = 316;
        featuresBox = createField(col1X, startY + stepY * 4, fullW, fieldH, data.features, 80, "Шрам над бровью, легкая хромота...");
        optionalBox = createField(col1X, startY + stepY * 5, fullW, fieldH, data.optional, 80, "Любая деталь по желанию (необязательно)");

        // Кнопка подтверждения теперь стоит строго ниже всех полей с запасом места
        confirmBtn = new DarkButton(col1X + 48, topPos + ySize - 32, 220, 20, Component.literal("ПОДТВЕРДИТЬ ПАСПОРТ (НАВСЕГДА)"), (btn) -> {
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

    // В 1.21.1 фон и рамки рисуются здесь — ПОСЛЕ размытия мира, чтобы
