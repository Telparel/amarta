package com.amarthaloe.client;

import com.amarthaloe.client.gui.AmarthaloeMenuScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.csrf.GLFW; // Изменено на GLFW для надежности импорта в 1.21

@EventBusSubscriber(modid = "amarthaloe", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class KeyMappings {
    // Категория в настройках управления
    public static final String CATEGORY = "key.categories.amarthaloe";
    
    // Создаем саму клавишу "M"
    public static final KeyMapping OPEN_MENU = new KeyMapping(
            "key.amarthaloe.open_menu", 
            InputConstants.Type.KEYSYM, 
            InputConstants.KEY_M, 
            CATEGORY
    );

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN_MENU);
    }
}
