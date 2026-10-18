package com.amarthaloe.events;

import com.amarthaloe.data.OfflineMailStorage;
import com.amarthaloe.data.PassportData;
import com.amarthaloe.network.PassportNetworking;
import com.amarthaloe.rpg.Faction;
import com.amarthaloe.rpg.LoreBookRegistry;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;

@EventBusSubscriber(modid = "amarthaloe", bus = EventBusSubscriber.Bus.GAME)
public class ServerEvents {

    public static class PartyInstance {
        public String partyId = UUID.randomUUID().toString().substring(0, 8);
        public String leaderUuid;
        public boolean friendlyFire = false;
        public final List<String> members = new ArrayList<>();

        public PartyInstance(String leaderUuid) {
            this.leaderUuid = leaderUuid;
            this.members.add(leaderUuid);
        }
    }

    public static final Map<String, PartyInstance> PLAYER_PARTIES = new HashMap<>();
    public static final Map<String, String> PENDING_PARTY_INVITES = new HashMap<>();
    private static final Map<UUID, Vec3> LAST_PLAYER_POS = new HashMap<>();
    private static int tickCounter = 0;

    public static void refreshAllNames(ServerPlayer sp) {
        if (sp.server != null) {
            OfflineMailStorage.GuildInfo g = OfflineMailStorage.get(sp.server).getPlayerGuild(sp.getStringUUID());
            PassportData d = sp.getData(PassportData.PASSPORT);
            d.guildTag = (g != null) ? g.tag : "";
            sp.setData(PassportData.PASSPORT, d);
        }
        sp.refreshDisplayName();
        sp.refreshTabListName();
        if (sp.server != null) {
            sp.server.getPlayerList().broadcastAll(
                new ClientboundPlayerInfoUpdatePacket(
                    EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME),
                    List.of(sp)
                )
            );
        }
    }

    public static Component buildFormattedDisplayName(Player player, String fallbackUsername) {
        PassportData data = player.getData(PassportData.PASSPORT);
        String baseName = (data.isLocked && !data.rpName.trim().isEmpty())
                ? data.rpName
                : fallbackUsername;

        String guildPrefix = (!data.guildTag.isEmpty()) ? "§b<" + data.guildTag + "> " : "";

        Faction activeFac = Faction.byId(data.activeTitleFaction);
        if (activeFac != null && Math.abs(data.getRep(activeFac)) >= 10) {
            String title = activeFac.getTitle(data.getRep(activeFac), data.isFemale());
            String color = data.getRep(activeFac) < 0 ? "§4" : activeFac.chatColor;
            return Component.literal(guildPrefix + color + "[" + title + "] §f" + baseName);
        } else {
            return Component.literal(guildPrefix + "§f" + baseName);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        tickCounter++;
        if (tickCounter % 20 == 0) {
            OfflineMailStorage mail = OfflineMailStorage.get(event.getServer());
            for (ServerPlayer sp : event.getServer().getPlayerList().getPlayers()) {
                Vec3 now = sp.position();
                Vec3 prev = LAST_PLAYER_POS.put(sp.getUUID(), now);
                if (prev != null && prev.distanceToSqr(now) < 400) {
                    int dist = (int) Math.round(Math.sqrt(prev.distanceToSqr(now)));
                    if (dist > 0) {
                        PassportData data = sp.getData(PassportData.PASSPORT);
                        boolean changed = false;
                        for (Map.Entry<String, Integer> entry : new ArrayList<>(data.activeQuests.entrySet())) {
                            OfflineMailStorage.BoardNotice q = mail.getBoardNotices().get(entry.getKey());
                            if (q != null && "VISIT".equals(q.questType)) {
                                int oldP = entry.getValue();
                                if (oldP < q.requiredCount) {
                                    int newP = Math.min(q.requiredCount, oldP + dist);
                                    data.activeQuests.put(q.id, newP);
                                    changed = true;
                                    if (newP >= q.requiredCount) {
                                        sp.sendSystemMessage(Component.literal("§6[Доска Заказов] §aМаршрут контракта «§e" + q.title + "§a» пройден! Откройте §b[M] -> ОБЪЯВЛЕНИЯ§a для получения награды."));
                                        sp.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.MASTER, 0.8f, 1.3f);
                                    }
                                }
                            }
                        }
                        if (changed) {
                            sp.setData(PassportData.PASSPORT, data);
                            PacketDistributor.sendToPlayer(sp, new PassportNetworking.PassportPayload(data.serializeNBT(sp.registryAccess())));
                        }
                    }
                }
                syncPartyAndGuildToPlayer(sp, true, false);
            }
        }
        if (tickCounter % 6000 == 0) {
            OfflineMailStorage mail = OfflineMailStorage.get(event.getServer());
            long npcQuests = mail.getBoardNotices().values().stream().filter(n -> !n.isPlayerNote).count();
            if (npcQuests < 15) {
                mail.spawnRandomNpcQuest(new Random());
                for (ServerPlayer sp : event.getServer().getPlayerList().getPlayers()) {
                    syncPartyAndGuildToPlayer(sp, false, true);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer killer) {
            ResourceLocation entityLoc = BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType());
            if (entityLoc == null) return;
            String killedId = entityLoc.toString();

            OfflineMailStorage mail = OfflineMailStorage.get(killer.server);
            PassportData data = killer.getData(PassportData.PASSPORT);
            boolean updated = false;

            for (Map.Entry<String, Integer> entry : new ArrayList<>(data.activeQuests.entrySet())) {
                OfflineMailStorage.BoardNotice q = mail.getBoardNotices().get(entry.getKey());
                if (q != null && "KILL".equals(q.questType) && q.targetId.equalsIgnoreCase(killedId)) {
                    int prog = entry.getValue();
                    if (prog < q.requiredCount) {
                        prog++;
                        data.activeQuests.put(q.id, prog);
                        updated = true;
                        if (prog >= q.requiredCount) {
                            killer.sendSystemMessage(Component.literal("§6[Доска Заказов] §aКонтракт «§e" + q.title + "§a» выполнен (" + prog + "/" + q.requiredCount + ")! Сдайте его во вкладке §bОБЪЯВЛЕНИЯ§a."));
                            killer.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 0.7f, 1.3f);
                        } else {
                            killer.sendSystemMessage(Component.literal("§6[Контракт] §7" + q.title + ": §e" + prog + "/" + q.requiredCount));
                        }
                    }
                }
            }

            if (updated) {
                killer.setData(PassportData.PASSPORT, data);
                PacketDistributor.sendToPlayer(killer, new PassportNetworking.PassportPayload(data.serializeNBT(killer.registryAccess())));
            }
        }
    }

    @SubscribeEvent
    public static void onLivingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer victim && event.getSource().getEntity() instanceof ServerPlayer attacker) {
            if (victim.getUUID().equals(attacker.getUUID())) return;
            String vUuid = victim.getStringUUID();
            String aUuid = attacker.getStringUUID();

            PartyInstance party = PLAYER_PARTIES.get(vUuid);
            if (party != null && party == PLAYER_PARTIES.get(aUuid) && !party.friendlyFire) {
                event.setCanceled(true);
                return;
            }

            OfflineMailStorage.GuildInfo guild = OfflineMailStorage.get(victim.server).getPlayerGuild(vUuid);
            if (guild != null && guild.memberRanks.containsKey(aUuid) && !guild.friendlyFire) {
                event.setCanceled(true);
            }
        }
    }

    // --- УСТАНОВКА НАЙДЕННОЙ ЛОРНОЙ КНИГИ ИЗ ИНВЕНТАРЯ НА ПОЛКУ ---
    public static void handleShelfInsertBook(ServerPlayer player) {
        PassportData data = player.getData(PassportData.PASSPORT);
        boolean foundAny = false;

        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack st = player.getInventory().getItem(i);
            if (st.isEmpty()) continue;

            CustomData customData = st.get(DataComponents.CUSTOM_DATA);
            if (customData != null) {
                CompoundTag tag = customData.copyTag();
                if (tag.contains("amarthaloe_lore_book")) {
                    String bookId = tag.getString("amarthaloe_lore_book");
                    LoreBookRegistry.LoreBook book = LoreBookRegistry.byId(bookId);
                    if (book != null && !data.unlockedBooks.contains(bookId)) {
                        data.unlockedBooks.add(bookId);
                        st.shrink(1);
                        foundAny = true;
                        player.sendSystemMessage(Component.literal("§6[Архив Либры] §aВы поставили редкий том «§e" + book.fullTitle() + "§a» на свою книжную полку!"));
                        player.playNotifySound(SoundEvents.BOOK_PUT, SoundSource.MASTER, 1.0f, 1.0f);
                    }
                }
            }
        }

        if (foundAny) {
            player.setData(PassportData.PASSPORT, data);
            syncPlayerDataWithOnlineStatus(player);
        } else {
            player.sendSystemMessage(Component.literal("§c[Архив Либры] В вашем инвентаре нет новых запечатанных томов для книжной полки."));
        }
    }

    // Создает физический предмет лорной книги в инвентарь игрока
    public static ItemStack createLoreBookItem(LoreBookRegistry.LoreBook book) {
        ItemStack stack = new ItemStack(Items.ENCHANTED_BOOK);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("§6📖 Запечатанный Том: §e" + book.spineTitle()));
        CompoundTag customTag = new CompoundTag();
        customTag.putString("amarthaloe_lore_book", book.id());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(customTag));
        return stack;
    }

    // --- ОБРАБОТЧИКИ ДОСКИ ОБЪЯВЛЕНИЙ И КОНТРАКТОВ ---
    public static void handleBoardAcceptQuest(ServerPlayer player, String questId) {
        OfflineMailStorage mail = OfflineMailStorage.get(player.server);
        OfflineMailStorage.BoardNotice q = mail.getBoardNotices().get(questId);
        if (q == null || q.isPlayerNote) return;

        PassportData data = player.getData(PassportData.PASSPORT);
        if (data.activeQuests.containsKey(questId)) return;
        if (data.activeQuests.size() >= 5) {
            player.sendSystemMessage(Component.literal("§c[Доска Заказов] Вы не можете держать больше 5 активных контрактов одновременно!"));
            return;
        }

        data.activeQuests.put(questId, 0);
        player.setData(PassportData.PASSPORT, data);
        PacketDistributor.sendToPlayer(player, new PassportNetworking.PassportPayload(data.serializeNBT(player.registryAccess())));
        player.sendSystemMessage(Component.literal("§6[Доска Заказов] §aВы взяли контракт «§e" + q.title + "§a» от заказчика §f" + q.authorName + "§a!"));
        player.playNotifySound(SoundEvents.BOOK_PAGE_TURN, SoundSource.MASTER, 1.0f, 1.0f);
    }

    public static void handleBoardCompleteQuest(ServerPlayer player, String questId) {
        OfflineMailStorage mail = OfflineMailStorage.get(player.server);
        OfflineMailStorage.BoardNotice q = mail.getBoardNotices().get(questId);
        if (q == null || q.isPlayerNote) return;

        PassportData data = player.getData(PassportData.PASSPORT);
        if (!data.activeQuests.containsKey(questId)) return;

        if ("GATHER".equals(q.questType)) {
            Item reqItem = BuiltInRegistries.ITEM.get(ResourceLocation.parse(q.targetId));
            int have = 0;
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack st = player.getInventory().getItem(i);
                if (!st.isEmpty() && st.is(reqItem)) have += st.getCount();
            }
            if (have < q.requiredCount) {
                player.sendSystemMessage(Component.literal("§c[Контракт] Недостаточно предметов в инвентаре! Требуется: " + q.targetDisplay + " (" + have + "/" + q.requiredCount + ")"));
                return;
            }
            int toRemove = q.requiredCount;
            for (int i = 0; i < player.getInventory().getContainerSize() && toRemove > 0; i++) {
                ItemStack st = player.getInventory().getItem(i);
                if (!st.isEmpty() && st.is(reqItem)) {
                    int take = Math.min(toRemove, st.getCount());
                    st.shrink(take);
                    toRemove -= take;
                }
            }
        } else {
            int progress = data.activeQuests.getOrDefault(questId, 0);
            if (progress < q.requiredCount) {
                player.sendSystemMessage(Component.literal("§c[Контракт] Условия еще не выполнены (" + progress + "/" + q.requiredCount + ")!"));
                return;
            }
        }

        data.activeQuests.remove(questId);
        data.nomes += q.rewardNomes;

        // Если за этот контракт полагается секретная лорная книга — ставим её на полку игрока!
        if (q.rewardBookId != null && !q.rewardBookId.isEmpty()) {
            LoreBookRegistry.LoreBook unlockedBook = LoreBookRegistry.byId(q.rewardBookId);
            if (unlockedBook != null && !data.unlockedBooks.contains(q.rewardBookId)) {
                data.unlockedBooks.add(q.rewardBookId);
                player.sendSystemMessage(Component.literal("§d📖 [Архив Либры] §fВы добыли тайный том «§e" + unlockedBook.fullTitle() + "§f»! Теперь он стоит на вашей полке во вкладке §bИНФОРМАЦИЯ§f."));
                player.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 0.9f, 1.25f);
            }
        }

        player.setData(PassportData.PASSPORT, data);

        mail.getBoardNotices().remove(questId);
        mail.ensureNpcQuestsPopulated();
        mail.setDirty();

        player.sendSystemMessage(Component.literal("§8§m================================================"));
        player.sendSystemMessage(Component.literal("§6§lКОНТРАКТ ИСПОЛНЕН: §e" + q.title));
        player.sendSystemMessage(Component.literal("§7Заказчик: §f" + q.authorName));
        player.sendSystemMessage(Component.literal("§a+ " + q.rewardNomes + " Номов §7(Баланс: §6" + data.nomes + " Номов§7)"));
        player.sendSystemMessage(Component.literal("§8§m================================================"));
        player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.MASTER, 1.0f, 1.1f);

        changeRep(player.createCommandSourceStack(), player, q.factionId, q.rewardRep, true);

        for (ServerPlayer sp : player.server.getPlayerList().getPlayers()) {
            syncPartyAndGuildToPlayer(sp, false, true);
        }
    }

    public static void handleBoardPostNote(ServerPlayer sender, String title, String content) {
        if (title.trim().isEmpty() || content.trim().isEmpty()) return;
        OfflineMailStorage mail = OfflineMailStorage.get(sender.server);
        PassportData d = sender.getData(PassportData.PASSPORT);
        String authorName = d.rpName.isEmpty() ? sender.getName().getString() : d.rpName;

        mail.createPlayerLeaflet(sender.getStringUUID(), authorName, title.trim(), content.trim());
        sender.sendSystemMessage(Component.literal("§6[Доска Объявлений] §aВаша листовка «§e" + title.trim() + "§a» приколота на городскую доску!"));
        sender.playNotifySound(SoundEvents.BOOK_PAGE_TURN, SoundSource.MASTER, 1.0f, 1.2f);

        for (ServerPlayer sp : sender.server.getPlayerList().getPlayers()) {
            syncPartyAndGuildToPlayer(sp, false, true);
        }
    }

    public static void handleBoardRemoveNote(ServerPlayer sender, String noteId) {
        OfflineMailStorage mail = OfflineMailStorage.get(sender.server);
        OfflineMailStorage.BoardNotice n = mail.getBoardNotices().get(noteId);
        if (n == null) return;
        if (n.isPlayerNote && (sender.getStringUUID().equals(n.authorUuid) || sender.hasPermissions(2))) {
            mail.getBoardNotices().remove(noteId);
            mail.setDirty();
            sender.sendSystemMessage(Component.literal("§6[Доска Объявлений] §7Листовка снята с доски."));
            for (ServerPlayer sp : sender.server.getPlayerList().getPlayers()) {
                syncPartyAndGuildToPlayer(sp, false, true);
            }
        }
    }

    public static void syncPartyAndGuildToPlayer(ServerPlayer sp, boolean syncParty, boolean syncGuild) {
        CompoundTag partyTag = new CompoundTag();
        if (!syncParty) {
            partyTag.putBoolean("ignoreParty", true);
        } else {
            String myUuid = sp.getStringUUID();
            PartyInstance party = PLAYER_PARTIES.get(myUuid);
            if (PENDING_PARTY_INVITES.containsKey(myUuid)) {
                String inviterUuid = PENDING_PARTY_INVITES.get(myUuid);
                partyTag.putString("pendingInviteFrom", inviterUuid);
                ServerPlayer invP = sp.server.getPlayerList().getPlayer(UUID.fromString(inviterUuid));
                String invName = invP != null ? invP.getDisplayName().getString() : "Игрок";
                partyTag.putString("pendingInviteName", invName);
            }
            if (party != null) {
                partyTag.putString("leaderUuid", party.leaderUuid);
                partyTag.putBoolean("friendlyFire", party.friendlyFire);
                ListTag mList = new ListTag();
                PassportData myData = sp.getData(PassportData.PASSPORT);

                for (String mUuid : party.members) {
                    CompoundTag mTag = new CompoundTag();
                    mTag.putString("uuid", mUuid);
                    mTag.putBoolean("isLeader", mUuid.equals(party.leaderUuid));

                    if (mUuid.startsWith("bot-")) {
                        CompoundTag botProf = myData.knownProfiles.get(mUuid);
                        mTag.putString("rpName", botProf != null ? botProf.getString("rpName") : "Боец Исономии");
                        mTag.putString("photoId", botProf != null ? botProf.getString("photoId") : "");
                        mTag.putFloat("hp", mUuid.contains("marfa") ? 16.0f : 20.0f);
                        mTag.putFloat("maxHp", 20.0f);
                        mTag.putInt("food", mUuid.contains("marfa") ? 14 : 20);
                        ItemStack botItem = new ItemStack(mUuid.contains("marfa") ? Items.WRITABLE_BOOK : Items.IRON_SWORD);
                        mTag.put("heldItem", botItem.saveOptional(sp.registryAccess()));
                    } else {
                        ServerPlayer mp = sp.server.getPlayerList().getPlayer(UUID.fromString(mUuid));
                        if (mp != null) {
                            PassportData md = mp.getData(PassportData.PASSPORT);
                            mTag.putString("rpName", md.rpName.isEmpty() ? mp.getName().getString() : md.rpName);
                            mTag.putString("photoId", md.photoId);
                            mTag.putFloat("hp", mp.getHealth());
                            mTag.putFloat("maxHp", mp.getMaxHealth());
                            mTag.putInt("food", mp.getFoodData().getFoodLevel());
                            ItemStack held = mp.getMainHandItem();
                            if (!held.isEmpty()) {
                                mTag.put("heldItem", held.saveOptional(sp.registryAccess()));
                            }
                        } else {
                            CompoundTag cached = myData.knownProfiles.get(mUuid);
                            mTag.putString("rpName", (cached != null ? cached.getString("rpName") : "Оффлайн") + " (Офф)");
                            mTag.putString("photoId", cached != null ? cached.getString("photoId") : "");
                            mTag.putFloat("hp", 0f);
                            mTag.putFloat("maxHp", 20f);
                            mTag.putInt("food", 0);
                        }
                    }
                    mList.add(mTag);
                }
                partyTag.put("members", mList);
            }
        }

        CompoundTag guildTag = new CompoundTag();
        if (!syncGuild) {
            guildTag.putBoolean("ignoreGuild", true);
        } else {
            OfflineMailStorage mail = OfflineMailStorage.get(sp.server);
            String myUuid = sp.getStringUUID();
            OfflineMailStorage.GuildInfo guild = mail.getPlayerGuild(myUuid);
            OfflineMailStorage.GuildInfo inviteGuild = mail.getPendingGuildInvite(myUuid);

            if (inviteGuild != null) {
                guildTag.putString("pendingGuildId", inviteGuild.guildId);
                guildTag.putString("pendingGuildName", inviteGuild.name + " <" + inviteGuild.tag + ">");
            }

            if (guild != null) {
                guildTag.putString("guildId", guild.guildId);
                guildTag.putString("name", guild.name);
                guildTag.putString("tag", guild.tag);
                guildTag.putString("avatarId", guild.avatarId);
                guildTag.putString("leaderUuid", guild.leaderUuid);
                guildTag.putBoolean("friendlyFire", guild.friendlyFire);
                guildTag.putString("myRank", guild.memberRanks.getOrDefault(myUuid, "MEMBER"));
                guildTag.putBoolean("canOpenChest", myUuid.equals(guild.leaderUuid) || guild.chestAllowed.contains(myUuid));

                ListTag mList = new ListTag();
                PassportData myData = sp.getData(PassportData.PASSPORT);
                for (Map.Entry<String, String> entry : guild.memberRanks.entrySet()) {
                    String mUuid = entry.getKey();
                    String rank = entry.getValue();
                    CompoundTag mTag = new CompoundTag();
                    mTag.putString("uuid", mUuid);
                    mTag.putString("rank", rank);
                    mTag.putString("rankDisplay", OfflineMailStorage.GuildInfo.getRankDisplayName(rank));
                    mTag.putBoolean("chestAccess", mUuid.equals(guild.leaderUuid) || guild.chestAllowed.contains(mUuid));

                    ServerPlayer mp = mUuid.startsWith("bot-") ? null : sp.server.getPlayerList().getPlayer(UUID.fromString(mUuid));
                    if (mp != null) {
                        PassportData md = mp.getData(PassportData.PASSPORT);
                        mTag.putString("rpName", md.rpName.isEmpty() ? mp.getName().getString() : md.rpName);
                        mTag.putBoolean("online", true);
                        mTag.putInt("hp", (int) mp.getHealth());
                    } else {
                        CompoundTag cached = myData.knownProfiles.get(mUuid);
                        mTag.putString("rpName", cached != null ? cached.getString("rpName") : "Гражданин");
                        mTag.putBoolean("online", false);
                        mTag.putInt("hp", 0);
                    }
                    mList.add(mTag);
                }
                guildTag.put("members", mList);
            }

            ListTag noticesList = new ListTag();
            for (OfflineMailStorage.BoardNotice n : mail.getBoardNotices().values()) {
                noticesList.add(n.toTag());
            }
            guildTag.put("boardNotices", noticesList);
        }

        PacketDistributor.sendToPlayer(sp, new PassportNetworking.PartyGuildSyncPayload(partyTag, guildTag));
    }

    public static void broadcastPartySync(MinecraftServer server, PartyInstance party) {
        for (String mUuid : party.members) {
            if (mUuid.startsWith("bot-")) continue;
            ServerPlayer p = server.getPlayerList().getPlayer(UUID.fromString(mUuid));
            if (p != null) syncPartyAndGuildToPlayer(p, true, false);
        }
    }

    public static void broadcastGuildSync(MinecraftServer server, OfflineMailStorage.GuildInfo guild) {
        for (String mUuid : guild.memberRanks.keySet()) {
            if (mUuid.startsWith("bot-")) continue;
            ServerPlayer p = server.getPlayerList().getPlayer(UUID.fromString(mUuid));
            if (p != null) {
                refreshAllNames(p);
                syncPlayerDataWithOnlineStatus(p);
                syncPartyAndGuildToPlayer(p, false, true);
            }
        }
    }

    public static void handlePartyInvite(ServerPlayer sender, String targetQuery) {
        String sUuid = sender.getStringUUID();
        PartyInstance party = PLAYER_PARTIES.get(sUuid);
        if (party != null && !party.leaderUuid.equals(sUuid)) {
            sender.sendSystemMessage(Component.literal("§c[Группа] Приглашать игроков может только лидер группы!"));
            return;
        }
        if (party != null && party.members.size() >= 5) {
            sender.sendSystemMessage(Component.literal("§c[Группа] В группе уже максимум участников (5/5)!"));
            return;
        }

        if (targetQuery.startsWith("bot-")) {
            if (party == null) {
                party = new PartyInstance(sUuid);
                PLAYER_PARTIES.put(sUuid, party);
            }
            if (!party.members.contains(targetQuery) && party.members.size() < 5) {
                party.members.add(targetQuery);
            }
            sender.sendSystemMessage(Component.literal("§6[Группа] §aТестовый соратник присоединился к вашей группе!"));
            syncPartyAndGuildToPlayer(sender, true, false);
            return;
        }

        ServerPlayer target = PassportNetworking.findPlayerByNameOrRpName(sender, targetQuery);
        if (target == null) {
            sender.sendSystemMessage(Component.literal("§c[Группа] Игрок «" + targetQuery + "» не найден в сети."));
            return;
        }
        if (target.getUUID().equals(sender.getUUID())) return;
        if (PLAYER_PARTIES.containsKey(target.getStringUUID())) {
            sender.sendSystemMessage(Component.literal("§c[Группа] Этот игрок уже состоит в другой группе."));
            return;
        }

        if (party == null) {
            party = new PartyInstance(sUuid);
            PLAYER_PARTIES.put(sUuid, party);
        }

        PENDING_PARTY_INVITES.put(target.getStringUUID(), sUuid);
        sender.sendSystemMessage(Component.literal("§6[Группа] §aПриглашение отправлено игроку §e" + target.getDisplayName().getString() + "§a!"));
        target.sendSystemMessage(Component.literal("§6[Группа] §e" + sender.getDisplayName().getString() + " §fприглашает вас в группу! Откройте §b[M] -> ГРУППЫ§f."));
        target.playNotifySound(SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.MASTER, 1.0f, 1.2f);

        syncPartyAndGuildToPlayer(sender, true, false);
        syncPartyAndGuildToPlayer(target, true, false);
    }

    public static void handlePartyAccept(ServerPlayer player, String leaderUuid) {
        String myUuid = player.getStringUUID();
        if (!PENDING_PARTY_INVITES.remove(myUuid, leaderUuid)) return;

        PartyInstance party = PLAYER_PARTIES.get(leaderUuid);
        if (party == null || party.members.size() >= 5) {
            player.sendSystemMessage(Component.literal("§c[Группа] Группа переполнена (5/5) или распущена."));
            syncPartyAndGuildToPlayer(player, true, false);
            return;
        }

        party.members.add(myUuid);
        PLAYER_PARTIES.put(myUuid, party);

        for (String mUuid : party.members) {
            if (mUuid.startsWith("bot-")) continue;
            ServerPlayer mp = player.server.getPlayerList().getPlayer(UUID.fromString(mUuid));
            if (mp != null) {
                mp.sendSystemMessage(Component.literal("§6[Группа] §e" + player.getDisplayName().getString() + " §aприсоединился к группе! (" + party.members.size() + "/5)"));
            }
        }
        broadcastPartySync(player.server, party);
    }

    public static void handlePartyDecline(ServerPlayer player) {
        PENDING_PARTY_INVITES.remove(player.getStringUUID());
        syncPartyAndGuildToPlayer(player, true, false);
    }

    public static void handlePartyToggleFF(ServerPlayer sender) {
        PartyInstance party = PLAYER_PARTIES.get(sender.getStringUUID());
        if (party == null || !party.leaderUuid.equals(sender.getStringUUID())) return;

        party.friendlyFire = !party.friendlyFire;
        String status = party.friendlyFire ? "§cВКЛЮЧЕН (урон по своим проходит!)" : "§aВЫКЛЮЧЕН (соратники защищены)";
        for (String mUuid : party.members) {
            if (mUuid.startsWith("bot-")) continue;
            ServerPlayer mp = sender.server.getPlayerList().getPlayer(UUID.fromString(mUuid));
            if (mp != null) {
                mp.sendSystemMessage(Component.literal("§6[Группа] §eЛидер изменил статус дружественного огня: " + status));
                mp.playNotifySound(SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.MASTER, 0.8f, 1.0f);
            }
        }
        broadcastPartySync(sender.server, party);
    }

    public static void handlePartyLeaveOrKick(ServerPlayer requester, String targetUuid) {
        String reqUuid = requester.getStringUUID();
        PartyInstance party = PLAYER_PARTIES.get(reqUuid);
        if (party == null) return;
        if (!reqUuid.equals(targetUuid) && !party.leaderUuid.equals(reqUuid)) return;

        party.members.remove(targetUuid);
        PLAYER_PARTIES.remove(targetUuid);

        if (!targetUuid.startsWith("bot-")) {
            ServerPlayer removedPlayer = requester.server.getPlayerList().getPlayer(UUID.fromString(targetUuid));
            if (removedPlayer != null) {
                removedPlayer.sendSystemMessage(Component.literal("§6[Группа] §eВы покинули группу."));
                syncPartyAndGuildToPlayer(removedPlayer, true, false);
            }
        }

        if (party.members.isEmpty() || (party.members.size() == 1 && !party.members.get(0).startsWith("bot-") && reqUuid.equals(targetUuid))) {
            for (String m : new ArrayList<>(party.members)) {
                PLAYER_PARTIES.remove(m);
                if (!m.startsWith("bot-")) {
                    ServerPlayer sp = requester.server.getPlayerList().getPlayer(UUID.fromString(m));
                    if (sp != null) syncPartyAndGuildToPlayer(sp, true, false);
                }
            }
            party.members.clear();
            return;
        }

        if (party.leaderUuid.equals(targetUuid)) {
            for (String m : party.members) {
                if (!m.startsWith("bot-")) {
                    party.leaderUuid = m;
                    break;
                }
            }
        }
        broadcastPartySync(requester.server, party);
    }

    public static void handleGuildCreate(ServerPlayer sender, String nameAndTag, String avatarId) {
        OfflineMailStorage mail = OfflineMailStorage.get(sender.server);
        String sUuid = sender.getStringUUID();
        if (mail.getPlayerGuild(sUuid) != null) {
            sender.sendSystemMessage(Component.literal("§c[Содружество] Вы уже состоите в содружестве!"));
            return;
        }

        String[] parts = nameAndTag.split("\\|", 2);
        String name = parts[0].trim();
        String tag = parts.length > 1 ? parts[1].trim().toUpperCase() : "CLAN";
        if (name.isEmpty()) return;
        if (tag.length() > 4) tag = tag.substring(0, 4);

        String guildId = "guild_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String chatGroupId = "group_guild_" + guildId.substring(6);

        OfflineMailStorage.GuildInfo guild = new OfflineMailStorage.GuildInfo();
        guild.guildId = guildId;
        guild.name = name;
        guild.tag = tag;
        guild.avatarId = avatarId;
        guild.leaderUuid = sUuid;
        guild.chatGroupId = chatGroupId;
        guild.memberRanks.put(sUuid, "LEADER");
        guild.chestAllowed.add(sUuid);

        OfflineMailStorage.GroupChatInfo chatGroup = new OfflineMailStorage.GroupChatInfo();
        chatGroup.groupId = chatGroupId;
        chatGroup.title = "[" + tag + "] " + name;
        chatGroup.avatarId = avatarId;
        chatGroup.ownerUuid = sUuid;
        chatGroup.members.add(sUuid);

        mail.createOrUpdateGroup(chatGroup);
        mail.addMessage(chatGroupId, chatGroupId, "§6[Содружество] §aОсновано содружество «" + name + "» <" + tag + ">!", true);
        mail.getGuilds().put(guildId, guild);
        mail.setDirty();

        sender.sendSystemMessage(Component.literal("§6[Содружество] §aВы основали содружество «§e" + name + "§a» с тегом §b<" + tag + ">§a! Автоматическая беседа создана в сообщениях."));
        broadcastGuildSync(sender.server, guild);
        syncMailAndNotifyUnread(sender);
    }

    public static void handleGuildInvite(ServerPlayer sender, String targetQuery) {
        OfflineMailStorage mail = OfflineMailStorage.get(sender.server);
        OfflineMailStorage.GuildInfo guild = mail.getPlayerGuild(sender.getStringUUID());
        if (guild == null) return;

        String myRank = guild.memberRanks.getOrDefault(sender.getStringUUID(), "MEMBER");
        if (!myRank.equals("LEADER") && !myRank.equals("OFFICER")) {
            sender.sendSystemMessage(Component.literal("§c[Содружество] Приглашать новых участников могут только Лидер и Офицеры!"));
            return;
        }

        ServerPlayer target = PassportNetworking.findPlayerByNameOrRpName(sender, targetQuery);
        if (target == null) {
            sender.sendSystemMessage(Component.literal("§c[Содружество] Игрок «" + targetQuery + "» не найден в сети."));
            return;
        }
        if (mail.getPlayerGuild(target.getStringUUID()) != null) {
            sender.sendSystemMessage(Component.literal("§c[Содружество] Этот игрок уже состоит в содружестве."));
            return;
        }

        guild.invitedPlayers.add(target.getStringUUID());
        mail.setDirty();

        sender.sendSystemMessage(Component.literal("§6[Содружество] §aПриглашение отправлено игроку §e" + target.getDisplayName().getString() + "§a."));
        target.sendSystemMessage(Component.literal("§6[Содружество] §eВас пригласили в содружество «§b" + guild.name + " <" + guild.tag + ">§e»! Откройте §b[M] -> СОДРУЖЕСТВА§e."));
        target.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 0.8f, 1.2f);
        syncPartyAndGuildToPlayer(target, false, true);
    }

    public static void handleGuildAccept(ServerPlayer player, String guildId) {
        OfflineMailStorage mail = OfflineMailStorage.get(player.server);
        OfflineMailStorage.GuildInfo guild = mail.getGuilds().get(guildId);
        String myUuid = player.getStringUUID();
        if (guild == null || !guild.invitedPlayers.remove(myUuid)) return;

        guild.memberRanks.put(myUuid, "MEMBER");
        OfflineMailStorage.GroupChatInfo chatGroup = mail.getGroupChats().get(guild.chatGroupId);
        if (chatGroup != null) {
            chatGroup.members.add(myUuid);
            mail.addMessage(guild.chatGroupId, guild.chatGroupId, "§6[Содружество] §e" + player.getDisplayName().getString() + " §aвступил(а) в содружество!", true);
        }
        mail.setDirty();

        for (String mUuid : guild.memberRanks.keySet()) {
            if (mUuid.startsWith("bot-")) continue;
            ServerPlayer mp = player.server.getPlayerList().getPlayer(UUID.fromString(mUuid));
            if (mp != null) {
                mp.sendSystemMessage(Component.literal("§b<" + guild.tag + "> §e" + player.getDisplayName().getString() + " §aтеперь часть нашего содружества!"));
            }
        }
        broadcastGuildSync(player.server, guild);
        syncMailAndNotifyUnread(player);
    }

    public static void handleGuildDecline(ServerPlayer player, String guildId) {
        OfflineMailStorage mail = OfflineMailStorage.get(player.server);
        OfflineMailStorage.GuildInfo guild = mail.getGuilds().get(guildId);
        if (guild != null) {
            guild.invitedPlayers.remove(player.getStringUUID());
            mail.setDirty();
        }
        syncPartyAndGuildToPlayer(player, false, true);
    }

    public static void handleGuildToggleFF(ServerPlayer sender) {
        OfflineMailStorage mail = OfflineMailStorage.get(sender.server);
        OfflineMailStorage.GuildInfo guild = mail.getPlayerGuild(sender.getStringUUID());
        if (guild == null) return;

        String myRank = guild.memberRanks.getOrDefault(sender.getStringUUID(), "MEMBER");
        if (!myRank.equals("LEADER") && !myRank.equals("OFFICER")) return;

        guild.friendlyFire = !guild.friendlyFire;
        mail.setDirty();

        String status = guild.friendlyFire ? "§cВКЛЮЧЕН (урон между соратниками активен!)" : "§aВЫКЛЮЧЕН (соратники защищены)";
        for (String mUuid : guild.memberRanks.keySet()) {
            if (mUuid.startsWith("bot-")) continue;
            ServerPlayer mp = sender.server.getPlayerList().getPlayer(UUID.fromString(mUuid));
            if (mp != null) {
                mp.sendSystemMessage(Component.literal("§b<" + guild.tag + "> §6[Уведомление Содружества] §fОгонь по своим: " + status));
                mp.playNotifySound(SoundEvents.ANVIL_USE, SoundSource.MASTER, 0.5f, 1.3f);
            }
        }
        broadcastGuildSync(sender.server, guild);
    }

    public static void handleGuildChangeRank(ServerPlayer sender, String targetUuid, String direction) {
        OfflineMailStorage mail = OfflineMailStorage.get(sender.server);
        OfflineMailStorage.GuildInfo guild = mail.getPlayerGuild(sender.getStringUUID());
        if (guild == null || !guild.leaderUuid.equals(sender.getStringUUID())) return;
        if (targetUuid.equals(guild.leaderUuid)) return;

        String[] order = {"MEMBER", "MENTOR", "OFFICER", "LEADER"};
        String current = guild.memberRanks.getOrDefault(targetUuid, "MEMBER");
        int idx = 0;
        for (int i = 0; i < order.length; i++) {
            if (order[i].equals(current)) idx = i;
        }

        if ("UP".equals(direction) && idx < 2) idx++;
        else if ("DOWN".equals(direction) && idx > 0) idx--;

        guild.memberRanks.put(targetUuid, order[idx]);
        mail.setDirty();
        broadcastGuildSync(sender.server, guild);
    }

    public static void handleGuildToggleChestAccess(ServerPlayer sender, String targetUuid) {
        OfflineMailStorage mail = OfflineMailStorage.get(sender.server);
        OfflineMailStorage.GuildInfo guild = mail.getPlayerGuild(sender.getStringUUID());
        if (guild == null || !guild.leaderUuid.equals(sender.getStringUUID())) return;
        if (targetUuid.equals(guild.leaderUuid)) return;

        if (guild.chestAllowed.contains(targetUuid)) {
            guild.chestAllowed.remove(targetUuid);
        } else {
            guild.chestAllowed.add(targetUuid);
        }
        mail.setDirty();
        broadcastGuildSync(sender.server, guild);
    }

    public static void handleGuildOpenChest(ServerPlayer sender) {
        OfflineMailStorage mail = OfflineMailStorage.get(sender.server);
        OfflineMailStorage.GuildInfo guild = mail.getPlayerGuild(sender.getStringUUID());
        if (guild == null) return;

        String myUuid = sender.getStringUUID();
        if (!myUuid.equals(guild.leaderUuid) && !guild.chestAllowed.contains(myUuid)) {
            sender.sendSystemMessage(Component.literal("§c[Содружество] Лидер клана не выдал вам доступ к сундуку содружества!"));
            return;
        }

        guild.chest.addListener(container -> mail.setDirty());
        sender.openMenu(new SimpleMenuProvider(
                (id, playerInv, player) -> ChestMenu.threeRows(id, playerInv, guild.chest),
                Component.literal("Сундук Содружества <" + guild.tag + ">")
        ));
    }

    public static void handleGuildKickOrLeave(ServerPlayer requester, String targetUuid) {
        OfflineMailStorage mail = OfflineMailStorage.get(requester.server);
        String reqUuid = requester.getStringUUID();
        OfflineMailStorage.GuildInfo guild = mail.getPlayerGuild(reqUuid);
        if (guild == null) return;

        if (!reqUuid.equals(targetUuid) && !guild.leaderUuid.equals(reqUuid)) return;

        if (targetUuid.equals(guild.leaderUuid)) {
            Set<String> oldMembers = new HashSet<>(guild.memberRanks.keySet());
            mail.getGuilds().remove(guild.guildId);
            mail.getGroupChats().remove(guild.chatGroupId);
            mail.setDirty();

            for (String mUuid : oldMembers) {
                if (mUuid.startsWith("bot-")) continue;
                ServerPlayer mp = requester.server.getPlayerList().getPlayer(UUID.fromString(mUuid));
                if (mp != null) {
                    mp.sendSystemMessage(Component.literal("§c[Содружество] Содружество «" + guild.name + "» было распущено лидером."));
                    refreshAllNames(mp);
                    syncPlayerDataWithOnlineStatus(mp);
                    syncPartyAndGuildToPlayer(mp, false, true);
                }
            }
            return;
        }

        guild.memberRanks.remove(targetUuid);
        guild.chestAllowed.remove(targetUuid);
        OfflineMailStorage.GroupChatInfo chatGroup = mail.getGroupChats().get(guild.chatGroupId);
        if (chatGroup != null) chatGroup.members.remove(targetUuid);
        mail.setDirty();

        if (!targetUuid.startsWith("bot-")) {
            ServerPlayer kicked = requester.server.getPlayerList().getPlayer(UUID.fromString(targetUuid));
            if (kicked != null) {
                kicked.sendSystemMessage(Component.literal("§e[Содружество] Вы больше не состоите в содружестве «" + guild.name + "»."));
                refreshAllNames(kicked);
                syncPlayerDataWithOnlineStatus(kicked);
                syncPartyAndGuildToPlayer(kicked, false, true);
            }
        }
        broadcastGuildSync(requester.server, guild);
    }

    public static void syncPlayerDataWithOnlineStatus(ServerPlayer sp) {
        PassportData data = sp.getData(PassportData.PASSPORT);
        for (String uuidStr : data.friends) {
            if (uuidStr.startsWith("bot-")) continue;
            try {
                ServerPlayer friend = sp.server.getPlayerList().getPlayer(UUID.fromString(uuidStr));
                if (friend != null) {
                    PassportData fData = friend.getData(PassportData.PASSPORT);
                    data.knownProfiles.put(uuidStr, fData.toProfileSnapshot(uuidStr, friend.getName().getString(), true));
                } else if (data.knownProfiles.containsKey(uuidStr)) {
                    data.knownProfiles.get(uuidStr).putBoolean("online", false);
                }
            } catch (Exception ignored) {}
        }

        OfflineMailStorage mail = OfflineMailStorage.get(sp.server);
        OfflineMailStorage.GuildInfo g = mail.getPlayerGuild(sp.getStringUUID());
        data.guildTag = (g != null) ? g.tag : "";

        data.groupChats.clear();
        for (OfflineMailStorage.GroupChatInfo grp : mail.getGroupsForPlayer(sp.getStringUUID())) {
            data.groupChats.put(grp.groupId, grp.toTag());
        }

        sp.setData(PassportData.PASSPORT, data);
        PacketDistributor.sendToPlayer(sp, new PassportNetworking.PassportPayload(data.serializeNBT(sp.registryAccess())));
    }

    public static void syncMailAndNotifyUnread(ServerPlayer sp) {
        PassportData data = sp.getData(PassportData.PASSPORT);
        OfflineMailStorage mail = OfflineMailStorage.get(sp.server);
        String myUuid = sp.getStringUUID();

        for (String friendUuid : data.friends) {
            PacketDistributor.sendToPlayer(sp, new PassportNetworking.SocialActionPayload("CLEAR_HISTORY", friendUuid, ""));
            List<String> history = mail.getHistory(myUuid, friendUuid);
            for (String line : history) {
                PacketDistributor.sendToPlayer(sp, new PassportNetworking.SocialActionPayload("RECEIVE_DM", friendUuid, line));
            }
        }

        for (OfflineMailStorage.GroupChatInfo g : mail.getGroupsForPlayer(myUuid)) {
            PacketDistributor.sendToPlayer(sp, new PassportNetworking.SocialActionPayload("CLEAR_HISTORY", g.groupId, ""));
            List<String> history = mail.getHistory(g.groupId, g.groupId);
            for (String line : history) {
                PacketDistributor.sendToPlayer(sp, new PassportNetworking.SocialActionPayload("RECEIVE_DM", g.groupId, line));
            }
        }

        Map<String, Integer> unreadMap = mail.getUnreadForPlayer(myUuid);
        for (Map.Entry<String, Integer> entry : unreadMap.entrySet()) {
            PacketDistributor.sendToPlayer(sp, new PassportNetworking.SocialActionPayload("SET_UNREAD", entry.getKey(), String.valueOf(entry.getValue())));
        }

        int totalUnread = mail.getTotalUnread(myUuid);
        if (totalUnread > 0) {
            sp.sendSystemMessage(Component.literal("§8§m------------------------------------------------------"));
            sp.sendSystemMessage(Component.literal("§6✉ [Почта Исономии] §eУ вас §c§l" + totalUnread + " §eнепрочитанных сообщений!"));
            sp.sendSystemMessage(Component.literal("§7Откройте меню §b[M] -> СООБЩЕНИЯ§7, чтобы прочитать их."));
            sp.sendSystemMessage(Component.literal("§8§m------------------------------------------------------"));
            sp.playNotifySound(SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.MASTER, 1.0f, 1.1f);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            PassportData data = sp.getData(PassportData.PASSPORT);
            refreshAllNames(sp);

            if (!data.isLocked) {
                sp.sendSystemMessage(Component.literal("§8§m======================================================"));
                sp.sendSystemMessage(Component.literal("§6§l Добро пожаловать в Амарталоэ и суровую Исономию!"));
                sp.sendSystemMessage(Component.literal("§7 Мир разделен на фракции, законы и контракты Либры."));
                sp.sendSystemMessage(Component.literal("§e➤ Нажмите клавишу §b[M]§e, чтобы открыть главное меню мода."));
                sp.sendSystemMessage(Component.literal("§c⚠ Обязательно заполните паспорт персонажа во вкладке «ИГРОК»."));
                sp.sendSystemMessage(Component.literal("§7 Данные заполняются §lодин раз§7, после чего вступят в силу в чате."));
                sp.sendSystemMessage(Component.literal("§8§m======================================================"));

                sp.connection.send(new ClientboundSetTitlesAnimationPacket(10, 100, 20));
                sp.connection.send(new ClientboundSetTitleTextPacket(Component.literal("§6§lАМАРТАЛОЭ")));
                sp.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("§eНажмите [M] и заполните паспорт!")));
                sp.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 0.9f, 0.8f);
            } else {
                String displayName = data.rpName.isEmpty() ? sp.getName().getString() : data.rpName;
                sp.sendSystemMessage(Component.literal("§6[Амарталоэ] §aДобро пожаловать в Амарталоэ, §e" + displayName + "§a!"));
            }

            syncPlayerDataWithOnlineStatus(sp);
            syncMailAndNotifyUnread(sp);
            syncPartyAndGuildToPlayer(sp, true, true);

            String myName = data.rpName.isEmpty() ? sp.getName().getString() : data.rpName;
            for (String friendUuid : data.friends) {
                if (friendUuid.startsWith("bot-")) continue;
                try {
                    ServerPlayer onlineFriend = sp.server.getPlayerList().getPlayer(UUID.fromString(friendUuid));
                    if (onlineFriend != null) {
                        onlineFriend.sendSystemMessage(Component.literal("§6[Друзья] §a● Ваш друг §e" + myName + " §aвошел на сервер!"));
                        onlineFriend.playNotifySound(SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.MASTER, 0.7f, 1.2f);
                        syncPlayerDataWithOnlineStatus(onlineFriend);
                    }
                } catch (Exception ignored) {}
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            LAST_PLAYER_POS.remove(sp.getUUID());
            PassportData data = sp.getData(PassportData.PASSPORT);
            String myName = data.rpName.isEmpty() ? sp.getName().getString() : data.rpName;
            String myUuid = sp.getStringUUID();

            for (String friendUuid : data.friends) {
                if (friendUuid.startsWith("bot-")) continue;
                try {
                    ServerPlayer onlineFriend = sp.server.getPlayerList().getPlayer(UUID.fromString(friendUuid));
                    if (onlineFriend != null) {
                        onlineFriend.sendSystemMessage(Component.literal("§6[Друзья] §7○ Ваш друг §e" + myName + " §7покинул сервер."));
                        PassportData fData = onlineFriend.getData(PassportData.PASSPORT);
                        if (fData.knownProfiles.containsKey(myUuid)) {
                            fData.knownProfiles.get(myUuid).putBoolean("online", false);
                        }
                        onlineFriend.setData(PassportData.PASSPORT, fData);
                        PacketDistributor.sendToPlayer(onlineFriend, new PassportNetworking.PassportPayload(fData.serializeNBT(onlineFriend.registryAccess())));
                    }
                } catch (Exception ignored) {}
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            syncPlayerDataWithOnlineStatus(sp);
            syncPartyAndGuildToPlayer(sp, true, true);
            refreshAllNames(sp);
        }
    }

    @SubscribeEvent
    public static void onNameFormat(PlayerEvent.NameFormat event) {
        event.setDisplayname(buildFormattedDisplayName(event.getEntity(), event.getUsername().getString()));
    }

    @SubscribeEvent
    public static void onTabListNameFormat(PlayerEvent.TabListNameFormat event) {
        event.setDisplayName(buildFormattedDisplayName(event.getEntity(), event.getEntity().getScoreboardName()));
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("passport")
                .then(Commands.literal("reset")
                    .requires(source -> source.hasPermission(2))
                    .then(Commands.argument("target", EntityArgument.player())
                        .executes(ctx -> {
                            ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
                            PassportData data = target.getData(PassportData.PASSPORT);
                            data.isLocked = false;
                            target.setData(PassportData.PASSPORT, data);
                            refreshAllNames(target);
                            syncPlayerDataWithOnlineStatus(target);
                            ctx.getSource().sendSuccess(() -> Component.literal("§aПаспорт игрока " + target.getName().getString() + " разблокирован!"), true);
                            return 1;
                        })
                    )
                )
                .then(Commands.literal("show")
                    .then(Commands.argument("target", EntityArgument.player())
                        .executes(ctx -> {
                            ServerPlayer sender = ctx.getSource().getPlayerOrException();
                            ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
                            PassportData d = sender.getData(PassportData.PASSPORT);
                            if (!d.isLocked) {
                                sender.sendSystemMessage(Component.literal("§cСначала заполните свой паспорт в меню (M)!"));
                                return 0;
                            }
                            sendPassportCard(sender, target, d);
                            return 1;
                        })
                    )
                )
        );

        // Команда управления секретными лорными книгами: /lorebook unlock|give <игрок> <id_книги>
        event.getDispatcher().register(
            Commands.literal("lorebook")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("unlock")
                    .then(Commands.argument("target", EntityArgument.player())
                        .then(Commands.argument("bookId", StringArgumentType.word())
                            .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                LoreBookRegistry.BOOKS.stream().filter(LoreBookRegistry.LoreBook::secretByDefault).map(LoreBookRegistry.LoreBook::id), builder))
                            .executes(ctx -> {
                                ServerPlayer t = EntityArgument.getPlayer(ctx, "target");
                                String bId = StringArgumentType.getString(ctx, "bookId");
                                LoreBookRegistry.LoreBook b = LoreBookRegistry.byId(bId);
                                if (b == null) {
                                    ctx.getSource().sendFailure(Component.literal("§cТом с ID «" + bId + "» не найден."));
                                    return 0;
                                }
                                PassportData d = t.getData(PassportData.PASSPORT);
                                d.unlockedBooks.add(bId);
                                t.setData(PassportData.PASSPORT, d);
                                syncPlayerDataWithOnlineStatus(t);
                                t.sendSystemMessage(Component.literal("§6[Архив Либры] §aНа вашей книжной полке открыт тайный том: «§e" + b.fullTitle() + "§a»!"));
                                return 1;
                            })
                        )
                    )
                )
                .then(Commands.literal("give")
                    .then(Commands.argument("target", EntityArgument.player())
                        .then(Commands.argument("bookId", StringArgumentType.word())
                            .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                LoreBookRegistry.BOOKS.stream().filter(LoreBookRegistry.LoreBook::secretByDefault).map(LoreBookRegistry.LoreBook::id), builder))
                            .executes(ctx -> {
                                ServerPlayer t = EntityArgument.getPlayer(ctx, "target");
                                String bId = StringArgumentType.getString(ctx, "bookId");
                                LoreBookRegistry.LoreBook b = LoreBookRegistry.byId(bId);
                                if (b == null) return 0;
                                t.getInventory().add(createLoreBookItem(b));
                                t.sendSystemMessage(Component.literal("§6[Архив Либры] §eВы получили запечатанный том «" + b.spineTitle() + "»!"));
                                return 1;
                            })
                        )
                    )
                )
        );

        event.getDispatcher().register(
            Commands.literal("nomes")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("add")
                    .then(Commands.argument("target", EntityArgument.player())
                        .then(Commands.argument("amount", IntegerArgumentType.integer(-100000, 100000))
                            .executes(ctx -> {
                                ServerPlayer t = EntityArgument.getPlayer(ctx, "target");
                                int amt = IntegerArgumentType.getInteger(ctx, "amount");
                                PassportData d = t.getData(PassportData.PASSPORT);
                                d.nomes = Math.max(0, d.nomes + amt);
                                t.setData(PassportData.PASSPORT, d);
                                syncPlayerDataWithOnlineStatus(t);
                                ctx.getSource().sendSuccess(() -> Component.literal("§6[Казна Исономии] §aБаланс игрока теперь: §e" + d.nomes + " Номов"), true);
                                return 1;
                            })
                        )
                    )
                )
                .then(Commands.literal("set")
                    .then(Commands.argument("target", EntityArgument.player())
                        .then(Commands.argument("amount", IntegerArgumentType.integer(0, 1000000))
                            .executes(ctx -> {
                                ServerPlayer t = EntityArgument.getPlayer(ctx, "target");
                                int amt = IntegerArgumentType.getInteger(ctx, "amount");
                                PassportData d = t.getData(PassportData.PASSPORT);
                                d.nomes = amt;
                                t.setData(PassportData.PASSPORT, d);
                                syncPlayerDataWithOnlineStatus(t);
                                ctx.getSource().sendSuccess(() -> Component.literal("§6[Казна Исономии] §aБаланс игрока установлен на: §e" + d.nomes + " Номов"), true);
                                return 1;
                            })
                        )
                    )
                )
        );

        event.getDispatcher().register(
            Commands.literal("rep")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("set")
                    .then(Commands.argument("target", EntityArgument.player())
                        .then(Commands.argument("faction", StringArgumentType.word())
                            .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                Arrays.stream(Faction.values()).map(f -> f.id), builder))
                            .then(Commands.argument("amount", IntegerArgumentType.integer(-2000, 2000))
                                .executes(ctx -> changeRep(ctx.getSource(), EntityArgument.getPlayer(ctx, "target"),
                                        StringArgumentType.getString(ctx, "faction"), IntegerArgumentType.getInteger(ctx, "amount"), false))
                            )
                        )
                    )
                )
                .then(Commands.literal("add")
                    .then(Commands.argument("target", EntityArgument.player())
                        .then(Commands.argument("faction", StringArgumentType.word())
                            .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                Arrays.stream(Faction.values()).map(f -> f.id), builder))
                            .then(Commands.argument("amount", IntegerArgumentType.integer(-2000, 2000))
                                .executes(ctx -> changeRep(ctx.getSource(), EntityArgument.getPlayer(ctx, "target"),
                                        StringArgumentType.getString(ctx, "faction"), IntegerArgumentType.getInteger(ctx, "amount"), true))
                            )
                        )
                    )
                )
        );

        event.getDispatcher().register(
            Commands.literal("socialtest")
                .then(Commands.literal("party")
                    .executes(ctx -> {
                        ServerPlayer sp = ctx.getSource().getPlayerOrException();
                        handlePartyInvite(sp, "bot-marfa");
                        handlePartyInvite(sp, "bot-inspector");
                        sp.sendSystemMessage(Component.literal("§6[Тест] §aСоздана тестовая группа (3/5)!"));
                        return 1;
                    })
                )
                .then(Commands.literal("board")
                    .executes(ctx -> {
                        ServerPlayer sp = ctx.getSource().getPlayerOrException();
                        OfflineMailStorage mail = OfflineMailStorage.get(sp.server);
                        mail.spawnRandomNpcQuest(new Random());
                        syncPartyAndGuildToPlayer(sp, false, true);
                        sp.sendSystemMessage(Component.literal("§6[Тест] §aНа доску объявлений повешен новый случайный свиток контракта!"));
                        return 1;
                    })
                )
                .then(Commands.literal("request")
                    .executes(ctx -> {
                        ServerPlayer sp = ctx.getSource().getPlayerOrException();
                        PassportData data = sp.getData(PassportData.PASSPORT);

                        String botId = "bot-viktor";
                        PassportData fakePass = new PassportData();
                        fakePass.isLocked = true;
                        fakePass.rpName = "Виктор Градов";
                        fakePass.gender = "Мужской";
                        fakePass.ageAndBirth = "34 года, 12 ноября";
                        fakePass.race = "Человек";
                        fakePass.heightWeight = "182 см / 79 кг";
                        fakePass.hair = "Русые, с проседью";
                        fakePass.eyes = "Карие, усталые";
                        fakePass.features = "Ожог на левом запястье, очки в роговой оправе";
                        fakePass.voice = "Хриплый тенор";
                        fakePass.optional = "Старший смены теплоузла Блока-7";

                        data.incomingRequests.add(botId);
                        data.knownProfiles.put(botId, fakePass.toProfileSnapshot(botId, "Viktor_B7", true));
                        syncPlayerDataWithOnlineStatus(sp);

                        sp.sendSystemMessage(Component.literal("§6[Друзья] §eВиктор Градов §fхочет добавить вас в друзья!"));
                        sp.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.MASTER, 0.8f, 1.2f);
                        return 1;
                    })
                )
                .then(Commands.literal("addbot")
                    .executes(ctx -> {
                        ServerPlayer sp = ctx.getSource().getPlayerOrException();
                        PassportData data = sp.getData(PassportData.PASSPORT);

                        String bot1 = "bot-marfa";
                        PassportData p1 = new PassportData();
                        p1.isLocked = true;
                        p1.rpName = "Марфа Соколова";
                        p1.gender = "Женский";
                        p1.ageAndBirth = "26 лет, 3 мая";
                        p1.race = "Человек";
                        p1.heightWeight = "168 см / 57 кг";
                        p1.hair = "Рыжие, собраны в пучок";
                        p1.eyes = "Зеленые";
                        p1.features = "Чернильные пятна на пальцах, значок НИИ";
                        p1.voice = "Мягкое сопрано";
                        p1.optional = "Архивариус сектора Интеллигенции";

                        String bot2 = "bot-inspector";
                        PassportData p2 = new PassportData();
                        p2.isLocked = true;
                        p2.rpName = "Август Вейн";
                        p2.gender = "Мужской";
                        p2.ageAndBirth = "41 год, 20 января";
                        p2.race = "Человек";
                        p2.heightWeight = "190 см / 85 кг";
                        p2.hair = "Черные, гладко зачесаны";
                        p2.eyes = "Холодные серые";
                        p2.features = "Безупречная выправка, серебряный перстень Либры";
                        p2.voice = "Сухой, металлический баритон";
                        p2.optional = "Ревизор Контрактов Правительства";

                        data.friends.add(bot1);
                        data.friends.add(bot2);
                        data.knownProfiles.put(bot1, p1.toProfileSnapshot(bot1, "Marfa_NII", true));
                        data.knownProfiles.put(bot2, p2.toProfileSnapshot(bot2, "Inspector_V", false));
                        syncPlayerDataWithOnlineStatus(sp);

                        sp.sendSystemMessage(Component.literal("§6[Тест] §aДобавлены 2 тестовых друга!"));
                        return 1;
                    })
                )
                .then(Commands.literal("msg")
                    .executes(ctx -> {
                        ServerPlayer sp = ctx.getSource().getPlayerOrException();
                        PassportData data = sp.getData(PassportData.PASSPORT);

                        String senderBot = data.friends.isEmpty() ? "bot-marfa" : data.friends.iterator().next();
                        CompoundTag prof = data.knownProfiles.get(senderBot);
                        String botName = prof != null ? prof.getString("rpName") : "Марфа Соколова";

                        String text = "§e" + botName + ": §fТоварищ, ревизоры Либры проверяют пропуска у 4-го энергоблока! Будь осторожнее.";
                        OfflineMailStorage.get(sp.server).addMessage(senderBot, sp.getStringUUID(), text, false);
                        syncMailAndNotifyUnread(sp);
                        return 1;
                    })
                )
                .then(Commands.literal("offlinemsg")
                    .executes(ctx -> {
                        ServerPlayer sp = ctx.getSource().getPlayerOrException();
                        PassportData data = sp.getData(PassportData.PASSPORT);
                        if (!data.friends.contains("bot-inspector")) {
                            sp.sendSystemMessage(Component.literal("§cСначала добавьте тестовых ботов командой /socialtest addbot"));
                            return 0;
                        }
                        OfflineMailStorage mail = OfflineMailStorage.get(sp.server);
                        mail.addMessage("bot-inspector", sp.getStringUUID(), "§eАвгуст Вейн: §fГражданин, вы отсутствовали на проверке.", false);
                        mail.addMessage("bot-inspector", sp.getStringUUID(), "§eАвгуст Вейн: §fЯвитесь в канцелярию Наместников Либры.", false);
                        syncMailAndNotifyUnread(sp);
                        return 1;
                    })
                )
                .then(Commands.literal("toggle")
                    .executes(ctx -> {
                        ServerPlayer sp = ctx.getSource().getPlayerOrException();
                        PassportData data = sp.getData(PassportData.PASSPORT);
                        for (String fUuid : data.friends) {
                            CompoundTag prof = data.knownProfiles.get(fUuid);
                            if (prof != null) {
                                boolean nowOnline = !prof.getBoolean("online");
                                prof.putBoolean("online", nowOnline);
                                String name = prof.getString("rpName");
                                if (nowOnline) {
                                    sp.sendSystemMessage(Component.literal("§6[Друзья] §a● Ваш друг §e" + name + " §aвошел на сервер!"));
                                    sp.playNotifySound(SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.MASTER, 0.7f, 1.2f);
                                } else {
                                    sp.sendSystemMessage(Component.literal("§6[Друзья] §7○ Ваш друг §e" + name + " §7покинул сервер."));
                                }
                            }
                        }
                        syncPlayerDataWithOnlineStatus(sp);
                        return 1;
                    })
                )
                .then(Commands.literal("clear")
                    .executes(ctx -> {
                        ServerPlayer sp = ctx.getSource().getPlayerOrException();
                        PassportData data = sp.getData(PassportData.PASSPORT);
                        data.friends.removeIf(id -> id.startsWith("bot-"));
                        data.incomingRequests.removeIf(id -> id.startsWith("bot-"));
                        syncPlayerDataWithOnlineStatus(sp);
                        sp.sendSystemMessage(Component.literal("§6[Тест] §eВсе тестовые боты удалены из списка друзей."));
                        return 1;
                    })
                )
        );
    }

    public static int changeRep(net.minecraft.commands.CommandSourceStack source, ServerPlayer target, String facId, int amount, boolean add) {
        Faction f = Faction.byId(facId);
        if (f == null) {
            source.sendFailure(Component.literal("§cНеизвестная фракция! Доступны: lower_city, middle_class, intelligentsia, government"));
            return 0;
        }
        PassportData data = target.getData(PassportData.PASSPORT);
        int oldVal = data.getRep(f);
        int oldTier = f.getTierIndex(oldVal);

        int newVal = Math.clamp(add ? oldVal + amount : amount, -2000, 2000);
        int newTier = f.getTierIndex(newVal);

        data.setRep(f, newVal);
        target.setData(PassportData.PASSPORT, data);
        refreshAllNames(target);
        syncPlayerDataWithOnlineStatus(target);

        String title = f.getTitle(newVal, data.isFemale());
        target.sendSystemMessage(Component.literal("§6[Весы Либры] §7Отношение с «" + f.displayName + "» изменилось: §e" + (newVal > 0 ? "+" : "") + newVal + " §7(Ранг: " + title + ")"));

        if (newTier != oldTier) {
            boolean isUpgrade = newTier > oldTier;
            triggerTierChangeNotification(target, f, title, newVal, isUpgrade);
        }

        return 1;
    }

    private static void triggerTierChangeNotification(ServerPlayer player, Faction f, String newTitle, int rep, boolean isUpgrade) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));

        String sign = rep > 0 ? "+" : "";
        if (isUpgrade) {
            Component mainTitle = Component.literal(f.chatColor + "§l" + f.displayName);
            Component subTitle = Component.literal("§a▲ Новая ступень: §f" + newTitle + " §7(" + sign + rep + ")");
            player.connection.send(new ClientboundSetTitleTextPacket(mainTitle));
            player.connection.send(new ClientboundSetSubtitleTextPacket(subTitle));

            player.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 0.85f, 1.15f);
            player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.MASTER, 0.6f, 0.9f);
        } else {
            Component mainTitle = Component.literal("§4§l" + f.displayName);
            Component subTitle = Component.literal("§c▼ Падение ранга: §f" + newTitle + " §7(" + sign + rep + ")");
            player.connection.send(new ClientboundSetTitleTextPacket(mainTitle));
            player.connection.send(new ClientboundSetSubtitleTextPacket(subTitle));

            player.playNotifySound(SoundEvents.WITHER_SPAWN, SoundSource.MASTER, 0.5f, 0.55f);
            player.playNotifySound(SoundEvents.ANVIL_LAND, SoundSource.MASTER, 0.4f, 0.6f);
        }
    }

    public static void sendPassportCard(ServerPlayer sender, ServerPlayer target, PassportData d) {
        Component card = Component.literal(
            "§8§m========================================\n" +
            "§6§lПАСПОРТ ИСОНОМИИ: §e" + d.rpName + "\n" +
            "§7Пол: §f" + d.gender + " §8| §7Раса: §f" + d.race + "\n" +
            "§7Возраст / ДР: §f" + d.ageAndBirth + " §8| §7Рост/Вес: §f" + d.heightWeight + "\n" +
            "§7Волосы: §f" + d.hair + " §8| §7Глаза: §f" + d.eyes + "\n" +
            "§7Особенности: §f" + d.features + "\n" +
            "§7Голос: §f" + d.voice + "\n" +
            (d.optional.isEmpty() ? "" : "§7Дополнительно: §f" + d.optional + "\n") +
            "§8§m========================================"
        );
        target.sendSystemMessage(Component.literal("§6[Исономия] §eГражданин предъявил вам паспорт:"));
        target.sendSystemMessage(card);
        sender.sendSystemMessage(Component.literal("§6[Исономия] §aВы показали паспорт игроку " + target.getDisplayName().getString()));
    }
}
