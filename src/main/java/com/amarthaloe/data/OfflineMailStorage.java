package com.amarthaloe.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
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

    private final Map<String, List<String>> chatHistories = new HashMap<>();
    private final Map<String, Map<String, Integer>> unreadCounts = new HashMap<>();

    // Хранилище загруженных изображений (imageId -> PNG байты)
    private final Map<String, byte[]> images = new HashMap<>();
    // Групповые чаты (groupId -> GroupChatInfo)
    private final Map<String, GroupChatInfo> groupChats = new HashMap<>();

    public static String getConversationKey(String uuid1, String uuid2) {
        if (uuid1.startsWith("group_")) return uuid1;
        if (uuid2.startsWith("group_")) return uuid2;
        return uuid1.compareTo(uuid2) < 0 ? uuid1 + "|" + uuid2 : uuid2 + "|" + uuid1;
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
        return storage;
    }

    public static OfflineMailStorage get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(OfflineMailStorage::new, OfflineMailStorage::load),
                "amarthaloe_mail"
        );
    }
}
