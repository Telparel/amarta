package com.amarthaloe.music;

import com.amarthaloe.client.gui.DarkButton;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

public class AmarthaloeMusicScreen extends Screen {

    private final int boxW = 350;
    private final int boxH = 242;
    private int leftPos;
    private int topPos;

    // 0 = Мои мелодии, 1 = Архив сервера, 2 = Радио
    private int activeTab = 0;
    private int listScroll = 0;

    private EditBox invitePlayerBox;
    private EditBox radioFreqBox;
    private EditBox radioNameBox;
    private EditBox radioUrlBox;
    private EditBox radioShareBox;

    public AmarthaloeMusicScreen() {
        super(Component.literal("Музыка и Радио Исономии"));
    }

    @Override
    protected void init() {
        super.init();
        this.leftPos = (this.width - this.boxW) / 2;
        this.topPos = (this.height - this.boxH) / 2;

        PacketDistributor.sendToServer(new MusicNetworking.MusicActionPayload("REQ_ARCHIVE", "", ""));

        // 3 основные вкладки
        DarkButton localTab = new DarkButton(leftPos + 10, topPos + 24, 86, 18, Component.literal("Мои мелодии"), (b) -> {
            activeTab = 0;
            listScroll = 0;
            this.rebuildWidgets();
        });
        localTab.setSelected(activeTab == 0);

        DarkButton archiveTab = new DarkButton(leftPos + 99, topPos + 24, 88, 18, Component.literal("Архив"), (b) -> {
            activeTab = 1;
            listScroll = 0;
            this.rebuildWidgets();
        });
        archiveTab.setSelected(activeTab == 1);

        DarkButton radioTab = new DarkButton(leftPos + 190, topPos + 24, 62, 18, Component.literal("📻 Радио"), (b) -> {
            activeTab = 2;
            listScroll = 0;
            this.rebuildWidgets();
        });
        radioTab.setSelected(activeTab == 2);

        this.addRenderableWidget(localTab);
        this.addRenderableWidget(archiveTab);
        this.addRenderableWidget(radioTab);

        if (activeTab != 2) {
            DarkButton uploadDiskBtn = new DarkButton(leftPos + 256, topPos + 24, 84, 18, Component.literal("+ .mid файл"), (b) -> {
                String added = MidiPlayerEngine.pickMidiFromDisk();
                if (added != null) {
                    activeTab = 0;
                    MidiPlayerEngine.loadMidiFile(added);
                    this.rebuildWidgets();
                }
            });
            this.addRenderableWidget(uploadDiskBtn);
            initMidiTab();
        } else {
            initRadioTab();
        }
    }

