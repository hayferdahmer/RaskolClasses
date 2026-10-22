package dev.raskol.classes.item;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class SetBonusServiceTest {
    @Test void thresholdsAccumulateAndPublishImmutableActiveSets(){
        var set=new SetDefinition("test_set","Test set",Map.of(2,new SetDefinition.Bonus(Map.of(ItemStat.STR,2.0),"+2 STR"),4,new SetDefinition.Bonus(Map.of(ItemStat.AGI,4.0),"+4 AGI"),6,new SetDefinition.Bonus(Map.of(ItemStat.INT,6.0),"+6 INT")));
        var svc=new SetBonusService();svc.definitions(Map.of(set.id(),set));var pieces=java.util.stream.IntStream.range(0,4).mapToObj(i->new ItemDefinition("test_"+i,"T","IRON_CHESTPLATE",ItemSlot.CHEST,null,ItemRarity.RARE,20,null,null,1,Map.of(),List.of(),0,set.id(),List.of(),false,null)).toList();
        Map<ItemStat,Double> stats=new java.util.EnumMap<>(ItemStat.class);svc.apply(UUID.randomUUID(),pieces,stats);
        assertEquals(2,stats.get(ItemStat.STR));assertEquals(4,stats.get(ItemStat.AGI));
    }
}
