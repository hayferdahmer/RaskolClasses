package dev.raskol.classes.api.items;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

/** Fired after an item has been created through ItemsApi. */
public final class ItemCreatedEvent extends Event {
    private static final HandlerList HANDLERS=new HandlerList();
    private final ItemStack item;
    public ItemCreatedEvent(ItemStack item){this.item=item.clone();}
    public ItemStack item(){return item.clone();}
    @Override public HandlerList getHandlers(){return HANDLERS;}
    public static HandlerList getHandlerList(){return HANDLERS;}
}