    private void initMidiTab() {
        boolean isArchive = (activeTab == 1);
        List<String> tracks = isArchive ? MusicNetworking.CLIENT_SERVER_ARCHIVE : MidiPlayerEngine.getLocalTracks();
        int maxRows = 5;
        listScroll = Math.clamp(listScroll, 0, Math.max(0, tracks.size() - maxRows));

        for (int i = 0; i < maxRows && (listScroll + i) < tracks.size(); i++) {
            String trackName = tracks.get(listScroll + i);
            int rowY = topPos + 48 + i * 20;

            String display = trackName.length() > 27 ? trackName.substring(0, 27) + "..." : trackName;
            DarkButton selectBtn = new DarkButton(leftPos + 10, rowY, 248, 18, Component.literal(display), (b) -> {
                if (!isArchive) {
                    MidiPlayerEngine.loadMidiFile(trackName);
                    this.rebuildWidgets();
                } else {
                    PacketDistributor.sendToServer(new MusicNetworking.MusicActionPayload("DOWNLOAD_TRACK", trackName, ""));
                }
            });
            selectBtn.setSelected(!isArchive && trackName.equals(MidiPlayerEngine.selectedFileName));
            this.addRenderableWidget(selectBtn);

            if (!isArchive) {
                this.addRenderableWidget(new DarkButton(leftPos + 262, rowY, 78, 18, Component.literal("↑ В архив"), (b) -> {
                    MidiPlayerEngine.uploadTrackToServer(trackName);
                }));
            } else {
                this.addRenderableWidget(new DarkButton(leftPos + 262, rowY, 78, 18, Component.literal("↓ Скачать"), (b) -> {
                    PacketDistributor.sendToServer(new MusicNetworking.MusicActionPayload("DOWNLOAD_TRACK", trackName, ""));
                }));
            }
        }

        int ctrlY = topPos + 154;
        String instName = MidiPlayerEngine.INSTRUMENTS[MidiPlayerEngine.selectedInstrument];
        this.addRenderableWidget(new DarkButton(leftPos + 10, ctrlY, 112, 18, Component.literal("Инстр.: §e" + instName), (b) -> {
            MidiPlayerEngine.selectedInstrument = (MidiPlayerEngine.selectedInstrument + 1) % MidiPlayerEngine.INSTRUMENTS.length;
            this.rebuildWidgets();
        }));

        String trackLabel = MidiPlayerEngine.selectedTrackFilter == -1 ? "Все" : "#" + (MidiPlayerEngine.selectedTrackFilter + 1);
        this.addRenderableWidget(new DarkButton(leftPos + 126, ctrlY, 98, 18, Component.literal("Дорожка: §e" + trackLabel), (b) -> {
            if (MidiPlayerEngine.loadedTrackCount > 0) {
                MidiPlayerEngine.selectedTrackFilter++;
                if (MidiPlayerEngine.selectedTrackFilter >= MidiPlayerEngine.loadedTrackCount) {
                    MidiPlayerEngine.selectedTrackFilter = -1;
                }
                this.rebuildWidgets();
            }
        }));

        this.addRenderableWidget(new DarkButton(leftPos + 228, ctrlY, 112, 18,
                Component.literal(MidiPlayerEngine.isPlaying ? "§c■ Стоп" : "§a▶ Играть"), (b) -> {
            if (MidiPlayerEngine.isPlaying) {
                PacketDistributor.sendToServer(new MusicNetworking.MusicActionPayload("ENSEMBLE_STOP", "", ""));
            } else {
                PacketDistributor.sendToServer(new MusicNetworking.MusicActionPayload("ENSEMBLE_START", MidiPlayerEngine.selectedFileName, ""));
            }
            this.rebuildWidgets();
        }));

        int ensY = topPos + 196;
        if (!MusicNetworking.CLIENT_PENDING_INVITE_FROM.isEmpty()) {
            this.addRenderableWidget(new DarkButton(leftPos + 196, ensY, 144, 18, Component.literal("§a✔ Принять ансамбль"), (b) -> {
                PacketDistributor.sendToServer(new MusicNetworking.MusicActionPayload("ENSEMBLE_ACCEPT", "", ""));
                MusicNetworking.CLIENT_PENDING_INVITE_FROM = "";
                this.rebuildWidgets();
            }));
        } else if (!MusicNetworking.CLIENT_ENSEMBLE_PARTNER.isEmpty()) {
            this.addRenderableWidget(new DarkButton(leftPos + 228, ensY, 112, 18, Component.literal("§cПокинуть"), (b) -> {
                PacketDistributor.sendToServer(new MusicNetworking.MusicActionPayload("ENSEMBLE_LEAVE", "", ""));
                MusicNetworking.CLIENT_ENSEMBLE_PARTNER = "";
                this.rebuildWidgets();
            }));
        } else {
            invitePlayerBox = new EditBox(this.font, leftPos + 10, ensY, 214, 18, Component.empty());
            invitePlayerBox.setMaxLength(32);
            invitePlayerBox.setHint(Component.literal("Ник или РП-имя для игры вместе...").withStyle(s -> s.withColor(0x555555)));
            this.addRenderableWidget(invitePlayerBox);

            this.addRenderableWidget(new DarkButton(leftPos + 228, ensY, 112, 18, Component.literal("♫ Пригласить"), (b) -> {
                String target = invitePlayerBox.getValue().trim();
                if (!target.isEmpty()) {
                    PacketDistributor.sendToServer(new MusicNetworking.MusicActionPayload("ENSEMBLE_INVITE", target, ""));
                    invitePlayerBox.setValue("");
                }
            }));
        }
    }

