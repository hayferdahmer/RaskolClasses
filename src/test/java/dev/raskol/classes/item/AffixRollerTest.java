package dev.raskol.classes.item;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AffixRollerTest {
    @Test void sameSeedProducesSameAffixesWithoutDuplicateStats(){
        List<Affix> pool=List.of(new Affix(ItemStat.STR,1,20,1),new Affix(ItemStat.AGI,1,20,3),new Affix(ItemStat.INT,1,20,2));
        var first=AffixRoller.roll(pool,3,30,42L);var second=AffixRoller.roll(pool,3,30,42L);
        assertEquals(first,second);assertEquals(3,first.size());
    }
    @Test void lowAndHighItemLevelsUseOppositeHalvesOfTheRange(){
        var pool=List.of(new Affix(ItemStat.STR,0,100,1));
        double low=AffixRoller.roll(pool,1,1,7).get(ItemStat.STR);
        double high=AffixRoller.roll(pool,1,60,7).get(ItemStat.STR);
        assertTrue(low<50);assertTrue(high>=50);
    }
}
