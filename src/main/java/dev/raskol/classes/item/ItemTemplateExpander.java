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
                           Map<ItemStat, Double> distribution, List<String> names, String setId,
                           Integer customModelData) {
        public Template { distribution=Map.copyOf(distribution); names=List.copyOf(names); }
        public Template(String id, PlayerClass playerClass, ItemSlot slot, WeaponType weaponType,
                        Map<ItemStat, Double> distribution, List<String> names) {
            this(id, playerClass, slot, weaponType, distribution, names, null, null);
        }
        public Template(String id, PlayerClass playerClass, ItemSlot slot, WeaponType weaponType,
                        Map<ItemStat, Double> distribution, List<String> names, String setId) {
            this(id, playerClass, slot, weaponType, distribution, names, setId, null);
        }
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
            result.add(new Template(pc.name().toLowerCase(java.util.Locale.ROOT)+"_"+slot.name().toLowerCase(java.util.Locale.ROOT),pc,slot,wt,distribution,List.of(starterName(pc,slot))));
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
            String id=(t.id()+"_"+level+"_"+rarity.name()).toLowerCase(java.util.Locale.ROOT);
            String name=(t.names().isEmpty()?t.playerClass().getDisplayName():t.names().get(0))
                    +" · "+rarityName(rarity)+" · ур. "+level;
            List<Affix> pool=new ArrayList<>();
            for(ItemStat stat:t.distribution().keySet()) {
                double center=Math.max(.1,stats.getOrDefault(stat,1.0)*.1);
                pool.add(new Affix(stat,Math.max(stat.min(),center),Math.min(stat.max(),center*2.0),1.0));
            }
            int count=switch(rarity){case COMMON->0;case UNCOMMON->1;case RARE->2;case EPIC,LEGENDARY->3;case MYTHIC->4;};
            result.add(new ItemDefinition(id,name,material,t.slot(),t.weaponType(),rarity,level,t.playerClass(),null,
                    Math.min(level,60),stats,pool,Math.min(count,pool.size()),t.setId(),List.of(),false,t.customModelData(),t.id()));
        }
        return List.copyOf(result);
    }
    private static double cost(ItemStat stat) {
        return switch(stat){case WEAPON_POWER,SPELL_POWER,HEAL_POWER->1;case STR,AGI,INT->1.5;case HP_BONUS->.25;
            case PHYS_RESIST,MAGIC_RESIST,PEN_PHYS_PCT,PEN_MAGIC_PCT->8;case CRIT_MELEE,CRIT_SPELL,CRIT_DAMAGE->10;
            case ATTACK_DAMAGE->.5;default->1;};
    }
    private static String defaultMaterial(Template t) {
        if(t.slot()==ItemSlot.MAIN_HAND)return switch(t.weaponType()){case BOW->"BOW";case CROSSBOW->"CROSSBOW";case SPEAR->"TRIDENT";case STAFF->"BLAZE_ROD";case WAND->"BLAZE_ROD";case DAGGER->"IRON_SWORD";case AXE->"IRON_AXE";case MACE->"MACE";case SWORD->"IRON_SWORD";};
        return switch(t.slot()){case HEAD->"IRON_HELMET";case CHEST->"IRON_CHESTPLATE";case LEGS->"IRON_LEGGINGS";case FEET->"IRON_BOOTS";default->"SHIELD";};
    }
    private static String rarityName(ItemRarity rarity) {
        return switch(rarity) { case COMMON -> "Обычный"; case UNCOMMON -> "Необычный"; case RARE -> "Редкий";
            case EPIC -> "Эпический"; case LEGENDARY -> "Легендарный"; case MYTHIC -> "Мифический"; };
    }
    private static String starterName(PlayerClass playerClass,ItemSlot slot) {
        if(slot==ItemSlot.MAIN_HAND)return switch(playerClass){
            case WARRIOR->"Меч дружинника";case HUNTER->"Лук дальнего следа";case ROGUE->"Клинок тихой руки";
            case PRIEST->"Булава милосердия";case MAGE->"Посох расколотой звезды";case WARLOCK->"Посох девятого угля";};
        return switch(playerClass){
            case WARRIOR->switch(slot){case HEAD->"Шлем Кровавого дозора";case CHEST->"Кираса Железной клятвы";case LEGS->"Поножи бастиона";default->"Сапоги штурмовика";};
            case HUNTER->switch(slot){case HEAD->"Капюшон дальнего следа";case CHEST->"Куртка следопыта";case LEGS->"Поножи тихой тропы";default->"Сапоги без следа";};
            case ROGUE->switch(slot){case HEAD->"Маска серого плаща";case CHEST->"Дублет невидимой руки";case LEGS->"Штаны ночного дозора";default->"Сапоги бесшумного шага";};
            case PRIEST->switch(slot){case HEAD->"Митра Белого Пламени";case CHEST->"Облачение милосердия";case LEGS->"Поножи хранителя";default->"Сандалии последней молитвы";};
            case MAGE->switch(slot){case HEAD->"Диадема расколотой звезды";case CHEST->"Мантия небесного осколка";case LEGS->"Поножи астролога";default->"Сапоги между мирами";};
            case WARLOCK->switch(slot){case HEAD->"Капюшон девятого угля";case CHEST->"Кираса чёрной печати";case LEGS->"Поножи связанного духа";default->"Сапоги пепельного пути";};
        };
    }
}
