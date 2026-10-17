package com.amarthaloe.client;

import com.amarthaloe.client.gui.AmarthaloeMenuScreen;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = "amarthaloe", bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public class ClientInputHandler {

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        // Проверяем, что игрок в мире, не в меню паузы и нажал нашу кнопку M
        if (mc.player != null && mc.screen == null) {
            while (KeyMappings.OPEN_MENU.consumeClick()) {
                mc.setScreen(new AmarthaloeMenuScreen());
            }
        }
    }
}
