package com.amarthaloe.env;

import com.amarthaloe.network.PassportNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.FurnaceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;

@EventBusSubscriber(modid = "amarthaloe", bus = EventBusSubscriber.Bus.GAME)
public class ClimateAndBiomeSystem {

    public static class PlayerClimateState {
        public float cold = 0.0f;       // 0..100 (Холод)
        public float heat = 0.0f;       // 0..100 (Перегрев)
        public float thirst = 100.0f;   // 0..100 (Водный баланс: 100 = напоен, 0 = обезвоживание)
        public int totalWarmth = 0;     // Суммарная защита от холода текущей брони
    }

    private static final Map<UUID, PlayerClimateState> STATES = new HashMap<>();
    private static int tickCounter = 0;

    public static PlayerClimateState getState(ServerPlayer player) {
        return STATES.computeIfAbsent(player.getUUID(), k -> new PlayerClimateState());
    }

    // --- 1. ПОДШИВКА МЕХА, ШКУРОК И КОЖИ К БРОНЕ НА НАКОВАЛЬНЕ ---
    @SubscribeEvent
    public static void onAnvilUpdate(AnvilUpdateEvent event) {
        ItemStack left = event.getLeft();
        ItemStack right = event.getRight();

        if (!(left.getItem() instanceof ArmorItem)) return;

        boolean isFurOrLeather = right.is(Items.RABBIT_HIDE) || right.is(Items.LEATHER)
                || right.is(Items.WHITE_WOOL) || right.is(Items.BROWN_WOOL) || right.is(Items.GRAY_WOOL);

        if (!isFurOrLeather) return;

        int currentLining = getArmorLiningLevel(left);
        if (currentLining >= 3) return;

        ItemStack output = left.copy();
        int newLining = currentLining + 1;

        CustomData cd = output.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag = cd != null ? cd.copyTag() : new CompoundTag();
        tag.putInt("amarthaloe_fur_lining", newLining);
        output.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));

        int totalWarmth = getSingleArmorPieceWarmth(output);
        List<Component> loreLines = new ArrayList<>();
        loreLines.add(Component.literal("§6❄ Меховая подкладка: §eУровень " + newLining + "/3"));
        loreLines.add(Component.literal("§b🛡 Защита от холода: §a+" + totalWarmth + "%"));
        output.set(DataComponents.LORE, new ItemLore(loreLines));

        event.setOutput(output);
        event.setMaterialCost(1);
        event.setCost(newLining * 2);
    }

    public static int getArmorLiningLevel(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        if (cd == null) return 0;
        return cd.copyTag().getInt("amarthaloe_fur_lining");
    }

    public static int getSingleArmorPieceWarmth(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof ArmorItem armor)) return 0;
        int base = 3;
        if (armor.getMaterial() == ArmorMaterials.LEATHER) {
            base = 12;
        } else if (armor.getMaterial() == ArmorMaterials.NETHERITE) {
            base = 7;
        } else if (armor.getMaterial() == ArmorMaterials.GOLD) {
            base = 4;
        }
        int lining = getArmorLiningLevel(stack);
        return base + (lining * 8);
    }

    public static int getHeavyMetalArmorCount(ServerPlayer player) {
        int count = 0;
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack st = player.getItemBySlot(slot);
            if (st.getItem() instanceof ArmorItem armor && armor.getMaterial() != ArmorMaterials.LEATHER) {
                count++;
            }
        }
        return count;
    }

    public static int getTotalPlayerWarmth(ServerPlayer player) {
        int sum = 0;
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            sum += getSingleArmorPieceWarmth(player.getItemBySlot(slot));
        }
        return Math.min(100, sum);
    }

    // --- 2. ПИТЬЕ ВОДЫ И ВОССТАНОВЛЕНИЕ ЖАЖДЫ ---
    @SubscribeEvent
    public static void onUseItemFinish(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        ItemStack item = event.getItem();
        PlayerClimateState st = getState(sp);

        if (item.is(Items.POTION) || item.is(Items.MILK_BUCKET)) {
            st.thirst = Math.min(100.0f, st.thirst + 55.0f);
            st.heat = Math.max(0.0f, st.heat - 25.0f);
            sp.playNotifySound(SoundEvents.GENERIC_DRINK, SoundSource.PLAYERS, 0.8f, 1.1f);
            syncClimateToClient(sp, st, 0);
        } else if (item.is(Items.MELON_SLICE) || item.is(Items.APPLE) || item.is(Items.SWEET_BERRIES)) {
            st.thirst = Math.min(100.0f, st.thirst + 20.0f);
            st.heat = Math.max(0.0f, st.heat - 8.0f);
        } else if (item.is(Items.MUSHROOM_STEW) || item.is(Items.RABBIT_STEW) || item.is(Items.BEETROOT_SOUP)) {
            st.thirst = Math.min(100.0f, st.thirst + 30.0f);
            st.cold = Math.max(0.0f, st.cold - 30.0f);
            syncClimateToClient(sp, st, 0);
        }
    }

    // --- 3. ЕЖЕСЕКУНДНЫЙ РАСЧЕТ КЛИМАТА ПО БИОМАМ ---
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        tickCounter++;
        if (tickCounter % 20 != 0) return;

        for (ServerPlayer sp : event.getServer().getPlayerList().getPlayers()) {
            if (sp.isCreative() || sp.isSpectator()) {
                PlayerClimateState st = getState(sp);
                st.cold = 0;
                st.heat = 0;
                st.thirst = 100;
                syncClimateToClient(sp, st, 0);
                continue;
            }

            updatePlayerClimate(sp);
        }
    }

    private static void updatePlayerClimate(ServerPlayer sp) {
        Level level = sp.level();
        BlockPos pos = sp.blockPosition();
        Biome biome = level.getBiome(pos).value();
        float temp = biome.getBaseTemperature();

        PlayerClimateState st = getState(sp);
        st.totalWarmth = getTotalPlayerWarmth(sp);

        boolean nearHeatSource = isNearHeatSource(level, pos, 4);
        boolean inWater = sp.isInWaterOrBubble();
        boolean underSky = level.canSeeSky(pos);
        boolean isRaining = level.isRaining();
        boolean isNight = level.isNight();

        // 1 = ХОЛОДНЫЙ БИОМ, 2 = ЖАРКИЙ БИОМ, 0 = ОБЫЧНЫЙ БИОМ
        int climateZone = 0;
        if (temp < 0.25f) climateZone = 1;
        else if (temp >= 0.95f || level.dimension() == Level.NETHER) climateZone = 2;

        // --- А. ХОЛОДНЫЕ БИОМЫ ---
        if (climateZone == 1) {
            st.heat = Math.max(0.0f, st.heat - 4.0f);
            st.thirst = Math.max(0.0f, st.thirst - 0.02f);

            if (nearHeatSource) {
                st.cold = Math.max(0.0f, st.cold - 4.5f);
            } else {
                int neededWarmth = (isRaining || isNight) ? 75 : 50;
                if (inWater) neededWarmth = 105;

                if (st.totalWarmth < neededWarmth) {
                    float factor = (neededWarmth - st.totalWarmth) / 45.0f; // Плавный рост холода
                    if (isRaining && underSky) factor *= 1.35f;
                    st.cold = Math.min(100.0f, st.cold + factor);
                } else {
                    st.cold = Math.max(0.0f, st.cold - 2.0f);
                }
            }

            if (st.cold >= 75.0f) {
                sp.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, st.cold >= 92 ? 1 : 0, false, false));
                sp.setTicksFrozen(Math.min(sp.getTicksRequiredToFreeze() + 20, sp.getTicksFrozen() + 30));
            }
            if (st.cold >= 95.0f && tickCounter % 60 == 0) {
                sp.hurt(level.damageSources().freeze(), 1.5f);
                sp.sendSystemMessage(Component.literal("§b❄ [Гипотермия] §cВы замерзаете! Согрейтесь у огня или утеплите броню на наковальне."));
            }
        }
        // --- Б. ЖАРКИЕ БИОМЫ (Медленный нагрев и умеренная жажда) ---
        else if (climateZone == 2) {
            st.cold = Math.max(0.0f, st.cold - 6.0f);

            int metalArmorPieces = getHeavyMetalArmorCount(sp);
            // Мягкий расход жажды (около 15-20 минут до полного истощения без брони)
            float thirstDrain = 0.06f + (metalArmorPieces * 0.035f);
            if (sp.isSprinting()) thirstDrain *= 1.35f;

            if (inWater) {
                st.heat = Math.max(0.0f, st.heat - 6.0f);
                st.thirst = Math.min(100.0f, st.thirst + 1.5f);
            } else {
                st.thirst = Math.max(0.0f, st.thirst - thirstDrain);
                if (underSky && !isNight) {
                    // Плавное накопление жары под солнцем
                    float heatGain = 0.32f + (metalArmorPieces * 0.22f);
                    if (isRaining) heatGain += 0.35f; // Песчаная буря
                    st.heat = Math.min(100.0f, st.heat + heatGain);
                } else {
                    float coolRate = metalArmorPieces >= 3 ? -0.5f : -2.2f;
                    st.heat = Math.clamp(st.heat + coolRate, 0.0f, 100.0f);
                }
            }

            if (st.heat >= 85.0f || st.thirst <= 12.0f) {
                sp.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 0, false, false));
                if (st.heat >= 96.0f && tickCounter % 60 == 0) {
                    sp.hurt(level.damageSources().onFire(), 1.5f);
                    sp.sendSystemMessage(Component.literal("§6☀ [Тепловой удар] §cВы перегрелись! Снимите металлическую броню, укройтесь в тени или выпейте воды."));
                }
                if (st.thirst <= 2.0f && tickCounter % 60 == 0) {
                    sp.hurt(level.damageSources().dryOut(), 1.5f);
                    sp.sendSystemMessage(Component.literal("§e💧 [Обезвоживание] §cВы страдаете от сильной жажды! Выпейте воды."));
                }
            }
        }
        // --- В. ОБЫЧНЫЕ БИОМЫ (Комфортный климат) ---
        else {
            st.cold = Math.max(0.0f, st.cold - 3.5f);
            st.heat = Math.max(0.0f, st.heat - 3.5f);
            st.thirst = Math.max(0.0f, st.thirst - (sp.isSprinting() ? 0.04f : 0.015f));

            if (st.thirst <= 2.0f && tickCounter % 80 == 0) {
                sp.hurt(level.damageSources().dryOut(), 1.0f);
                sp.sendSystemMessage(Component.literal("§e💧 [Жажда] §cВыпейте воды, чтобы восстановить силы!"));
            }
        }

        syncClimateToClient(sp, st, climateZone);
    }

    private static boolean isNearHeatSource(Level level, BlockPos center, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockState bs = level.getBlockState(center.offset(dx, dy, dz));
                    if (bs.is(Blocks.FIRE) || bs.is(Blocks.SOUL_FIRE) || bs.is(Blocks.LAVA)
                            || bs.is(Blocks.TORCH) || bs.is(Blocks.WALL_TORCH) || bs.is(Blocks.LANTERN)
                            || bs.is(Blocks.MAGMA_BLOCK)) {
                        return true;
                    }
                    if (bs.getBlock() instanceof CampfireBlock && bs.getValue(CampfireBlock.LIT)) return true;
                    if (bs.getBlock() instanceof FurnaceBlock && bs.getValue(FurnaceBlock.LIT)) return true;
                }
            }
        }
        return false;
    }

    public static void syncClimateToClient(ServerPlayer sp, PlayerClimateState st, int zone) {
        String dataStr = String.format(Locale.US, "%.1f|%.1f|%.1f|%d|%d",
                st.cold, st.heat, st.thirst, st.totalWarmth, zone);
        PacketDistributor.sendToPlayer(sp, new PassportNetworking.SocialActionPayload("CLIMATE_SYNC", "", dataStr));
    }
}
