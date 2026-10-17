package com.amarthaloe;

import com.amarthaloe.data.PassportData;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod("amarthaloe")
public class AmarthaloeMod {
    public static final String MODID = "amarthaloe";

    public AmarthaloeMod(IEventBus modEventBus) {
        // Регистрируем систему паспортов и хранения данных игрока
        PassportData.ATTACHMENTS.register(modEventBus);
    }
}
