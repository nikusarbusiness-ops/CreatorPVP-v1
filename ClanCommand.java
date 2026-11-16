package de.clanpvp;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.regex.Pattern;

public class ClanCommand implements CommandExecutor, TabCompleter {

    public static final String PREFIX = ChatColor.DARK_GRAY + "[" + ChatColor.GOLD + "Clan"
            + ChatColor.DARK_GRAY + "] " + ChatColor.GRAY;

    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{3,16}");
    private static final Pattern TAG = Pattern.compile("[A-Za-z0-9]{2,4}");

    private final ClanPvP plugin;
    private final ClanManager cm;

    public ClanCommand(ClanPvP plugin) {
        this.plugin = plugin;
        this.cm = plugin.clans();
    }

    private void say(Player p, String msg) { p.sendMessage(PREFIX + msg); }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] a) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Dieser Befehl ist nur für Spieler.");
            return true;
        }
        if (cmd.getName().equalsIgnoreCase("cc")) {
            chat(p, a, 0);
            return true;
        }
        if (a.length == 0) {
            help(p);
            return true;
        }
        switch (a[0].toLowerCase()) {
            case "create", "erstellen" -> create(p, a);
            case "invite", "einladen" -> invite(p, a);
            case "accept", "join", "annehmen" -> accept(p, a);
            case "deny", "ablehnen" -> deny(p, a);
            case "leave", "verlassen" -> leave(p);
            case "kick" -> kick(p, a);
            case "disband", "aufloesen", "auflösen" -> disband(p);
            case "leader", "fuehrung", "führung" -> leader(p, a);
            case "tag" -> tag(p, a);
            case "info" -> info(p, a);
            case "list", "liste" -> list(p);
            case "chat" -> chat(p, a, 1);
            default -> help(p);
        }
        return true;
    }

    private void help(Player p) {
        p.sendMessage(ChatColor.GOLD + "=== Clan-Befehle ===");
        String[] lines = {
                "/clan create <Name> <Tag> §7- Clan erstellen (Tag: 2-4 Zeichen)",
                "/clan invite <Spieler> §7- Spieler einladen",
                "/clan accept [Clan] §7- Einladung annehmen",
                "/clan deny [Clan] §7- Einladung ablehnen",
                "/clan leave §7- Clan verlassen",
                "/clan kick <Spieler> §7- Mitglied rauswerfen",
                "/clan leader <Spieler> §7- Führung übertragen",
                "/clan tag <Tag> §7- Clan-Tag ändern",
                "/clan disband §7- Clan auflösen",
                "/clan info [Clan] §7- Clan-Infos",
                "/clan list §7- Alle Clans",
                "/cc [Nachricht] §7- Clan-Chat (ohne Text: an/aus)"
        };
        for (String l : lines) p.sendMessage(ChatColor.YELLOW + l);
    }

    private Clan leaderClan(Player p) {
        Clan c = cm.get(p.getUniqueId());
        if (c == null) {
            say(p, ChatColor.RED + "Du bist in keinem Clan.");
            return null;
        }
        if (!c.leader.equals(p.getUniqueId())) {
            say(p, ChatColor.RED + "Nur der Clan-Leader darf das.");
            return null;
        }
        return c;
    }

    private void create(Player p, String[] a) {
        if (a.length < 3) {
            say(p, ChatColor.RED + "Nutze: /clan create <Name> <Tag>");
            return;
        }
        if (cm.get(p.getUniqueId()) != null) {
            say(p, ChatColor.RED + "Du bist bereits in einem Clan.");
            return;
        }
        if (!NAME.matcher(a[1]).matches()) {
            say(p, ChatColor.RED + "Der Name muss 3-16 Zeichen lang sein (Buchstaben, Zahlen, _).");
            return;
        }
        if (!TAG.matcher(a[2]).matches()) {
            say(p, ChatColor.RED + "Der Tag muss 2-4 Zeichen lang sein (Buchstaben, Zahlen).");
            return;
        }
        if (cm.byName(a[1]) != null) {
            say(p, ChatColor.RED + "Dieser Clan-Name ist bereits vergeben.");
            return;
        }
        if (cm.tagTaken(a[2], null)) {
            say(p, ChatColor.RED + "Dieser Tag ist bereits vergeben.");
            return;
        }
        cm.create(p, a[1], a[2]);
        say(p, ChatColor.GREEN + "Clan " + ChatColor.GOLD + a[1] + ChatColor.GREEN + " [" + a[2] + "] erstellt!");
    }

    private void invite(Player p, String[] a) {
        Clan c = leaderClan(p);
        if (c == null) return;
        if (a.length < 2) {
            say(p, ChatColor.RED + "Nutze: /clan invite <Spieler>");
            return;
        }
        Player t = Bukkit.getPlayerExact(a[1]);
        if (t == null) {
            say(p, ChatColor.RED + "Der Spieler ist nicht online.");
            return;
        }
        if (cm.get(t.getUniqueId()) != null) {
            say(p, ChatColor.RED + t.getName() + " ist bereits in einem Clan.");
            return;
        }
        if (c.members.size() >= plugin.getConfig().getInt("max-clan-mitglieder", 10)) {
            say(p, ChatColor.RED + "Dein Clan ist voll.");
            return;
        }
        cm.invite(c, t.getUniqueId());
        say(p, ChatColor.GREEN + "Einladung an " + t.getName() + " gesendet (60 Sekunden gültig).");
        t.sendMessage(PREFIX + "Du wurdest in den Clan " + ChatColor.GOLD + c.name + " [" + c.tag + "]"
                + ChatColor.GRAY + " eingeladen.");
        TextComponent yes = new TextComponent(ChatColor.GREEN + "[Annehmen] ");
        yes.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/clan accept " + c.name));
        TextComponent no = new TextComponent(ChatColor.RED + "[Ablehnen]");
        no.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/clan deny " + c.name));
        t.spigot().sendMessage(yes, no);
    }

    private String pickInvite(Player p, String[] a) {
        Map<String, Long> inv = cm.invites(p.getUniqueId());
        String key;
        if (a.length >= 2) key = a[1].toLowerCase();
        else if (inv.size() == 1) key = inv.keySet().iterator().next();
        else {
            say(p, ChatColor.RED + "Nutze: /clan " + a[0] + " <Clan>");
            return null;
        }
        if (!inv.containsKey(key)) {
            say(p, ChatColor.RED + "Du hast keine gültige Einladung von diesem Clan.");
            return null;
        }
        return key;
    }

    private void accept(Player p, String[] a) {
        if (cm.get(p.getUniqueId()) != null) {
            say(p, ChatColor.RED + "Du bist bereits in einem Clan.");
            return;
        }
        String key = pickInvite(p, a);
        if (key == null) return;
        Clan c = cm.byName(key);
        if (c == null) {
            say(p, ChatColor.RED + "Diesen Clan gibt es nicht mehr.");
            return;
        }
        if (c.members.size() >= plugin.getConfig().getInt("max-clan-mitglieder", 10)) {
            say(p, ChatColor.RED + "Dieser Clan ist voll.");
            return;
        }
        cm.clearInvites(p.getUniqueId());
        cm.addMember(c, p);
        cm.broadcast(c, ChatColor.GREEN + p.getName() + " ist dem Clan beigetreten!");
    }

    private void deny(Player p, String[] a) {
        String key = pickInvite(p, a);
        if (key == null) return;
        cm.invites(p.getUniqueId()).remove(key);
        say(p, ChatColor.YELLOW + "Einladung abgelehnt.");
    }

    private void leave(Player p) {
        Clan c = cm.get(p.getUniqueId());
        if (c == null) {
            say(p, ChatColor.RED + "Du bist in keinem Clan.");
            return;
        }
        if (c.leader.equals(p.getUniqueId())) {
            if (c.members.size() == 1) {
                cm.disband(c);
                say(p, ChatColor.YELLOW + "Du warst allein - der Clan wurde aufgelöst.");
            } else {
                say(p, ChatColor.RED + "Übertrage zuerst die Führung (/clan leader <Spieler>) oder löse den Clan auf.");
            }
            return;
        }
        cm.removeMember(c, p.getUniqueId());
        say(p, ChatColor.YELLOW + "Du hast den Clan verlassen.");
        cm.broadcast(c, ChatColor.YELLOW + p.getName() + " hat den Clan verlassen.");
    }

    private UUID findMember(Clan c, String name) {
        for (Map.Entry<UUID, String> en : c.members.entrySet()) {
            if (en.getValue().equalsIgnoreCase(name)) return en.getKey();
        }
        return null;
    }

    private void kick(Player p, String[] a) {
        Clan c = leaderClan(p);
        if (c == null) return;
        if (a.length < 2) {
            say(p, ChatColor.RED + "Nutze: /clan kick <Spieler>");
            return;
        }
        UUID tu = findMember(c, a[1]);
        if (tu == null) {
            say(p, ChatColor.RED + "Das ist kein Mitglied deines Clans.");
            return;
        }
        if (tu.equals(p.getUniqueId())) {
            say(p, ChatColor.RED + "Du kannst dich nicht selbst rauswerfen.");
            return;
        }
        String name = c.members.get(tu);
        cm.removeMember(c, tu);
        cm.broadcast(c, ChatColor.YELLOW + name + " wurde aus dem Clan geworfen.");
        Player t = Bukkit.getPlayer(tu);
        if (t != null) t.sendMessage(PREFIX + ChatColor.RED + "Du wurdest aus dem Clan " + c.name + " geworfen.");
    }

    private void disband(Player p) {
        Clan c = leaderClan(p);
        if (c == null) return;
        cm.broadcast(c, ChatColor.RED + "Der Clan wurde aufgelöst.");
        cm.disband(c);
    }

    private void leader(Player p, String[] a) {
        Clan c = leaderClan(p);
        if (c == null) return;
        if (a.length < 2) {
            say(p, ChatColor.RED + "Nutze: /clan leader <Spieler>");
            return;
        }
        UUID tu = findMember(c, a[1]);
        if (tu == null) {
            say(p, ChatColor.RED + "Das ist kein Mitglied deines Clans.");
            return;
        }
        c.leader = tu;
        cm.save();
        cm.broadcast(c, ChatColor.GOLD + c.members.get(tu) + ChatColor.GRAY + " ist jetzt der neue Clan-Leader.");
    }

    private void tag(Player p, String[] a) {
        Clan c = leaderClan(p);
        if (c == null) return;
        if (a.length < 2 || !TAG.matcher(a[1]).matches()) {
            say(p, ChatColor.RED + "Nutze: /clan tag <Tag> (2-4 Zeichen, Buchstaben/Zahlen)");
            return;
        }
        if (cm.tagTaken(a[1], c)) {
            say(p, ChatColor.RED + "Dieser Tag ist bereits vergeben.");
            return;
        }
        c.tag = a[1];
        cm.save();
        cm.refresh(c);
        cm.broadcast(c, ChatColor.GREEN + "Der Clan-Tag ist jetzt [" + c.tag + "].");
    }

    private void info(Player p, String[] a) {
        Clan c = a.length >= 2 ? cm.byName(a[1]) : cm.get(p.getUniqueId());
        if (c == null) {
            say(p, ChatColor.RED + (a.length >= 2 ? "Diesen Clan gibt es nicht." : "Du bist in keinem Clan."));
            return;
        }
        p.sendMessage(ChatColor.GOLD + "=== " + c.name + " [" + c.tag + "] ===");
        p.sendMessage(ChatColor.YELLOW + "Leader: " + ChatColor.WHITE + c.members.getOrDefault(c.leader, "?"));
        p.sendMessage(ChatColor.YELLOW + "Mitglieder (" + c.members.size() + "):");
        for (Map.Entry<UUID, String> m : c.members.entrySet()) {
            boolean on = Bukkit.getPlayer(m.getKey()) != null;
            p.sendMessage((on ? ChatColor.GREEN : ChatColor.GRAY) + " - " + m.getValue()
                    + (m.getKey().equals(c.leader) ? ChatColor.GOLD + " (Leader)" : ""));
        }
    }

    private void list(Player p) {
        List<Clan> all = new ArrayList<>(cm.all());
        if (all.isEmpty()) {
            say(p, ChatColor.YELLOW + "Es gibt noch keine Clans.");
            return;
        }
        all.sort((x, y) -> Integer.compare(y.members.size(), x.members.size()));
        p.sendMessage(ChatColor.GOLD + "=== Clans (" + all.size() + ") ===");
        for (Clan c : all) {
            p.sendMessage(ChatColor.YELLOW + c.name + ChatColor.GRAY + " [" + c.tag + "] - "
                    + c.members.size() + " Mitglieder");
        }
    }

    private void chat(Player p, String[] a, int from) {
        Clan c = cm.get(p.getUniqueId());
        if (c == null) {
            say(p, ChatColor.RED + "Du bist in keinem Clan.");
            return;
        }
        if (a.length <= from) {
            boolean on = cm.toggleChat(p.getUniqueId());
            say(p, ChatColor.YELLOW + "Clan-Chat " + (on ? ChatColor.GREEN + "aktiviert" : ChatColor.RED + "deaktiviert")
                    + ChatColor.YELLOW + ".");
            return;
        }
        cm.sendChat(p, c, String.join(" ", Arrays.copyOfRange(a, from, a.length)));
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        if (!(s instanceof Player p) || cmd.getName().equalsIgnoreCase("cc")) return List.of();
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            out.addAll(List.of("create", "invite", "accept", "deny", "leave", "kick", "leader",
                    "tag", "disband", "info", "list", "chat", "help"));
        } else if (a.length == 2) {
            switch (a[0].toLowerCase()) {
                case "invite" -> Bukkit.getOnlinePlayers().forEach(o -> out.add(o.getName()));
                case "accept", "join", "deny" -> out.addAll(cm.invites(p.getUniqueId()).keySet());
                case "kick", "leader" -> {
                    Clan c = cm.get(p.getUniqueId());
                    if (c != null) out.addAll(c.members.values());
                }
                case "info" -> cm.all().forEach(c -> out.add(c.name));
                default -> { }
            }
        }
        String last = a[a.length - 1].toLowerCase();
        out.removeIf(x -> !x.toLowerCase().startsWith(last));
        return out;
    }
}
