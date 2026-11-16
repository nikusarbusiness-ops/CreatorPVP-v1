package de.clanpvp;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Combat-Tag: Nach einem Treffer sind beide Spieler 20 Sekunden (konfigurierbar) im Kampf. */
public class CombatManager implements Listener {

    private final ClanPvP plugin;
    private final Map<UUID, Long> end = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> opponent = new ConcurrentHashMap<>();

    public CombatManager(ClanPvP plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 10L, 10L);
    }

    public boolean isInCombat(UUID u) {
        Long e = end.get(u);
        return e != null && e > System.currentTimeMillis();
    }

    public UUID getOpponent(UUID u) { return opponent.get(u); }

    public void clear(UUID u) {
        end.remove(u);
        opponent.remove(u);
    }

    private void tag(Player p, Player other) {
        boolean was = isInCombat(p.getUniqueId());
        long ms = plugin.getConfig().getInt("combat-seconds", 20) * 1000L;
        end.put(p.getUniqueId(), System.currentTimeMillis() + ms);
        opponent.put(p.getUniqueId(), other.getUniqueId());
        if (!was) {
            p.sendMessage(ClanCommand.PREFIX + ChatColor.RED + "Du bist jetzt im Kampf! Logge dich nicht aus!");
        }
    }

    private void actionBar(Player p, String text) {
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(text));
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Long> en : end.entrySet()) {
            UUID id = en.getKey();
            Player p = Bukkit.getPlayer(id);
            if (p == null) {
                end.remove(id);
                opponent.remove(id);
                continue;
            }
            if (en.getValue() <= now) {
                end.remove(id);
                opponent.remove(id);
                p.sendMessage(ClanCommand.PREFIX + ChatColor.GREEN + "Du bist nicht mehr im Kampf.");
                actionBar(p, ChatColor.GREEN + "Du bist nicht mehr im Kampf");
            } else {
                long sec = (en.getValue() - now + 999) / 1000;
                actionBar(p, ChatColor.RED + "⚔ Im Kampf: " + ChatColor.YELLOW + sec + "s");
            }
        }
    }

    static Player attackerOf(Entity damager) {
        if (damager instanceof Player p) return p;
        if (damager instanceof Projectile pr && pr.getShooter() instanceof Player p) return p;
        return null;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFriendlyFire(EntityDamageByEntityEvent e) {
        if (plugin.getConfig().getBoolean("friendly-fire", false)) return;
        if (!(e.getEntity() instanceof Player victim)) return;
        Player attacker = attackerOf(e.getDamager());
        if (attacker == null || attacker.equals(victim)) return;
        if (plugin.clans().sameClan(attacker.getUniqueId(), victim.getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        Player attacker = attackerOf(e.getDamager());
        if (attacker == null || attacker.equals(victim)) return;
        tag(victim, attacker);
        tag(attacker, victim);
    }
}
