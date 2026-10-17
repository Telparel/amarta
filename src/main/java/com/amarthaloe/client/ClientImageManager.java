package com.amarthaloe.client;

import com.amarthaloe.network.PassportNetworking;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

public class ClientImageManager {

    public static final long MAX_FILE_SIZE = 2L * 1024L * 1024L; // 2 МБ

    public record LoadedImage(ResourceLocation location, int width, int height) {}

    private static final Map<String, LoadedImage> CACHE = new HashMap<>();
    private static final Set<String> QUEUED_OR_REQUESTED = new HashSet<>();
    private static final Queue<String> REQUEST_QUEUE = new ArrayDeque<>();
    private static String activeDownloadingId = null;
    private static long activeDownloadStartTime = 0;

    private static final Map<String, byte[][]> INCOMING_CHUNKS = new HashMap<>();

    public static void pickImageFromDisk(BiConsumer<String, byte[]> onPicked) {
        CompletableFuture.runAsync(() -> {
            String selectedPath;
            try (MemoryStack stack = MemoryStack.stackPush()) {
                PointerBuffer filters = stack.mallocPointer(3);
                filters.put(stack.UTF8("*.png"));
                filters.put(stack.UTF8("*.jpg"));
                filters.put(stack.UTF8("*.jpeg"));
                filters.flip();
                selectedPath = TinyFileDialogs.tinyfd_openFileDialog(
                        "Выберите изображение (макс. 2 МБ)", "", filters, "Изображения (PNG, JPG)", false
                );
            }
            if (selectedPath == null || selectedPath.isEmpty()) return;

            try {
                File f = new File(selectedPath);
                if (!f.exists()) return;
                if (f.length() > MAX_FILE_SIZE) {
                    Minecraft.getInstance().execute(() -> {
                        if (Minecraft.getInstance().player != null) {
                            Minecraft.getInstance().player.sendSystemMessage(Component.literal("§c[Амарталоэ] Файл слишком большой! Максимальный размер — 2 МБ."));
                        }
                    });
                    return;
                }

                BufferedImage bimg = ImageIO.read(f);
                if (bimg == null) return;
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(bimg, "png", baos);
                byte[] pngBytes = baos.toByteArray();

                if (pngBytes.length > MAX_FILE_SIZE + 512 * 1024) {
                    Minecraft.getInstance().execute(() -> {
                        if (Minecraft.getInstance().player != null) {
                            Minecraft.getInstance().player.sendSystemMessage(Component.literal("§c[Амарталоэ] Слишком высокое разрешение изображения (лимит 2 МБ)!"));
                        }
                    });
                    return;
                }

                String imageId = "img_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
                Minecraft.getInstance().execute(() -> {
                    registerLocalBytes(imageId, pngBytes);
                    PassportNetworking.sendImageInChunksToServer(imageId, pngBytes);
                    onPicked.accept(imageId, pngBytes);
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public static void registerLocalBytes(String imageId, byte[] pngBytes) {
        if (imageId == null || imageId.isEmpty() || pngBytes == null || pngBytes.length == 0) return;
        try {
            NativeImage nativeImg = NativeImage.read(new ByteArrayInputStream(pngBytes));
            int w = nativeImg.getWidth();
            int h = nativeImg.getHeight();
            DynamicTexture dynTex = new DynamicTexture(nativeImg);
            ResourceLocation loc = ResourceLocation.fromNamespaceAndPath("amarthaloe", "dynamic/" + imageId.toLowerCase());
            Minecraft.getInstance().getTextureManager().register(loc, dynTex);
            CACHE.put(imageId, new LoadedImage(loc, w, h));
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (imageId.equals(activeDownloadingId)) {
                activeDownloadingId = null;
                pumpQueue();
            }
        }
    }

    public static void receiveChunkFromServer(String imageId, int chunkIdx, int totalChunks, byte[] data) {
        byte[][] chunks = INCOMING_CHUNKS.computeIfAbsent(imageId, k -> new byte[totalChunks][]);
        if (chunkIdx >= 0 && chunkIdx < chunks.length) {
            chunks[chunkIdx] = data;
        }
        for (byte[] c : chunks) {
            if (c == null) return;
        }
        INCOMING_CHUNKS.remove(imageId);
        int totalLen = 0;
        for (byte[] c : chunks) totalLen += c.length;
        byte[] full = new byte[totalLen];
        int pos = 0;
        for (byte[] c : chunks) {
            System.arraycopy(c, 0, full, pos, c.length);
            pos += c.length;
        }
        registerLocalBytes(imageId, full);
    }

    private static void pumpQueue() {
        // Если предыдущая загрузка зависла дольше чем на 3 секунды — идем дальше по очереди
        if (activeDownloadingId != null && System.currentTimeMillis() - activeDownloadStartTime > 3000) {
            activeDownloadingId = null;
        }
        if (activeDownloadingId == null && !REQUEST_QUEUE.isEmpty()) {
            activeDownloadingId = REQUEST_QUEUE.poll();
            activeDownloadStartTime = System.currentTimeMillis();
            PacketDistributor.sendToServer(new PassportNetworking.SocialActionPayload("REQUEST_IMAGE", activeDownloadingId, ""));
        }
    }

    public static LoadedImage getOrRequest(String imageId) {
        if (imageId == null || imageId.isEmpty()) return null;
        if (CACHE.containsKey(imageId)) return CACHE.get(imageId);
        if (!QUEUED_OR_REQUESTED.contains(imageId)) {
            QUEUED_OR_REQUESTED.add(imageId);
            REQUEST_QUEUE.add(imageId);
        }
        pumpQueue();
        return null;
    }

    public static boolean drawImage(GuiGraphics g, String imageId, int x, int y, int w, int h) {
        LoadedImage img = getOrRequest(imageId);
        if (img == null) return false;
        g.blit(img.location(), x, y, w, h, 0, 0, img.width(), img.height(), img.width(), img.height());
        return true;
    }
}
