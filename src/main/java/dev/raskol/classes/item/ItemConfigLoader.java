package dev.raskol.classes.item;

import dev.raskol.classes.classsystem.PlayerClass;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Validates and loads item definitions and expands configured templates. */
public final class ItemConfigLoader {
    private ItemConfigLoader() { }
    public static ItemRegistry load(File file) throws Exception {
        return load(file,ignored->{});
    }
    public static ItemRegistry load(File file,java.util.function.Consumer<String> warning) throws Exception {
        YamlConfiguration yaml=YamlConfiguration.loadConfiguration(file);
        if(yaml.getInt("schema-version", -1)!=1) throw new IllegalArgumentException("items.yml schema-version must be 1");
        int maxLevel=yaml.getInt("settings.max-item-level",60);if(maxLevel<1||maxLevel>60)throw new IllegalArgumentException("settings.max-item-level must be 1..60");
        List<ItemDefinition> defs=new ArrayList<>();
        ConfigurationSection entries=yaml.getConfigurationSection("items");
        if(entries!=null) for(String id:entries.getKeys(false)) {
            ConfigurationSection s=entries.getConfigurationSection(id);
            if(s==null) throw new IllegalArgumentException("items."+id+" must be a section");
            try { ItemDefinition def=parse(id,s);if(def.itemLevel()>maxLevel)throw new IllegalArgumentException("item-level exceeds settings.max-item-level");validateWeaponType(yaml,def);defs.add(def); } catch(RuntimeException ex) { warning.accept("Предмет "+id+" отключён: "+ex.getMessage()); }
        }
        ConfigurationSection templates=yaml.getConfigurationSection("templates");
        {
            List<ItemTemplateExpander.Template> ts=new ArrayList<>(); Map<String,String> materials=new java.util.HashMap<>();
            if(templates!=null) for(String id:templates.getKeys(false)) {
                ConfigurationSection s=templates.getConfigurationSection(id); if(s==null) continue;
                PlayerClass pc=PlayerClass.valueOf(s.getString("class", "").toUpperCase(Locale.ROOT));
                ItemSlot slot=ItemSlot.valueOf(s.getString("slot", "").toUpperCase(Locale.ROOT));
                WeaponType wt=s.contains("weapon-type")?WeaponType.valueOf(s.getString("weapon-type").toUpperCase(Locale.ROOT)):null;
                if((slot==ItemSlot.MAIN_HAND)!=(wt!=null))throw new IllegalArgumentException("template weapon type must match MAIN_HAND: "+id);
                Map<ItemStat,Double> distribution=statMap(s.getConfigurationSection("distribution"),true);
                List<String> names=s.getStringList("names");
                if(s.contains("material"))materials.put(id,s.getString("material"));
                ts.add(new ItemTemplateExpander.Template(id,pc,slot,wt,distribution,names));
            }
            if(ts.isEmpty()) ts.addAll(ItemTemplateExpander.starterTemplates());
            for(Map<?,?> generated:yaml.getMapList("generate")) {
                Object template=generated.get("template"), material=generated.get("material");
                if(template!=null&&material!=null) materials.put(template.toString(),material.toString());
            }
            List<Integer> levels=yaml.getIntegerList("settings.generated-levels");
            if(levels.isEmpty()) levels=List.of(20,40,60);
            if(levels.stream().anyMatch(level->level<1||level>maxLevel)||levels.stream().distinct().count()!=levels.size())throw new IllegalArgumentException("settings.generated-levels must be unique values up to settings.max-item-level");
            ConfigurationSection budget=yaml.getConfigurationSection("budget");
            List<ItemDefinition> expanded=ItemTemplateExpander.expand(ts,materials,levels,rarityList(yaml),
                    enumDoubleMap(budget==null?null:budget.getConfigurationSection("rarity-mult"),ItemRarity.class),
                    enumDoubleMap(budget==null?null:budget.getConfigurationSection("slot-weight"),ItemSlot.class),
                    enumDoubleMap(budget==null?null:budget.getConfigurationSection("stat-cost"),ItemStat.class));
            for(ItemDefinition def:expanded){Material material=Material.matchMaterial(def.material());if(material==null||!material.isItem())throw new IllegalArgumentException("Template "+def.id()+" uses invalid item material "+def.material());validateWeaponType(yaml,def);}
            defs.addAll(expanded);
        }
        Map<String,SetDefinition> sets=new java.util.LinkedHashMap<>();ConfigurationSection setSections=yaml.getConfigurationSection("sets");
        if(setSections!=null)for(String id:setSections.getKeys(false)){
            ConfigurationSection set=setSections.getConfigurationSection(id);if(set==null)continue;
            Map<Integer,SetDefinition.Bonus> bonuses=new java.util.LinkedHashMap<>();ConfigurationSection section=set.getConfigurationSection("bonuses");
            if(section!=null)for(String threshold:section.getKeys(false)){
                int pieces=Integer.parseInt(threshold);ConfigurationSection bonus=section.getConfigurationSection(threshold);if(bonus==null)continue;
                bonuses.put(pieces,new SetDefinition.Bonus(statMap(bonus.getConfigurationSection("stats"),false),bonus.getString("description","")));
            }
            sets.put(id,new SetDefinition(id,set.getString("display-name",id),bonuses));
        }
        defs.removeIf(def->{if(def.setId()!=null&&!sets.containsKey(def.setId())){warning.accept("Предмет "+def.id()+" отключён: неизвестный сет "+def.setId());return true;}return false;});
        java.util.Set<String> templateIds=templates==null?java.util.Set.of():templates.getKeys(false);
        if(templateIds.isEmpty())templateIds=ItemTemplateExpander.starterTemplates().stream().map(ItemTemplateExpander.Template::id).collect(java.util.stream.Collectors.toSet());
        java.util.Set<String> validTemplates=templateIds;
        defs.removeIf(def->{if(def.template()!=null&&!validTemplates.contains(def.template())){warning.accept("Предмет "+def.id()+" отключён: неизвестный шаблон "+def.template());return true;}return false;});
        return new ItemRegistry(defs,sets);
    }
    private static List<ItemRarity> rarityList(YamlConfiguration y) {
        List<String> values=y.getStringList("settings.generated-rarities");
        List<ItemRarity> result=values.isEmpty()?List.of(ItemRarity.COMMON,ItemRarity.RARE,ItemRarity.EPIC):values.stream()
                .map(v->ItemRarity.valueOf(v.toUpperCase(Locale.ROOT))).toList();
        if(result.stream().distinct().count()!=result.size())throw new IllegalArgumentException("Duplicate generated rarity");
        return result;
    }
    private static ItemDefinition parse(String id,ConfigurationSection s) {
        ItemSlot slot=ItemSlot.valueOf(s.getString("slot", "").toUpperCase(Locale.ROOT));
        String material=s.getString("material", "");
        if(Material.matchMaterial(material)==null) throw new IllegalArgumentException("unknown material "+material);
        if((slot==ItemSlot.HEAD&&!material.endsWith("_HELMET"))||(slot==ItemSlot.CHEST&&!material.endsWith("_CHESTPLATE"))
                ||(slot==ItemSlot.LEGS&&!material.endsWith("_LEGGINGS"))||(slot==ItemSlot.FEET&&!material.endsWith("_BOOTS")))
            throw new IllegalArgumentException("material does not fit slot "+slot);
        String cls=s.getString("class","ALL");
        PlayerClass pc="ALL".equalsIgnoreCase(cls)?null:PlayerClass.valueOf(cls.toUpperCase(Locale.ROOT));
        String spec=s.getString("spec");
        if(spec!=null){dev.raskol.classes.spec.Spec parsed=dev.raskol.classes.spec.Spec.fromId(spec);if(parsed==null)throw new IllegalArgumentException("unknown spec "+spec);
            if(pc==null||parsed.playerClass()!=pc)throw new IllegalArgumentException("spec does not belong to item class");}
        List<Affix> pool=new ArrayList<>();
        for(Map<?,?> a:s.getMapList("affix-pool")) {
            ItemStat stat=requiredStat(a.get("stat"));
            pool.add(new Affix(stat,number(a.get("min")),number(a.get("max")),number(a.get("weight"))));
        }
        Integer custom=s.contains("custom-model-data")?s.getInt("custom-model-data"):null;
        return new ItemDefinition(id,s.getString("display-name",id),material,slot,
                s.contains("weapon-type")?WeaponType.valueOf(s.getString("weapon-type").toUpperCase(Locale.ROOT)):null,
                ItemRarity.valueOf(s.getString("rarity","").toUpperCase(Locale.ROOT)),s.getInt("item-level",1),pc,
                spec,s.getInt("level-requirement",1),statMap(s.getConfigurationSection("base-stats"),false),
                pool,s.getInt("affix-count",0),s.getString("set"),s.getStringList("lore"),s.getBoolean("glow",false),custom,s.getString("template"));
    }
    private static void validateWeaponType(YamlConfiguration yaml,ItemDefinition def){
        if(def.slot()!=ItemSlot.MAIN_HAND)return;
        ConfigurationSection type=yaml.getConfigurationSection("settings.weapon-types."+def.weaponType().name());
        if(type==null)throw new IllegalArgumentException("weapon type not configured: "+def.weaponType());
        List<String> materials=type.getStringList("materials");boolean materialAllowed=materials.isEmpty()||materials.stream().anyMatch(m->m.equals(def.material())||(m.startsWith("*")&&def.material().endsWith(m.substring(1))));
        if(!materialAllowed)throw new IllegalArgumentException("material not allowed for "+def.weaponType());
        List<String> classes=type.getStringList("classes");if(def.playerClass()!=null&&!classes.contains("ALL")&&!classes.contains(def.playerClass().name()))throw new IllegalArgumentException("weapon type not allowed for class "+def.playerClass());
    }
    private static Map<ItemStat,Double> statMap(ConfigurationSection s,boolean percentages) {
        Map<ItemStat,Double> map=new EnumMap<>(ItemStat.class); if(s==null)return map;
        double sum=0;
        for(String key:s.getKeys(false)) { ItemStat stat=ItemStat.fromId(key); if(stat==null)throw new IllegalArgumentException("unknown stat "+key);
            double val=s.getDouble(key,Double.NaN); if(!Double.isFinite(val)||val<0||(percentages&&val>100))throw new IllegalArgumentException("invalid stat value "+key);
            if(!percentages&&(val<stat.min()||val>stat.max())) throw new IllegalArgumentException("stat out of range "+key);
            map.put(stat,val);sum+=val; }
        if(percentages&&Math.abs(sum-100)>0.01)throw new IllegalArgumentException("template distribution must total 100");
        return map;
    }
    private static <E extends Enum<E>> Map<E,Double> enumDoubleMap(ConfigurationSection section,Class<E> type){
        if(section==null)return Map.of();Map<E,Double> result=new java.util.EnumMap<>(type);
        for(String key:section.getKeys(false)){E enumKey;
            if(type==ItemStat.class){ItemStat stat=ItemStat.fromId(key);if(stat==null)throw new IllegalArgumentException("unknown budget stat: "+key);enumKey=type.cast(stat);}
            else enumKey=Enum.valueOf(type,key.toUpperCase(Locale.ROOT));
            double value=section.getDouble(key,Double.NaN);
            if(!Double.isFinite(value)||value<=0)throw new IllegalArgumentException("invalid budget value: "+key);result.put(enumKey,value);}
        return Map.copyOf(result);
    }
    private static ItemStat requiredStat(Object value){ ItemStat s=ItemStat.fromId(String.valueOf(value)); if(s==null)throw new IllegalArgumentException("unknown stat "+value);return s; }
    private static double number(Object value){if(!(value instanceof Number n)||!Double.isFinite(n.doubleValue()))throw new IllegalArgumentException("expected finite number");return n.doubleValue();}
}
