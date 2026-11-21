package com.amarthaloe.music;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import javax.sound.midi.*;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;

public class MidiPlayerEngine {

    public static final String[] INSTRUMENTS = {
        "Арфа", "Гитара", "Флейта", "Бас", "Колокол", "Банджо", "Колокольчик", "Электро", "Барабан"
    };

    public record ScheduledNote(long timeMs, int noteIndex, float volume, int trackIdx) {}

    private static List<ScheduledNote> currentNotes = new ArrayList<>();
    private static int playCursor = 0;
    private static long playStartTimeMs = 0;
    public static boolean isPlaying = false;

    public static String selectedFileName = "";
    public static int selectedInstrument = 0;
    public static int selectedTrackFilter = -1; // -1 = Все дорожки, 0..N = конкретная дорожка
    public static int loadedTrackCount = 0;

    public static Path getMusicDir() {
        Path dir = Minecraft.getInstance().gameDirectory.toPath().resolve("amarthaloe_music");
        try {
            if (!Files.exists(dir)) Files.createDirectories(dir);
        } catch (Exception ignored) {}
        return dir;
    }

    public static List<String> getLocalTracks() {
        List<String> list = new ArrayList<>();
        File[] files = getMusicDir().toFile().listFiles((d, name) ->
                name.toLowerCase().endsWith(".mid") || name.toLowerCase().endsWith(".midi"));
        if (files != null) {
            Arrays.sort(files, Comparator.comparing(File::getName));
            for (File f : files) list.add(f.getName());
        }
        return list;
    }

    // Выбор .mid файла с диска через системный диалог
    public static String pickMidiFromDisk() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filters = stack.mallocPointer(2);
            filters.put(stack.UTF8("*.mid"));
            filters.put(stack.UTF8("*.midi"));
            filters.flip();

            String pathStr = TinyFileDialogs.tinyfd_openFileDialog(
                    "Выберите MIDI-файл для Амарталоэ", "", filters, "MIDI файлы (*.mid, *.midi)", false);
            if (pathStr == null || pathStr.isEmpty()) return null;

            Path src = Path.of(pathStr);
            String fileName = src.getFileName().toString();
            Path dest = getMusicDir().resolve(fileName);
            Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
            return fileName;
        } catch (Exception e) {
            return null;
        }
    }

    public static void saveDownloadedTrack(String name, byte[] data) {
        try {
            Files.write(getMusicDir().resolve(name), data);
        } catch (Exception ignored) {}
    }

    public static void uploadTrackToServer(String fileName) {
        try {
            Path p = getMusicDir().resolve(fileName);
            if (!Files.exists(p)) return;
            byte[] bytes = Files.readAllBytes(p);
            if (bytes.length > MusicStorage.MAX_MIDI_SIZE) return;
            PacketDistributor.sendToServer(new MusicNetworking.MidiFilePayload(fileName, bytes));
        } catch (Exception ignored) {}
    }

    // Разбор MIDI-файла в список нот с точным временем (в миллисекундах)
    public static boolean loadMidiFile(String fileName) {
        stopPlayback();
        try {
            File file = getMusicDir().resolve(fileName).toFile();
            if (!file.exists()) return false;

            Sequence seq = MidiSystem.getSequence(file);
            Track[] tracks = seq.getTracks();
            loadedTrackCount = tracks.length;
            selectedFileName = fileName;

            int ppq = seq.getResolution();
            float divisionType = seq.getDivisionType();

            // Собираем все события смены темпа (микросекунд на четверть)
            List<long[]> tempoEvents = new ArrayList<>();
            tempoEvents.add(new long[]{0, 500000}); // Стандартные 120 BPM

            for (Track t : tracks) {
                for (int i = 0; i < t.size(); i++) {
                    MidiEvent ev = t.get(i);
                    if (ev.getMessage() instanceof MetaMessage mm && mm.getType() == 0x51) {
                        byte[] d = mm.getData();
                        if (d.length == 3) {
                            long mpq = ((d[0] & 0xFF) << 16) | ((d[1] & 0xFF) << 8) | (d[2] & 0xFF);
                            tempoEvents.add(new long[]{ev.getTick(), mpq});
                        }
                    }
                }
            }
            tempoEvents.sort(Comparator.comparingLong(a -> a[0]));

            List<ScheduledNote> parsed = new ArrayList<>();
            for (int tIdx = 0; tIdx < tracks.length; tIdx++) {
                Track track = tracks[tIdx];
                for (int i = 0; i < track.size(); i++) {
                    MidiEvent ev = track.get(i);
                    if (ev.getMessage() instanceof ShortMessage sm) {
                        if (sm.getCommand() == ShortMessage.NOTE_ON && sm.getData2() > 0) {
                            int midiKey = sm.getData1();
                            float vol = sm.getData2() / 127.0f;
                            int mcNote = mapMidiKeyToMinecraftNote(midiKey);
                            long ms = tickToMs(ev.getTick(), ppq, divisionType, tempoEvents);
                            parsed.add(new ScheduledNote(ms, mcNote, vol, tIdx));
                        }
                    }
                }
            }

            parsed.sort(Comparator.comparingLong(ScheduledNote::timeMs));
            currentNotes = parsed;
            return !currentNotes.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    // Автоматический октавный сдвиг в диапазон нотных блоков Minecraft (F#3=54 .. F#5=78)
    private static int mapMidiKeyToMinecraftNote(int midiKey) {
        int key = midiKey;
        while (key < 54) key += 12;
        while (key > 78) key -= 12;
        return key - 54; // 0..24
    }

    private static long tickToMs(long targetTick, int ppq, float divisionType, List<long[]> tempoEvents) {
        if (divisionType != Sequence.PPQ) {
            return (long) ((targetTick / (divisionType * ppq)) * 1000.0);
        }
        double totalMicros = 0;
        long prevTick = 0;
        long currentMpq = 500000;

        for (long[] te : tempoEvents) {
            if (te[0] >= targetTick) break;
            totalMicros += (double) (te[0] - prevTick) * currentMpq / ppq;
            prevTick = te[0];
            currentMpq = te[1];
        }
        totalMicros += (double) (targetTick - prevTick) * currentMpq / ppq;
        return (long) (totalMicros / 1000.0);
    }

    public static void startSyncedPlayback() {
        if (currentNotes.isEmpty() && !selectedFileName.isEmpty()) {
            loadMidiFile(selectedFileName);
        }
        if (currentNotes.isEmpty()) return;
        playCursor = 0;
        playStartTimeMs = System.currentTimeMillis();
        isPlaying = true;
    }

    public static void stopPlayback() {
        isPlaying = false;
        playCursor = 0;
    }

    // Вызывается каждый клиентский тик и отправляет ноты, время которых настало
    public static void tickPlayback() {
        if (!isPlaying || Minecraft.getInstance().player == null) return;

        long elapsed = System.currentTimeMillis() - playStartTimeMs;
        int sentThisTick = 0;

        while (playCursor < currentNotes.size()) {
            ScheduledNote n = currentNotes.get(playCursor);
            if (n.timeMs() > elapsed) break;

            if ((selectedTrackFilter == -1 || n.trackIdx() == selectedTrackFilter) && sentThisTick < 8) {
                PacketDistributor.sendToServer(new MusicNetworking.PlayNotePayload(
                        selectedInstrument, n.noteIndex(), n.volume()
                ));
                sentThisTick++;
            }
            playCursor++;
        }

        if (playCursor >= currentNotes.size()) {
            isPlaying = false;
        }
    }
}
