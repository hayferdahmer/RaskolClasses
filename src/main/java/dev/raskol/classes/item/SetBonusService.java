package dev.raskol.classes.item;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Builds set contributions and publishes immutable active-set snapshots. */
public final class SetBonusService {
    public record ActiveSet(String id,String displayName,int pieces,List<Integer> activeThresholds) {
        public ActiveSet { activeThresholds=List.copyOf(activeThresholds); }
    }
    private volatile Map<String,SetDefinition> definitions=Map.of();
    private final Map<UUID,List<ActiveSet>> active=new ConcurrentHashMap<>();
    public void definitions(Map<String,SetDefinition> definitions){this.definitions=Map.copyOf(definitions);}
    public List<ActiveSet> active(UUID player){return active.getOrDefault(player,List.of());}
    public void clear(UUID player){active.remove(player);}
    public void apply(UUID player,List<ItemDefinition> items,Map<ItemStat,Double> stats){
        Map<String,Integer> counts=new HashMap<>();for(ItemDefinition item:items)if(item.setId()!=null)counts.merge(item.setId(),1,Integer::sum);
        List<ActiveSet> result=new ArrayList<>();
        counts.forEach((id,pieces)->{SetDefinition set=definitions.get(id);if(set==null)return;List<Integer> thresholds=new ArrayList<>();
            set.bonuses().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry->{if(pieces>=entry.getKey()){
                thresholds.add(entry.getKey());entry.getValue().stats().forEach((stat,value)->stats.merge(stat,stat.clamp(value),Double::sum));}});
            result.add(new ActiveSet(id,set.displayName(),pieces,thresholds));});
        active.put(player,List.copyOf(result));
    }
}
