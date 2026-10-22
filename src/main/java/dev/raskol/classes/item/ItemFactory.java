package dev.raskol.classes.item;

import dev.raskol.classes.RaskolClasses;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Creates item stacks from validated registry definitions. */
public final class ItemFactory {
    private final ItemPdc pdc;
    public ItemFactory(RaskolClasses plugin) { pdc=new ItemPdc(plugin); }
    public ItemStack create(ItemDefinition def,long seed) {
        Material material=Material.matchMaterial(def.material());
        if(material==null || !material.isItem()) throw new IllegalArgumentException("Invalid material: "+def.material());
        ItemStack item=new ItemStack(material); ItemMeta meta=item.getItemMeta();
        meta.displayName(Component.text(color(def.displayName()), def.rarity()==ItemRarity.EPIC?NamedTextColor.LIGHT_PURPLE:
                def.rarity()==ItemRarity.RARE?NamedTextColor.BLUE:NamedTextColor.WHITE));
        List<Component> lore=new ArrayList<>();
        for(String line:def.lore()) lore.add(Component.text(color(line),NamedTextColor.GRAY));
        String requirement=def.playerClass()==null?"Любой класс":def.playerClass().getDisplayName();
        if(def.spec()!=null)requirement+=" · "+def.spec();
        requirement+=" · уровень "+def.levelRequirement();
        lore.add(Component.text("Требуется: "+requirement,NamedTextColor.RED));
        Map<ItemStat,Double> affixValues=AffixRoller.roll(def.affixPool(),def.affixCount(),def.itemLevel(),seed);
        def.baseStats().forEach((stat,value)->lore.add(Component.text("+"+String.format(java.util.Locale.ROOT,"%.1f",value)+" "+stat.id(),NamedTextColor.GREEN)));
        affixValues.forEach((stat,value)->lore.add(Component.text("+"+String.format(java.util.Locale.ROOT,"%.1f",value)+" "+stat.id()+" (аффикс)",NamedTextColor.AQUA)));
        lore.add(Component.text(def.rarity()+" · ур. "+def.itemLevel(),NamedTextColor.DARK_GRAY));
        meta.lore(lore); meta.setEnchantmentGlintOverride(def.glow()); if(def.customModelData()!=null)meta.setCustomModelData(def.customModelData());
        item.setItemMeta(meta); pdc.write(item,def,seed,affixValues); return item;
    }
    public ItemPdc pdc(){return pdc;}
    private static String color(String value){return value.replace('&','§');}
}
