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

    // --- СВИТОК ЗАКАЗА ИЛИ ЛИСТОВКА ИГРОКА НА ДОСКЕ ОБЪЯВЛЕНИЙ ---
    public static class BoardNotice {
        public String id = "";
        public boolean isPlayerNote = false;
        public String authorName = "";
        public String authorUuid = "";
        public String factionId = "";      // lower_city, middle_class, intelligentsia, government
        public String title = "";
        public String description = "";
        public String questType = "";      // KILL, GATHER, VISIT, NOTE
        public String targetId = "";       // minecraft:zombie, minecraft:iron_ingot, patrol_distance и т.д.
        public String targetDisplay = "";  // Наглядное описание цели
        public int requiredCount = 1;
        public int difficulty = 1;         // 1..3 звезды
        public int rewardRep = 10;
        public int rewardNomes = 25;
        public int boardX = 40;            // Координата X на большом полотне доски (0..740)
        public int boardY = 40;            // Координата Y на большом полотне доски (0..440)

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

    private static final List<QuestTemplate> QUEST_POOL = List.of(
        // НИЖНИЙ ГОРОД
        new QuestTemplate(Faction.LOWER_CITY, "Зачистка затопленных подвалов",
            "В подвалах под пятой хрущёвкой снова расплодились мертвецы. Ребята боятся спускаться за трубами. Упокой тварей — заплатим честно.",
            "KILL", "minecraft:zombie", "Убить Зомби", 6, 1, 15, 35),
        new QuestTemplate(Faction.LOWER_CITY, "Сырьё для подпольной кухни",
            "Пайки от Среднего Класса снова урезали. Принеси свежей (или хотя бы условно съедобной) плоти и костей для варева во дворах.",
            "GATHER", "minecraft:rotten_flesh", "Сдать Гнилую плоть", 16, 1, 12, 25),
        new QuestTemplate(Faction.LOWER_CITY, "Обход дальних пустырей",
            "Нужно проверить старые тропы контрабандистов вокруг жилмассива. Пройди маршрут пешком и убедись, что патрулей нет.",
            "VISIT", "patrol_blocks", "Пройти блоков по миру", 250, 2, 20, 50),

        // СРЕДНИЙ КЛАСС
        new QuestTemplate(Faction.MIDDLE_CLASS, "Сверхплановая норма угля",
            "Котельная ЖЭК-4 на грани остановки! Если батареи в панельках остынут, с нас снимут трудодни. Срочно требуется партия угля.",
            "GATHER", "minecraft:coal", "Сдать Уголь", 20, 1, 15, 40),
        new QuestTemplate(Faction.MIDDLE_CLASS, "Металл для ремонта труб",
            "Магистраль в 3-м микрорайоне дала течь. Комбинату срочно нужны железные слитки для сварки заплаток. Работа сдельная!",
            "GATHER", "minecraft:iron_ingot", "Сдать Железные слитки", 10, 2, 25, 65),
        new QuestTemplate(Faction.MIDDLE_CLASS, "Паутина в вентиляционных шахтах",
            "В шахтах лифтов завелись гигантские пауки, срывают график смены! Истребите паразитов во имя Трудового Равенства.",
            "KILL", "minecraft:spider", "Убить Пауков", 5, 2, 20, 55),

        // ИНТЕЛЛИГЕНЦИЯ
        new QuestTemplate(Faction.INTELLIGENTSIA, "Костные образцы для НИИ-7",
            "Лаборатория биомеханики изучает влияние постапокалиптической радиации на скелетов. Требуется ликвидировать особи для отчета.",
            "KILL", "minecraft:skeleton", "Убить Скелетов", 7, 2, 25, 60),
        new QuestTemplate(Faction.INTELLIGENTSIA, "Медные проводники для ЭВМ",
            "Вычислительная машина Архива перегорела при расчете баланса пайков. Принесите медные слитки для замены контуров.",
            "GATHER", "minecraft:copper_ingot", "Сдать Медные слитки", 14, 1, 15, 45),
        new QuestTemplate(Faction.INTELLIGENTSIA, "Топографическая разведка сектора",
            "Для обновления карт Исономии требуется пеший замер периметра. Пройдите указанную дистанцию с замером координат.",
            "VISIT", "patrol_blocks", "Пройти блоков в разведке", 400, 3, 35, 85),

        // ПРАВИТЕЛЬСТВО (НАМЕСТНИКИ ЛИБРЫ)
        new QuestTemplate(Faction.GOVERNMENT, "Контракт №404: Ликвидация подрывников",
            "Взрывоопасные мутанты (Криперы) угрожают целостности муниципальных построек Либры. Контракт требует их полного устранения.",
            "KILL", "minecraft:creeper", "Убить Криперов", 4, 3, 40, 100),
        new QuestTemplate(Faction.GOVERNMENT, "Продналог в Фонд Равенства",
            "Согласно указу Барона Контрактов, каждый сектор обязан пополнить неприкосновенный зерновой резерв свежим хлебом.",
            "GATHER", "minecraft:bread", "Сдать Хлеб", 12, 2, 25, 70),
        new QuestTemplate(Faction.GOVERNMENT, "Инспекция границ Юрисдикции",
            "Проведите официальный патрульный обход территории Исономии. Любое отклонение от протокола недопустимо.",
            "VISIT", "patrol_blocks", "Пройти блоков в патруле", 500, 3, 45, 110)
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

    // Автоматически поддерживает на доске не менее 10 разнообразных НПС-заказов на большом полотне
    public void ensureNpcQuestsPopulated() {
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

    // Подбирает свободную точку на большом холсте доски (850x550), чтобы свитки не перекрывали друг друга
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
