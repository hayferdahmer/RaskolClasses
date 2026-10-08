// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.pet;

import org.bukkit.entity.EntityType;

import java.util.Map;
import java.util.Set;

/**
 * 1.14.6: определение боевого пета (реестр, pure).
 * wolf      — Охотник/beastmaster (узел bm_pet_wolf): постоянный до смерти/выхода.
 * demon     — Чернокнижник/demonology (dm_summon_demon): ttl 12 с.
 * shadowfiend — Жрец/shadow (sh_shadowfiend): ttl 8 с.
 *
 * Scaling (дизайн A2): hp = baseHp + attr×2; dmg = baseDmg + power×0.3;
 * attr/power берутся по полям hpAttr ("str"/"int") и powerTag ("wp"/"sp"),
 * затем умножаются на (1 + spec2 pet_hp_pct / pet_dmg_pct / 100) в PetMath.
 * Элементалей Мага в деревьях НЕТ — в реестр не входят.
 */
public record PetDef(String id,
                     EntityType entityType,
                     double baseHp,
                     double baseDmg,
                     int ttlSeconds,
                     String nameTemplate,
                     String hpAttr,
                     String powerTag) {

    private static final Map<String, PetDef> REGISTRY = Map.of(
            "wolf", new PetDef("wolf", EntityType.WOLF,
                    20.0, 4.0, 0, "Волк %s", "str", "wp"),
            "demon", new PetDef("demon", EntityType.VEX,
                    30.0, 8.0, 12, "Демон %s", "int", "sp"),
            "shadowfiend", new PetDef("shadowfiend", EntityType.VEX,
                    12.0, 5.0, 8, "Тенескот %s", "int", "sp"));

    public static PetDef byId(String id) {
        return id == null ? null : REGISTRY.get(id);
    }

    public static Set<String> ids() {
        return REGISTRY.keySet();
    }

    /** «Волк hayferdahmer» (A3). */
    public String displayName(String ownerName) {
        return nameTemplate.formatted(ownerName == null ? "?" : ownerName);
    }

    public boolean temporary() {
        return ttlSeconds > 0;
    }
}
