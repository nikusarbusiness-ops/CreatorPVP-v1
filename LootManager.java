package de.clanpvp;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Kill / Combat-Logout: Der Killer bekommt ein Menü mit dem Inventar des Opfers und darf sich 1 Item aussuchen.
 * Den Rest behält das Opfer (bei Combat-Logout wird es beim nächsten Login abgezogen).
 */
public class LootManager implements Listener {

    static class Session {
        final Player killer;
        final UUID victimId;
        final String victimName;
        final ItemStack[] items;
        Inventory inv;
        boolean done;
        BukkitTask timeout;

        Session(Player killer, UUID victimId, String victimName, ItemStack[] items) {
            this.killer = killer;
            this.victimId = victimId;
            this.victimName = victimName;
            this.items = items;
        }
    }

    static class LootHolder implements InventoryHolder {
        final Session session;
        LootHolder(Session s) { this.session = s; }
        @Override
        public Inventory getInventory() { return session.inv; }
    }

    private final ClanPvP plugin;
    private final Map<UUID, Session> sessions = new HashMap<>();

    public LootManager(ClanPvP plugin) {
        this.plugin = plugin;
    }

    public void shutdown() {
        for (Session s : new ArrayList<>(sessions.values())) cancel(s);
    }

    // ---------------------------------------------------------------- Hilfsmethoden

    private static boolean has(ItemStack i) {
        return i != null && i.getType() != Material.AIR && i.getAmount() > 0;
    }

    private static ItemStack[] snapshot(PlayerInventory inv) {
        ItemStack[] src = inv.getContents();
        ItemStack[] out = new ItemStack[src.length];
        for (int i = 0; i < src.length; i++) out[i] = has(src[i]) ? src[i].clone() : null;
        return out;
    }

    private static boolean anyItem(ItemStack[] arr) {
        for (ItemStack i : arr) if (has(i)) return true;
        return false;
    }

    private static String describe(ItemStack it) {
        String n = it.getType().name().toLowerCase().replace('_', ' ');
        return it.getAmount() + "x " + n;
    }

    static void removeStack(PlayerInventory inv, ItemStack stack) {
        int left = stack.getAmount();
        for (int i = 0; i < 41 && left > 0; i++) {
            ItemStack cur = inv.getItem(i);
            if (!has(cur) || !cur.isSimilar(stack)) continue;
            int take = Math.min(left, cur.getAmount());
            if (take >= cur.getAmount()) {
                inv.setItem(i, null);
            } else {
                ItemStack c2 = cur.clone();
                c2.setAmount(cur.getAmount() - take);
                inv.setItem(i, c2);
            }
            left -= take;
        }
    }

    // ---------------------------------------------------------------- Session

