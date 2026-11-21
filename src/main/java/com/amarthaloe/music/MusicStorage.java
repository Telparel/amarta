package com.amarthaloe.music;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;

public class MusicStorage extends SavedData {

    // Максимальный размер одного MIDI-файла в архиве сервера — 256 КБ
    public static final int MAX_MIDI_SIZE = 256 * 1024;
    private final Map<String, byte[]> serverArchive = new LinkedHashMap<>();

    public boolean saveTrack(String name, byte[] data) {
        if (data == null || data.length == 0 || data.length > MAX_MIDI_SIZE) return false;
        String cleanName = name.replaceAll("[^a-zA-Zа-яА-Я0-9_\\-\\. ]", "").trim();
        if (cleanName.isEmpty()) return false;
        if (!cleanName.toLowerCase().endsWith(".mid") && !cleanName.toLowerCase().endsWith(".midi")) {
            cleanName += ".mid";
        }
        serverArchive.put(cleanName, data);
        this.setDirty();
        return true;
    }

    public byte[] getTrack(String name) {
        return serverArchive.get(name);
    }

    public List<String> getTrackNames() {
        return new ArrayList<>(serverArchive.keySet());
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        CompoundTag tracksTag = new CompoundTag();
        for (Map.Entry<String, byte[]> entry : serverArchive.entrySet()) {
            tracksTag.putByteArray(entry.getKey(), entry.getValue());
        }
        tag.put("tracks", tracksTag);
        return tag;
    }

    public static MusicStorage load(CompoundTag tag, HolderLookup.Provider provider) {
        MusicStorage storage = new MusicStorage();
        if (tag.contains("tracks")) {
            CompoundTag tracksTag = tag.getCompound("tracks");
            for (String key : tracksTag.getAllKeys()) {
                storage.serverArchive.put(key, tracksTag.getByteArray(key));
            }
        }
        return storage;
    }

    public static MusicStorage get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(MusicStorage::new, MusicStorage::load),
                "amarthaloe_music_archive"
        );
    }
}
