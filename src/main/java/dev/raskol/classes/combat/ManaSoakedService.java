// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.combat;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 1.6.0 пакет 3: «Пропитанный маной» как честный резист (+физ/+маг при мана ≥ порога).
 * Старый механизм (−15% урона через событие) выключен конфигом
 * classes.MAGE.passives.mana_soaked.enabled: false.
 *
 * 1.6.1 (B2): не-маги теряют модификатор.
 * 1.6.3 (производительность): полный проход по онлайн-игрокам с getClassOf —
 * только раз в 100 тиков (5 с) для ПОПОЛНЕНИЯ множества магов; рабочий тик
 * (20 тиков) ходит лишь по mageIds. Снятие модификатора экс-мага происходит
 * в рабочем тике за ~1 с (проверка класса по множеству), оффлайн-чистка —
 * через ResistService.clear на quit.
 */
public final class ManaSoakedService {

    private static final String SOURCE = "mana_soaked";
    private static final long SCAN_INTERVAL_TICKS = 100L; // пополнение множества раз в 5 с

    private final RaskolClasses plugin;
    private final Set<UUID> mageIds = ConcurrentHashMap.newKeySet();
    private long lastScanTick = -1L;

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
        long tick = Bukkit.getCurrentTick();
        if (lastScanTick < 0 || tick - lastScanTick >= SCAN_INTERVAL_TICKS) {
            lastScanTick = tick;
            scanForMages();
        }
        double threshold = plugin.getConfig()
                .getDouble("classes.MAGE.passives.mana_soaked.threshold", 50.0);
        double phys = plugin.getConfig().getDouble("resist.mana-soaked.physical", 15.0);
        double magic = plugin.getConfig().getDouble("resist.mana-soaked.magic", 15.0);

        for (UUID uuid : mageIds) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player == null) {
                mageIds.remove(uuid); // модификаторы уже сняты ResistService.clear на quit
                continue;
            }
            // 1.6.1 B2: экс-маг теряет резист за ~1 с (рабочий тик)
            if (plugin.getClassProvider().getClassOf(player) != PlayerClass.MAGE) {
                plugin.getResists().removeModifiersBySource(uuid, SOURCE);
                mageIds.remove(uuid);
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

    /** Полный проход по онлайну: добавить новых магов, снять модификаторы экс-магов. */
    private void scanForMages() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            if (plugin.getClassProvider().getClassOf(player) == PlayerClass.MAGE) {
                mageIds.add(uuid);
            } else if (mageIds.remove(uuid)) {
                plugin.getResists().removeModifiersBySource(uuid, SOURCE);
            }
        }
        mageIds.removeIf(id -> plugin.getServer().getPlayer(id) == null);
    }
}
