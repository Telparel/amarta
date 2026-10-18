package com.amarthaloe.data;

import com.amarthaloe.rpg.Faction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;

public class OfflineMailStorage extends SavedData {

    public static class GroupChatInfo {
        public String groupId = "";
        public String title = "";
        public String avatarId = "";
        public String ownerUuid = "";
        public final Set<String> members = new HashSet<>();

        public CompoundTag toTag() {
            CompoundTag t = new CompoundTag();
            t.putString("groupId", groupId);
            t.putString("title", title);
            t.putString("avatarId", avatarId);
            t.putString("ownerUuid", ownerUuid);
            ListTag mList = new ListTag();
            for (String m : members) mList.add(StringTag.valueOf(m));
            t.put("members", mList);
            return t;
        }

        public static GroupChatInfo fromTag(CompoundTag t) {
            GroupChatInfo info = new GroupChatInfo();
            info.groupId = t.getString("groupId");
            info.title = t.getString("title");
            info.avatarId = t.getString("avatarId");
            info.ownerUuid = t.getString("ownerUuid");
            ListTag mList = t.getList("members", Tag.TAG_STRING);
            for (int i = 0; i < mList.size(); i++) info.members.add(mList.getString(i));
            return info;
        }
    }

    public static class GuildInfo {
        public String guildId = "";
        public String name = "";
        public String tag = "";
        public String avatarId = "";
        public String leaderUuid = "";
        public boolean friendlyFire = false;
        public String chatGroupId = "";

        public final Map<String, String> memberRanks = new LinkedHashMap<>();
        public final Set<String> chestAllowed = new HashSet<>();
        public final Set<String> invitedPlayers = new HashSet<>();
        public final SimpleContainer chest = new SimpleContainer(27);

        public static String getRankDisplayName(String rank) {
            return switch (rank) {
                case "LEADER" -> "Лидер";
                case "OFFICER" -> "Офицер";
                case "MENTOR" -> "Ментор";
                default -> "Участник";
            };
        }

        public CompoundTag toTag(HolderLookup.Provider provider) {
            CompoundTag t = new CompoundTag();
            t.putString("guildId", guildId);
            t.putString("name", name);
            t.putString("tag", tag);
            t.putString("avatarId", avatarId);
            t.putString("leaderUuid", leaderUuid);
            t.putBoolean("friendlyFire", friendlyFire);
            t.putString("chatGroupId", chatGroupId);

            CompoundTag ranksTag = new CompoundTag();
            for (Map.Entry<String, String> e : memberRanks.entrySet()) {
                ranksTag.putString(e.getKey(), e.getValue());
            }
            t.put("memberRanks", ranksTag);

            ListTag chestList = new ListTag();
            for (String u : chestAllowed) chestList.add(StringTag.valueOf(u));
            t.put("chestAllowed", chestList);

            ListTag invList = new ListTag();
            for (String u : invitedPlayers) invList.add(StringTag.valueOf(u));
            t.put("invitedPlayers", invList);

            NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);
            for (int i = 0; i < 27; i++) items.set(i, chest.getItem(i));
            CompoundTag invTag = new CompoundTag();
            ContainerHelper.saveAllItems(invTag, items, provider);
            t.put("chestInv", invTag);

            return t;
        }

