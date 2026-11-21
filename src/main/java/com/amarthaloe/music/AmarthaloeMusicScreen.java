package com.amarthaloe.music;

import com.amarthaloe.client.gui.DarkButton;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

public class AmarthaloeMusicScreen extends Screen {

    private final int boxW = 340;
    private final int boxH = 235;
    private int leftPos;
    private int topPos;

    private boolean showingServerArchive = false;
    private int listScroll = 0;
    private EditBox invitePlayerBox;

    public AmarthaloeMusicScreen() {
        super(Component.literal("Музыкальный Проигрыватель"));
    }

    @Override
    protected void init() {
        super.init();
        this.leftPos = (this.width - this.boxW) / 2;
        this.topPos = (this.height - this.boxH) / 2;

        // Запрашиваем актуальный список мелодий с сервера при открытии
        PacketDistributor.sendToServer(new MusicNetworking.MusicActionPayload("REQ_ARCHIVE", "", ""));

        // Вкладки: Мои файлы / Архив сервера
        DarkButton localTab = new DarkButton(leftPos + 10, topPos + 24, 95, 18, Component.literal("Мои мелодии"), (b) -> {
            showingServerArchive = false;
            listScroll = 0;
            this.rebuildWidgets();
        });
        localTab.setSelected(!showingServerArchive);

        DarkButton archiveTab = new DarkButton(leftPos + 109, topPos + 24, 105, 18, Component.literal("Архив сервера"), (b) -> {
            showingServerArchive = true;
            listScroll = 0;
            this.rebuildWidgets();
        });
        archiveTab.setSelected(showingServerArchive);

        DarkButton uploadDiskBtn = new DarkButton(leftPos + 218, topPos + 24, 112, 18, Component.literal("+ Загрузить .mid"), (b) -> {
            String added = MidiPlayerEngine.pickMidiFromDisk();
            if (added != null) {
                showingServerArchive = false;
                MidiPlayerEngine.loadMidiFile(added);
                this.rebuildWidgets();
            }
        });

        this.addRenderableWidget(localTab);
        this.addRenderableWidget(archiveTab);
        this.addRenderableWidget(uploadDiskBtn);

        // Список треков (до 5 строк одновременно)
        List<String> tracks = showingServerArchive ? MusicNetworking.CLIENT_SERVER_ARCHIVE : MidiPlayerEngine.getLocalTracks();
        int maxRows = 5;
        listScroll = Math.clamp(listScroll, 0, Math.max(0, tracks.size() - maxRows));

        for (int i = 0; i < maxRows && (listScroll + i) < tracks.size(); i++) {
            String trackName = tracks.get(listScroll + i);
            int rowY = topPos + 48 + i * 20;

            String display = trackName.length() > 26 ? trackName.substring(0, 26) + "..." : trackName;
            DarkButton selectBtn = new DarkButton(leftPos + 10, rowY, 240, 18, Component.literal(display), (b) -> {
                if (!showingServerArchive) {
                    MidiPlayerEngine.loadMidiFile(trackName);
                    this.rebuildWidgets();
                } else {
                    PacketDistributor.sendToServer(new MusicNetworking.MusicActionPayload("DOWNLOAD_TRACK", trackName, ""));
                }
            });
            selectBtn.setSelected(!showingServerArchive && trackName.equals(MidiPlayerEngine.selectedFileName));
            this.addRenderableWidget(selectBtn);

            if (!showingServerArchive) {
                DarkButton toServerBtn = new DarkButton(leftPos + 254, rowY, 76, 18, Component.literal("↑ В архив"), (b) -> {
                    MidiPlayerEngine.uploadTrackToServer(trackName);
                });
                this.addRenderableWidget(toServerBtn);
            } else {
                DarkButton dlBtn = new DarkButton(leftPos + 254, rowY, 76, 18, Component.literal("↓ Скачать"), (b) -> {
                    PacketDistributor.sendToServer(new MusicNetworking.MusicActionPayload("DOWNLOAD_TRACK", trackName, ""));
                });
                this.addRenderableWidget(dlBtn);
            }
        }

        // Панель управления: Инструмент, Дорожка, Играть/Стоп
        int ctrlY = topPos + 154;
        String instName = MidiPlayerEngine.INSTRUMENTS[MidiPlayerEngine.selectedInstrument];
        DarkButton instBtn = new DarkButton(leftPos + 10, ctrlY, 108, 18, Component.literal("Инстр.: §e" + instName), (b) -> {
            MidiPlayerEngine.selectedInstrument = (MidiPlayerEngine.selectedInstrument + 1) % MidiPlayerEngine.INSTRUMENTS.length;
            this.rebuildWidgets();
        });

        String trackLabel = MidiPlayerEngine.selectedTrackFilter == -1 ? "Все" : "#" + (MidiPlayerEngine.selectedTrackFilter + 1);
        DarkButton trackBtn = new DarkButton(leftPos + 122, ctrlY, 96, 18, Component.literal("Дорожка: §e" + trackLabel), (b) -> {
            if (MidiPlayerEngine.loadedTrackCount > 0) {
                MidiPlayerEngine.selectedTrackFilter++;
                if (MidiPlayerEngine.selectedTrackFilter >= MidiPlayerEngine.loadedTrackCount) {
                    MidiPlayerEngine.selectedTrackFilter = -1;
                }
                this.rebuildWidgets();
            }
        });

        DarkButton playStopBtn = new DarkButton(leftPos + 222, ctrlY, 108, 18,
                Component.literal(MidiPlayerEngine.isPlaying ? "§c■ Стоп" : "§a▶ Играть"), (b) -> {
            if (MidiPlayerEngine.isPlaying) {
                PacketDistributor.sendToServer(new MusicNetworking.MusicActionPayload("ENSEMBLE_STOP", "", ""));
            } else {
                PacketDistributor.sendToServer(new MusicNetworking.MusicActionPayload("ENSEMBLE_START", MidiPlayerEngine.selectedFileName, ""));
            }
            this.rebuildWidgets();
        });

        this.addRenderableWidget(instBtn);
        this.addRenderableWidget(trackBtn);
        this.addRenderableWidget(playStopBtn);

        // Блок Ансамбля (приглашение напарника или принятие приглашения)
        int ensY = topPos + 196;
        if (!MusicNetworking.CLIENT_PENDING_INVITE_FROM.isEmpty()) {
            DarkButton acceptBtn = new DarkButton(leftPos + 190, ensY, 140, 18, Component.literal("§a✔ Принять ансамбль"), (b) -> {
                PacketDistributor.sendToServer(new MusicNetworking.MusicActionPayload("ENSEMBLE_ACCEPT", "", ""));
                MusicNetworking.CLIENT_PENDING_INVITE_FROM = "";
                this.rebuildWidgets();
            });
            this.addRenderableWidget(acceptBtn);
        } else if (!MusicNetworking.CLIENT_ENSEMBLE_PARTNER.isEmpty()) {
            DarkButton leaveBtn = new DarkButton(leftPos + 222, ensY, 108, 18, Component.literal("§cПокинуть"), (b) -> {
                PacketDistributor.sendToServer(new MusicNetworking.MusicActionPayload("ENSEMBLE_LEAVE", "", ""));
                MusicNetworking.CLIENT_ENSEMBLE_PARTNER = "";
                this.rebuildWidgets();
            });
            this.addRenderableWidget(leaveBtn);
        } else {
            invitePlayerBox = new EditBox(this.font, leftPos + 10, ensY, 208, 18, Component.empty());
            invitePlayerBox.setMaxLength(32);
            invitePlayerBox.setHint(Component.literal("Ник или РП-имя для игры вместе...").withStyle(s -> s.withColor(0x555555)));
            this.addRenderableWidget(invitePlayerBox);

            DarkButton invBtn = new DarkButton(leftPos + 222, ensY, 108, 18, Component.literal("♫ Пригласить"), (b) -> {
                String target = invitePlayerBox.getValue().trim();
                if (!target.isEmpty()) {
                    PacketDistributor.sendToServer(new MusicNetworking.MusicActionPayload("ENSEMBLE_INVITE", target, ""));
                    invitePlayerBox.setValue("");
                }
            });
            this.addRenderableWidget(invBtn);
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        g.fill(leftPos - 2, topPos - 2, leftPos + boxW + 2, topPos + boxH + 2, 0x77000000);
        g.fillGradient(leftPos, topPos, leftPos + boxW, topPos + boxH, 0xF217130D, 0xF20B0A08);
        g.renderOutline(leftPos, topPos, boxW, boxH, 0xFF3A3020);
        g.renderOutline(leftPos + 1, topPos + 1, boxW - 2, boxH - 2, 0xFFD4AF37);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);

        g.drawString(this.font, "§6♫ МУЗЫКАЛЬНЫЙ ПРОИГРЫВАТЕЛЬ ИСОНОМИИ", leftPos + 10, topPos + 9, 0xFFFFFFFF);
        g.fill(leftPos + 10, topPos + 150, leftPos + boxW - 10, topPos + 151, 0xFF5A4832);
        g.fill(leftPos + 10, topPos + 178, leftPos + boxW - 10, topPos + 179, 0xFF5A4832);

        g.drawString(this.font, "§6АНСАМБЛЬ (СИНХРОННАЯ ИГРА):", leftPos + 10, topPos + 184, 0xFFE0C068);

        if (!MusicNetworking.CLIENT_PENDING_INVITE_FROM.isEmpty()) {
            g.drawString(this.font, "§e" + MusicNetworking.CLIENT_PENDING_INVITE_FROM + " §fзовет сыграть!", leftPos + 10, topPos + 201, 0xFFFFFFFF);
        } else if (!MusicNetworking.CLIENT_ENSEMBLE_PARTNER.isEmpty()) {
            g.drawString(this.font, "§a● В ансамбле с: §e" + MusicNetworking.CLIENT_ENSEMBLE_PARTNER, leftPos + 10, topPos + 201, 0xFFFFFFFF);
        }

        g.drawString(this.font, "§8Колесико мыши — прокрутка списка • Горячая клавиша: [N]", leftPos + 10, topPos + boxH - 13, 0xFF777777);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        List<String> tracks = showingServerArchive ? MusicNetworking.CLIENT_SERVER_ARCHIVE : MidiPlayerEngine.getLocalTracks();
        int maxScroll = Math.max(0, tracks.size() - 5);
        int next = Math.clamp(listScroll - (int) Math.signum(scrollY), 0, maxScroll);
        if (next != listScroll) {
            listScroll = next;
            this.rebuildWidgets();
        }
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
