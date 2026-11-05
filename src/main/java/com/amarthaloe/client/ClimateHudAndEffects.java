package com.amarthaloe.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

import java.util.Random;

@EventBusSubscriber(modid = "amarthaloe", bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public class ClimateHudAndEffects {

    public static float clientCold = 0.0f;
    public static float clientHeat = 0.0f;
    public static float clientThirst = 100.0f;
    public static float clientToxicity = 0.0f;
    public static int clientWarmth = 0;
    public static int clientZone = 0; // 1 = Холод, 2 = Жара, 0 = Умеренный

    private static final Random RANDOM = new Random();
    private static int clientTicks = 0;

    public static void updateFromNetwork(String payload) {
        try {
            String[] p = payload.split("\\|");
            clientCold = Float.parseFloat(p[0]);
            clientHeat = Float.parseFloat(p[1]);
            clientThirst = Float.parseFloat(p[2]);
            clientToxicity = Float.parseFloat(p[3]);
            clientWarmth = Integer.parseInt(p[4]);
            clientZone = Integer.parseInt(p[5]);
        } catch (Exception ignored) {}
    }

    // --- 1. ВИЗУАЛЬНЫЕ БУРИ, ЧАСТИЦЫ И АТМОСФЕРНЫЕ ЗВУКИ БИОМОВ ---
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.isPaused()) return;

        clientTicks++;
        Level level = mc.level;
        BlockPos pos = mc.player.blockPosition();
        Biome biome = level.getBiome(pos).value();
        float temp = biome.getBaseTemperature();
        boolean underSky = level.canSeeSky(pos);
        boolean isRaining = level.isRaining();

        // А. ХОЛОДНЫЕ БИОМЫ: Снежные вихри, пар изо рта и завывание вьюги
        if (temp < 0.25f && underSky) {
            int snowIntensity = isRaining ? 28 : 8; // В метель в 3.5 раза гуще!
            for (int i = 0; i < snowIntensity; i++) {
                double px = mc.player.getX() + (RANDOM.nextDouble() - 0.5) * 18.0;
                double py = mc.player.getY() + RANDOM.nextDouble() * 8.0 - 1.0;
                double pz = mc.player.getZ() + (RANDOM.nextDouble() - 0.5) * 18.0;
                // Боковой шквальный ветер метели
                level.addParticle(ParticleTypes.SNOWFLAKE, px, py, pz, 0.22, -0.08, 0.12);
                if (isRaining && i % 3 == 0) {
                    level.addParticle(ParticleTypes.WHITE_ASH, px, py, pz, 0.35, -0.04, 0.18);
                }
            }

            // Пар изо рта на морозе каждые 2.5 секунды
            if (clientTicks % 50 == 0) {
                var look = mc.player.getLookAngle();
                level.addParticle(ParticleTypes.CLOUD,
                        mc.player.getX() + look.x * 0.4,
                        mc.player.getEyeY() - 0.1,
                        mc.player.getZ() + look.z * 0.4,
                        look.x * 0.03, 0.01, look.z * 0.03);
            }

            // Звук ледяной вьюги и хруста льда
            if (clientTicks % (isRaining ? 45 : 90) == 0) {
                level.playLocalSound(pos, SoundEvents.ELYTRA_FLYING, SoundSource.AMBIENT, isRaining ? 0.35f : 0.15f, 0.55f, false);
            }
            if (clientCold > 50 && clientTicks % 70 == 0) {
                level.playLocalSound(pos, SoundEvents.PLAYER_HURT_FREEZE, SoundSource.PLAYERS, 0.35f, 1.3f, false);
            }
        }
        // Б. ЖАРКИЕ БИОМЫ: Песчаные бури, пыльные вихри и гул суховея
        else if (temp >= 0.95f && underSky) {
            boolean sandstorm = isRaining || clientHeat > 45.0f;
            int sandCount = isRaining ? 40 : (sandstorm ? 16 : 5);
            BlockParticleOption sandParticle = new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.SAND.defaultBlockState());

            for (int i = 0; i < sandCount; i++) {
                double px = mc.player.getX() + (RANDOM.nextDouble() - 0.5) * 16.0;
                double py = mc.player.getY() + RANDOM.nextDouble() * 6.0;
                double pz = mc.player.getZ() + (RANDOM.nextDouble() - 0.5) * 16.0;
                level.addParticle(sandParticle, px, py, pz, 0.4, -0.02, 0.2);
                if (isRaining && i % 2 == 0) {
                    level.addParticle(ParticleTypes.ASH, px, py, pz, 0.5, 0.02, 0.25);
                }
            }

            // Акустика песчаной бури и сухого ветра пустыни
            if (clientTicks % (isRaining ? 40 : 85) == 0) {
                level.playLocalSound(pos, SoundEvents.BREEZE_IDLE_GROUND, SoundSource.AMBIENT, isRaining ? 0.6f : 0.25f, 0.6f, false);
            }
        }
        // В. ОБЫЧНЫЕ БИОМЫ: Индустриальный пепел во время кислотного дождя или живая атмосфера в ясную погоду
        else if (underSky) {
            if (isRaining || clientToxicity > 20.0f) {
                for (int i = 0; i < 12; i++) {
                    double px = mc.player.getX() + (RANDOM.nextDouble() - 0.5) * 14.0;
                    double py = mc.player.getY() + RANDOM.nextDouble() * 6.0;
                    double pz = mc.player.getZ() + (RANDOM.nextDouble() - 0.5) * 14.0;
                    level.addParticle(ParticleTypes.ASH, px, py, pz, 0.02, -0.05, 0.02);
                    if (i % 4 == 0) {
                        level.addParticle(ParticleTypes.SPORE_BLOSSOM_AIR, px, py, pz, 0.0, -0.03, 0.0);
                    }
                }
                if (clientTicks % 80 == 0) {
                    level.playLocalSound(pos, SoundEvents.LAVA_EXTINGUISH, SoundSource.AMBIENT, 0.15f, 1.6f, false);
                }
            } else {
                // Спокойная атмосфера окраин Исономии: редкая пыльца/листва на ветру и пение птиц/шелест
                if (clientTicks % 6 == 0) {
                    double px = mc.player.getX() + (RANDOM.nextDouble() - 0.5) * 12.0;
                    double py = mc.player.getY() + RANDOM.nextDouble() * 5.0;
                    double pz = mc.player.getZ() + (RANDOM.nextDouble() - 0.5) * 12.0;
                    level.addParticle(ParticleTypes.WHITE_ASH, px, py, pz, 0.03, -0.01, 0.02);
                }
                if (clientTicks % 160 == 0) {
                    if (level.isDay()) {
                        level.playLocalSound(pos, SoundEvents.PARROT_AMBIENT, SoundSource.AMBIENT, 0.25f, 0.85f + RANDOM.nextFloat() * 0.3f, false);
                    } else {
                        level.playLocalSound(pos, SoundEvents.BEACON_AMBIENT, SoundSource.AMBIENT, 0.2f, 0.5f, false);
                    }
                }
            }
        }
    }

    // --- 2. ОТРИСОВКА ЭКРАННЫХ ЭФФЕКТОВ БУРИ, ИЗМОРОЗИ, ЖАРЫ И ШКАЛ КЛИМАТА ---
    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.player.isCreative() || mc.player.isSpectator()) return;

        GuiGraphics g = event.getGuiGraphics();
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();

        // 1. Полноэкранные атмосферные виньетки при экстремальном климате или буре
        boolean isRaining = mc.level != null && mc.level.isRaining() && mc.level.canSeeSky(mc.player.blockPosition());

        if (clientZone == 1 && (clientCold > 15.0f || isRaining)) {
            // Ледяная дымка метели и обмерзание краев экрана
            int alpha = (int) Math.min(140, clientCold * 1.2f + (isRaining ? 35 : 0));
            int col = (alpha << 24) | 0x78C8FF;
            drawScreenEdgesVignette(g, sw, sh, col, 28);
        } else if (clientZone == 2 && (clientHeat > 15.0f || isRaining)) {
            // Песчаная мгла и солнечное марево по краям экрана
            int alpha = (int) Math.min(150, clientHeat * 1.2f + (isRaining ? 55 : 0));
            int col = (alpha << 24) | (isRaining ? 0xC89646 : 0xFF7828);
            drawScreenEdgesVignette(g, sw, sh, col, 32);
        } else if (clientToxicity > 15.0f) {
            // Кислотно-пепельный смог Исономии
            int alpha = (int) Math.min(130, clientToxicity * 1.2f);
            int col = (alpha << 24) | 0x558B46;
            drawScreenEdgesVignette(g, sw, sh, col, 24);
        }

        // 2. Компактная приборная панель выживания справа над хотбаром
        int panelW = 92;
        int panelH = (clientToxicity > 2.0f) ? 38 : 28;
        int px = sw / 2 + 96;
        int py = sh - panelH - 4;

        g.fill(px, py, px + panelW, py + panelH, 0xCC0D0D0D);
        g.renderOutline(px, py, panelW, panelH, 0xFF8F7A42);

        // Полоска 1: Жажда (Вода)
        drawMiniBar(g, mc, px + 4, py + 4, 84, clientThirst / 100.0f, 0xFF2885E8,
                "💧 Вода: " + Math.round(clientThirst) + "%");

        // Полоска 2: Холод / Жара / Тепло брони
        if (clientCold >= clientHeat && (clientZone == 1 || clientCold > 1.0f)) {
            drawMiniBar(g, mc, px + 4, py + 15, 84, clientCold / 100.0f, 0xFF55D8FF,
                    "❄ Холод: " + Math.round(clientCold) + "% (🛡" + clientWarmth + "%)");
        } else if (clientZone == 2 || clientHeat > 1.0f) {
            drawMiniBar(g, mc, px + 4, py + 15, 84, clientHeat / 100.0f, 0xFFFF6E22,
                    "☀ Жара: " + Math.round(clientHeat) + "%");
        } else {
            drawMiniBar(g, mc, px + 4, py + 15, 84, clientWarmth / 100.0f, 0xFF42A868,
                    "🛡 Тепло брони: " + clientWarmth + "%");
        }

        // Полоска 3 (появляется при кислотных осадках / смоге в обычных биомах)
        if (clientToxicity > 2.0f) {
            drawMiniBar(g, mc, px + 4, py + 26, 84, clientToxicity / 100.0f, 0xFF68B832,
                    "☣ Смог: " + Math.round(clientToxicity) + "%");
        }
    }

    private static void drawMiniBar(GuiGraphics g, Minecraft mc, int x, int y, int w, float ratio, int color, String label) {
        g.fill(x, y, x + w, y + 8, 0xFF1A1A1A);
        int fillW = (int) (w * Math.clamp(ratio, 0.0f, 1.0f));
        g.fill(x, y, x + fillW, y + 8, color);
        g.renderOutline(x, y, w, 8, 0xFF3A3A3A);

        g.pose().pushPose();
        g.pose().translate(x + 3, y + 1.5f, 0);
        g.pose().scale(0.68f, 0.68f, 1.0f);
        g.drawString(mc.font, label, 0, 0, 0xFFFFFFFF, true);
        g.pose().popPose();
    }

    private static void drawScreenEdgesVignette(GuiGraphics g, int sw, int sh, int color, int thickness) {
        g.fillGradient(0, 0, sw, thickness, color, 0x00000000);
        g.fillGradient(0, sh - thickness, sw, sh, 0x00000000, color);
        g.fill(0, 0, 8, sh, color);
        g.fill(sw - 8, 0, sw, sh, color);
    }
}
