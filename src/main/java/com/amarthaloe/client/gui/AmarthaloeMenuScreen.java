package com.amarthaloe.client.gui;

import com.amarthaloe.data.PassportData;
import com.amarthaloe.network.PassportNetworking;
import com.amarthaloe.rpg.Faction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
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

    // Репутация
    private int selectedFactionIdx = 3;

    // Друзья и Мессенджер
    private EditBox addFriendBox;
    private EditBox chatInputBox;
    private String viewingFriendPassportUuid = null; // Если не null, показываем паспорт выбранного друга
    private String activeChatFriendUuid = null;      // С кем открыт диалог во вкладке СООБЩЕНИЯ
    private int friendsPage = 0;

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
                this.viewingFriendPassportUuid = null;
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
            case "ДРУЗЬЯ" -> initFriendsTab();
            case "СООБЩЕНИЯ" -> initMessagesTab();
        }
    }

    private void initFriendsTab() {
        if (Minecraft.getInstance().player == null) return;
        PassportData data = Minecraft.getInstance().player.getData(PassportData.PASSPORT);
        int winX = leftPos + 110;
        int winY = topPos + 8;

        // Если сейчас открыт просмотр паспорта друга — рисуем только кнопку «Назад к списку друзей»
        if (viewingFriendPassportUuid != null) {
            DarkButton backBtn = new DarkButton(winX + 14, winY + ySize - 44, 150, 20, Component.literal("◄ Назад к списку друзей"), (b) -> {
                viewingFriendPassportUuid = null;
                this.rebuildWidgets();
            });
            this.addRenderableWidget(backBtn);
            return;
        }

        // Поле ввода ника или РП-имени для добавления в друзья
        addFriendBox = createField(winX + 12, winY + 24, 210, 18, "", 40, "Введите ник или РП-имя...");
        DarkButton addBtn = new DarkButton(winX + 228, winY + 24, 102, 18, Component.literal("+ Добавить"), (b) -> {
            String val = addFriendBox.getValue().trim();
            if (!val.isEmpty()) {
                PacketDistributor.sendToServer(new PassportNetworking.SocialActionPayload("ADD_FRIEND", val, ""));
                addFriendBox.setValue("");
            }
        });
        this.addRenderableWidget(addBtn);

        int currentY = winY + 52;

        // 1. Входящие заявки в друзья (показываем до 2 сверху, если есть)
        List<String> reqs = new ArrayList<>(data.incomingRequests);
        for (int i = 0; i < Math.min(2, reqs.size()); i++) {
            String reqUuid = reqs.get(i);
            int rowY = currentY + i * 24;

            DarkButton acceptBtn = new DarkButton(winX + 206, rowY, 60, 18, Component.literal("Принять"), (b) -> {
                PacketDistributor.sendToServer(new PassportNetworking.SocialActionPayload("ACCEPT_FRIEND", reqUuid, ""));
                data.incomingRequests.remove(reqUuid);
                data.friends.add(reqUuid);
                this.rebuildWidgets();
            });
            DarkButton declineBtn = new DarkButton(winX + 270, rowY, 60, 18, Component.literal("Отклонить"), (b) -> {
                PacketDistributor.sendToServer(new PassportNetworking.SocialActionPayload("DECLINE_FRIEND", reqUuid, ""));
                data.incomingRequests.remove(reqUuid);
                this.rebuildWidgets();
            });
            this.addRenderableWidget(acceptBtn);
            this.addRenderableWidget(declineBtn);
        }

        if (!reqs.isEmpty()) {
            currentY += Math.min(2, reqs.size()) * 24 + 14;
        } else {
            currentY += 12;
        }

        // 2. Список друзей с быстрыми кнопками: [Паспорт] [ЛС] [В группу] [✖]
        List<String> friendList = new ArrayList<>(data.friends);
        int perPage = 5;
        int startIdx = friendsPage * perPage;

        for (int i = 0; i < perPage && (startIdx + i) < friendList.size(); i++) {
            String fUuid = friendList.get(startIdx + i);
            int rowY = currentY + i * 32;

            // Кнопка: Паспорт
            DarkButton passBtn = new DarkButton(winX + 142, rowY + 4, 54, 18, Component.literal("Паспорт"), (b) -> {
                this.viewingFriendPassportUuid = fUuid;
                this.rebuildWidgets();
            });
            // Кнопка: Написать в ЛС
            DarkButton dmBtn = new DarkButton(winX + 199, rowY + 4, 34, 18, Component.literal("ЛС"), (b) -> {
                this.activeChatFriendUuid = fUuid;
                this.currentTab = "СООБЩЕНИЯ";
                this.rebuildWidgets();
            });
            // Кнопка: Пригласить в группу
            DarkButton partyBtn = new DarkButton(winX + 236, rowY + 4, 68, 18, Component.literal("+ В группу"), (b) -> {
                PacketDistributor.sendToServer(new PassportNetworking.SocialActionPayload("INVITE_PARTY", fUuid, ""));
            });
            // Кнопка: Удалить
            DarkButton delBtn = new DarkButton(winX + 307, rowY + 4, 20, 18, Component.literal("✖"), (b) -> {
                PacketDistributor.sendToServer(new PassportNetworking.SocialActionPayload("REMOVE_FRIEND", fUuid, ""));
                data.friends.remove(fUuid);
                this.rebuildWidgets();
            });

            this.addRenderableWidget(passBtn);
            this.addRenderableWidget(dmBtn);
            this.addRenderableWidget(partyBtn);
            this.addRenderableWidget(delBtn);
        }
    }

    private void initMessagesTab() {
        if (Minecraft.getInstance().player == null) return;
        PassportData data = Minecraft.getInstance().player.getData(PassportData.PASSPORT);
        int winX = leftPos + 110;
        int winY = topPos + 8;

        List<String> friendList = new ArrayList<>(data.friends);
        if (activeChatFriendUuid == null && !friendList.isEmpty()) {
            activeChatFriendUuid = friendList.get(0);
        }

        // Левая колонка контактов внутри вкладки СООБЩЕНИЯ
        for (int i = 0; i < Math.min(8, friendList.size()); i++) {
            String fUuid = friendList.get(i);
            CompoundTag prof = data.knownProfiles.get(fUuid);
            String name = prof != null ? prof.getString("rpName") : "Друг";
            if (name.length() > 11) name = name.substring(0, 11) + ".";

            DarkButton contactBtn = new DarkButton(winX + 10, winY + 28 + i * 24, 90, 20, Component.literal(name), (b) -> {
                activeChatFriendUuid = fUuid;
                this.rebuildWidgets();
            });
            contactBtn.setSelected(fUuid.equals(activeChatFriendUuid));
            this.addRenderableWidget(contactBtn);
        }

        if (activeChatFriendUuid != null) {
            chatInputBox = createField(winX + 108, winY + ySize - 42, 165, 18, "", 120, "Написать сообщение...");
            DarkButton sendBtn = new DarkButton(winX + 277, winY + ySize - 42, 55, 18, Component.literal("Отпр."), (b) -> {
                String msg = chatInputBox.getValue().trim();
                if (!msg.isEmpty()) {
                    PacketDistributor.sendToServer(new PassportNetworking.SocialActionPayload("SEND_DM", activeChatFriendUuid, msg));
                    chatInputBox.setValue("");
                }
            });
            this.addRenderableWidget(sendBtn);
        }
    }

    private void initReputationTab() {
        int mainWinX = leftPos + 110;
        int mainWinY = topPos + 8;

        DarkButton prevBtn = new DarkButton(mainWinX + 12, mainWinY + 38, 22, 24, Component.literal("◄"), (btn) -> {
            selectedFactionIdx = (selectedFactionIdx - 1 + Faction.values().length) % Faction.values().length;
        });
        DarkButton nextBtn = new DarkButton(mainWinX + 308, mainWinY + 38, 22, 24, Component.literal("►"), (btn) -> {
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
            boolean unlocked = Math.abs(rep) >= 10;
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
            case "ДРУЗЬЯ" -> renderFriendsContent(guiGraphics, mainWinX, mainWinY);
            case "СООБЩЕНИЯ" -> renderMessagesContent(guiGraphics, mainWinX, mainWinY);
            default -> {
                guiGraphics.drawString(this.font, "РАЗДЕЛ: " + currentTab, mainWinX + 15, mainWinY + 15, 0xFFE0C068);
                guiGraphics.drawString(this.font, "Модуль в разработке...", mainWinX + 15, mainWinY + 35, 0xFF777777);
            }
        }
    }

    private void renderFriendsContent(GuiGraphics g, int winX, int winY) {
        if (Minecraft.getInstance().player == null) return;
        PassportData data = Minecraft.getInstance().player.getData(PassportData.PASSPORT);

        // Режим просмотра паспорта выбранного друга
        if (viewingFriendPassportUuid != null) {
            CompoundTag prof = data.knownProfiles.get(viewingFriendPassportUuid);
            g.drawString(this.font, "ПАСПОРТ ДРУГА", winX + 14, winY + 10, 0xFFE0C068);
            g.fill(winX + 12, winY + 24, winX + 330, winY + 25, 0xFF8F7A42);

            if (prof == null || !prof.getBoolean("isLocked")) {
                g.drawString(this.font, "Этот игрок еще не заполнил свой официальный паспорт.", winX + 15, winY + 45, 0xFFFF5555);
                return;
            }

            int y = winY + 36;
            int step = 20;
            drawPassportRow(g, "Имя персонажа:", prof.getString("rpName"), winX + 15, y);
            drawPassportRow(g, "Пол:", prof.getString("gender"), winX + 190, y);
            drawPassportRow(g, "Возраст / ДР:", prof.getString("ageAndBirth"), winX + 15, y + step);
            drawPassportRow(g, "Раса:", prof.getString("race"), winX + 190, y + step);
            drawPassportRow(g, "Рост / Вес:", prof.getString("heightWeight"), winX + 15, y + step * 2);
            drawPassportRow(g, "Волосы:", prof.getString("hair"), winX + 190, y + step * 2);
            drawPassportRow(g, "Глаза:", prof.getString("eyes"), winX + 15, y + step * 3);
            drawPassportRow(g, "Голос:", prof.getString("voice"), winX + 190, y + step * 3);
            drawPassportRow(g, "Особенности внешности:", prof.getString("features"), winX + 15, y + step * 4 + 4);
            drawPassportRow(g, "Дополнительно:", prof.getString("optional").isEmpty() ? "—" : prof.getString("optional"), winX + 15, y + step * 5 + 8);
            return;
        }

        g.drawString(this.font, "СПИСОК ДРУЗЕЙ И КОНТАКТОВ", winX + 12, winY + 10, 0xFFE0C068);

        int currentY = winY + 52;
        List<String> reqs = new ArrayList<>(data.incomingRequests);
        if (!reqs.isEmpty()) {
            g.drawString(this.font, "Входящие запросы в друзья:", winX + 12, currentY - 10, 0xFF55FF55);
            for (int i = 0; i < Math.min(2, reqs.size()); i++) {
                String reqUuid = reqs.get(i);
                CompoundTag prof = data.knownProfiles.get(reqUuid);
                String name = prof != null ? prof.getString("rpName") + " (" + prof.getString("username") + ")" : "Неизвестный игрок";
                int rowY = currentY + i * 24;
                g.fill(winX + 12, rowY, winX + 202, rowY + 18, 0xFF1A241A);
                g.renderOutline(winX + 12, rowY, 190, 18, 0xFF3A6A3A);
                g.drawString(this.font, name, winX + 18, rowY + 5, 0xFFFFFFFF);
            }
            currentY += Math.min(2, reqs.size()) * 24 + 14;
        } else {
            currentY += 12;
        }

        g.fill(winX + 12, currentY - 8, winX + 330, currentY - 7, 0xFF3A3A3A);

        List<String> friendList = new ArrayList<>(data.friends);
        if (friendList.isEmpty()) {
            g.drawString(this.font, "У вас пока нет добавленных друзей.", winX + 15, currentY + 10, 0xFF888888);
            g.drawString(this.font, "Введите ник или РП-имя персонажа сверху и нажмите «+ Добавить».", winX + 15, currentY + 24, 0xFF666666);
            return;
        }

        int perPage = 5;
        int startIdx = friendsPage * perPage;
        for (int i = 0; i < perPage && (startIdx + i) < friendList.size(); i++) {
            String fUuid = friendList.get(startIdx + i);
            CompoundTag prof = data.knownProfiles.get(fUuid);
            String rpName = prof != null ? prof.getString("rpName") : "Игрок";
            String username = prof != null ? prof.getString("username") : "";
            boolean online = prof != null && prof.getBoolean("online");

            int rowY = currentY + i * 32;
            g.fill(winX + 12, rowY, winX + 330, rowY + 26, 0xFF151515);
            g.renderOutline(winX + 12, rowY, 318, 26, online ? 0xFF2E5A2E : 0xFF2B2B2B);

            // Индикатор сети + Имя
            String statusDot = online ? "§a● " : "§7○ ";
            g.drawString(this.font, statusDot + "§f" + rpName, winX + 18, rowY + 4, 0xFFFFFFFF);
            g.drawString(this.font, (online ? "§aВ сети" : "§8Не в сети") + (username.isEmpty() ? "" : " §8(" + username + ")"), winX + 18, rowY + 14, 0xFF888888);
        }
    }

    private void renderMessagesContent(GuiGraphics g, int winX, int winY) {
        if (Minecraft.getInstance().player == null) return;
        PassportData data = Minecraft.getInstance().player.getData(PassportData.PASSPORT);

        g.drawString(this.font, "ЛИЧНЫЕ СООБЩЕНИЯ (МЕССЕНДЖЕР)", winX + 12, winY + 10, 0xFFE0C068);
        g.fill(winX + 104, winY + 24, winX + 105, winY + ySize - 24, 0xFF3A3A3A);

        if (activeChatFriendUuid == null) {
            g.drawString(this.font, "Добавьте друга, чтобы начать переписку.", winX + 115, winY + 40, 0xFF888888);
            return;
        }

        CompoundTag prof = data.knownProfiles.get(activeChatFriendUuid);
        String chatPartnerName = prof != null ? prof.getString("rpName") : "Собеседник";
        boolean online = prof != null && prof.getBoolean("online");

        g.drawString(this.font, "Диалог: §e" + chatPartnerName + (online ? " §a(В сети)" : " §7(Не в сети)"), winX + 110, winY + 26, 0xFFFFFFFF);
        g.fill(winX + 108, winY + 38, winX + 332, winY + ySize - 48, 0xFF0B0B0B);
        g.renderOutline(winX + 108, winY + 38, 224, ySize - 86, 0xFF2B2B2B);

        List<String> history = PassportNetworking.CLIENT_DM_HISTORY.getOrDefault(activeChatFriendUuid, List.of());
        int maxLines = 11;
        int start = Math.max(0, history.size() - maxLines);
        int msgY = winY + 44;

        for (int i = start; i < history.size(); i++) {
            List<FormattedCharSequence> wrapped = this.font.split(Component.literal(history.get(i)), 214);
            for (FormattedCharSequence line : wrapped) {
                if (msgY < winY + ySize - 56) {
                    g.drawString(this.font, line, winX + 113, msgY, 0xFFFFFFFF);
                    msgY += 11;
                }
            }
        }
    }

    private void renderReputationContent(GuiGraphics g, int winX, int winY) {
        if (Minecraft.getInstance().player == null) return;
        PassportData data = Minecraft.getInstance().player.getData(PassportData.PASSPORT);

        g.drawString(this.font, "ВЕСЫ ЛИБРЫ: РЕПУТАЦИЯ В ИСОНОМИИ", winX + 12, winY + 8, 0xFFE0C068);
        g.drawString(this.font, "Пороги рангов: ±10, ±50, ±100, ±500, ±1000", winX + 12, winY + 20, 0xFF888888);

        Faction current = Faction.values()[selectedFactionIdx];
        int rep = data.getRep(current);
        String title = current.getTitle(rep, data.isFemale());
        int[] bounds = current.getBracketBounds(rep);

        int barX = winX + 38;
        int barY = winY + 38;
        int barW = 266;
        int barH = 24;

        g.fill(barX, barY, barX + barW, barY + barH, 0xFF181818);
        int fillCol = rep < 0 ? 0xFF8B2626 : current.color;

        int minB = bounds[0];
        int maxB = bounds[1];
        float progress = Math.clamp((float)(rep - minB) / (float)(maxB - minB), 0.0f, 1.0f);
        int fillW = (int)(barW * progress);

        g.fill(barX + 1, barY + 1, barX + fillW, barY + barH - 1, (fillCol & 0x00FFFFFF) | 0x66000000);
        g.fill(barX + 1, barY + barH - 3, barX + fillW, barY + barH - 1, fillCol);

        g.renderOutline(barX, barY, barW, barH, current.color);
        g.fill(barX + barW / 2, barY, barX + barW / 2 + 1, barY + barH, 0x55FFFFFF);

        g.drawCenteredString(this.font, current.displayName, barX + barW / 2, barY + 8, 0xFFFFFFFF);

        String leftLabel = (minB > 0 ? "+" : "") + minB;
        String rightLabel = (maxB > 0 ? "+" : "") + maxB;
        String centerLabel = (rep > 0 ? "+" : "") + rep + " " + title;

        g.drawString(this.font, leftLabel, barX + 2, barY + barH + 4, 0xFFAAAAAA);
        g.drawCenteredString(this.font, centerLabel, barX + barW / 2, barY + barH + 4, rep < 0 ? 0xFFFF6666 : current.color);
        int rWidth = this.font.width(rightLabel);
        g.drawString(this.font, rightLabel, barX + barW - rWidth - 2, barY + barH + 4, 0xFFAAAAAA);

        List<FormattedCharSequence> wrappedDesc = this.font.split(Component.literal(current.description), 310);
        int descY = barY + barH + 16;
        for (FormattedCharSequence line : wrappedDesc) {
            int lineW = this.font.width(line);
            g.drawString(this.font, line, winX + 171 - lineW / 2, descY, 0xFF888888);
            descY += 10;
        }

        g.fill(winX + 12, winY + 104, winX + 330, winY + 105, 0xFF3A3A3A);
        g.drawString(this.font, "СВОДКА ПО ВСЕМ СТОРОНАМ ИСОНОМИИ:", winX + 12, winY + 111, 0xFFE0C068);

        int listY = winY + 125;
        int rowStep = 31;
        Faction[] all = Faction.values();

        for (int i = 0; i < all.length; i++) {
            Faction f = all[i];
            int fRep = data.getRep(f);
            String fTitle = f.getTitle(fRep, data.isFemale());
            int rowY = listY + i * rowStep;

            int boxBg = (i == selectedFactionIdx) ? 0xFF1D1A14 : 0xFF141414;
            int boxBorder = (i == selectedFactionIdx) ? f.color : 0xFF2B2B2B;
            g.fill(winX + 12, rowY, winX + 330, rowY + 26, boxBg);
            g.renderOutline(winX + 12, rowY, 318, 26, boxBorder);

            g.fill(winX + 14, rowY + 2, winX + 18, rowY + 24, f.color);

            g.drawString(this.font, f.displayName, winX + 24, rowY + 4, 0xFFFFFFFF);
            String rankStr = (fRep > 0 ? "+" : "") + fRep + " • " + fTitle;
            int rankW = this.font.width(rankStr);
            g.drawString(this.font, rankStr, winX + 324 - rankW, rowY + 4, fRep < 0 ? 0xFFFF5555 : f.color);

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
            g.drawString(this.font, "Статус: УТВЕРЖЕН (Смена через администрацию)", winX + 12, winY + 20, 0xFF55FF55);
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
