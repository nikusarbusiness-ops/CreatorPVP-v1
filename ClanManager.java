package de.clanpvp;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ClanManager implements Listener {

    private final ClanPvP plugin;
    private final File file;
    private final Map<String, Clan> clans = new ConcurrentHashMap<>();
    private final Map<UUID, Clan> byPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Long>> invites = new ConcurrentHashMap<>();
    private final Set<UUID> chatMode = ConcurrentHashMap.newKeySet();

    public ClanManager(ClanPvP plugin) {
        this.plugin = plugin;
        plugin.getDataFolder().mkdirs();
        this.file = new File(plugin.getDataFolder(), "clans.yml");
        load();
    }

    // ---------------------------------------------------------------- Speicher

    private void load() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sec = y.getConfigurationSection("clans");
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            ConfigurationSection cs = sec.getConfigurationSection(key);
            if (cs == null) continue;
            Clan c = new Clan(cs.getString("name", key), cs.getString("tag", "??"),
                    UUID.fromString(cs.getString("leader")));
            ConfigurationSection ms = cs.getConfigurationSection("members");
            if (ms != null) {
                for (String u : ms.getKeys(false)) c.members.put(UUID.fromString(u), ms.getString(u, "?"));
            }
            register(c);
        }
    }

    public synchronized void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Clan c : clans.values()) {
            String p = "clans." + c.name.toLowerCase() + ".";
            y.set(p + "name", c.name);
            y.set(p + "tag", c.tag);
            y.set(p + "leader", c.leader.toString());
            for (Map.Entry<UUID, String> m : c.members.entrySet()) {
                y.set(p + "members." + m.getKey(), m.getValue());
            }
        }
        try {
            y.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("clans.yml konnte nicht gespeichert werden: " + ex.getMessage());
        }
    }

    private void register(Clan c) {
        clans.put(c.name.toLowerCase(), c);
        for (UUID u : c.members.keySet()) byPlayer.put(u, c);
    }

    // ---------------------------------------------------------------- Abfragen

    public Clan get(UUID player) { return byPlayer.get(player); }

    public Clan byName(String name) { return clans.get(name.toLowerCase()); }

    public Collection<Clan> all() { return clans.values(); }

    public boolean tagTaken(String tag, Clan except) {
        for (Clan c : clans.values()) {
            if (c != except && c.tag.equalsIgnoreCase(tag)) return true;
        }
        return false;
    }

    public boolean sameClan(UUID a, UUID b) {
        Clan ca = byPlayer.get(a);
        return ca != null && ca == byPlayer.get(b);
    }

    // ---------------------------------------------------------------- Änderungen

    public void create(Player p, String name, String tag) {
        Clan c = new Clan(name, tag, p.getUniqueId());
        c.members.put(p.getUniqueId(), p.getName());
        register(c);
        save();
        applyTag(p);
    }

    public void addMember(Clan c, Player p) {
        c.members.put(p.getUniqueId(), p.getName());
        byPlayer.put(p.getUniqueId(), c);
        save();
        applyTag(p);
    }

    public void removeMember(Clan c, UUID u) {
        c.members.remove(u);
        byPlayer.remove(u);
        chatMode.remove(u);
        save();
        Player p = Bukkit.getPlayer(u);
        if (p != null) applyTag(p);
    }

    public void disband(Clan c) {
        clans.remove(c.name.toLowerCase());
        List<UUID> list = new ArrayList<>(c.members.keySet());
        for (UUID u : list) {
            byPlayer.remove(u);
            chatMode.remove(u);
        }
        save();
        for (UUID u : list) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) applyTag(p);
        }
    }

    public void refresh(Clan c) {
        for (UUID u : c.members.keySet()) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) applyTag(p);
        }
    }

    public void broadcast(Clan c, String msg) {
        for (UUID u : c.members.keySet()) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) p.sendMessage(ClanCommand.PREFIX + msg);
        }
    }

    // ---------------------------------------------------------------- Einladungen

    public void invite(Clan c, UUID target) {
        invites.computeIfAbsent(target, k -> new ConcurrentHashMap<>())
                .put(c.name.toLowerCase(), System.currentTimeMillis() + 60_000L);
    }

    public Map<String, Long> invites(UUID u) {
        Map<String, Long> m = invites.get(u);
        if (m == null) return new ConcurrentHashMap<>();
        long now = System.currentTimeMillis();
        m.values().removeIf(t -> t < now);
        return m;
    }

    public void clearInvites(UUID u) { invites.remove(u); }

    // ---------------------------------------------------------------- Clan-Chat

    public boolean toggleChat(UUID u) {
        if (chatMode.remove(u)) return false;
        chatMode.add(u);
        return true;
    }

    public void sendChat(Player p, Clan c, String msg) {
        String line = ChatColor.DARK_GRAY + "[" + ChatColor.GOLD + "Clan-Chat" + ChatColor.DARK_GRAY + "] "
                + ChatColor.YELLOW + p.getName() + ChatColor.GRAY + ": " + ChatColor.WHITE + msg;
        for (UUID u : c.members.keySet()) {
            Player t = Bukkit.getPlayer(u);
            if (t != null) t.sendMessage(line);
        }
        plugin.getLogger().info("[ClanChat " + c.tag + "] " + p.getName() + ": " + msg);
    }

    // ---------------------------------------------------------------- Clan-Tag (Tab + über dem Kopf)

    public void applyTag(Player p) {
        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        for (Team t : new ArrayList<>(sb.getTeams())) {
            if (t.getName().startsWith("clan_") && t.hasEntry(p.getName())) {
                t.removeEntry(p.getName());
                if (t.getEntries().isEmpty()) t.unregister();
            }
        }
        Clan c = byPlayer.get(p.getUniqueId());
        if (c == null) return;
        String teamName = "clan_" + c.tag.toLowerCase();
        Team t = sb.getTeam(teamName);
        if (t == null) t = sb.registerNewTeam(teamName);
        t.setPrefix(ChatColor.GOLD + "[" + c.tag + "] " + ChatColor.RESET);
        t.addEntry(p.getName());
    }

    // ---------------------------------------------------------------- Events

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        applyTag(e.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        chatMode.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        Clan c = byPlayer.get(p.getUniqueId());
        if (c == null) return;
        if (chatMode.contains(p.getUniqueId())) {
            e.setCancelled(true);
            sendChat(p, c, e.getMessage());
            return;
        }
        e.setFormat(ChatColor.GOLD + "[" + c.tag + "] " + ChatColor.WHITE + "%1$s"
                + ChatColor.GRAY + ": " + ChatColor.WHITE + "%2$s");
    }
}
