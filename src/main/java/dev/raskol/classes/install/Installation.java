// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.install;

import org.bukkit.Location;
import org.bukkit.entity.ItemDisplay;

import java.util.UUID;

/**
 * Живая инсталляция на земле (1.5.0, Пакет 2).
 * Владелец, тип, точка, срок жизни + ItemDisplay-визуал.
 */
public final class Installation {

    private final UUID owner;
    private final InstallationType type;
    private final Location location;
    private final long expiresAt;
    private ItemDisplay display;
    private long lastTick;

    public Installation(UUID owner, InstallationType type, Location location, long expiresAt) {
        this.owner = owner;
        this.type = type;
        this.location = location.clone();
        this.expiresAt = expiresAt;
        this.lastTick = 0L;
    }

    public UUID getOwner() { return owner; }
    public InstallationType getType() { return type; }
    public Location getLocation() { return location; }
    public long getExpiresAt() { return expiresAt; }
    public ItemDisplay getDisplay() { return display; }
    public void setDisplay(ItemDisplay display) { this.display = display; }
    public long getLastTick() { return lastTick; }
    public void setLastTick(long lastTick) { this.lastTick = lastTick; }
}
