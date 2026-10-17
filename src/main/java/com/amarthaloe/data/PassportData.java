package com.amarthaloe.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.util.INBTSerializable;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

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
    }

    // Регистрация привязки данных к игроку в NeoForge 1.21.1
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, "amarthaloe");

    public static final Supplier<AttachmentType<PassportData>> PASSPORT = ATTACHMENTS.register(
            "passport", () -> AttachmentType.serializable(PassportData::new).copyOnDeath().build()
    );
}
