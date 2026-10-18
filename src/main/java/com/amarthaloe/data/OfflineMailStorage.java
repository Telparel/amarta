package com.amarthaloe.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;

public class OfflineMailStorage extends SavedData {

    public static class GroupChatInfo {
        public String groupId = "";
        public String title = "";
        public String avatarId = "";
        public String ownerUuid = "";
        public final Set<String> members = new HashSet<>();

        public CompoundTag toTag() {
            CompoundTag t = new CompoundTag();
            t.putString("groupId", groupId);
            t.putString("title", title);
            t.putString("avatarId", avatarId);
            t.putString("ownerUuid", ownerUuid);
            ListTag mList = new ListTag();
            for (String m : members) mList.add(StringTag.valueOf(m));
            t.put("members", mList);
            return t;
        }

        public static GroupChatInfo fromTag(CompoundTag t) {
            GroupChatInfo info = new GroupChatInfo();
            info.groupId = t.getString("groupId");
            info.title = t.getString("title");
            info.avatarId = t.getString("avatarId");
            info.ownerUuid = t.getString("ownerUuid");
            ListTag mList = t.getList("members", Tag.TAG_STRING);
            for (int i = 0; i < mList.size(); i++) info.members.add(mList.getString(i));
            return info;
        }
    }

    public static class GuildInfo {
        public String guildId = "";
        public String name = "";
        public String tag = ""; // До 4 символов
        public String avatarId = "";
        public String leaderUuid = "";
        public boolean friendlyFire = false;
        public String chatGroupId = ""; // ID автоматически созданной беседы содружества

        // UUID участника -> Ранг ("LEADER", "OFFICER", "MENTOR", "MEMBER")
        public final Map<String, String> memberRanks = new LinkedHashMap<>();
        // UUID участников, которым Лидер выдал доступ к клановому сундуку
        public final Set<String> chestAllowed = new HashSet<>();
        // Входящие приглашения (UUID приглашенных игроков)
        public final Set<String> invitedPlayers = new HashSet<>();
        // Клановый сундук (27 слотов)
        public final SimpleContainer chest = new SimpleContainer(27);

        public static String getRankDisplayName(String rank) {
            return switch (rank) {
                case "LEADER" -> "Лидер";
                case "OFFICER" -> "Офицер";
                case "MENTOR" -> "Ментор";
                default -> "Участник";
            };
        }

        public CompoundTag toTag(HolderLookup.Provider provider) {
            CompoundTag t = new CompoundTag();
            t.putString("guildId", guildId);
            t.putString("name", name);
            t.putString("tag", tag);
            t.putString("avatarId", avatarId);
            t.putString("leaderUuid", leaderUuid);
            t.putBoolean("friendlyFire", friendlyFire);
            t.putString("chatGroupId", chatGroupId);

            CompoundTag ranksTag = new CompoundTag();
            for (Map.Entry<String, String> e : memberRanks.entrySet()) {
                ranksTag.putString(e.getKey(), e.getValue());
            }
            t.put("memberRanks", ranksTag);

            ListTag chestList = new ListTag();
            for (String u : chestAllowed) chestList.add(StringTag.valueOf(u));
            t.put("chestAllowed", chestList);

            ListTag invList = new ListTag();
            for (String u : invitedPlayers) invList.add(StringTag.valueOf(u));
            t.put("invitedPlayers", invList);

            NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);
            for (int i = 0; i < 27; i++) items.set(i, chest.getItem(i));
            CompoundTag invTag = new CompoundTag();
            ContainerHelper.saveAllItems(invTag, items, provider);
            t.put("chestInv", invTag);

            return t;
        }

