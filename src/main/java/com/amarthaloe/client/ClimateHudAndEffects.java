package com.amarthaloe.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

import java.util.Random;

@EventBusSubscriber(modid = "amarthaloe", bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public class ClimateHudAndEffects {

    public static float clientCold = 0.0f;
    public static float clientHeat = 0.0f;
    public static float clientThirst = 100.0f;
    public static int clientWarmth = 0;
    public static int clientZone = 0; // 1 = Холод, 2 = Жара, 0 = Обычный

    // Плавный коэффициент густоты бури (0.0 = ясно, 1.0 = максимальная буря с ограниченным обзором)
    private static float stormFogIntensity = 0.0f;
    private static final Random RANDOM = new Random();
    private static int clientTicks = 0;

    public static void updateFromNetwork(String payload) {
        try {
            String[] p = payload.split("\\|");
            clientCold = Float.parseFloat(p[0]);
            clientHeat = Float.parseFloat(p[1]);
            clientThirst = Float.parseFloat(p[2]);
            clientWarmth = Integer.parseInt(p[3]);
            clientZone = Integer.parseInt(p[4]);
        } catch (Exception ignored) {}
    }

    // --- 1. ЗВУКИ БУРИ И ПЛАВНЫЙ РАСЧЕТ СНИЖЕНИЯ ОБЛАСТИ ЗРЕНИЯ ---
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
        boolean isStormWeather = level.isRaining();

        float targetStorm = 0.0f;

        // А. ХОЛОДНЫЕ БИОМЫ: Буран и морозное дыхание
        if (temp < 0.25f && underSky) {
            if (isStormWeather) {
                targetStorm = 1.0f; // Буран сильно снижает видимость
            } else if (clientCold > 50.0f) {
                targetStorm = (clientCold - 50.0f) / 100.0f;
            }

            // Легкий пар изо рта на морозе раз в 3 секунды
            if (clientTicks % 60 == 0) {
                var look = mc.player.getLookAngle();
                level.addParticle(ParticleTypes.CLOUD,
                        mc.player.getX() + look.x * 0.4,
                        mc.player.getEyeY() - 0.1,
                        mc.player.getZ() + look.z * 0.4,
                        look.x * 0.02, 0.01, look.z * 0.02);
            }

            // Звук завывающей вьюги
            if (clientTicks % (isStormWeather ? 45 : 95) == 0) {
                level.playLocalSound(pos, SoundEvents.ELYTRA_FLYING, SoundSource.AMBIENT, isStormWeather ? 0.35f : 0.12f, 0.55f, false);
            }
            if (clientCold > 60 && clientTicks % 75 == 0) {
                level.playLocalSound(pos, SoundEvents.PLAYER_HURT_FREEZE, SoundSource.PLAYERS, 0.3f, 1.3f, false);
            }
        }
        // Б. ЖАРКИЕ БИОМЫ: Песчаная буря и гул суховея
        else if (temp >= 0.95f && underSky) {
            if (isStormWeather) {
                targetStorm = 1.0f; // Песчаная буря плотно закрывает обзор
            } else if (clientHeat > 55.0f) {
                targetStorm = (clientHeat - 55.0f) / 90.0f;
            }

            if (clientTicks % (isStormWeather ? 40 : 90) == 0) {
                level.playLocalSound(pos, SoundEvents.BREEZE_IDLE_GROUND, SoundSource.AMBIENT, isStormWeather ? 0.55f : 0.2f, 0.6f, false);
            }
        }

        // Плавно меняем плотность тумана бури, чтобы обзор сужался и расширялся естественно
        if (stormFogIntensity < targetStorm) {
            stormFogIntensity = Math.min(targetStorm, stormFogIntensity + 0.015f);
        } else if (stormFogIntensity > targetStorm) {
            stormFogIntensity = Math.max(targetStorm, stormFogIntensity - 0.015f);
        }
    }

    // --- 2. СНИЖЕНИЕ ДАЛЬНОСТИ ОБЗОРА В БУРАНЕ И ПЕСЧАНОЙ БУРЕ ---
    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        if (stormFogIntensity <= 0.01f) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.isCreative() || mc.player.isSpectator()) return;

        float origFar = event.getFarPlaneDistance();
        // На пике бурана или песчаной бури видимость падает до 14 блоков!
        float stormFar = 14.0f;
        float newFar = origFar + (stormFar - origFar) * stormFogIntensity;

        event.setNearPlaneDistance(Math.max(1.5f, newFar * 0.15f));
        event.setFarPlaneDistance(newFar);
        event.setCanceled(true);
    }

    // --- 3. ОКРАШИВАНИЕ ТУМАНА (БЕЛАЯ МГЛА В МЕТЕЛЬ / ПЕСЧАНАЯ МГЛА В ПУСТЫНЕ) ---
    @SubscribeEvent
    public static void onComputeFogColor(ViewportEvent.ComputeFogColor event) {
        if (stormFogIntensity <= 0.01f) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        float temp = mc.level.getBiome(mc.player.blockPosition()).value().getBaseTemperature();
        if (temp < 0.25f) {
            // Холодная снежная мгла бурана
            event.setRed(lerp(event.getRed(), 0.85f, stormFogIntensity));
            event.setGreen(lerp(event.getGreen(), 0.92f, stormFogIntensity));
            event.setBlue(lerp(event.getBlue(), 0.98f, stormFogIntensity));
        } else if (temp >= 0.95f) {
            // Густая пыльно-желтая пелена песчаной бури
            event.setRed(lerp(event.getRed(), 0.82f, stormFogIntensity));
            event.setGreen(lerp(event.getGreen(), 0.64f, stormFogIntensity));
            event.setBlue(lerp(event.getBlue(), 0.36f, stormFogIntensity));
        }
    }

    private static float lerp(float start, float end, float delta) {
        return start + (end - start) * Math.clamp(delta, 0.0f, 1.0f);
    }

    // --- 4. ОТРИСОВКА КРАЕВ ЭКРАНА И ШКАЛ ХОЛОДА / ЖАРЫ / ВОДЫ ---
    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.player.isCreative() || mc.player.isSpectator()) return;

        GuiGraphics g = event.getGuiGraphics();
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();

        if (clientZone == 1 && (clientCold > 20.0f || stormFogIntensity > 0.2f)) {
            int alpha = (int) Math.min(135, clientCold * 1.1f + stormFogIntensity * 45.0f);
            drawScreenEdgesVignette(g, sw, sh, (alpha << 24) | 0x78C8FF, 26);
        } else if (clientZone == 2 && (clientHeat > 20.0f || stormFogIntensity > 0.2f)) {
            int alpha = (int) Math.min(140, clientHeat * 1.1f + stormFogIntensity * 50.0f);
            drawScreenEdgesVignette(g, sw, sh, (alpha << 24) | 0xD88A38, 28);
        }

        // Компактная панель выживания справа над хотбаром (только Вода и Холод/Жара/Тепло брони)
        int panelW = 92;
        int panelH = 28;
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
