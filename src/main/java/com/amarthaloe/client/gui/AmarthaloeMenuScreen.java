package com.amarthaloe.client.gui;

import com.amarthaloe.client.ClientImageManager;
import com.amarthaloe.client.PartyHudOverlay;
import com.amarthaloe.data.PassportData;
import com.amarthaloe.network.PassportNetworking;
import com.amarthaloe.rpg.Faction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
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

    // Группы (Party) и Содружества (Guilds)
    private EditBox partyInviteBox;
    private EditBox guildNameBox, guildTagBox, guildInviteBox;
    private String pendingGuildAvatarId = "";
    private int guildMembersOffset = 0;

    // Прокрутка мессенджера и списка контактов
    private float chatScrollOffset = 0.0f;
    private int maxChatScroll = 0;
    private boolean isDraggingChatScrollbar = false;
    private int contactListOffset = 0;

    // Полноэкранный просмотр фотографии по клику (Лайтбокс)
    private String zoomedImageId = null;
    private record ClickableImageArea(int x, int y, int w, int h, String imageId) {}
    private final List<ClickableImageArea> clickableImages = new ArrayList<>();
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
            case "ГРУППЫ" -> initPartyTab();
            case "СОДРУЖЕСТВА" -> initGuildsTab();
            case "СООБЩЕНИЯ" -> initMessagesTab();
            case "НАСТРОЙКИ" -> initSettingsTab();
        }
    }

    private void initPartyTab() {
        if (Minecraft.getInstance().player == null) return;
        int winX = leftPos + 110;
        int winY = topPos + 8;
        String myUuid = Minecraft.getInstance().player.getStringUUID();
        CompoundTag party = PassportNetworking.CLIENT_PARTY_DATA;

        // Поле приглашения игрока в группу
        partyInviteBox = createField(winX + 12, winY + 24, 200, 18, "", 40, "Ник или РП-имя игрока...");
        DarkButton invBtn = new DarkButton(winX + 218, winY + 24, 112, 18, Component.literal("+ Пригласить (до 5)"), (b) -> {
            String val = partyInviteBox.getValue().trim();
            if (!val.isEmpty()) {
                PacketDistributor.sendToServer(new PassportNetworking.SocialActionPayload("INVITE_PARTY", val, ""));
                partyInviteBox.setValue("");
            }
        });
        this.addRenderableWidget(invBtn);

        // Если есть входящее приглашение в группу
        if (party.contains("pendingInviteFrom")) {
            String leaderUuid = party.getString("pendingInviteFrom");
            DarkButton acceptBtn = new DarkButton(winX + 195, winY + 50, 64, 18, Component.literal("Принять"), (b) -> {
                PacketDistributor.sendToServer(new PassportNetworking.SocialActionPayload("PARTY_ACCEPT", leaderUuid, ""));
                party.remove("pendingInviteFrom");
                this.rebuildWidgets();
            });
            DarkButton declineBtn = new DarkButton(winX + 264, winY + 50, 66, 18, Component.literal("Отклонить"), (b) -> {
                PacketDistributor.sendToServer(new PassportNetworking.SocialActionPayload("PARTY_DECLINE", "", ""));
                party.remove("pendingInviteFrom");
                this.rebuildWidgets();
            });
            this.addRenderableWidget(acceptBtn);
            this.addRenderableWidget(declineBtn);
        }

        if (party.contains("members")) {
            boolean isLeader = myUuid.equals(party.getString("leaderUuid"));
            boolean ff = party.getBoolean("friendlyFire");

            // Переключатель дружественного огня и кнопка Выхода
            DarkButton ffBtn = new DarkButton(winX + 12, winY + 50, 195, 18,
                    Component.literal("Огонь по своим: " + (ff ? "§cВКЛЮЧЕН" : "§aВЫКЛЮЧЕН")), (b) -> {
                PacketDistributor.sendToServer(new PassportNetworking.SocialActionPayload("PARTY_TOGGLE_FF", "", ""));
            });
            ffBtn.active = isLeader;
            this.addRenderableWidget(ffBtn);

            DarkButton leaveBtn = new DarkButton(winX + 212, winY + 50, 118, 18, Component.literal("Покинуть группу"), (b) -> {
                PacketDistributor.sendToServer(new PassportNetworking.SocialActionPayload("PARTY_LEAVE", "", ""));
            });
            this.addRenderableWidget(leaveBtn);

            ListTag members = party.getList("members", Tag.TAG_COMPOUND);
            int startY = winY + 80;
            for (int i = 0; i < members.size(); i++) {
                CompoundTag m = members.getCompound(i);
                String mUuid = m.getString("uuid");
                if (isLeader && !mUuid.equals(myUuid)) {
                    DarkButton kickBtn = new DarkButton(winX + 305, startY + i * 34 + 6, 22, 18, Component.literal("✖"), (b) -> {
                        PacketDistributor.sendToServer(new PassportNetworking.SocialActionPayload("PARTY_KICK", mUuid, ""));
                    });
                    this.addRenderableWidget(kickBtn);
                }
            }
        }
    }

    private void initGuildsTab() {
        if (Minecraft.getInstance().player == null) return;
        int winX = leftPos + 110;
        int winY = topPos + 8;
        String myUuid = Minecraft.getInstance().player.getStringUUID();
        CompoundTag guild = PassportNetworking.CLIENT_GUILD_DATA;

        // Если игрок ЕЩЕ НЕ СОСТОИТ в содружестве
        if (!guild.contains("guildId")) {
            if (guild.contains("pendingGuildId")) {
                String gId = guild.getString("pendingGuildId");
                DarkButton accBtn = new DarkButton(winX + 195, winY + 32, 64, 18, Component.literal("Вступить"), (b) -> {
                    PacketDistributor.sendToServer(new PassportNetworking.SocialActionPayload("GUILD_ACCEPT", gId, ""));
                    guild.remove("pendingGuildId");
                    this.rebuildWidgets();
                });
                DarkButton decBtn = new DarkButton(winX + 264, winY + 32, 66, 18, Component.literal("Отклонить"), (b) -> {
                    PacketDistributor.sendToServer(new PassportNetworking.SocialActionPayload("GUILD_DECLINE", gId, ""));
                    guild.remove("pendingGuildId");
                    this.rebuildWidgets();
                });
                this.addRenderableWidget(accBtn);
                this.addRenderableWidget(decBtn);
            }

            guildNameBox = createField(winX + 20, winY + 82, 180, 18, "", 28, "Название содружества...");
            guildTagBox = createField(winX + 210, winY + 82, 90, 18, "", 4, "ТЕГ (до 4 букв)");

            DarkButton avaBtn = new DarkButton(winX + 20, winY + 112, 180, 20,
                    Component.literal(pendingGuildAvatarId.isEmpty() ? "📷 Аватарка клана (до 2 МБ)" : "✔ Аватарка загружена"), (b) -> {
                ClientImageManager.pickImageFromDisk((imgId, bytes) -> {
                    this.pendingGuildAvatarId = imgId;
                    this.rebuildWidgets();
                });
            });
            DarkButton createBtn = new DarkButton(winX + 20, winY + 145, 280, 22, Component.literal("ОСНОВАТЬ СОДРУЖЕСТВО"), (b) -> {
                String gName = guildNameBox.getValue().trim();
                String gTag = guildTagBox.getValue().trim();
                if (!gName.isEmpty() && !gTag.isEmpty()) {
                    PacketDistributor.sendToServer(new PassportNetworking.SocialActionPayload("GUILD_CREATE", gName + "|" + gTag, pendingGuildAvatarId));
                    pendingGuildAvatarId = "";
                }
            });
            this.addRenderableWidget(avaBtn);
            this.addRenderableWidget(createBtn);
            return;
        }

        // Игрок СОСТОИТ в содружестве
        String myRank = guild.getString("myRank");
        boolean isLeader = "LEADER".equals(myRank);
        boolean isOfficer = isLeader || "OFFICER".equals(myRank);
        boolean ff = guild.getBoolean("friendlyFire");
        boolean canChest = guild.getBoolean("canOpenChest");

        // Верхний ряд управления содружеством
        DarkButton ffBtn = new DarkButton(winX + 54, winY + 34, 130, 18,
                Component.literal("ПвП клана: " + (ff ? "§cВКЛ" : "§aВЫКЛ")), (b) -> {
            PacketDistributor.sendToServer(new PassportNetworking.SocialActionPayload("GUILD_TOGGLE_FF", "", ""));
        });
        ffBtn.active = isOfficer;
        this.addRenderableWidget(ffBtn);

        DarkButton chestBtn = new DarkButton(winX + 188, winY + 34, 92, 18, Component.literal("📦 Сундук клана"), (b) -> {
            PacketDistributor.sendToServer(new PassportNetworking.SocialActionPayload("GUILD_OPEN_CHEST", "", ""));
        });
        chestBtn.active = canChest;
        this.addRenderableWidget(chestBtn);

        DarkButton leaveBtn = new DarkButton(winX + 284, winY + 34, 48, 18, Component.literal(isLeader ? "Роспуск" : "Выйти"), (b) -> {
            PacketDistributor.sendToServer(new PassportNetworking.SocialActionPayload("GUILD_LEAVE", "", ""));
        });
        this.addRenderableWidget(leaveBtn);

        // Поле приглашения новых участников
        if (isOfficer) {
            guildInviteBox = createField(winX + 12, winY + 58, 210, 16, "", 36, "Пригласить по нику или РП-имени...");
            DarkButton invBtn = new DarkButton(winX + 226, winY + 58, 106, 16, Component.literal("+ Пригласить"), (b) -> {
                String val = guildInviteBox.getValue().trim();
                if (!val.isEmpty()) {
                    PacketDistributor.sendToServer(new PassportNetworking.SocialActionPayload("GUILD_INVITE", val, ""));
                    guildInviteBox.setValue("");
                }
            });
            this.addRenderableWidget(invBtn);
        }

        // Список участников с кнопками выдачи рангов (▲/▼), доступа к сундуку и исключения
        ListTag members = guild.getList("members", Tag.TAG_COMPOUND);
        int startY = winY + 90;
        int maxRows = 5;

        for (int i = 0; i < maxRows && (guildMembersOffset + i) < members.size(); i++) {
            CompoundTag m = members.getCompound(guildMembersOffset + i);
            String mUuid = m.getString("uuid");
            boolean chestAccess = m.getBoolean("chestAccess");
            int rowY = startY + i * 32;

            if (isLeader && !mUuid.equals(myUuid)) {
                DarkButton upBtn = new DarkButton(winX + 198, rowY + 4, 18, 18, Component.literal("▲"), (b) -> {
                    PacketDistributor.sendToServer(new PassportNetworking.SocialActionPayload("GUILD_CHANGE_RANK", mUuid, "UP"));
                });
                DarkButton downBtn = new DarkButton(winX + 218, rowY + 4, 18, 18, Component.literal("▼"), (b) -> {
                    PacketDistributor.sendToServer(new PassportNetworking.SocialActionPayload("GUILD_CHANGE_RANK", mUuid, "DOWN"));
                });
                DarkButton toggleChestBtn = new DarkButton(winX + 239, rowY + 4, 66, 18,
                        Component.literal(chestAccess ? "Сундук:§aДа" : "Сундук:§cНет"), (b) -> {
                    PacketDistributor.sendToServer(new PassportNetworking.SocialActionPayload("GUILD_TOGGLE_CHEST", mUuid, ""));
                });
                DarkButton kickBtn = new DarkButton(winX + 308, rowY + 4, 20, 18, Component.literal("✖"), (b) -> {
                    PacketDistributor.sendToServer(new PassportNetworking.SocialActionPayload("GUILD_KICK", mUuid, ""));
                });
                this.addRenderableWidget(upBtn);
                this.addRenderableWidget(downBtn);
                this.addRenderableWidget(toggleChestBtn);
                this.addRenderableWidget(kickBtn);
            }
        }
    }

    private void initSettingsTab() {
        int winX = leftPos + 110;
        int winY = topPos + 8;

        DarkButton toggleHudBtn = new DarkButton(winX + 20, winY + 45, 260, 22,
                Component.literal("Окна группы на экране (WoW HUD): " + (PartyHudOverlay.showPartyHud ? "§aВКЛЮЧЕНЫ" : "§cСКРЫТЫ")), (b) -> {
            PartyHudOverlay.showPartyHud = !PartyHudOverlay.showPartyHud;
            this.rebuildWidgets();
        });

        String posName = switch (PartyHudOverlay.hudPosition) {
            case 1 -> "Справа вверху";
            case 2 -> "Слева по центру";
            default -> "Слева вверху (по умолчанию)";
        };
        DarkButton posHudBtn = new DarkButton(winX + 20, winY + 75, 260, 22,
                Component.literal("Расположение окон группы: §e" + posName), (b) -> {
            PartyHudOverlay.hudPosition = (PartyHudOverlay.hudPosition + 1) % 3;
            this.rebuildWidgets();
        });

        this.addRenderableWidget(toggleHudBtn);
        this.addRenderableWidget(posHudBtn);
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
                chatScrollOffset = 0.0f;
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

        DarkButton prevBtn = new DarkButton(mainWinX + 12, mainWinY + 38, 22, 24
