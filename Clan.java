package de.clanpvp;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class Clan {
    public String name;
    public String tag;
    public UUID leader;
    public final Map<UUID, String> members = new LinkedHashMap<>();

    public Clan(String name, String tag, UUID leader) {
        this.name = name;
        this.tag = tag;
        this.leader = leader;
    }
}