        public static GuildInfo fromTag(CompoundTag t, HolderLookup.Provider provider) {
            GuildInfo g = new GuildInfo();
            g.guildId = t.getString("guildId");
            g.name = t.getString("name");
            g.tag = t.getString("tag");
            g.avatarId = t.getString("avatarId");
            g.leaderUuid = t.getString("leaderUuid");
            g.friendlyFire = t.getBoolean("friendlyFire");
            g.chatGroupId = t.getString("chatGroupId");

            if (t.contains("memberRanks")) {
                CompoundTag ranksTag = t.getCompound("memberRanks");
                for (String key : ranksTag.getAllKeys()) {
                    g.memberRanks.put(key, ranksTag.getString(key));
                }
            }
            if (t.contains("chestAllowed")) {
                ListTag list = t.getList("chestAllowed", Tag.TAG_STRING);
                for (int i = 0; i < list.size(); i++) g.chestAllowed.add(list.getString(i));
            }
            if (t.contains("invitedPlayers")) {
                ListTag list = t.getList("invitedPlayers", Tag.TAG_STRING);
                for (int i = 0; i < list.size(); i++) g.invitedPlayers.add(list.getString(i));
            }
            if (t.contains("chestInv")) {
                NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);
                ContainerHelper.loadAllItems(t.getCompound("chestInv"), items, provider);
                for (int i = 0; i < 27; i++) g.chest.setItem(i, items.get(i));
            }
            return g;
        }
    }

    public static class BoardNotice {
        public String id = "";
        public boolean isPlayerNote = false;
        public String authorName = "";
        public String authorUuid = "";
        public String factionId = "";
        public String title = "";
        public String description = "";
        public String questType = "";
        public String targetId = "";
        public String targetDisplay = "";
        public int requiredCount = 1;
        public int difficulty = 1;
        public int rewardRep = 10;
        public int rewardNomes = 25;
        public int boardX = 40;
        public int boardY = 40;

        public CompoundTag toTag() {
            CompoundTag t = new CompoundTag();
            t.putString("id", id);
            t.putBoolean("isPlayerNote", isPlayerNote);
            t.putString("authorName", authorName);
            t.putString("authorUuid", authorUuid);
            t.putString("factionId", factionId);
            t.putString("title", title);
            t.putString("description", description);
            t.putString("questType", questType);
            t.putString("targetId", targetId);
            t.putString("targetDisplay", targetDisplay);
            t.putInt("requiredCount", requiredCount);
            t.putInt("difficulty", difficulty);
            t.putInt("rewardRep", rewardRep);
            t.putInt("rewardNomes", rewardNomes);
            t.putInt("boardX", boardX);
            t.putInt("boardY", boardY);
            return t;
        }

        public static BoardNotice fromTag(CompoundTag t) {
            BoardNotice b = new BoardNotice();
            b.id = t.getString("id");
            b.isPlayerNote = t.getBoolean("isPlayerNote");
            b.authorName = t.getString("authorName");
            b.authorUuid = t.getString("authorUuid");
            b.factionId = t.getString("factionId");
            b.title = t.getString("title");
            b.description = t.getString("description");
            b.questType = t.getString("questType");
            b.targetId = t.getString("targetId");
            b.targetDisplay = t.getString("targetDisplay");
            b.requiredCount = t.getInt("requiredCount");
            b.difficulty = t.getInt("difficulty");
            b.rewardRep = t.getInt("rewardRep");
            b.rewardNomes = t.getInt("rewardNomes");
            b.boardX = t.getInt("boardX");
            b.boardY = t.getInt("boardY");
            return b;
        }
    }

    private static final Map<Faction, String[]> NPC_NAMES = Map.of(
        Faction.LOWER_CITY, new String[]{
            "Хромой Игнат (Подвал №4)", "Тётя Зина с Чёрного Рынка", "Старший по Блоку-9 Костыль", "Старьёвщик Макар", "Семён из Теплоузла"
        },
        Faction.MIDDLE_CLASS, new String[]{
            "Бригадир Завода №12 Громов", "Диспетчер ЖЭК-4 Валентина", "Мастер смены Кузьмич", "Завскладом Пайков Петрович", "Техник-электрик Ларионов"
        },
        Faction.INTELLIGENTSIA, new String[]{
            "Старший Архивариус Верещагин", "Лаборант НИИ-7 Софья", "Конструктор Госплана Эдуард", "Кафедра Баланса (Проф. Левин)", "Редактор Бюллетеня Исономии"
        },
        Faction.GOVERNMENT, new String[]{
            "Ревизор Весов Август Вейн", "Канцелярия Барона Либры", "Судебный Исполнитель Протокола", "Секретарь Комитета Равенства", "Комендант Периметра Гектор"
        }
    );

    private record QuestTemplate(Faction faction, String title, String desc, String type, String targetId, String targetDisplay, int count, int diff, int rep, int nomes) {}

    // УСЛОЖНЕННЫЙ ПУЛ КОНТРАКТОВ ИСОНОМИИ (1..3 ЗВЕЗДЫ)
    private static final List<QuestTemplate> QUEST_POOL = List.of(
        // --- НИЖНИЙ ГОРОД ---
        new QuestTemplate(Faction.LOWER_CITY, "Тотальная зачистка коллекторов",
            "В коллекторах под жилым массивом прорвало старую дамбу, и оттуда хлынули орды мертвецов. Перебей не менее тридцати тварей, иначе подвалы не отстоять.",
            "KILL", "minecraft:zombie", "Убить Зомби", 30, 1, 15, 45),
        new QuestTemplate(Faction.LOWER_CITY, "Оптовая поставка для трущоб",
            "Голод в нижних ярусах усиливается. Нам нужна крупная партия органики и плоти для переработки на технический жир и приманки.",
            "GATHER", "minecraft:rotten_flesh", "Сдать Гнилую плоть", 64, 1, 15, 40),
        new QuestTemplate(Faction.LOWER_CITY, "Дальний рейд контрабандистов",
            "Нужно протоптать безопасный обходной маршрут вокруг блокпостов Наместников. Предстоит долгий пеший переход по пустошам.",
            "VISIT", "patrol_blocks", "Пройти блоков по миру", 2500, 2, 25, 70),
        new QuestTemplate(Faction.LOWER_CITY, "Охота на болотных ведьм",
            "В заброшенных хрущёвках на окраине обосновались ведьмы — травят колодцы и пугают старьёвщиков. Ликвидируй их всех до единой.",
            "KILL", "minecraft:witch", "Убить Ведьм", 10, 3, 45, 130),

        // --- СРЕДНИЙ КЛАСС ---
        new QuestTemplate(Faction.MIDDLE_CLASS, "Двойная норма угля для ТЭЦ",
            "ТЭЦ-2 работает на пределе износа! Чтобы не заморозить спальные районы Исономии, требуется сдать два полных стака каменного угля.",
            "GATHER", "minecraft:coal", "Сдать Уголь", 128, 1, 15, 50),
        new QuestTemplate(Faction.MIDDLE_CLASS, "Тяжелый металл для Пятилетки",
            "Литейный цех остановил конвейер из-за нехватки сырья. Требуется крупная партия железных слитков для выполнения квартального плана.",
            "GATHER", "minecraft:iron_ingot", "Сдать Железные слитки", 64, 2, 30, 85),
        new QuestTemplate(Faction.MIDDLE_CLASS, "Дезинсекция вентиляционных шахт",
            "Шахты метрополитена и старых заводов кишат гигантскими пауками. Рабочие отказываются выходить в ночную смену. Зачистите гнезда!",
            "KILL", "minecraft:spider", "Убить Пауков", 35, 2, 25, 75),
        new QuestTemplate(Faction.MIDDLE_CLASS, "Алмазные резцы для буровых",
            "Для проходческих щитов метростроя Исономии срочно требуются сверхпрочные алмазы. Контракт высокой сложности, оплата по высшему разряду.",
            "GATHER", "minecraft:diamond", "Сдать Алмазы", 16, 3, 50, 150),

        // --- ИНТЕЛЛИГЕНЦИЯ ---
        new QuestTemplate(Faction.INTELLIGENTSIA, "Массовый сбор костных образцов",
            "Отдел радиологии НИИ-7 проводит масштабное исследование мутаций. Требуется уничтожить крупную популяцию скелетов в окрестностях.",
            "KILL", "minecraft:skeleton", "Убить Скелетов", 40, 2, 30, 80),
        new QuestTemplate(Faction.INTELLIGENTSIA, "Партия меди для главного Архива",
            "Для модернизации телеграфной и вычислительной сети Исономии необходима полная партия очищенных медных слитков.",
            "GATHER", "minecraft:copper_ingot", "Сдать Медные слитки", 64, 1, 15, 50),
        new QuestTemplate(Faction.INTELLIGENTSIA, "Золотые контакты для ЭВМ «Весы»",
            "Высокоточные реле центральной ЭВМ требуют некорродирующего металла. Принесите 32 золотых слитка в лабораторию Госплана.",
            "GATHER", "minecraft:gold_ingot", "Сдать Золотые слитки", 32, 2, 30, 90),
        new QuestTemplate(Faction.INTELLIGENTSIA, "Изучение пространственных аномалий",
            "В районе разрушенных НИИ замечены странники Края (Эндермены), искажающие метрику пространства. Устраните 20 особей для стабилизации фона.",
            "KILL", "minecraft:enderman", "Убить Эндерменов", 20, 3, 50, 140),

        // --- ПРАВИТЕЛЬСТВО (НАМЕСТНИКИ ЛИБРЫ) ---
        new QuestTemplate(Faction.GOVERNMENT, "Особый Протокол: Подрывники",
            "Диверсионная угроза! Скопления Криперов подрывают опоры ЛЭП и муниципальные стены. Барон Либра требует уничтожить 30 особей.",
            "KILL", "minecraft:creeper", "Убить Криперов", 30, 3, 50, 145),
        new QuestTemplate(Faction.GOVERNMENT, "Государственный зерновой резерв",
            "Во исполнение Декрета о Равенстве Пайков каждый подрядчик обязан сдать крупную партию свежего хлеба в распределитель Наместников.",
            "GATHER", "minecraft:bread", "Сдать Хлеб", 64, 2, 25, 80),
        new QuestTemplate(Faction.GOVERNMENT, "Валютная ревизия Контрактов",
            "Канцелярия Барона Контрактов изымает из теневого оборота драгоценные камни. Сдайте партию изумрудов в казну Юрисдикции.",
            "GATHER", "minecraft:emerald", "Сдать Изумруды", 48, 3, 55, 160),
        new QuestTemplate(Faction.GOVERNMENT, "Большой инспекционный марш-бросок",
            "Проведите полный пеший дозор дальних рубежей Исономии. Только безупречная выносливость докажет вашу верность Закону Равенства.",
            "VISIT", "patrol_blocks", "Пройти блоков в патруле", 5000, 3, 60, 170)
    );

    private final Map<String, List<String>> chatHistories = new HashMap<>();
    private final Map<String, Map<String, Integer>> unreadCounts = new HashMap<>();
    private final Map<String, byte[]> images = new HashMap<>();
    private final Map<String, GroupChatInfo> groupChats = new HashMap<>();
    private final Map<String, GuildInfo> guilds = new HashMap<>();
    private final Map<String, BoardNotice> boardNotices = new LinkedHashMap<>();

    public Map<String, BoardNotice> getBoardNotices() {
        return boardNotices;
    }

    public void ensureNpcQuestsPopulated() {
        // Удаляем старые слишком легкие квесты (где требовалось меньше 10 единиц, кроме редких 3-звездочных)
        boardNotices.values().removeIf(n -> !n.isPlayerNote && n.requiredCount < 10);

        long npcCount = boardNotices.values().stream().filter(n -> !n.isPlayerNote).count();
        Random rand = new Random();
        while (npcCount < 10) {
            spawnRandomNpcQuest(rand);
            npcCount++;
        }
    }

    public BoardNotice spawnRandomNpcQuest(Random rand) {
        QuestTemplate tpl = QUEST_POOL.get(rand.nextInt(QUEST_POOL.size()));
        String[] names = NPC_NAMES.get(tpl.faction());
        String npcName = names[rand.nextInt(names.length)];

        BoardNotice n = new BoardNotice();
        n.id = "q_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        n.isPlayerNote = false;
        n.authorName = npcName;
        n.factionId = tpl.faction().id;
        n.title = tpl.title();
        n.description = tpl.desc();
        n.questType = tpl.type();
        n.targetId = tpl.targetId();
        n.targetDisplay = tpl.targetDisplay();
        n.requiredCount = tpl.count();
        n.difficulty = tpl.diff();
        n.rewardRep = tpl.rep();
        n.rewardNomes = tpl.nomes();

        findFreeBoardPosition(n, rand);
        boardNotices.put(n.id, n);
        this.setDirty();
        return n;
    }

    public BoardNotice createPlayerLeaflet(String authorUuid, String authorName, String title, String content) {
        Random rand = new Random();
        BoardNotice n = new BoardNotice();
        n.id = "note_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        n.isPlayerNote = true;
        n.authorUuid = authorUuid;
        n.authorName = authorName;
        n.title = title;
        n.description = content;
        n.questType = "NOTE";
        findFreeBoardPosition(n, rand);
        boardNotices.put(n.id, n);
        this.setDirty();
        return n;
    }

    private void findFreeBoardPosition(BoardNotice notice, Random rand) {
        for (int attempt = 0; attempt < 40; attempt++) {
            int x = 25 + rand.nextInt(700);
            int y = 25 + rand.nextInt(420);
            boolean collision = false;
            for (BoardNotice other : boardNotices.values()) {
                if (Math.abs(other.boardX - x) < 105 && Math.abs(other.boardY - y) < 75) {
                    collision = true;
                    break;
                }
            }
            if (!collision) {
                notice.boardX = x;
                notice.boardY = y;
                return;
            }
        }
        notice.boardX = 30 + rand.nextInt(700);
        notice.boardY = 30 + rand.nextInt(420);
    }

    public static String getConversationKey(String uuid1, String uuid2) {
        if (uuid1.startsWith("group_")) return uuid1;
        if (uuid2.startsWith("group_")) return uuid2;
        return uuid1.compareTo(uuid2) < 0 ? uuid1 + "|" + uuid2 : uuid2 + "|" + uuid1;
    }

    public Map<String, GuildInfo> getGuilds() {
        return guilds;
    }

    public GuildInfo getPlayerGuild(String playerUuid) {
        for (GuildInfo g : guilds.values()) {
            if (g.memberRanks.containsKey(playerUuid)) return g;
        }
        return null;
    }

    public GuildInfo getPendingGuildInvite(String playerUuid) {
        for (GuildInfo g : guilds.values()) {
            if (g.invitedPlayers.contains(playerUuid)) return g;
        }
        return null;
    }

    public void saveImage(String imageId, byte[] bytes) {
        if (bytes.length <= 2 * 1024 * 1024 + 512 * 1024) {
            images.put(imageId, bytes);
            this.setDirty();
        }
    }

    public byte[] getImage(String imageId) {
        return images.get(imageId);
    }

    public Map<String, GroupChatInfo> getGroupChats() {
        return groupChats;
    }

    public List<GroupChatInfo> getGroupsForPlayer(String playerUuid) {
        List<GroupChatInfo> res = new ArrayList<>();
        for (GroupChatInfo g : groupChats.values()) {
            if (g.members.contains(playerUuid)) res.add(g);
        }
        return res;
    }

    public void createOrUpdateGroup(GroupChatInfo group) {
        groupChats.put(group.groupId, group);
        this.setDirty();
    }

    public void addMessage(String senderUuid, String targetUuid, String formattedLine, boolean targetIsOnline) {
        String key = getConversationKey(senderUuid, targetUuid);
        List<String> list = chatHistories.computeIfAbsent(key, k -> new ArrayList<>());
        list.add(formattedLine);
        if (list.size() > 60) {
            list.remove(0);
        }

        if (!targetIsOnline) {
            String unreadKey = senderUuid.startsWith("group_") ? senderUuid : (targetUuid.startsWith("group_") ? targetUuid : senderUuid);
            Map<String, Integer> targetUnread = unreadCounts.computeIfAbsent(targetUuid, k -> new HashMap<>());
            targetUnread.put(unreadKey, targetUnread.getOrDefault(unreadKey, 0) + 1);
        }
        this.setDirty();
    }

    public void incrementUnread(String recipientUuid, String chatKey) {
        Map<String, Integer> targetUnread = unreadCounts.computeIfAbsent(recipientUuid, k -> new HashMap<>());
        targetUnread.put(chatKey, targetUnread.getOrDefault(chatKey, 0) + 1);
        this.setDirty();
    }

    public List<String> getHistory(String uuid1, String uuid2) {
        return chatHistories.getOrDefault(getConversationKey(uuid1, uuid2), Collections.emptyList());
    }

    public Map<String, Integer> getUnreadForPlayer(String playerUuid) {
        return unreadCounts.getOrDefault(playerUuid, Collections.emptyMap());
    }

    public int getTotalUnread(String playerUuid) {
        int sum = 0;
        for (int count : getUnreadForPlayer(playerUuid).values()) sum += count;
        return sum;
    }

    public void markAsRead(String playerUuid, String partnerUuid) {
        if (unreadCounts.containsKey(playerUuid)) {
            if (unreadCounts.get(playerUuid).remove(partnerUuid) != null) {
                this.setDirty();
            }
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        CompoundTag chatsTag = new CompoundTag();
        for (Map.Entry<String, List<String>> entry : chatHistories.entrySet()) {
            ListTag list = new ListTag();
            for (String msg : entry.getValue()) list.add(StringTag.valueOf(msg));
            chatsTag.put(entry.getKey(), list);
        }
        tag.put("chatHistories", chatsTag);

        CompoundTag unreadTag = new CompoundTag();
        for (Map.Entry<String, Map<String, Integer>> entry : unreadCounts.entrySet()) {
            CompoundTag pTag = new CompoundTag();
            for (Map.Entry<String, Integer> sub : entry.getValue().entrySet()) {
                pTag.putInt(sub.getKey(), sub.getValue());
            }
            unreadTag.put(entry.getKey(), pTag);
        }
        tag.put("unreadCounts", unreadTag);

        CompoundTag imagesTag = new CompoundTag();
        for (Map.Entry<String, byte[]> entry : images.entrySet()) {
            imagesTag.putByteArray(entry.getKey(), entry.getValue());
        }
        tag.put("images", imagesTag);

        CompoundTag groupsTag = new CompoundTag();
        for (Map.Entry<String, GroupChatInfo> entry : groupChats.entrySet()) {
            groupsTag.put(entry.getKey(), entry.getValue().toTag());
        }
        tag.put("groupChats", groupsTag);

        CompoundTag guildsTag = new CompoundTag();
        for (Map.Entry<String, GuildInfo> entry : guilds.entrySet()) {
            guildsTag.put(entry.getKey(), entry.getValue().toTag(provider));
        }
        tag.put("guilds", guildsTag);

        ListTag boardList = new ListTag();
        for (BoardNotice n : boardNotices.values()) {
            boardList.add(n.toTag());
        }
        tag.put("boardNotices", boardList);

        return tag;
    }

    public static OfflineMailStorage load(CompoundTag tag, HolderLookup.Provider provider) {
        OfflineMailStorage storage = new OfflineMailStorage();
        if (tag.contains("chatHistories")) {
            CompoundTag chatsTag = tag.getCompound("chatHistories");
            for (String key : chatsTag.getAllKeys()) {
                ListTag list = chatsTag.getList(key, Tag.TAG_STRING);
                List<String> history = new ArrayList<>();
                for (int i = 0; i < list.size(); i++) history.add(list.getString(i));
                storage.chatHistories.put(key, history);
            }
        }
        if (tag.contains("unreadCounts")) {
            CompoundTag unreadTag = tag.getCompound("unreadCounts");
            for (String playerUuid : unreadTag.getAllKeys()) {
                CompoundTag pTag = unreadTag.getCompound(playerUuid);
                Map<String, Integer> map = new HashMap<>();
                for (String senderUuid : pTag.getAllKeys()) map.put(senderUuid, pTag.getInt(senderUuid));
                storage.unreadCounts.put(playerUuid, map);
            }
        }
        if (tag.contains("images")) {
            CompoundTag imagesTag = tag.getCompound("images");
            for (String key : imagesTag.getAllKeys()) {
                storage.images.put(key, imagesTag.getByteArray(key));
            }
        }
        if (tag.contains("groupChats")) {
            CompoundTag groupsTag = tag.getCompound("groupChats");
            for (String key : groupsTag.getAllKeys()) {
                storage.groupChats.put(key, GroupChatInfo.fromTag(groupsTag.getCompound(key)));
            }
        }
        if (tag.contains("guilds")) {
            CompoundTag guildsTag = tag.getCompound("guilds");
            for (String key : guildsTag.getAllKeys()) {
                storage.guilds.put(key, GuildInfo.fromTag(guildsTag.getCompound(key), provider));
            }
        }
        if (tag.contains("boardNotices")) {
            ListTag bList = tag.getList("boardNotices", Tag.TAG_COMPOUND);
            for (int i = 0; i < bList.size(); i++) {
                BoardNotice n = BoardNotice.fromTag(bList.getCompound(i));
                storage.boardNotices.put(n.id, n);
            }
        }
        storage.ensureNpcQuestsPopulated();
        return storage;
    }

    public static OfflineMailStorage get(MinecraftServer server) {
        OfflineMailStorage s = server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(OfflineMailStorage::new, OfflineMailStorage::load),
                "amarthaloe_mail"
        );
        s.ensureNpcQuestsPopulated();
        return s;
    }
}
