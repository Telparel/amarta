package com.amarthaloe.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

public class AuthStorage extends SavedData {

    // UUID игрока (или ник в нижнем регистре) -> SHA-256 хеш пароля
    private final Map<String, String> passwordHashes = new HashMap<>();

    public static String sha256(String rawPassword) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(("amarthaloe_salt_" + rawPassword).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return Integer.toHexString(rawPassword.hashCode());
        }
    }

    public boolean isRegistered(String playerUuid) {
        return passwordHashes.containsKey(playerUuid);
    }

    public void registerPassword(String playerUuid, String rawPassword) {
        passwordHashes.put(playerUuid, sha256(rawPassword));
        this.setDirty();
    }

    public boolean verifyPassword(String playerUuid, String rawPassword) {
        String stored = passwordHashes.get(playerUuid);
        if (stored == null) return false;
        return stored.equals(sha256(rawPassword));
    }

    public boolean resetPassword(String playerUuid) {
        if (passwordHashes.remove(playerUuid) != null) {
            this.setDirty();
            return true;
        }
        return false;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        CompoundTag mapTag = new CompoundTag();
        for (Map.Entry<String, String> entry : passwordHashes.entrySet()) {
            mapTag.putString(entry.getKey(), entry.getValue());
        }
        tag.put("passwords", mapTag);
        return tag;
    }

    public static AuthStorage load(CompoundTag tag, HolderLookup.Provider provider) {
        AuthStorage storage = new AuthStorage();
        if (tag.contains("passwords")) {
            CompoundTag mapTag = tag.getCompound("passwords");
            for (String key : mapTag.getAllKeys()) {
                storage.passwordHashes.put(key, mapTag.getString(key));
            }
        }
        return storage;
    }

    public static AuthStorage get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(AuthStorage::new, AuthStorage::load),
                "amarthaloe_auth"
        );
    }
}
