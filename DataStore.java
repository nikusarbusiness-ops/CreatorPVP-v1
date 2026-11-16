package de.clanpvp;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Speichert Spielerrekord und Items, die offline gewordenen Spielern noch abgenommen werden müssen. */
public class DataStore {

    private final ClanPvP plugin;
    private final File file;
    private final YamlConfiguration cfg;

    public DataStore(ClanPvP plugin) {
        this.plugin = plugin;
        plugin.getDataFolder().mkdirs();
        this.file = new File(plugin.getDataFolder(), "data.yml");
        this.cfg = YamlConfiguration.loadConfiguration(file);
    }

    public synchronized void save() {
        try {
            cfg.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("data.yml konnte nicht gespeichert werden: " + ex.getMessage());
        }
    }

    public int getRecord() { return cfg.getInt("record.count", 0); }

    public String getRecordDate() { return cfg.getString("record.date", "-"); }

    /** @return true, wenn ein bestehender Rekord (>0) gebrochen wurde */
    public synchronized boolean checkRecord(int online) {
        int old = getRecord();
        if (online <= old) return false;
        cfg.set("record.count", online);
        cfg.set("record.date", LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")));
        save();
        return old > 0;
    }

    public synchronized void addPendingLoss(UUID id, ItemStack item) {
        List<ItemStack> list = new ArrayList<>();
        for (Object o : cfg.getList("pending." + id, new ArrayList<>())) {
            if (o instanceof ItemStack is) list.add(is);
        }
        list.add(item);
        cfg.set("pending." + id, list);
        save();
    }

    public synchronized List<ItemStack> takePending(UUID id) {
        List<ItemStack> list = new ArrayList<>();
        for (Object o : cfg.getList("pending." + id, new ArrayList<>())) {
            if (o instanceof ItemStack is) list.add(is);
        }
        if (!list.isEmpty()) {
            cfg.set("pending." + id, null);
            save();
        }
        return list;
    }
}
