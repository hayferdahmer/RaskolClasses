// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.combat;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;

/**
 * 1.6.0 пакет 3: «Пропитанный маной» как честный резист.
 * Старый механизм (−15% входящего урона через событие в PassiveListener)
 * выключается конфигом classes.MAGE.passives.mana_soaked.enabled: false.
 * Здесь: раз в 20 тиков проверяем ману мага; при мана ≥ порога ставим
 * permanent-модификатор mana_soaked (+физ/+маг из resist.mana-soaked.*),
 * при падении ниже порога — снимаем. Переключение idempotent.
 * FIX 1.6.1 (B2): игрок, сменивший класс с мага, гарантированно теряет
 * модификатор (раньше ветка не-мага делала continue и резист оставался навсегда).
 */
public final class ManaSoakedService {

    private static final String SOURCE = "mana_soaked";

    private final RaskolClasses plugin;

    public ManaSoakedService(RaskolClasses plugin) {
        this.plugin = plugin;
    }

    public BukkitTask start() {
        return plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    private void tick() {
        if (!plugin.getConfig().getBoolean("resist.mana-soaked.enabled", true)) {
            return;
        }
        double threshold = plugin.getConfig()
                .getDouble("classes.MAGE.passives.mana_soaked.threshold", 50.0);
        double phys = plugin.getConfig().getDouble("resist.mana-soaked.physical", 15.0);
        double magic = plugin.getConfig().getDouble("resist.mana-soaked.magic", 15.0);
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            PlayerClass pc = plugin.getClassProvider().getClassOf(player);
            if (pc != PlayerClass.MAGE) {
                // 1.6.1 (B2): экс-маг не должен держать резист «Пропитан маной»
                plugin.getResists().removeModifiersBySource(uuid, SOURCE);
                continue;
            }
            double mana = plugin.getResources().getValue(uuid);
            boolean soaked = mana >= threshold;
            boolean has = plugin.getResists().hasModifier(uuid, SOURCE);
            if (soaked && !has) {
                plugin.getResists().addPermanentModifier(uuid, SOURCE, phys, magic);
            } else if (!soaked && has) {
                plugin.getResists().removeModifiersBySource(uuid, SOURCE);
            }
        }
    }
}
