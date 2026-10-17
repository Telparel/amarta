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

    // Ключ диалога (UUID1_UUID2 в алфавитном порядке) -> История сообщений (до 50 последних строк)
    private final Map<String, List<String>> chatHistories = new HashMap<>();

    // Получатель UUID -> (Отправитель UUID -> Количество непрочитанных сообщений)
    private final Map<String, Map<String, Integer>> unreadCounts = new HashMap<>();

    public static String getConversationKey(String uuid1, String uuid2) {
        return uuid1.compareTo(uuid2) < 0 ? uuid1 + "|" + uuid2 : uuid2 + "|" + uuid1;
    }

    public void addMessage(String senderUuid, String targetUuid, String formattedLine, boolean targetIsOnline) {
        String key = getConversationKey(senderUuid, targetUuid);
        List<String> list = chatHistories.computeIfAbsent(key, k -> new ArrayList<>());
        list.add(formattedLine);
        if (list.size() > 50) {
            list.remove(0);
        }

        if (!targetIsOnline) {
            Map<String, Integer> targetUnread = unreadCounts.computeIfAbsent(targetUuid, k -> new HashMap<>());
            targetUnread.put(senderUuid, targetUnread.getOrDefault(senderUuid, 0) + 1);
        }
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
        for (int count : getUnreadForPlayer(playerUuid).values()) {
            sum += count;
        }
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
            for (String msg : entry.getValue()) {
                list.add(StringTag.valueOf(msg));
            }
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
        return tag;
    }

    public static OfflineMailStorage load(CompoundTag tag, HolderLookup.Provider provider) {
        OfflineMailStorage storage = new OfflineMailStorage();
        if (tag.contains("chatHistories")) {
            CompoundTag chatsTag = tag.getCompound("chatHistories");
            for (String key : chatsTag.getAllKeys()) {
                ListTag list = chatsTag.getList(key, Tag.TAG_STRING);
                List<String> history = new ArrayList<>();
                for (int i = 0; i < list.size(); i++) {
                    history.add(list.getString(i));
                }
                storage.chatHistories.put(key, history);
            }
        }
        if (tag.contains("unreadCounts")) {
            CompoundTag unreadTag = tag.getCompound("unreadCounts");
            for (String playerUuid : unreadTag.getAllKeys()) {
                CompoundTag pTag = unreadTag.getCompound(playerUuid);
                Map<String, Integer> map = new HashMap<>();
                for (String senderUuid : pTag.getAllKeys()) {
                    map.put(senderUuid, pTag.getInt(senderUuid));
                }
                storage.unreadCounts.put(playerUuid, map);
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
