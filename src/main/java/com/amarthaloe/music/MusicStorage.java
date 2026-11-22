package com.amarthaloe.music;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;

public class MusicStorage extends SavedData {

    public static final int MAX_MIDI_SIZE = 256 * 1024;
    private final Map<String, byte[]> serverArchive = new LinkedHashMap<>();

    // Серверные радиостанции: Частота -> "Название|URL"
    private final Map<String, String> radioStations = new LinkedHashMap<>();

    public MusicStorage() {
        ensureDefaultStations();
    }

    private void ensureDefaultStations() {
        if (radioStations.isEmpty()) {
            radioStations.put("91.7", "Nightingale|http://choco.hostingradio.ru:10010/fm");
            radioStations.put("97.0", "La Intellecto|http://emgregion.hostingradio.ru:8064/moscow.europaplus.mp3");
            radioStations.put("101.5", "HuliNam|http://emgregion.hostingradio.ru:8064/kaluga.novoe.mp3");
            radioStations.put("104.2", "1405 год|http://emgregion.hostingradio.ru:8064/moscow.retrofm.mp3");
        }
    }

    public void addRadioStation(String freq, String name, String url) {
        radioStations.put(freq.trim(), name.trim().replace("|", "") + "|" + url.trim());
        this.setDirty();
    }

    public boolean removeRadioStation(String freq) {
        if (radioStations.remove(freq.trim()) != null) {
            this.setDirty();
            return true;
        }
        return false;
    }

    public String serializeRadioStations() {
        List<String> entries = new ArrayList<>();
        for (Map.Entry<String, String> e : radioStations.entrySet()) {
            entries.add(e.getKey() + "|" + e.getValue());
        }
        return String.join("\n", entries);
    }

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

        CompoundTag radioTag = new CompoundTag();
        for (Map.Entry<String, String> entry : radioStations.entrySet()) {
            radioTag.putString(entry.getKey(), entry.getValue());
        }
        tag.put("radio", radioTag);
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
        if (tag.contains("radio")) {
            storage.radioStations.clear();
            CompoundTag radioTag = tag.getCompound("radio");
            for (String key : radioTag.getAllKeys()) {
                storage.radioStations.put(key, radioTag.getString(key));
            }
            storage.ensureDefaultStations();
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
