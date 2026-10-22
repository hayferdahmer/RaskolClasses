package dev.raskol.classes.item;

import dev.raskol.classes.classsystem.PlayerClass;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Pure aggregation of base, rolled and set contributions. */
public final class ItemStatAggregator {
    private ItemStatAggregator() { }
    /** Combines independent sources such as equipment, sets, gems and future enchantments. */
    public static Map<ItemStat, Double> aggregateSources(java.util.Collection<? extends StatSource> sources) {
        Map<ItemStat, Double> result=new EnumMap<>(ItemStat.class);
        for(StatSource source:sources)if(source!=null&&source.stats()!=null)
            source.stats().forEach((stat,value)->{if(stat!=null&&value!=null&&Double.isFinite(value))result.merge(stat,stat.clamp(value),Double::sum);});
        result.replaceAll(ItemStat::clamp);
        return Map.copyOf(result);
    }
    public static Map<ItemStat, Double> aggregate(List<ItemDefinition> definitions,
                                                   List<Map<ItemStat, Double>> rolled,
                                                   PlayerClass playerClass, String spec, int level) {
        Map<ItemStat, Double> result = new EnumMap<>(ItemStat.class);
        for (int i = 0; i < definitions.size(); i++) {
            ItemDefinition def = definitions.get(i);
            if (!eligible(def, playerClass, spec, level)) continue;
            def.baseStats().forEach((stat, value) -> result.merge(stat, stat.clamp(value), Double::sum));
            if (i < rolled.size()) rolled.get(i).forEach((stat, value) -> result.merge(stat, stat.clamp(value), Double::sum));
        }
        result.replaceAll((stat, value) -> stat.clamp(value));
        return Map.copyOf(result);
    }
    public static boolean eligible(ItemDefinition def, PlayerClass pc, String spec, int level) {
        return (def.playerClass() == null || def.playerClass() == pc)
                && (def.spec() == null || def.spec().equalsIgnoreCase(spec)) && level >= def.levelRequirement();
    }
}
