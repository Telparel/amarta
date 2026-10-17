package com.amarthaloe.client.gui;

import com.amarthaloe.client.ClientImageManager;
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
    private DarkButton genderBtn, confirmBtn, uploadPhotoBtn;
    private String selectedGender = "Мужской";
    private String pendingPassportPhotoId = "";

    // Репутация
    private int selectedFactionIdx = 3;

    // Друзья, Групповые чаты и Мессенджер
    private EditBox addFriendBox;
    private EditBox chatInputBox;
    private EditBox groupTitleBox, inviteGroupMemberBox;
    private String viewingFriendPassportUuid = null;
    private String activeChatFriendUuid = null;
    private boolean creatingGroupMode = false;
    private String pendingGroupAvatarId = "";
    private int friendsPage = 0;

    // Прокрутка (скроллбар) мессенджера и списка контактов
    private float chatScrollOffset = 0.0f; // 0 = самый низ (новые сообщения), >0 = сдвиг вверх к старым
    private int maxChatScroll = 0;
    private boolean isDraggingChatScrollbar = false;
    private int contactListOffset = 0;

    // Полноэкранный просмотр фотографии по клику (Лайтбокс)
    private String zoomedImageId = null;
    private record ClickableImageArea(int x, int y, int w, int h, String imageId) {}
    private final List<ClickableImageArea> clickableImages = new ArrayList<>();

    // Внутренняя структура элемента ленты чата (текстовая строка или фото)
    private record ChatRenderLine(boolean isImage, FormattedCharSequence text, String imageId, int height) {}

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

        int totalUnread = PassportNetworking.getTotalClientUnread();

        for (int i = 0; i < tabs.length; i++) {
            String tabName = tabs[i];
            String displayTab = tabName;
            if (tabName.equals("СООБЩЕНИЯ") && totalUnread > 0) {
                displayTab = "СООБЩЕНИЯ §c[+" + totalUnread + "]";
            }

            DarkButton btn = new DarkButton(startX, startY + (i * (btnHeight + 4)), btnWidth, btnHeight, Component.literal(displayTab), (button) -> {
                this.currentTab = tabName;
                this.viewingFriendPassportUuid = null;
                this.creatingGroupMode = false;
                this.zoomedImageId = null;
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

        if (viewingFriendPassportUuid != null) {
            DarkButton backBtn = new DarkButton(winX + 14, winY + ySize - 44, 150, 20, Component.literal("◄ Назад к списку друзей"), (b) -> {
                viewingFriendPassportUuid = null;
                this.rebuildWidgets();
            });
            this.addRenderableWidget(backBtn);
            return;
        }

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

        List<String> friendList = new ArrayList<>(data.friends);
        int perPage = 5;
        int startIdx = friendsPage * perPage;

        for (int i = 0; i < perPage && (startIdx + i) < friendList.size(); i++) {
            String fUuid = friendList.get(startIdx + i);
            int rowY = currentY + i * 32;

            DarkButton passBtn = new DarkButton(winX + 142, rowY + 4, 54, 18, Component.literal("Паспорт"), (b) -> {
                this.viewingFriendPassportUuid = fUuid;
                this.rebuildWidgets();
            });
            DarkButton dmBtn = new DarkButton(winX + 199, rowY + 4, 34, 18, Component.literal("ЛС"), (b) -> {
                this.activeChatFriendUuid = fUuid;
                this.chatScrollOffset = 0.0f;
                this.currentTab = "СООБЩЕНИЯ";
                this.rebuildWidgets();
            });
            DarkButton partyBtn = new DarkButton(winX + 236, rowY + 4, 68, 18, Component.literal("+ В группу"), (b) -> {
                PacketDistributor.sendToServer(new PassportNetworking.SocialActionPayload("INVITE_PARTY", fUuid, ""));
            });
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

        DarkButton newGroupBtn = new DarkButton(winX + 8, winY + 24, 92, 18, Component.literal("+ Создать беседу"), (b) -> {
            creatingGroupMode = !creatingGroupMode;
            this.rebuildWidgets();
        });
        newGroupBtn.setSelected(creatingGroupMode);
        this.addRenderableWidget(newGroupBtn);

        if (creatingGroupMode) {
            groupTitleBox = createField(winX + 115, winY + 55, 200, 18, "", 32, "Название группового чата...");
            DarkButton pickGroupAvaBtn = new DarkButton(winX + 115, winY + 82, 200, 20,
                    Component.literal(pendingGroupAvatarId.isEmpty() ? "📷 Выбрать аватарку (до 2 МБ)" : "✔ Аватарка загружена!"), (b) -> {
                ClientImageManager.pickImageFromDisk((imgId, bytes) -> {
                    this.pendingGroupAvatarId = imgId;
                    this.rebuildWidgets();
                });
            });
            DarkButton confirmCreateGroupBtn = new DarkButton(winX + 115, winY + 112, 200, 20, Component.literal("СОЗДАТЬ ГРУППОВОЙ ЧАТ"), (b) -> {
                String title = groupTitleBox.getValue().trim();
                if (!title.isEmpty()) {
                    PacketDistributor.sendToServer(new PassportNetworking.SocialActionPayload("CREATE_GROUP", title, pendingGroupAvatarId));
                    creatingGroupMode = false;
                    pendingGroupAvatarId = "";
                    this.rebuildWidgets();
                }
            });
            this.addRenderableWidget(pickGroupAvaBtn);
            this.addRenderableWidget(confirmCreateGroupBtn);
            return;
        }

        List<String> contactIds = new ArrayList<>();
        contactIds.addAll(data.groupChats.keySet());
        contactIds.addAll(data.friends);

        if (activeChatFriendUuid == null && !contactIds.isEmpty()) {
            activeChatFriendUuid = contactIds.get(0);
        }

        if (activeChatFriendUuid != null && PassportNetworking.CLIENT_UNREAD_COUNTS.getOrDefault(activeChatFriendUuid, 0) > 0) {
            PassportNetworking.CLIENT_UNREAD_COUNTS.remove(activeChatFriendUuid);
            PacketDistributor.sendToServer(new PassportNetworking.SocialActionPayload("MARK_READ", activeChatFriendUuid, ""));
        }

        int maxVisibleContacts = 8;
        if (contactListOffset > Math.max(0, contactIds.size() - maxVisibleContacts)) {
            contactListOffset = Math.max(0, contactIds.size() - maxVisibleContacts);
        }

        for (int i = 0; i < maxVisibleContacts && (contactListOffset + i) < contactIds.size(); i++) {
            String cId = contactIds.get(contactListOffset + i);
            String name;
            if (cId.startsWith("group_")) {
                CompoundTag gTag = data.groupChats.get(cId);
                name = gTag != null ? "§6#§f" + gTag.getString("title") : "Беседа";
            } else {
                CompoundTag prof = data.knownProfiles.get(cId);
                name = prof != null ? prof.getString("rpName") : "Друг";
            }

            int unread = PassportNetworking.CLIENT_UNREAD_COUNTS.getOrDefault(cId, 0);
            if (name.length() > 9) name = name.substring(0, 9) + ".";
            if (unread > 0) name = name + " §c+" + unread;

            DarkButton contactBtn = new DarkButton(winX + 26, winY + 46 + i * 24, 74, 20, Component.literal(name), (b) -> {
                activeChatFriendUuid = cId;
                chatScrollOffset = 0.0f; // При переключении диалога сбрасываем скролл в самый низ
                PassportNetworking.CLIENT_UNREAD_COUNTS.remove(cId);
                PacketDistributor.sendToServer(new PassportNetworking.SocialActionPayload("MARK_READ", cId, ""));
                this.rebuildWidgets();
            });
            contactBtn.setSelected(cId.equals(activeChatFriendUuid));
            this.addRenderableWidget(contactBtn);
        }

        if (activeChatFriendUuid != null) {
            if (activeChatFriendUuid.startsWith("group_")) {
                inviteGroupMemberBox = createField(winX + 215, winY + 22, 78, 15, "", 32, "Ник/Имя...");
                DarkButton addMemberBtn = new DarkButton(winX + 295, winY + 22, 37, 15, Component.literal("+Уч."), (b) -> {
                    String val = inviteGroupMemberBox.getValue().trim();
                    if (!val.isEmpty()) {
                        PacketDistributor.sendToServer(new PassportNetworking.SocialActionPayload("ADD_GROUP_MEMBER", activeChatFriendUuid, val));
                        inviteGroupMemberBox.setValue("");
                    }
                });
                this.addRenderableWidget(addMemberBtn);
            }

            chatInputBox = createField(winX + 108, winY + ySize - 42, 138, 18, "", 120, "Сообщение...");
            DarkButton sendPhotoBtn = new DarkButton(winX + 249, winY + ySize - 42, 26, 18, Component.literal("📷"), (b) -> {
                ClientImageManager.pickImageFromDisk((imgId, bytes) -> {
                    chatScrollOffset = 0.0f;
                    PacketDistributor.sendToServer(new PassportNetworking.SocialActionPayload("SEND_DM", activeChatFriendUuid, "[IMG:" + imgId + "]"));
                });
            });
            DarkButton sendBtn = new DarkButton(winX + 278, winY + ySize - 42, 54, 18, Component.literal("Отпр."), (b) -> {
                String msg = chatInputBox.getValue().trim();
                if (!msg.isEmpty()) {
                    chatScrollOffset = 0.0f;
                    PacketDistributor.sendToServer(new PassportNetworking.SocialActionPayload("SEND_DM", activeChatFriendUuid, msg));
                    chatInputBox.setValue("");
                }
            });
            this.addRenderableWidget(sendPhotoBtn);
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
        if (this.pendingPassportPhotoId.isEmpty() && !data.photoId.isEmpty()) {
            this.pendingPassportPhotoId = data.photoId;
        }

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

        uploadPhotoBtn = new DarkButton(col1X, topPos + ySize - 32, 118, 20,
                Component.literal(pendingPassportPhotoId.isEmpty() ? "📷 Фото (до 2 МБ)" : "✔ Фото выбрано"), (btn) -> {
            ClientImageManager.pickImageFromDisk((imgId, bytes) -> {
                this.pendingPassportPhotoId = imgId;
                this.rebuildWidgets();
            });
        });
        this.addRenderableWidget(uploadPhotoBtn);

        confirmBtn = new DarkButton(col1X + 124, topPos + ySize - 32, 192, 20, Component.literal("УТВЕРДИТЬ ПАСПОРТ (НАВСЕГДА)"), (btn) -> {
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
            current.photoId = pendingPassportPhotoId;

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
        clickableImages.clear();
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

        if (zoomedImageId != null) {
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(0, 0, 400);
            guiGraphics.fill(0, 0, this.width, this.height, 0xDD000000);

            ClientImageManager.LoadedImage img = ClientImageManager.getOrRequest(zoomedImageId);
            if (img != null) {
                int maxW = (int) (this.width * 0.75);
                int maxH = (int) (this.height * 0.75);
                float scale = Math.min((float) maxW / img.width(), (float) maxH / img.height());
                int drawW = Math.max(32, (int) (img.width() * scale));
                int drawH = Math.max(32, (int) (img.height() * scale));
                int drawX = (this.width - drawW) / 2;
                int drawY = (this.height - drawH) / 2;

                guiGraphics.fill(drawX - 3, drawY - 3, drawX + drawW + 3, drawY + drawH + 3, 0xFF141414);
                guiGraphics.renderOutline(drawX - 3, drawY - 3, drawW + 6, drawH + 6, 0xFFD4AF37);
                ClientImageManager.drawImage(guiGraphics, zoomedImageId, drawX, drawY, drawW, drawH);
            }
            guiGraphics.drawCenteredString(this.font, "§eНажмите в любое место экрана, чтобы закрыть фото", this.width / 2, this.height - 22, 0xFFFFFFFF);
            guiGraphics.pose().popPose();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (zoomedImageId != null) {
            zoomedImageId = null;
            return true;
        }

        // Проверяем клик по ползунку прокрутки чата
        if ("СООБЩЕНИЯ".equals(currentTab) && !creatingGroupMode && activeChatFriendUuid != null && maxChatScroll > 0) {
            int winX = leftPos + 110;
            int winY = topPos + 8;
            int scrollX = winX + 324;
            int boxY = winY + 40;
            int boxH = ySize - 88;
            if (mouseX >= scrollX && mouseX <= scrollX + 8 && mouseY >= boxY && mouseY <= boxY + boxH) {
                isDraggingChatScrollbar = true;
                updateScrollFromMouse(mouseY, boxY, boxH);
                return true;
            }
        }

        for (ClickableImageArea area : clickableImages) {
            if (mouseX >= area.x && mouseX <= area.x + area.w && mouseY >= area.y && mouseY <= area.y + area.h) {
                if (area.imageId != null && !area.imageId.isEmpty()) {
                    zoomedImageId = area.imageId;
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        isDraggingChatScrollbar = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (isDraggingChatScrollbar && maxChatScroll > 0) {
            int winY = topPos + 8;
            int boxY = winY + 40;
            int boxH = ySize - 88;
            updateScrollFromMouse(mouseY, boxY, boxH);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    private void updateScrollFromMouse(double mouseY, int boxY, int boxH) {
        int thumbH = Math.max(18, (int) ((float) boxH / (boxH + maxChatScroll) * boxH));
        int trackH = Math.max(1, boxH - thumbH);
        float ratioFromTop = (float) Math.clamp((mouseY - boxY - thumbH / 2.0) / trackH, 0.0, 1.0);
        // Вверху (ratio=0) показываем самые старые (chatScrollOffset = maxChatScroll), внизу (ratio=1) — новые (0)
        this.chatScrollOffset = (1.0f - ratioFromTop) * maxChatScroll;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if ("СООБЩЕНИЯ".equals(currentTab) && !creatingGroupMode) {
            int winX = leftPos + 110;
            int winY = topPos + 8;

            // Если курсор над лентой сообщений — крутим историю переписки!
            if (mouseX >= winX + 108 && mouseX <= winX + 332 && mouseY >= winY + 40 && mouseY <= winY + ySize - 48) {
                if (maxChatScroll > 0) {
                    chatScrollOffset = Math.clamp(chatScrollOffset + (float) (scrollY * 18.0f), 0.0f, (float) maxChatScroll);
                    return true;
                }
            }
            // Если курсор над левой колонкой контактов/групп — крутим список контактов!
            if (mouseX >= winX + 6 && mouseX <= winX + 104 && mouseY >= winY + 44 && mouseY <= winY + ySize - 16) {
                if (Minecraft.getInstance().player != null) {
                    PassportData data = Minecraft.getInstance().player.getData(PassportData.PASSPORT);
                    int totalContacts = data.groupChats.size() + data.friends.size();
                    int maxOffset = Math.max(0, totalContacts - 8);
                    int newOffset = Math.clamp(contactListOffset - (int) Math.signum(scrollY), 0, maxOffset);
                    if (newOffset != contactListOffset) {
                        contactListOffset = newOffset;
                        this.rebuildWidgets();
                    }
                    return true;
                }
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void renderAvatarBox(GuiGraphics g, String photoId, int x, int y, int size, boolean clickable) {
        g.fill(x, y, x + size, y + size, 0xFF1A1A1A);
        boolean drawn = ClientImageManager.drawImage(g, photoId, x + 1, y + 1, size - 2, size - 2);
        if (!drawn) {
            g.drawCenteredString(this.font, "?", x + size / 2, y + (size - 8) / 2, 0xFF555555);
        } else if (clickable) {
            clickableImages.add(new ClickableImageArea(x, y, size, size, photoId));
        }
        g.renderOutline(x, y, size, size, 0xFF8F7A42);
    }

    private void renderApprovedPassportSheet(GuiGraphics g, int winX, int winY, String rpName, String gender,
                                             String ageAndBirth, String race, String heightWeight, String hair,
                                             String eyes, String features, String voice, String optional, String photoId) {
        int photoX = winX + 14;
        int photoY = winY + 42;
        int photoW = 68;
        int photoH = 86;

        g.fill(photoX, photoY, photoX + photoW, photoY + photoH, 0xFF161616);
        boolean hasPhoto = ClientImageManager.drawImage(g, photoId, photoX + 2, photoY + 2, photoW - 4, photoH - 4);
        g.renderOutline(photoX, photoY, photoW, photoH, 0xFFD4AF37);

        if (hasPhoto) {
            clickableImages.add(new ClickableImageArea(photoX, photoY, photoW, photoH, photoId));
            g.drawCenteredString(this.font, "§8[Клик — увелич.]", photoX + photoW / 2, photoY + photoH + 3, 0xFF888888);
        } else {
            g.drawCenteredString(this.font, "Нет фото", photoX + photoW / 2, photoY + 38, 0xFF555555);
        }

        int textX1 = winX + 90;
        int textX2 = winX + 220;
        int y = winY + 44;
        int step = 20;

        drawPassportRow(g, "Имя:", rpName, textX1, y);
        drawPassportRow(g, "Пол:", gender, textX2, y);

        drawPassportRow(g, "Возраст/ДР:", ageAndBirth, textX1, y + step);
        drawPassportRow(g, "Раса:", race, textX2, y + step);

        drawPassportRow(g, "Рост/Вес:", heightWeight, textX1, y + step * 2);
        drawPassportRow(g, "Волосы:", hair, textX2, y + step * 2);

        drawPassportRow(g, "Глаза:", eyes, textX1, y + step * 3);
        drawPassportRow(g, "Голос:", voice, textX2, y + step * 3);

        drawPassportRow(g, "Особенности внешности:", features, winX + 15, winY + 145);
        drawPassportRow(g, "Дополнительно:", optional.isEmpty() ? "—" : optional, winX + 15, winY + 168);
    }

    private void renderFriendsContent(GuiGraphics g, int winX, int winY) {
        if (Minecraft.getInstance().player == null) return;
        PassportData data = Minecraft.getInstance().player.getData(PassportData.PASSPORT);

        if (viewingFriendPassportUuid != null) {
            CompoundTag prof = data.knownProfiles.get(viewingFriendPassportUuid);
            g.drawString(this.font, "ПАСПОРТ ДРУГА", winX + 14, winY + 10, 0xFFE0C068);
            g.fill(winX + 12, winY + 24, winX + 330, winY + 25, 0xFF8F7A42);

            if (prof == null || !prof.getBoolean("isLocked")) {
                g.drawString(this.font, "Этот игрок еще не заполнил свой официальный паспорт.", winX + 15, winY + 45, 0xFFFF5555);
                return;
            }

            renderApprovedPassportSheet(g, winX, winY,
                    prof.getString("rpName"), prof.getString("gender"), prof.getString("ageAndBirth"),
                    prof.getString("race"), prof.getString("heightWeight"), prof.getString("hair"),
                    prof.getString("eyes"), prof.getString("features"), prof.getString("voice"),
                    prof.getString("optional"), prof.getString("photoId"));
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
            boolean online = prof != null && prof.getBoolean("online");
            String photoId = prof != null ? prof.getString("photoId") : "";

            int rowY = currentY + i * 32;
            g.fill(winX + 12, rowY, winX + 330, rowY + 26, 0xFF151515);
            g.renderOutline(winX + 12, rowY, 318, 26, online ? 0xFF2E5A2E : 0xFF2B2B2B);

            renderAvatarBox(g, photoId, winX + 15, rowY + 3, 20, true);

            String statusDot = online ? "§a● " : "§7○ ";
            g.drawString(this.font, statusDot + "§f" + rpName, winX + 39, rowY + 4, 0xFFFFFFFF);
            g.drawString(this.font, online ? "§aВ сети" : "§8Не в сети (ЛС)", winX + 39, rowY + 14, 0xFF888888);
        }
    }

    private void renderMessagesContent(GuiGraphics g, int winX, int winY) {
        if (Minecraft.getInstance().player == null) return;
        PassportData data = Minecraft.getInstance().player.getData(PassportData.PASSPORT);

        g.drawString(this.font, "МЕССЕНДЖЕР И ГРУППОВЫЕ ЧАТЫ", winX + 12, winY + 10, 0xFFE0C068);
        g.fill(winX + 104, winY + 24, winX + 105, winY + ySize - 24, 0xFF3A3A3A);

        if (creatingGroupMode) {
            g.drawString(this.font, "СОЗДАНИЕ ГРУППОВОГО ЧАТА", winX + 115, winY + 32, 0xFFE0C068);
            g.drawString(this.font, "Введите название беседы и выберите аватарку:", winX + 115, winY + 44, 0xFFAAAAAA);
            if (!pendingGroupAvatarId.isEmpty()) {
                renderAvatarBox(g, pendingGroupAvatarId, winX + 195, winY + 142, 42, true);
            }
            return;
        }

        List<String> contactIds = new ArrayList<>();
        contactIds.addAll(data.groupChats.keySet());
        contactIds.addAll(data.friends);

        for (int i = 0; i < 8 && (contactListOffset + i) < contactIds.size(); i++) {
            String cId = contactIds.get(contactListOffset + i);
            String avaId = "";
            if (cId.startsWith("group_")) {
                CompoundTag gTag = data.groupChats.get(cId);
                if (gTag != null) avaId = gTag.getString("avatarId");
            } else {
                CompoundTag prof = data.knownProfiles.get(cId);
                if (prof != null) avaId = prof.getString("photoId");
            }
            renderAvatarBox(g, avaId, winX + 8, winY + 48 + i * 24, 16, true);
        }

        if (activeChatFriendUuid == null) {
            g.drawString(this.font, "Выберите контакт или создайте беседу.", winX + 115, winY + 45, 0xFF888888);
            return;
        }

        String headerTitle;
        String headerAvatarId = "";
        if (activeChatFriendUuid.startsWith("group_")) {
            CompoundTag gTag = data.groupChats.get(activeChatFriendUuid);
            headerTitle = gTag != null ? "§6Беседа: §e" + gTag.getString("title") : "Групповой чат";
            if (gTag != null) headerAvatarId = gTag.getString("avatarId");
        } else {
            CompoundTag prof = data.knownProfiles.get(activeChatFriendUuid);
            String chatPartnerName = prof != null ? prof.getString("rpName") : "Собеседник";
            boolean online = prof != null && prof.getBoolean("online");
            headerTitle = "§e" + chatPartnerName + (online ? " §a(В сети)" : " §7(Почта)");
            if (prof != null) headerAvatarId = prof.getString("photoId");
        }

        renderAvatarBox(g, headerAvatarId, winX + 108, winY + 21, 16, true);
        g.drawString(this.font, headerTitle, winX + 128, winY + 25, 0xFFFFFFFF);

        // Область просмотра истории сообщений
        int boxX = winX + 108;
        int boxY = winY + 40;
        int boxW = 224;
        int boxH = ySize - 88;

        g.fill(boxX, boxY, boxX + boxW, boxY + boxH, 0xFF0B0B0B);
        g.renderOutline(boxX, boxY, boxW, boxH, 0xFF2B2B2B);

        // 1. Формируем полный список элементов (текстовые строки + блоки картинок) в строгом хронологическом порядке
        List<String> history = PassportNetworking.CLIENT_DM_HISTORY.getOrDefault(activeChatFriendUuid, List.of());
        List<ChatRenderLine> renderLines = new ArrayList<>();
        int totalHeight = 6;

        for (String raw : history) {
            if (raw.startsWith("[IMG:") && raw.endsWith("]")) {
                String inner = raw.substring(5, raw.length() - 1);
                String[] parts = inner.split("\\|", 2);
                String imgId = parts[0];
                String senderName = parts.length > 1 ? parts[1] : "Игрок";

                FormattedCharSequence caption = Component.literal("§e" + senderName + " §7прислал(а) фото §8(клик):").getVisualOrderText();
                renderLines.add(new ChatRenderLine(false, caption, null, 11));
                totalHeight += 11;

                renderLines.add(new ChatRenderLine(true, null, imgId, 42));
                totalHeight += 42;
            } else {
                List<FormattedCharSequence> wrapped = this.font.split(Component.literal(raw), 204);
                for (FormattedCharSequence line : wrapped) {
                    renderLines.add(new ChatRenderLine(false, line, null, 11));
                    totalHeight += 11;
                }
            }
        }

        // 2. Вычисляем диапазон скролла (0 = самый низ, maxChatScroll = самый верх)
        this.maxChatScroll = Math.max(0, totalHeight - (boxH - 6));
        this.chatScrollOffset = Math.clamp(this.chatScrollOffset, 0.0f, (float) this.maxChatScroll);

        // Если сообщений мало, рисуем сверху вниз; если много — привязываем к низу + учитываем chatScrollOffset
        int currentY = (totalHeight <= boxH - 6)
                ? (boxY + 4)
                : (boxY + boxH - 4 - totalHeight + (int) this.chatScrollOffset);

        // Включаем аппаратную обрезку (Scissor), чтобы элементы плавно уходили за края рамок при прокрутке
        g.enableScissor(boxX + 1, boxY + 1, boxX + boxW - 9, boxY + boxH - 1);

        for (ChatRenderLine item : renderLines) {
            if (currentY + item.height >= boxY && currentY <= boxY + boxH) {
                if (!item.isImage) {
                    g.drawString(this.font, item.text, boxX + 5, currentY, 0xFFFFFFFF);
                } else {
                    int imgX = boxX + 7;
                    int imgY = currentY + 1;
                    int imgW = 54;
                    int imgH = 38;
                    g.fill(imgX, imgY, imgX + imgW, imgY + imgH, 0xFF1A1A1A);
                    if (ClientImageManager.drawImage(g, item.imageId, imgX + 1, imgY + 1, imgW - 2, imgH - 2)) {
                        // Записываем кликабельную область только для видимой части внутри окна чата
                        int clipTop = Math.max(imgY, boxY + 1);
                        int clipBottom = Math.min(imgY + imgH, boxY + boxH - 1);
                        if (clipBottom > clipTop) {
                            clickableImages.add(new ClickableImageArea(imgX, clipTop, imgW, clipBottom - clipTop, item.imageId));
                        }
                    } else {
                        g.drawString(this.font, "Загрузка...", imgX + 4, imgY + 14, 0xFF777777);
                    }
                    g.renderOutline(imgX, imgY, imgW, imgH, 0xFF8F7A42);
                }
            } else if (item.isImage) {
                // Даже если картинка сейчас прокручена за экран, ставим ее в очередь на фоновую загрузку
                ClientImageManager.getOrRequest(item.imageId);
            }
            currentY += item.height;
        }

        g.disableScissor();

        // 3. Рисуем вертикальный ползунок прокрутки справа
        int scrollBarX = boxX + boxW - 7;
        g.fill(scrollBarX, boxY + 1, scrollBarX + 6, boxY + boxH - 1, 0xFF141414);
        if (maxChatScroll > 0) {
            int thumbH = Math.max(18, (int) ((float) boxH / (boxH + maxChatScroll) * (boxH - 2)));
            int trackH = (boxH - 2) - thumbH;
            float ratioFromTop = 1.0f - (this.chatScrollOffset / (float) this.maxChatScroll);
            int thumbY = boxY + 1 + (int) (ratioFromTop * trackH);

            g.fill(scrollBarX + 1, thumbY, scrollBarX + 5, thumbY + thumbH, isDraggingChatScrollbar ? 0xFFFFD700 : 0xFF8F7A42);
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
            g.drawString(this.font, "Статус: УТВЕРЖДЕН (Смена через администрацию)", winX + 12, winY + 20, 0xFF55FF55);
            g.fill(winX + 12, winY + 33, winX + 330, winY + 34, 0xFF8F7A42);

            renderApprovedPassportSheet(g, winX, winY,
                    data.rpName, data.gender, data.ageAndBirth,
                    data.race, data.heightWeight, data.hair,
                    data.eyes, data.features, data.voice,
                    data.optional, data.photoId);

            g.fill(winX + 12, winY + 198, winX + 330, winY + 199, 0xFF3A3A3A);
            g.drawString(this.font, "Показать паспорт: /passport show <ник>", winX + 15, winY + 208, 0xFFE0C068);
        }
    }

    private void drawPassportRow(GuiGraphics g, String label, String value, int x, int y) {
        g.drawString(this.font, label, x, y, 0xFF888888);
        int labelWidth = this.font.width(label) + 4;
        g.drawString(this.font, value.isEmpty() ? "Не указано" : value, x + labelWidth, y, 0xFFFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