    private void initRadioTab() {
        // Громкость и кнопка выключения в верхней строке справа
        this.addRenderableWidget(new DarkButton(leftPos + 256, topPos + 24, 22, 18, Component.literal("-"), (b) -> {
            RadioPlayerEngine.adjustVolume(-0.1f);
            this.rebuildWidgets();
        }));
        this.addRenderableWidget(new DarkButton(leftPos + 280, topPos + 24, 22, 18, Component.literal("+"), (b) -> {
            RadioPlayerEngine.adjustVolume(0.1f);
            this.rebuildWidgets();
        }));
        this.addRenderableWidget(new DarkButton(leftPos + 304, topPos + 24, 36, 18, Component.literal("§c■"), (b) -> {
            RadioPlayerEngine.stop();
            this.rebuildWidgets();
        }));

        // Список радиостанций (до 4 строк)
        List<RadioPlayerEngine.RadioStation> stations = RadioPlayerEngine.getAllStations();
        int maxRows = 4;
        listScroll = Math.clamp(listScroll, 0, Math.max(0, stations.size() - maxRows));

        for (int i = 0; i < maxRows && (listScroll + i) < stations.size(); i++) {
            RadioPlayerEngine.RadioStation st = stations.get(listScroll + i);
            int rowY = topPos + 48 + i * 20;

            String prefix = st.isCustom() ? "§b★ " : "§6";
            String label = prefix + st.freq() + " FM §f— " + st.name();
            int btnW = st.isCustom() ? 298 : 330;

            DarkButton playBtn = new DarkButton(leftPos + 10, rowY, btnW, 18, Component.literal(label), (b) -> {
                RadioPlayerEngine.playStation(st.freq(), st.name(), st.url());
                this.rebuildWidgets();
            });
            playBtn.setSelected(RadioPlayerEngine.isPlaying && st.url().equals(RadioPlayerEngine.currentUrl));
            this.addRenderableWidget(playBtn);

            if (st.isCustom()) {
                this.addRenderableWidget(new DarkButton(leftPos + 312, rowY, 28, 18, Component.literal("§c✖"), (b) -> {
                    RadioPlayerEngine.removeCustomStation(st.freq());
                    this.rebuildWidgets();
                }));
            }
        }

        // Форма добавления своего радиопотока
        int addY = topPos + 148;
        radioFreqBox = new EditBox(this.font, leftPos + 10, addY, 52, 16, Component.empty());
        radioFreqBox.setMaxLength(8);
        radioFreqBox.setHint(Component.literal("99.5").withStyle(s -> s.withColor(0x555555)));

        radioNameBox = new EditBox(this.font, leftPos + 66, addY, 100, 16, Component.empty());
        radioNameBox.setMaxLength(24);
        radioNameBox.setHint(Component.literal("Название").withStyle(s -> s.withColor(0x555555)));

        radioUrlBox = new EditBox(this.font, leftPos + 170, addY, 118, 16, Component.empty());
        radioUrlBox.setMaxLength(256);
        radioUrlBox.setHint(Component.literal("http://...mp3").withStyle(s -> s.withColor(0x555555)));

        DarkButton saveRadioBtn = new DarkButton(leftPos + 292, addY - 1, 48, 18, Component.literal("§a+ Сохр."), (b) -> {
            String f = radioFreqBox.getValue().trim();
            String n = radioNameBox.getValue().trim();
            String u = radioUrlBox.getValue().trim();
            if (!f.isEmpty() && !n.isEmpty() && u.startsWith("http")) {
                RadioPlayerEngine.addCustomStation(f, n, u);
                RadioPlayerEngine.playStation(f, n, u);
                this.rebuildWidgets();
            }
        });

        this.addRenderableWidget(radioFreqBox);
        this.addRenderableWidget(radioNameBox);
        this.addRenderableWidget(radioUrlBox);
        this.addRenderableWidget(saveRadioBtn);

        // Поделиться текущей волной с игроком или ансамблем
        int shareY = topPos + 196;
        radioShareBox = new EditBox(this.font, leftPos + 10, shareY, 214, 18, Component.empty());
        radioShareBox.setMaxLength(32);
        radioShareBox.setHint(Component.literal("Ник игрока (или пусто для ансамбля)").withStyle(s -> s.withColor(0x555555)));
        this.addRenderableWidget(radioShareBox);

        this.addRenderableWidget(new DarkButton(leftPos + 228, shareY, 112, 18, Component.literal("📻 Поделиться"), (b) -> {
            if (!RadioPlayerEngine.currentUrl.isEmpty()) {
                String payload = RadioPlayerEngine.currentFreq + "|" + RadioPlayerEngine.currentName + "|" + RadioPlayerEngine.currentUrl;
                PacketDistributor.sendToServer(new MusicNetworking.MusicActionPayload("RADIO_SHARE", radioShareBox.getValue().trim(), payload));
                radioShareBox.setValue("");
            }
        }));
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

        g.drawString(this.font, "§6♫ ПРОИГРЫВАТЕЛЬ И РАДИО ИСОНОМИИ", leftPos + 10, topPos + 9, 0xFFFFFFFF);

        if (activeTab != 2) {
            g.fill(leftPos + 10, topPos + 150, leftPos + boxW - 10, topPos + 151, 0xFF5A4832);
            g.fill(leftPos + 10, topPos + 178, leftPos + boxW - 10, topPos + 179, 0xFF5A4832);
            g.drawString(this.font, "§6АНСАМБЛЬ (СИНХРОННАЯ ИГРА):", leftPos + 10, topPos + 184, 0xFFE0C068);

            if (!MusicNetworking.CLIENT_PENDING_INVITE_FROM.isEmpty()) {
                g.drawString(this.font, "§e" + MusicNetworking.CLIENT_PENDING_INVITE_FROM + " §fзовет сыграть!", leftPos + 10, topPos + 201, 0xFFFFFFFF);
            } else if (!MusicNetworking.CLIENT_ENSEMBLE_PARTNER.isEmpty()) {
                g.drawString(this.font, "§a● В ансамбле с: §e" + MusicNetworking.CLIENT_ENSEMBLE_PARTNER, leftPos + 10, topPos + 201, 0xFFFFFFFF);
            }
        } else {
            g.fill(leftPos + 10, topPos + 132, leftPos + boxW - 10, topPos + 133, 0xFF5A4832);
            int volPct = Math.round(RadioPlayerEngine.volume * 100);
            String nowPlaying = RadioPlayerEngine.isPlaying
                    ? "§a● " + RadioPlayerEngine.currentFreq + " FM «" + RadioPlayerEngine.currentName + "» §7(" + RadioPlayerEngine.statusText + ")"
                    : "§7○ Эфир выключен";
            g.drawString(this.font, nowPlaying + " §8| Громк.: §e" + volPct + "%", leftPos + 10, topPos + 136, 0xFFFFFFFF);

            g.fill(leftPos + 10, topPos + 174, leftPos + boxW - 10, topPos + 175, 0xFF5A4832);
            g.drawString(this.font, "§6ПОДЕЛИТЬСЯ ТЕКУЩЕЙ ВОЛНОЙ:", leftPos + 10, topPos + 182, 0xFFE0C068);
        }

        g.drawString(this.font, "§8Колесико мыши — прокрутка списка • Горячая клавиша: [N]", leftPos + 10, topPos + boxH - 13, 0xFF777777);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int size = (activeTab == 2)
                ? RadioPlayerEngine.getAllStations().size()
                : (activeTab == 1 ? MusicNetworking.CLIENT_SERVER_ARCHIVE.size() : MidiPlayerEngine.getLocalTracks().size());
        int maxRows = (activeTab == 2) ? 4 : 5;
        int maxScroll = Math.max(0, size - maxRows);
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
