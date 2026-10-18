package com.amarthaloe.client;

import com.amarthaloe.network.PassportNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

@EventBusSubscriber(modid = "amarthaloe", bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public class PartyHudOverlay {

    // Настройки игрока (регулируются во вкладке НАСТРОЙКИ)
    public static boolean showPartyHud = true;
    // 0 = Слева сверху (по умолчанию), 1 = Справа сверху, 2 = Слева по центру
    public static int hudPosition = 0;

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (!showPartyHud) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;

        CompoundTag party = PassportNetworking.CLIENT_PARTY_DATA;
        if (party == null || !party.contains("members")) return;

        ListTag members = party.getList("members", Tag.TAG_COMPOUND);
        if (members.isEmpty()) return;

        GuiGraphics g = event.getGuiGraphics();
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();

        int frameW = 136;
        int frameH = 32;
        int startX = 8;
        int startY = 8;

        if (hudPosition == 1) {
            startX = screenW - frameW - 8;
            startY = 8;
        } else if (hudPosition == 2) {
            startX = 8;
            startY = Math.max(8, (screenH - (members.size() * (frameH + 5))) / 2);
        }

        for (int i = 0; i < members.size(); i++) {
            CompoundTag m = members.getCompound(i);
            int y = startY + i * (frameH + 5);

            String name = m.getString("rpName");
            String photoId = m.getString("photoId");
            boolean isLeader = m.getBoolean("isLeader");
            float hp = m.getFloat("hp");
            float maxHp = Math.max(1.0f, m.getFloat("maxHp"));
            int food = m.getInt("food");

            ItemStack heldItem = ItemStack.EMPTY;
            if (m.contains("heldItem") && mc.level != null) {
                heldItem = ItemStack.parseOptional(mc.level.registryAccess(), m.getCompound("heldItem"));
            }

            // Темная фэнтези-подложка фрейма в стиле WoW
            g.fill(startX, y, startX + frameW, y + frameH, 0xCC0E0E0E);
            g.renderOutline(startX, y, frameW, frameH, isLeader ? 0xFFD4AF37 : 0xFF3A3A3A);

            // 1. Аватарка персонажа слева (26x26)
            int avaX = startX + 3;
            int avaY = y + 3;
            g.fill(avaX, avaY, avaX + 26, avaY + 26, 0xFF1A1A1A);
            if (!ClientImageManager.drawImage(g, photoId, avaX + 1, avaY + 1, 24, 24)) {
                g.drawCenteredString(mc.font, "?", avaX + 13, avaY + 9, 0xFF666666);
            }
            g.renderOutline(avaX, avaY, 26, 26, isLeader ? 0xFFFFD700 : 0xFF8F7A42);

            // 2. Имя игрока (с короной лидера)
            String displayName = (isLeader ? "§6★ §f" : "§f") + (name.length() > 13 ? name.substring(0, 13) : name);
            g.drawString(mc.font, displayName, startX + 33, y + 3, 0xFFFFFFFF);

            // 3. Полоска здоровья + цифры
            int barX = startX + 33;
            int hpY = y + 13;
            int barW = 78;
            float hpRatio = Math.clamp(hp / maxHp, 0.0f, 1.0f);
            g.fill(barX, hpY, barX + barW, hpY + 8, 0xFF2A0808);
            g.fill(barX, hpY, barX + (int) (barW * hpRatio), hpY + 8, 0xFFB81D1D);
            g.renderOutline(barX, hpY, barW, 8, 0xFF4A1515);

            String hpText = String.format("%.0f/%.0f", hp, maxHp);
            g.pose().pushPose();
            g.pose().translate(barX + barW / 2.0f, hpY + 1.5f, 0);
            g.pose().scale(0.75f, 0.75f, 1.0f);
            g.drawCenteredString(mc.font, hpText, 0, 0, 0xFFFFFFFF);
            g.pose().popPose();

            // 4. Полоска голода (сытости) под здоровьем
            int foodY = y + 23;
            float foodRatio = Math.clamp(food / 20.0f, 0.0f, 1.0f);
            g.fill(barX, foodY, barX + barW, foodY + 5, 0xFF241705);
            g.fill(barX, foodY, barX + (int) (barW * foodRatio), foodY + 5, 0xFFD98218);
            g.renderOutline(barX, foodY, barW, 5, 0xFF4A3210);

            // 5. Слот текущего предмета в руке справа (20x20)
            int itemBoxX = startX + frameW - 22;
            int itemBoxY = y + 6;
            g.fill(itemBoxX, itemBoxY, itemBoxX + 18, itemBoxY + 18, 0xFF161616);
            g.renderOutline(itemBoxX, itemBoxY, 18, 18, 0xFF555555);
            if (!heldItem.isEmpty()) {
                g.renderItem(heldItem, itemBoxX + 1, itemBoxY + 1);
                g.renderItemDecorations(mc.font, heldItem, itemBoxX + 1, itemBoxY + 1);
            }
        }
    }
}
