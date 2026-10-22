package dev.raskol.classes.api.items;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

/** Fired when a future item mutator replaces an item instance. */
public final class ItemMutatedEvent extends Event {
    private static final HandlerList HANDLERS=new HandlerList();
    private final ItemStack before,after;
    public ItemMutatedEvent(ItemStack before,ItemStack after){this.before=before.clone();this.after=after.clone();}
    public ItemStack before(){return before.clone();}
    public ItemStack after(){return after.clone();}
    @Override public HandlerList getHandlers(){return HANDLERS;}
    public static HandlerList getHandlerList(){return HANDLERS;}
}
