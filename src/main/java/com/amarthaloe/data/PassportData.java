package com.amarthaloe.data;

import com.amarthaloe.rpg.Faction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.util.INBTSerializable;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.*;
import java.util.function.Supplier;

public class PassportData implements INBTSerializable<CompoundTag> {
    public boolean isLocked = false;
    public String rpName = "";
    public String gender = "Мужской";
    public String ageAndBirth = "";
    public String race = "";
    public String heightWeight = "";
    public String hair = "";
    public String eyes = "";
    public String features = "";
    public String voice = "";
    public String optional = "";
    public String photoId = "";

    public final Map<Faction, Integer> reputation = new EnumMap<>(Faction.class);
    public String activeTitleFaction = "";
    public String guildTag = ""; // Тег содружества (до 4 букв) для чата и Таба

    public final Set<String> friends = new HashSet<>();
    public final Set<String> incomingRequests = new HashSet<>();
    public final Map<String, CompoundTag> knownProfiles = new HashMap<>();
    public final Map<String, CompoundTag> groupChats = new LinkedHashMap<>();

    public PassportData() {
        for (Faction f : Faction.values()) {
            reputation.put(f, 0);
        }
    }

    public int getRep(Faction f) {
        return reputation.getOrDefault(f, 0);
    }

    public void setRep(Faction f, int value) {
        reputation.put(f, value);
    }

    public boolean isFemale() {
        return "Женский".equalsIgnoreCase(gender);
    }

    public CompoundTag toProfileSnapshot(String uuid, String username, boolean online) {
        CompoundTag snap = new CompoundTag();
        snap.putString("uuid", uuid);
        snap.putString("username", username);
        snap.putString("rpName", rpName.isEmpty() ? username : rpName);
        snap.putBoolean("online", online);
        snap.putBoolean("isLocked", isLocked);
        snap.putString("gender", gender);
        snap.putString("ageAndBirth", ageAndBirth);
        snap.putString("race", race);
        snap.putString("heightWeight", heightWeight);
        snap.putString("hair", hair);
        snap.putString("eyes", eyes);
        snap.putString("features", features);
        snap.putString("voice", voice);
        snap.putString("optional", optional);
        snap.putString("photoId", photoId);
        return snap;
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("isLocked", isLocked);
        tag.putString("rpName", rpName);
        tag.putString("gender", gender);
        tag.putString("ageAndBirth", ageAndBirth);
        tag.putString("race", race);
        tag.putString("heightWeight", heightWeight);
        tag.putString("hair", hair);
        tag.putString("eyes", eyes);
        tag.putString("features", features);
        tag.putString("voice", voice);
        tag.putString("optional", optional);
        tag.putString("photoId", photoId);
        tag.putString("activeTitleFaction", activeTitleFaction);
        tag.putString("guildTag", guildTag);

        CompoundTag repTag = new CompoundTag();
        for (Faction f : Faction.values()) {
            repTag.putInt(f.id, getRep(f));
        }
        tag.put("reputation", repTag);

        ListTag friendsList = new ListTag();
        for (String f : friends) friendsList.add(StringTag.valueOf(f));
        tag.put("friends", friendsList);

        ListTag reqList = new ListTag();
        for (String r : incomingRequests) reqList.add(StringTag.valueOf(r));
        tag.put("incomingRequests", reqList);

        CompoundTag profilesTag = new CompoundTag();
        for (Map.Entry<String, CompoundTag> entry : knownProfiles.entrySet()) {
            profilesTag.put(entry.getKey(), entry.getValue());
        }
        tag.put("knownProfiles", profilesTag);

        CompoundTag groupsTag = new CompoundTag();
        for (Map.Entry<String, CompoundTag> entry : groupChats.entrySet()) {
            groupsTag.put(entry.getKey(), entry.getValue());
        }
        tag.put("groupChats", groupsTag);

        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        isLocked = tag.getBoolean("isLocked");
        rpName = tag.getString("rpName");
        gender = tag.contains("gender") ? tag.getString("gender") : "Мужской";
        ageAndBirth = tag.getString("ageAndBirth");
        race = tag.getString("race");
        heightWeight = tag.getString("heightWeight");
        hair = tag.getString("hair");
        eyes = tag.getString("eyes");
        features = tag.getString("features");
        voice = tag.getString("voice");
        optional = tag.getString("optional");
        photoId = tag.getString("photoId");
        activeTitleFaction = tag.getString("activeTitleFaction");
        guildTag = tag.getString("guildTag");

        if (tag.contains("reputation")) {
            CompoundTag repTag = tag.getCompound("reputation");
            for (Faction f : Faction.values()) {
                reputation.put(f, repTag.getInt(f.id));
            }
        }

        friends.clear();
        if (tag.contains("friends")) {
            ListTag list = tag.getList("friends", Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) friends.add(list.getString(i));
        }

        incomingRequests.clear();
        if (tag.contains("incomingRequests")) {
            ListTag list = tag.getList("incomingRequests", Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) incomingRequests.add(list.getString(i));
        }

        knownProfiles.clear();
        if (tag.contains("knownProfiles")) {
            CompoundTag profilesTag = tag.getCompound("knownProfiles");
            for (String key : profilesTag.getAllKeys()) {
                knownProfiles.put(key, profilesTag.getCompound(key));
            }
        }

        groupChats.clear();
        if (tag.contains("groupChats")) {
            CompoundTag groupsTag = tag.getCompound("groupChats");
            for (String key : groupsTag.getAllKeys()) {
                groupChats.put(key, groupsTag.getCompound(key));
            }
        }
    }

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, "amarthaloe");

    public static final Supplier<AttachmentType<PassportData>> PASSPORT = ATTACHMENTS.register(
            "passport", () -> AttachmentType.serializable(PassportData::new).copyOnDeath().build()
    );
}
