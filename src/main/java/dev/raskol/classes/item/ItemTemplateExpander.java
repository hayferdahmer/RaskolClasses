package dev.raskol.classes.item;

import dev.raskol.classes.classsystem.PlayerClass;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Expands class/slot templates into level and rarity variants using a stat budget. */
public final class ItemTemplateExpander {
    public record Template(String id, PlayerClass playerClass, ItemSlot slot, WeaponType weaponType,
                           Map<ItemStat, Double> distribution, List<String> names) {
        public Template { distribution=Map.copyOf(distribution); names=List.copyOf(names); }
    }
    private static final Map<ItemRarity,Double> RARITY_MULT=Map.of(ItemRarity.COMMON,1.0,ItemRarity.UNCOMMON,1.15,
            ItemRarity.RARE,1.35,ItemRarity.EPIC,1.60,ItemRarity.LEGENDARY,1.90,ItemRarity.MYTHIC,2.30);
    private static final Map<ItemSlot,Double> SLOT_WEIGHT=Map.of(ItemSlot.MAIN_HAND,1.0,ItemSlot.CHEST,.9,
            ItemSlot.LEGS,.8,ItemSlot.HEAD,.6,ItemSlot.FEET,.5,ItemSlot.OFF_HAND,.5);
    private ItemTemplateExpander() { }
    public static List<Template> starterTemplates() {
        List<Template> result=new ArrayList<>();
        for(PlayerClass pc:PlayerClass.values()) for(ItemSlot slot:List.of(ItemSlot.MAIN_HAND,ItemSlot.HEAD,ItemSlot.CHEST,ItemSlot.LEGS,ItemSlot.FEET)) {
            WeaponType wt=slot==ItemSlot.MAIN_HAND?switch(pc){case WARRIOR->WeaponType.SWORD;case HUNTER->WeaponType.BOW;
                case ROGUE->WeaponType.DAGGER;case PRIEST->WeaponType.MACE;case MAGE,WARLOCK->WeaponType.STAFF;}:null;
            ItemStat primary=switch(pc){case WARRIOR->ItemStat.STR;case HUNTER,ROGUE->ItemStat.AGI;case PRIEST,MAGE,WARLOCK->ItemStat.INT;};
            Map<ItemStat,Double> distribution=new EnumMap<>(ItemStat.class);
            if(slot==ItemSlot.MAIN_HAND){distribution.put(ItemStat.ATTACK_DAMAGE,30.0);distribution.put(pc==PlayerClass.WARRIOR||pc==PlayerClass.HUNTER||pc==PlayerClass.ROGUE?ItemStat.WEAPON_POWER:ItemStat.SPELL_POWER,45.0);distribution.put(pc==PlayerClass.WARRIOR||pc==PlayerClass.HUNTER||pc==PlayerClass.ROGUE?ItemStat.CRIT_MELEE:ItemStat.CRIT_SPELL,25.0);}
            else {distribution.put(ItemStat.HP_BONUS,45.0);distribution.put(slot==ItemSlot.CHEST?ItemStat.PHYS_RESIST:ItemStat.MAGIC_RESIST,30.0);distribution.put(primary,25.0);}
            result.add(new Template(pc.name().toLowerCase(java.util.Locale.ROOT)+"_"+slot.name().toLowerCase(java.util.Locale.ROOT),pc,slot,wt,distribution,List.of(pc.getDisplayName()+" "+slot.name())));
        }
        return List.copyOf(result);
    }
    public static List<ItemDefinition> expand(List<Template> templates, Map<String,String> materials,
                                               List<Integer> levels,List<ItemRarity> rarities) {
        return expand(templates,materials,levels,rarities,RARITY_MULT,SLOT_WEIGHT,Map.of());
    }
    public static List<ItemDefinition> expand(List<Template> templates,Map<String,String> materials,List<Integer> levels,
                                               List<ItemRarity> rarities,Map<ItemRarity,Double> rarityMult,
                                               Map<ItemSlot,Double> slotWeight,Map<ItemStat,Double> statCost) {
        List<ItemDefinition> result=new ArrayList<>();
        for(Template t:templates) for(int level:levels) for(ItemRarity rarity:rarities) {
            double budget=level*slotWeight.getOrDefault(t.slot(),SLOT_WEIGHT.get(t.slot()))*rarityMult.getOrDefault(rarity,RARITY_MULT.get(rarity));
            Map<ItemStat,Double> stats=new EnumMap<>(ItemStat.class);
            t.distribution().forEach((stat,share)->stats.put(stat,Math.round((budget*share/100.0/statCost.getOrDefault(stat,cost(stat)))*10.0)/10.0));
            String material=materials.getOrDefault(t.id(),defaultMaterial(t));
            String id=(t.playerClass().name()+"_"+t.slot().name()+"_"+level+"_"+rarity.name()).toLowerCase(java.util.Locale.ROOT);
            String name=(t.names().isEmpty()?t.playerClass().getDisplayName():t.names().get(0))+" "+rarity.name()+" "+level;
            List<Affix> pool=new ArrayList<>();
            for(ItemStat stat:t.distribution().keySet()) {
                double center=Math.max(.1,stats.getOrDefault(stat,1.0)*.1);
                pool.add(new Affix(stat,Math.max(stat.min(),center),Math.min(stat.max(),center*2.0),1.0));
            }
            int count=switch(rarity){case COMMON->0;case UNCOMMON->1;case RARE->2;case EPIC,LEGENDARY->3;case MYTHIC->4;};
            result.add(new ItemDefinition(id,name,material,t.slot(),t.weaponType(),rarity,level,t.playerClass(),null,
                    Math.min(level,60),stats,pool,Math.min(count,pool.size()),null,List.of(),false,null,t.id()));
        }
        return List.copyOf(result);
    }
    private static double cost(ItemStat stat) {
        return switch(stat){case WEAPON_POWER,SPELL_POWER,HEAL_POWER->1;case STR,AGI,INT->1.5;case HP_BONUS->.25;
            case PHYS_RESIST,MAGIC_RESIST,PEN_PHYS_PCT,PEN_MAGIC_PCT->8;case CRIT_MELEE,CRIT_SPELL,CRIT_DAMAGE->10;
            case ATTACK_DAMAGE->.5;default->1;};
    }
    private static String defaultMaterial(Template t) {
        if(t.slot()==ItemSlot.MAIN_HAND)return switch(t.weaponType()){case BOW->"BOW";case STAFF->"BLAZE_ROD";case DAGGER->"IRON_SWORD";case AXE->"IRON_AXE";case MACE->"MACE";default->"IRON_SWORD";};
        return switch(t.slot()){case HEAD->"IRON_HELMET";case CHEST->"IRON_CHESTPLATE";case LEGS->"IRON_LEGGINGS";case FEET->"IRON_BOOTS";default->"SHIELD";};
    }
}
