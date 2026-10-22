package dev.raskol.classes.gui.book;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.item.ItemDefinition;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import java.util.List;
import java.util.UUID;

/** Admin inventory browser with page navigation and item issuance. */
public final class ItemBrowserMenu implements Listener {
    private final RaskolClasses plugin;
    public ItemBrowserMenu(RaskolClasses plugin){this.plugin=plugin;}
    public void open(Player player,int page){open(player,page,"ALL","ALL","ALL");}
    public void open(Player player,int page,String classFilter,String slotFilter,String rarityFilter){
        String cls=normalize(classFilter),slot=normalize(slotFilter),rarity=normalize(rarityFilter);
        List<ItemDefinition> all=filtered(cls,slot,rarity);
        int pages=Math.max(1,(all.size()+44)/45);int safe=Math.max(0,Math.min(page,pages-1));Holder holder=new Holder(player.getUniqueId(),safe,cls,slot,rarity);
        Inventory inventory=Bukkit.createInventory(holder,54,Component.text("Предметы · "+(safe+1)+"/"+pages,NamedTextColor.DARK_PURPLE));holder.inventory=inventory;
        int offset=safe*45;for(int i=offset;i<Math.min(offset+45,all.size());i++){
            ItemDefinition def=all.get(i);ItemStack stack=plugin.getItems().factory().create(def,i+safe*1000L);
            stack.editMeta(meta->{List<Component> lore=meta.lore()==null?new java.util.ArrayList<>():new java.util.ArrayList<>(meta.lore());lore.add(Component.text("Кликните, чтобы выдать себе",NamedTextColor.YELLOW));meta.lore(lore);});inventory.setItem(i-offset,stack);
        }
        inventory.setItem(45,button(Material.ARROW,"Назад"));
        inventory.setItem(46,button(Material.NAME_TAG,"Класс: "+cls));inventory.setItem(47,button(Material.CHEST,"Слот: "+slot));
        inventory.setItem(48,button(Material.NETHER_STAR,"Редкость: "+rarity));
        inventory.setItem(49,button(Material.BARRIER,"Закрыть"));inventory.setItem(53,button(Material.ARROW,"Далее"));player.openInventory(inventory);
    }
    private List<ItemDefinition> filtered(String cls,String slot,String rarity){return plugin.getItems().registry().all().stream()
                .filter(d->"ALL".equals(cls)||d.playerClass()!=null&&d.playerClass().name().equals(cls))
                .filter(d->"ALL".equals(slot)||d.slot().name().equals(slot))
                .filter(d->"ALL".equals(rarity)||d.rarity().name().equals(rarity))
                .sorted(java.util.Comparator.comparing(ItemDefinition::id)).toList();}
    @EventHandler public void click(InventoryClickEvent event){
        if(!(event.getView().getTopInventory().getHolder(false) instanceof Holder holder))return;
        event.setCancelled(true);if(!(event.getWhoClicked() instanceof Player player)||!player.getUniqueId().equals(holder.owner))return;
        int slot=event.getRawSlot();if(slot<0||slot>=54)return;
        if(slot==49){player.closeInventory();return;}if(slot==45){open(player,holder.page-1,holder.cls,holder.slot,holder.rarity);return;}
        if(slot==53){open(player,holder.page+1,holder.cls,holder.slot,holder.rarity);return;}
        if(slot==46){open(player,0,next(holder.cls,java.util.Arrays.stream(dev.raskol.classes.classsystem.PlayerClass.values()).map(Enum::name).toList()),holder.slot,holder.rarity);return;}
        if(slot==47){open(player,0,holder.cls,next(holder.slot,java.util.Arrays.stream(ItemSlot.values()).map(Enum::name).toList()),holder.rarity);return;}
        if(slot==48){open(player,0,holder.cls,holder.slot,next(holder.rarity,java.util.Arrays.stream(ItemRarity.values()).map(Enum::name).toList()));return;}
        if(slot>=45)return;
        List<ItemDefinition> all=filtered(holder.cls,holder.slot,holder.rarity);int index=holder.page*45+slot;
        if(index>=all.size())return;ItemDefinition def=all.get(index);var leftovers=player.getInventory().addItem(plugin.getItemsApi().create(def.id(),def.itemLevel(),java.util.concurrent.ThreadLocalRandom.current().nextLong()));
        leftovers.values().forEach(item->player.getWorld().dropItemNaturally(player.getLocation(),item));player.sendMessage(Component.text("Выдан предмет: "+def.displayName(),NamedTextColor.GREEN));
    }
    private static String normalize(String value){if(value==null||value.isBlank())return "ALL";return value.toUpperCase(java.util.Locale.ROOT);}
    private static String next(String current,List<String> values){List<String> cycle=new java.util.ArrayList<>();cycle.add("ALL");cycle.addAll(values);int index=cycle.indexOf(current);return cycle.get((index+1)%cycle.size());}
    private static ItemStack button(Material type,String name){ItemStack stack=new ItemStack(type);stack.editMeta(meta->meta.displayName(Component.text(name,NamedTextColor.YELLOW)));return stack;}
    private static final class Holder implements InventoryHolder {
        private final UUID owner;private final int page;private final String cls,slot,rarity;private Inventory inventory;
        private Holder(UUID owner,int page,String cls,String slot,String rarity){this.owner=owner;this.page=page;this.cls=cls;this.slot=slot;this.rarity=rarity;}
        @Override public Inventory getInventory(){return inventory;}
    }
}

