package dev.raskol.classes.item;

import dev.raskol.classes.api.combat.CombatProfile;
import dev.raskol.classes.combat.DamageProfile;
import dev.raskol.classes.combat.school.School;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class CombatProfileTest {
    @Test void profileAcceptsValidImmutableCombatContract(){
        var profile=new CombatProfile(30,24,new DamageProfile(70,30,0),20,10,
                Map.of(School.FIRE,5.0),25,15,Map.of(School.FROST,10.0),Set.of(),
                Map.of(School.ARCANE,20.0),15,1.5,10,Set.of("STUN"),25,Map.of("armor",12.0));
        assertEquals(30,profile.level());assertEquals(25,profile.reflect());assertEquals(10,profile.ccResist());
    }
    @Test void profileRejectsOutOfRangeAndNonFiniteValues(){
        assertThrows(IllegalArgumentException.class,()->new CombatProfile(30,24,new DamageProfile(1,0,0),41,0,
                Map.of(),0,0,Map.of(),Set.of(),Map.of(),0,1,0,Set.of(),0,Map.of()));
        assertThrows(IllegalArgumentException.class,()->new CombatProfile(30,24,new DamageProfile(Double.NaN,0,0),0,0,
                Map.of(),0,0,Map.of(),Set.of(),Map.of(),0,1,0,Set.of(),0,Map.of()));
    }
}

