package de.clanpvp;

import org.bukkit.plugin.java.JavaPlugin;

public class ClanPvP extends JavaPlugin {

    private DataStore data;
    private ClanManager clans;
    private CombatManager combat;
    private LootManager loot;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        data = new DataStore(this);
        clans = new ClanManager(this);
        combat = new CombatManager(this);
        loot = new LootManager(this);

        getServer().getPluginManager().registerEvents(clans, this);
        getServer().getPluginManager().registerEvents(combat, this);
        getServer().getPluginManager().registerEvents(loot, this);

        ClanCommand clanCommand = new ClanCommand(this);
        getCommand("clan").setExecutor(clanCommand);
        getCommand("clan").setTabCompleter(clanCommand);
        getCommand("cc").setExecutor(clanCommand);

        RekordCommand rekord = new RekordCommand(this);
        getCommand("rekord").setExecutor(rekord);
        getServer().getPluginManager().registerEvents(rekord, this);

        // Tags für bereits online Spieler (z. B. nach /reload)
        getServer().getOnlinePlayers().forEach(clans::applyTag);
    }

    @Override
    public void onDisable() {
        if (loot != null) loot.shutdown();
        if (clans != null) clans.save();
        if (data != null) data.save();
    }

    public DataStore data() { return data; }
    public ClanManager clans() { return clans; }
    public CombatManager combat() { return combat; }
    public LootManager loot() { return loot; }
}