    void start(Player killer, UUID victimId, String victimName, ItemStack[] snap) {
        if (!anyItem(snap) || sessions.containsKey(victimId) || !killer.isOnline()) return;
        Session s = new Session(killer, victimId, victimName, snap);
        s.inv = Bukkit.createInventory(new LootHolder(s), 45,
                ChatColor.DARK_RED + "Beute von " + victimName + ChatColor.GRAY + " - wähle 1 Item");
        for (int i = 0; i < snap.length && i < 45; i++) s.inv.setItem(i, snap[i]);
        sessions.put(victimId, s);

        long ticks = plugin.getConfig().getInt("auswahl-sekunden", 30) * 20L;
        s.timeout = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!s.done) autoPick(s);
        }, ticks);

        killer.sendMessage(ClanCommand.PREFIX + ChatColor.GREEN + "Wähle 1 Item von " + ChatColor.GOLD + victimName
                + ChatColor.GREEN + " aus!");
        killer.openInventory(s.inv);
    }

    private void cancel(Session s) {
        s.done = true;
        if (s.timeout != null) s.timeout.cancel();
        sessions.remove(s.victimId);
        if (s.killer.isOnline() && s.killer.getOpenInventory().getTopInventory().getHolder() instanceof LootHolder) {
            s.killer.closeInventory();
        }
    }

    private void autoPick(Session s) {
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < s.items.length; i++) if (has(s.items[i])) idx.add(i);
        if (idx.isEmpty() || !s.killer.isOnline()) {
            cancel(s);
            return;
        }
        finish(s, idx.get(ThreadLocalRandom.current().nextInt(idx.size())));
    }

    private void finish(Session s, int slot) {
        if (s.done) return;
        s.done = true;
        if (s.timeout != null) s.timeout.cancel();
        sessions.remove(s.victimId);

        boolean restToVictim = plugin.getConfig().getBoolean("rest-an-opfer", true);
        List<Integer> take = new ArrayList<>();
        if (restToVictim) {
            take.add(slot);
        } else {
            for (int i = 0; i < s.items.length; i++) if (has(s.items[i])) take.add(i);
        }

        Player killer = s.killer;
        Player victim = Bukkit.getPlayer(s.victimId);
        for (int i : take) {
            ItemStack it = s.items[i];
            if (!has(it)) continue;
            if (victim != null && victim.isOnline()) {
                removeStack(victim.getInventory(), it);
            } else {
                plugin.data().addPendingLoss(s.victimId, it.clone());
            }
            Map<Integer, ItemStack> left = killer.getInventory().addItem(it.clone());
            for (ItemStack r : left.values()) killer.getWorld().dropItemNaturally(killer.getLocation(), r);
        }

        ItemStack chosen = s.items[slot];
        killer.closeInventory();
        killer.sendMessage(ClanCommand.PREFIX + ChatColor.GREEN + "Du hast "
                + ChatColor.GOLD + (chosen != null ? describe(chosen) : "ein Item")
                + ChatColor.GREEN + " von " + s.victimName + " genommen.");
        if (victim != null && victim.isOnline()) {
            victim.sendMessage(ClanCommand.PREFIX + ChatColor.RED + killer.getName() + " hat dir "
                    + (chosen != null ? describe(chosen) : "ein Item") + " abgenommen."
                    + (restToVictim ? ChatColor.GRAY + " Den Rest hast du behalten." : ""));
        }
    }

    // ---------------------------------------------------------------- Events

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        CombatManager cm = plugin.combat();
        Player killer = victim.getKiller();
        if (killer == null && cm.isInCombat(victim.getUniqueId())) {
            UUID opp = cm.getOpponent(victim.getUniqueId());
            if (opp != null) killer = Bukkit.getPlayer(opp);
        }
        cm.clear(victim.getUniqueId());
        if (killer == null || killer.equals(victim)) return;

        ItemStack[] snap = snapshot(victim.getInventory());
        if (!anyItem(snap)) return;

        // Opfer behält sein Inventar, der Killer nimmt sich daraus 1 Item
        e.setKeepInventory(true);
        e.getDrops().clear();

        final Player k = killer;
        Bukkit.getScheduler().runTask(plugin, () -> start(k, victim.getUniqueId(), victim.getName(), snap));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();

        // Killer verlässt den Server während der Auswahl -> nichts wird genommen
        for (Session s : new ArrayList<>(sessions.values())) {
            if (s.killer.getUniqueId().equals(id)) cancel(s);
        }

        CombatManager cm = plugin.combat();
        if (!cm.isInCombat(id)) return;
        UUID oppId = cm.getOpponent(id);
        cm.clear(id);
        Bukkit.broadcastMessage(ClanCommand.PREFIX + ChatColor.RED + p.getName()
                + ChatColor.GRAY + " hat sich im Kampf ausgeloggt!");

        Player opp = oppId == null ? null : Bukkit.getPlayer(oppId);
        if (opp == null || !opp.isOnline() || opp.equals(p)) return;
        start(opp, id, p.getName(), snapshot(p.getInventory()));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Session s = sessions.get(p.getUniqueId());
        if (s != null && s.killer.isOnline()) autoPick(s);

        List<ItemStack> pending = plugin.data().takePending(p.getUniqueId());
        if (!pending.isEmpty()) {
            for (ItemStack it : pending) removeStack(p.getInventory(), it);
            p.sendMessage(ClanCommand.PREFIX + ChatColor.RED + "Du wurdest im Kampf besiegt/ausgeloggt - "
                    + pending.size() + " Item(s) wurden dir abgenommen.");
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder() instanceof LootHolder h)) return;
        e.setCancelled(true);
        if (e.getClickedInventory() != top) return;
        Session s = h.session;
        if (s.done || !e.getWhoClicked().getUniqueId().equals(s.killer.getUniqueId())) return;
        int slot = e.getRawSlot();
        if (slot < 0 || slot >= s.items.length || !has(s.items[slot])) return;
        finish(s, slot);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof LootHolder) e.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof LootHolder h)) return;
        Session s = h.session;
        if (s.done || !plugin.isEnabled()) return;
        // Menü wieder öffnen, bis ein Item gewählt wurde (oder die Zeit abläuft)
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!s.done && s.killer.isOnline()) s.killer.openInventory(s.inv);
        });
    }
}
