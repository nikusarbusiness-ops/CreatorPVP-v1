package de.clanpvp;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/** /rekord - höchster gleichzeitiger Spielerstand aller Zeiten. */
public class RekordCommand implements CommandExecutor, Listener {

    private final ClanPvP plugin;

    public RekordCommand(ClanPvP plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        int online = Bukkit.getOnlinePlayers().size();
        if (plugin.data().checkRecord(online)) {
            Bukkit.broadcastMessage(ChatColor.GOLD + "★ Neuer Spielerrekord: " + ChatColor.YELLOW + online
                    + ChatColor.GOLD + " Spieler gleichzeitig online! ★");
        }
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] args) {
        s.sendMessage(ChatColor.GOLD + "=== Spielerrekord ===");
        s.sendMessage(ChatColor.YELLOW + "All-Time-Rekord: " + ChatColor.WHITE + plugin.data().getRecord()
                + " Spieler gleichzeitig");
        s.sendMessage(ChatColor.YELLOW + "Aufgestellt am: " + ChatColor.WHITE + plugin.data().getRecordDate());
        s.sendMessage(ChatColor.YELLOW + "Aktuell online: " + ChatColor.WHITE + Bukkit.getOnlinePlayers().size());
        s.sendMessage(ChatColor.YELLOW + "Spieler insgesamt (jemals gejoint): " + ChatColor.WHITE
                + Bukkit.getOfflinePlayers().length);
        return true;
    }
}
