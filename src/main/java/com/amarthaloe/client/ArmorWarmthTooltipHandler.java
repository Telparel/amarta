package com.amarthaloe.client;

import com.amarthaloe.env.ClimateAndBiomeSystem;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

@EventBusSubscriber(modid = "amarthaloe", bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public class ArmorWarmthTooltipHandler {

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof ArmorItem armor)) return;

        int warmth = ClimateAndBiomeSystem.getSingleArmorPieceWarmth(stack);
        int lining = ClimateAndBiomeSystem.getArmorLiningLevel(stack);

        event.getToolTip().add(Component.literal("§b❄ Теплоизоляция: §a+" + warmth + "% §7(Подшивка: " + lining + "/3)"));

        if (armor.getMaterial() != ArmorMaterials.LEATHER) {
            event.getToolTip().add(Component.literal("§6☀ В пустыне: §cНагревается на солнце (+Жажда)"));
        } else {
            event.getToolTip().add(Component.literal("§6☀ В пустыне: §aДышащий материал (не греется)"));
        }

        if (lining < 3) {
            event.getToolTip().add(Component.literal("§8💡 Подшейте Кожу, Шкурку или Шерсть на наковальне (+8% тепла)"));
        }
    }
}
