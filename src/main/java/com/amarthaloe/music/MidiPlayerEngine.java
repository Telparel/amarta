package com.amarthaloe.music;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
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
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MidiPlayerEngine {

    public static final String[] INSTRUMENTS = {
        "Арфа", "Гитара", "Флейта", "Бас", "Колокол", "Банджо", "Колокольчик", "Электро", "Барабан"
    };

    public record ScheduledNote(long timeMs, int noteIndex, float volume, int trackIdx, boolean isPercussion) {}

    private static volatile List<ScheduledNote> currentNotes = new ArrayList<>();
    private static volatile int playCursor = 0;
    private static volatile long playStartTimeMs = 0;
    public static volatile boolean isPlaying = false;

    public static String selectedFileName = "";
    public static volatile int selectedInstrument = 0;
    public static volatile int selectedTrackFilter = -1;
    public static int loadedTrackCount = 0;

    private static final ConcurrentLinkedQueue<Byte> SERVER_NOTE_QUEUE = new ConcurrentLinkedQueue<>();
    private static volatile float lastBatchVolume = 1.0f;

    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Amarthaloe-MidiTimer");
        t.setDaemon(true);
        return t;
    });

    static {
        SCHEDULER.scheduleAtFixedRate(MidiPlayerEngine::preciseStep, 10, 10, TimeUnit.MILLISECONDS);
    }

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

            List<long[]> tempoEvents = new ArrayList<>();
            tempoEvents.add(new long[]{0, 500000});

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
                            boolean isPercussion = (sm.getChannel() == 9);
                            int midiKey = sm.getData1();
                            float vol = sm.getData2() / 127.0f;
                            int mcNote = mapMidiKeyToMinecraftNote(midiKey);
                            long ms = tickToMs(ev.getTick(), ppq, divisionType, tempoEvents);
                            parsed.add(new ScheduledNote(ms, mcNote, vol, tIdx, isPercussion));
                        }
                    }
                }
            }

            parsed.sort(Comparator.comparingLong(ScheduledNote::timeMs));

            List<ScheduledNote> cleaned = new ArrayList<>();
            long[] lastNoteTime = new long[25];
            Arrays.fill(lastNoteTime, -1000);
            for (ScheduledNote n : parsed) {
                if (n.isPercussion() || (n.timeMs() - lastNoteTime[n.noteIndex()] >= 40)) {
                    cleaned.add(n);
                    if (!n.isPercussion()) lastNoteTime[n.noteIndex()] = n.timeMs();
                }
            }

            currentNotes = cleaned;
            return !currentNotes.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    private static int mapMidiKeyToMinecraftNote(int midiKey) {
        int key = midiKey;
        while (key < 54) key += 12;
        while (key > 78) key -= 12;
        return key - 54;
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
        // Если участник ансамбля не выбрал файл — автоматически берем первый доступный из папки
        if (currentNotes.isEmpty()) {
            List<String> local = getLocalTracks();
            if (!local.isEmpty()) {
                loadMidiFile(local.get(0));
            }
        }
        if (currentNotes.isEmpty()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.sendSystemMessage(Component.literal("§c[Ансамбль] У вас не выбран MIDI-файл! Выберите трек или скачайте его из архива в [N]."));
            }
            return;
        }
        SERVER_NOTE_QUEUE.clear();
        playCursor = 0;
        playStartTimeMs = System.currentTimeMillis();
        isPlaying = true;
    }

    public static void stopPlayback() {
        isPlaying = false;
        playCursor = 0;
        SERVER_NOTE_QUEUE.clear();
    }

    private static void preciseStep() {
        if (!isPlaying) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            isPlaying = false;
            return;
        }

        List<ScheduledNote> notes = currentNotes;
        long elapsed = System.currentTimeMillis() - playStartTimeMs;
        int inst = selectedInstrument;
        int trackFilter = selectedTrackFilter;
        boolean playingDrums = (inst == 8);

        int playedNow = 0;
        while (playCursor < notes.size()) {
            ScheduledNote n = notes.get(playCursor);
            if (n.timeMs() > elapsed) break;

            boolean matchesTrack = (trackFilter == -1 || n.trackIdx() == trackFilter);
            boolean matchesType = playingDrums ? n.isPercussion() : !n.isPercussion();

            if (matchesTrack && matchesType && playedNow < 3) {
                int noteIdx = n.noteIndex();
                float vol = n.volume();
                lastBatchVolume = vol;

                mc.execute(() -> {
                    if (mc.player != null && mc.level != null) {
                        float pitch = (float) Math.pow(2.0, (noteIdx - 12.0) / 12.0);
                        SoundEvent sound = MusicNetworking.getInstrumentSound(inst);
                        mc.level.playLocalSound(
                                mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                                sound, SoundSource.RECORDS, vol * 1.4f, pitch, false
                        );
                    }
                });

                SERVER_NOTE_QUEUE.add((byte) noteIdx);
                playedNow++;
            }
            playCursor++;
        }

        if (playCursor >= notes.size()) {
            isPlaying = false;
        }
    }

    public static void tickPlayback() {
        if (SERVER_NOTE_QUEUE.isEmpty() || Minecraft.getInstance().player == null) return;

        List<Byte> batch = new ArrayList<>(4);
        while (batch.size() < 4 && !SERVER_NOTE_QUEUE.isEmpty()) {
            Byte b = SERVER_NOTE_QUEUE.poll();
            if (b != null) batch.add(b);
        }
        SERVER_NOTE_QUEUE.clear();

        if (!batch.isEmpty()) {
            byte[] arr = new byte[batch.size()];
            for (int i = 0; i < batch.size(); i++) arr[i] = batch.get(i);
            PacketDistributor.sendToServer(new MusicNetworking.PlayNotesBatchPayload(
                    selectedInstrument, arr, lastBatchVolume
            ));
        }
    }
}
