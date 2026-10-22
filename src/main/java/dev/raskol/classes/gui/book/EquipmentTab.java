package dev.raskol.classes.gui.book;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.gui.ClassBook;
import dev.raskol.classes.item.ItemDefinition;
import dev.raskol.classes.item.ItemSlot;
import dev.raskol.classes.item.ItemStat;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Equipment and item-stat view in the class book. */
public final class EquipmentTab implements BookTabView {
    @Override public ClassBook.Tab id(){return ClassBook.Tab.GEAR;}
    @Override public void render(RenderCtx ctx){
        Player p=ctx.player();RaskolClasses plugin=ctx.plugin();
        List<ItemStack> equipped=java.util.Arrays.asList(p.getInventory().getItemInMainHand(),p.getInventory().getHelmet(),
                p.getInventory().getChestplate(),p.getInventory().getLeggings(),p.getInventory().getBoots());
        int[] slots={BookSlots.SLOT_GEAR_WEAPON,BookSlots.GEAR_ARMOR_SLOTS[0],BookSlots.GEAR_ARMOR_SLOTS[1],BookSlots.GEAR_ARMOR_SLOTS[2],BookSlots.GEAR_ARMOR_SLOTS[3]};
        for(int i=0;i<equipped.size();i++){
            ItemStack stack=equipped.get(i);ItemDefinition def=plugin.getItems().definitionOf(stack);
            ctx.inv().setItem(slots[i],stack==null||stack.getType().isAir()
                    ?BookItems.infoItem(Material.GRAY_STAINED_GLASS_PANE,"Слот пуст",List.of())
                    :stack.clone());
            if(def==null&&stack!=null&&!stack.getType().isAir())ctx.inv().setItem(slots[i],BookItems.infoItem(stack.getType(),"Обычный предмет",List.of("Статы RaskolClasses не применяются.")));
        }
        Map<ItemStat,Double> stats=plugin.getItems().snapshot(p.getUniqueId()).stats();List<String> lore=new ArrayList<>();
        stats.forEach((stat,value)->lore.add(stat.id()+": +"+String.format(java.util.Locale.ROOT,"%.1f",value)));
        if(lore.isEmpty())lore.add("Предметных статов нет.");
        for(String unmet:plugin.getItems().snapshot(p.getUniqueId()).unmetRequirements())lore.add("§cТребуется: "+unmet);
        ctx.inv().setItem(BookSlots.SLOT_GEAR_STATS,BookItems.infoItem(Material.NETHERITE_INGOT,"Снаряжение и статы",lore));
        var active=plugin.getItems().setBonuses().active(p.getUniqueId());
        for(int i=0;i<BookSlots.GEAR_SET_SLOTS.length;i++){
            if(i<active.size()){var set=active.get(i);ctx.inv().setItem(BookSlots.GEAR_SET_SLOTS[i],BookItems.infoItem(Material.IRON_CHESTPLATE,set.displayName(),List.of("Предметов: "+set.pieces(),"Активные пороги: "+set.activeThresholds())));}
            else ctx.inv().setItem(BookSlots.GEAR_SET_SLOTS[i],BookItems.infoItem(Material.GRAY_STAINED_GLASS_PANE,"Сет",List.of("Нет надетого сета.")));
        }
    }
}
