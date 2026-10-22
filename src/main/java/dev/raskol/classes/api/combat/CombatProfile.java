package dev.raskol.classes.api.combat;

import dev.raskol.classes.combat.DamageProfile;
import dev.raskol.classes.combat.school.School;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Immutable combat contract for externally managed living entities. Percentages use percentage points. */
public record CombatProfile(int level,double attackDamage,DamageProfile damageWeights,
                            double penPhysPct,double penMagicPct,Map<School,Double> penSchoolPct,
                            double physResist,double magicResist,Map<School,Double> schoolResists,
                            Set<School> immunities,Map<School,Double> vulnerabilities,
                            double critChance,double critMult,double ccResist,Set<String> ccImmunities,
                            double reflect,Map<String,Double> armorStats) {
    public CombatProfile {
        if(level<1||level>60||damageWeights==null||!finiteNonNegative(attackDamage)||!finiteNonNegative(damageWeights.physical())
                ||!finiteNonNegative(damageWeights.magic())||!finiteNonNegative(damageWeights.trueDamage())
                ||!finiteRange(penPhysPct,0,40)||!finiteRange(penMagicPct,0,40)
                ||!finiteRange(physResist,0,60)||!finiteRange(magicResist,0,60)||!finiteRange(critChance,0,100)
                ||!finiteRange(critMult,0,5)||!finiteRange(ccResist,0,50)||!finiteRange(reflect,0,25)||damageWeights==null)
            throw new IllegalArgumentException("Invalid combat profile");
        penSchoolPct=validated(penSchoolPct,0,40,"school penetration");
        schoolResists=validated(schoolResists,0,60,"school resistance");
        vulnerabilities=validated(vulnerabilities,-100,300,"vulnerability");
        armorStats=validated(armorStats,0,1000,"armor stat");
        immunities=Set.copyOf(immunities);ccImmunities=Set.copyOf(ccImmunities);
    }
    private static boolean finiteNonNegative(double n){return Double.isFinite(n)&&n>=0;}
    private static boolean finiteRange(double n,double min,double max){return Double.isFinite(n)&&n>=min&&n<=max;}
    private static <K> Map<K,Double> validated(Map<K,Double> input,double min,double max,String label){
        Map<K,Double> copy=Map.copyOf(input);for(var entry:copy.entrySet())
            if(entry.getKey()==null||entry.getValue()==null||!finiteRange(entry.getValue(),min,max))
                throw new IllegalArgumentException("Invalid "+label);return copy;
    }
}