        public static GuildInfo fromTag(CompoundTag t, HolderLookup.Provider provider) {
            GuildInfo g = new GuildInfo();
            g.guildId = t.getString("guildId");
            g.name = t.getString("name");
            g.tag = t.getString("tag");
            g.avatarId = t.getString("avatarId");
            g.leaderUuid = t.getString("leaderUuid");
            g.friendlyFire = t.getBoolean("friendlyFire");
            g.chatGroupId = t.getString("chatGroupId");

            if (t.contains("memberRanks")) {
                CompoundTag ranksTag = t.getCompound("memberRanks");
                for (String key : ranksTag.getAllKeys()) {
                    g.memberRanks.put(key, ranksTag.getString(key));
                }
            }
            if (t.contains("chestAllowed")) {
                ListTag list = t.getList("chestAllowed", Tag.TAG_STRING);
                for (int i = 0; i < list.size(); i++) g.chestAllowed.add(list.getString(i));
            }
            if (t.contains("invitedPlayers")) {
                ListTag list = t.getList("invitedPlayers", Tag.TAG_STRING);
                for (int i = 0; i < list.size(); i++) g.invitedPlayers.add(list.getString(i));
            }
            if (t.contains("chestInv")) {
                NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);
                ContainerHelper.loadAllItems(t.getCompound("chestInv"), items, provider);
                for (int i = 0; i < 27; i++) g.chest.setItem(i, items.get(i));
            }
            return g;
        }
    }

    private final Map<String, List<String>> chatHistories = new HashMap<>();
    private final Map<String, Map<String, Integer>> unreadCounts = new HashMap<>();
    private final Map<String, byte[]> images = new HashMap<>();
    private final Map<String, GroupChatInfo> groupChats = new HashMap<>();
    private final Map<String, GuildInfo> guilds = new HashMap<>();

    public static String getConversationKey(String uuid1, String uuid2) {
        if (uuid1.startsWith("group_")) return uuid1;
        if (uuid2.startsWith("group_")) return uuid2;
        return uuid1.compareTo(uuid2) < 0 ? uuid1 + "|" + uuid2 : uuid2 + "|" + uuid1;
    }

    public Map<String, GuildInfo> getGuilds() {
        return guilds;
    }

    public GuildInfo getPlayerGuild(String playerUuid) {
        for (GuildInfo g : guilds.values()) {
            if (g.memberRanks.containsKey(playerUuid)) return g;
        }
        return null;
    }

    public GuildInfo getPendingGuildInvite(String playerUuid) {
        for (GuildInfo g : guilds.values()) {
            if (g.invitedPlayers.contains(playerUuid)) return g;
        }
        return null;
    }

    public void saveImage(String imageId, byte[] bytes) {
        if (bytes.length <= 2 * 1024 * 1024 + 512 * 1024) {
            images.put(imageId, bytes);
            this.setDirty();
        }
    }

    public byte[] getImage(String imageId) {
        return images.get(imageId);
    }

    public Map<String, GroupChatInfo> getGroupChats() {
        return groupChats;
    }

    public List<GroupChatInfo> getGroupsForPlayer(String playerUuid) {
        List<GroupChatInfo> res = new ArrayList<>();
        for (GroupChatInfo g : groupChats.values()) {
            if (g.members.contains(playerUuid)) res.add(g);
        }
        return res;
    }

    public void createOrUpdateGroup(GroupChatInfo group) {
        groupChats.put(group.groupId, group);
        this.setDirty();
    }

    public void addMessage(String senderUuid, String targetUuid, String formattedLine, boolean targetIsOnline) {
        String key = getConversationKey(senderUuid, targetUuid);
        List<String> list = chatHistories.computeIfAbsent(key, k -> new ArrayList<>());
        list.add(formattedLine);
        if (list.size() > 60) {
            list.remove(0);
        }

        if (!targetIsOnline) {
            String unreadKey = senderUuid.startsWith("group_") ? senderUuid : (targetUuid.startsWith("group_") ? targetUuid : senderUuid);
            Map<String, Integer> targetUnread = unreadCounts.computeIfAbsent(targetUuid, k -> new HashMap<>());
            targetUnread.put(unreadKey, targetUnread.getOrDefault(unreadKey, 0) + 1);
        }
        this.setDirty();
    }

    public void incrementUnread(String recipientUuid, String chatKey) {
        Map<String, Integer> targetUnread = unreadCounts.computeIfAbsent(recipientUuid, k -> new HashMap<>());
        targetUnread.put(chatKey, targetUnread.getOrDefault(chatKey, 0) + 1);
        this.setDirty();
    }

    public List<String> getHistory(String uuid1, String uuid2) {
        return chatHistories.getOrDefault(getConversationKey(uuid1, uuid2), Collections.emptyList());
    }

    public Map<String, Integer> getUnreadForPlayer(String playerUuid) {
        return unreadCounts.getOrDefault(playerUuid, Collections.emptyMap());
    }

    public int getTotalUnread(String playerUuid) {
        int sum = 0;
        for (int count : getUnreadForPlayer(playerUuid).values()) sum += count;
        return sum;
    }

    public void markAsRead(String playerUuid, String partnerUuid) {
        if (unreadCounts.containsKey(playerUuid)) {
            if (unreadCounts.get(playerUuid).remove(partnerUuid) != null) {
                this.setDirty();
            }
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        CompoundTag chatsTag = new CompoundTag();
        for (Map.Entry<String, List<String>> entry : chatHistories.entrySet()) {
            ListTag list = new ListTag();
            for (String msg : entry.getValue()) list.add(StringTag.valueOf(msg));
            chatsTag.put(entry.getKey(), list);
        }
        tag.put("chatHistories", chatsTag);

        CompoundTag unreadTag = new CompoundTag();
        for (Map.Entry<String, Map<String, Integer>> entry : unreadCounts.entrySet()) {
            CompoundTag pTag = new CompoundTag();
            for (Map.Entry<String, Integer> sub : entry.getValue().entrySet()) {
                pTag.putInt(sub.getKey(), sub.getValue());
            }
            unreadTag.put(entry.getKey(), pTag);
        }
        tag.put("unreadCounts", unreadTag);

        CompoundTag imagesTag = new CompoundTag();
        for (Map.Entry<String, byte[]> entry : images.entrySet()) {
            imagesTag.putByteArray(entry.getKey(), entry.getValue());
        }
        tag.put("images", imagesTag);

        CompoundTag groupsTag = new CompoundTag();
        for (Map.Entry<String, GroupChatInfo> entry : groupChats.entrySet()) {
            groupsTag.put(entry.getKey(), entry.getValue().toTag());
        }
        tag.put("groupChats", groupsTag);

        CompoundTag guildsTag = new CompoundTag();
        for (Map.Entry<String, GuildInfo> entry : guilds.entrySet()) {
            guildsTag.put(entry.getKey(), entry.getValue().toTag(provider));
        }
        tag.put("guilds", guildsTag);

        return tag;
    }

    public static OfflineMailStorage load(CompoundTag tag, HolderLookup.Provider provider) {
        OfflineMailStorage storage = new OfflineMailStorage();
        if (tag.contains("chatHistories")) {
            CompoundTag chatsTag = tag.getCompound("chatHistories");
            for (String key : chatsTag.getAllKeys()) {
                ListTag list = chatsTag.getList(key, Tag.TAG_STRING);
                List<String> history = new ArrayList<>();
                for (int i = 0; i < list.size(); i++) history.add(list.getString(i));
                storage.chatHistories.put(key, history);
            }
        }
        if (tag.contains("unreadCounts")) {
            CompoundTag unreadTag = tag.getCompound("unreadCounts");
            for (String playerUuid : unreadTag.getAllKeys()) {
                CompoundTag pTag = unreadTag.getCompound(playerUuid);
                Map<String, Integer> map = new HashMap<>();
                for (String senderUuid : pTag.getAllKeys()) map.put(senderUuid, pTag.getInt(senderUuid));
                storage.unreadCounts.put(playerUuid, map);
            }
        }
        if (tag.contains("images")) {
            CompoundTag imagesTag = tag.getCompound("images");
            for (String key : imagesTag.getAllKeys()) {
                storage.images.put(key, imagesTag.getByteArray(key));
            }
        }
        if (tag.contains("groupChats")) {
            CompoundTag groupsTag = tag.getCompound("groupChats");
            for (String key : groupsTag.getAllKeys()) {
                storage.groupChats.put(key, GroupChatInfo.fromTag(groupsTag.getCompound(key)));
            }
        }
        if (tag.contains("guilds")) {
            CompoundTag guildsTag = tag.getCompound("guilds");
            for (String key : guildsTag.getAllKeys()) {
                storage.guilds.put(key, GuildInfo.fromTag(guildsTag.getCompound(key), provider));
            }
        }
        return storage;
    }

    public static OfflineMailStorage get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(OfflineMailStorage::new, OfflineMailStorage::load),
                "amarthaloe_mail"
        );
    }
}
