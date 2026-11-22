package com.amarthaloe.music;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class RadioPlayerEngine {

    public record RadioStation(String freq, String name, String url, boolean isCustom) {}

    public static final List<RadioStation> SERVER_STATIONS = new ArrayList<>();
    public static final List<RadioStation> CUSTOM_STATIONS = new ArrayList<>();

    public static volatile boolean isPlaying = false;
    public static volatile float volume = 0.5f; // 0.0 .. 1.0
    public static volatile String currentFreq = "";
    public static volatile String currentName = "";
    public static volatile String currentUrl = "";
    public static volatile String statusText = "Выключено";

    private static Thread streamThread = null;
    private static volatile boolean stopRequested = false;

    static {
        loadCustomStations();
    }

    public static List<RadioStation> getAllStations() {
        List<RadioStation> all = new ArrayList<>(SERVER_STATIONS);
        all.addAll(CUSTOM_STATIONS);
        return all;
    }

    public static void addCustomStation(String freq, String name, String url) {
        String cleanFreq = freq.trim().replace("|", "");
        String cleanName = name.trim().replace("|", "");
        String cleanUrl = url.trim().replace("|", "");
        if (cleanFreq.isEmpty() || cleanName.isEmpty() || !cleanUrl.startsWith("http")) return;

        CUSTOM_STATIONS.removeIf(s -> s.freq().equalsIgnoreCase(cleanFreq));
        CUSTOM_STATIONS.add(new RadioStation(cleanFreq, cleanName, cleanUrl, true));
        saveCustomStations();
    }

    public static void removeCustomStation(String freq) {
        CUSTOM_STATIONS.removeIf(s -> s.freq().equalsIgnoreCase(freq));
        saveCustomStations();
    }

    private static Path getCustomRadioFile() {
        return MidiPlayerEngine.getMusicDir().resolve("radio_custom.txt");
    }

    public static void saveCustomStations() {
        try {
            List<String> lines = new ArrayList<>();
            for (RadioStation s : CUSTOM_STATIONS) {
                lines.add(s.freq() + "|" + s.name() + "|" + s.url());
            }
            Files.write(getCustomRadioFile(), lines, StandardCharsets.UTF_8);
        } catch (Exception ignored) {}
    }

    public static void loadCustomStations() {
        CUSTOM_STATIONS.clear();
        try {
            Path p = getCustomRadioFile();
            if (!Files.exists(p)) return;
            for (String line : Files.readAllLines(p, StandardCharsets.UTF_8)) {
                String[] parts = line.split("\\|", 3);
                if (parts.length == 3) {
                    CUSTOM_STATIONS.add(new RadioStation(parts[0], parts[1], parts[2], true));
                }
            }
        } catch (Exception ignored) {}
    }

    public static void playStation(String freq, String name, String url) {
        stop();
        // Выключаем MIDI, если оно играло
        MidiPlayerEngine.stopPlayback();

        currentFreq = freq;
        currentName = name;
        currentUrl = url;
        stopRequested = false;
        isPlaying = true;
        statusText = "Подключение...";

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.5f, 0.8f);
        }

        streamThread = new Thread(() -> runStream(url), "Amarthaloe-RadioStream");
        streamThread.setDaemon(true);
        streamThread.start();
    }

    public static void stop() {
        stopRequested = true;
        isPlaying = false;
        statusText = "Выключено";
        if (streamThread != null) {
            streamThread.interrupt();
            streamThread = null;
        }
    }

    public static void adjustVolume(float delta) {
        volume = Math.clamp(volume + delta, 0.0f, 1.0f);
    }

    private static void runStream(String streamUrl) {
        SourceDataLine line = null;
        Bitstream bitstream = null;
        HttpURLConnection conn = null;

        try {
            conn = openConnectionWithRedirects(streamUrl, 3);
            InputStream in = new BufferedInputStream(conn.getInputStream(), 16384);
            bitstream = new Bitstream(in);
            Decoder decoder = new Decoder();

            byte[] pcmBytes = new byte[4608];
            statusText = "В эфире";

            while (!stopRequested) {
                Header header = bitstream.readFrame();
                if (header == null) break;

                SampleBuffer output = (SampleBuffer) decoder.decodeFrame(header, bitstream);
                if (line == null) {
                    AudioFormat format = new AudioFormat(
                            output.getSampleFrequency(),
                            16,
                            output.getChannelCount(),
                            true,
                            false
                    );
                    line = AudioSystem.getSourceDataLine(format);
                    line.open(format);
                    line.start();
                }

                short[] samples = output.getBuffer();
                int len = output.getBufferLength();
                int byteLen = len * 2;
                if (pcmBytes.length < byteLen) {
                    pcmBytes = new byte[byteLen];
                }

                float vol = volume;
                for (int i = 0; i < len; i++) {
                    int scaled = (int) (samples[i] * vol);
                    if (scaled > 32767) scaled = 32767;
                    else if (scaled < -32768) scaled = -32768;
                    pcmBytes[i * 2] = (byte) (scaled & 0xFF);
                    pcmBytes[i * 2 + 1] = (byte) ((scaled >> 8) & 0xFF);
                }

                line.write(pcmBytes, 0, byteLen);
                bitstream.closeFrame();
            }
        } catch (Exception e) {
            if (!stopRequested) {
                statusText = "Ошибка потока";
                Minecraft mc = Minecraft.getInstance();
                mc.execute(() -> {
                    if (mc.player != null) {
                        mc.player.sendSystemMessage(Component.literal("§c[Радио] Не удалось подключиться к волне «" + currentName + "»."));
                    }
                });
            }
        } finally {
            isPlaying = false;
            if (stopRequested) statusText = "Выключено";
            try { if (bitstream != null) bitstream.close(); } catch (Exception ignored) {}
            try {
                if (line != null) {
                    line.stop();
                    line.close();
                }
            } catch (Exception ignored) {}
            if (conn != null) conn.disconnect();
        }
    }

    private static HttpURLConnection openConnectionWithRedirects(String urlStr, int maxRedirects) throws Exception {
        String current = urlStr;
        for (int i = 0; i <= maxRedirects; i++) {
            HttpURLConnection conn = (HttpURLConnection) URI.create(current).toURL().openConnection();
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setRequestProperty("Icy-MetaData", "0");
            conn.setConnectTimeout(6000);
            conn.setReadTimeout(8000);
            conn.setInstanceFollowRedirects(false);

            int code = conn.getResponseCode();
            if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                String loc = conn.getHeaderField("Location");
                conn.disconnect();
                if (loc == null) throw new RuntimeException("Empty redirect");
                current = loc;
            } else {
                return conn;
            }
        }
        throw new RuntimeException("Too many redirects");
    }
}
