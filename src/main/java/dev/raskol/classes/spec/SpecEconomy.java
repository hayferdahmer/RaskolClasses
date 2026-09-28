// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.spec;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.classsystem.SkillLevelProvider;
import dev.raskol.classes.hook.EconomyHook;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 1.11.4 (P4e): экономика и арм-окно отречения, вынесены из SpecService.
 * Арм-окно 30 с, одноразовое (confirm снимает pending) — эксплойт двойного
 * сброса за одну оплату невозможен.
 */
public final class SpecEconomy {

    private static final int REQUIRED_LEVEL = 40;
    private static final long PENDING_MILLIS = 30_000L;

    private final RaskolClasses plugin;
    private final EconomyHook economy;
    private final Map<UUID, Long> pendingUntil = new ConcurrentHashMap<>();

    public SpecEconomy(RaskolClasses plugin, EconomyHook economy) {
        this.plugin = plugin;
        this.economy = economy;
    }

    public EconomyHook economy() {
        return economy;
    }

    public int respecCost(Player player) {
        PlayerClass pc = plugin.getClassProvider().getClassOf(player);
        int level = pc == null
                ? REQUIRED_LEVEL
                : plugin.getSkillLevels().getLevel(player.getUniqueId(), pc.profileSkillName());
        if (level == SkillLevelProvider.NO_SKILL_SYSTEM || level < 1) {
            level = REQUIRED_LEVEL;
        }
        return SpecMath.respecCost(level,
                plugin.getConfig().getInt("spec.respec-base-cost", 250),
                plugin.getConfig().getInt("spec.respec-per-level", 10));
    }

    public void requestRespec(Player player) {
        pendingUntil.put(player.getUniqueId(), System.currentTimeMillis() + PENDING_MILLIS);
    }

    public boolean isPending(UUID uuid) {
        Long until = pendingUntil.get(uuid);
        return until != null && until > System.currentTimeMillis();
    }

    public void clearPending(UUID uuid) {
        pendingUntil.remove(uuid);
    }

    /**
     * Оплата отречения. Возвращает null = оплата прошла (или админ-бypass);
     * иначе — причину отказа (NO_ECONOMY / POOR).
     */
    public SpecService.RespecResult pay(Player player) {
        if (player.hasPermission("raskolclasses.admin")) {
            return null;
        }
        UUID uuid = player.getUniqueId();
        if (!economy.available()) {
            return SpecService.RespecResult.NO_ECONOMY;
        }
        int cost = respecCost(player);
        if (economy.balance(uuid) < cost) {
            return SpecService.RespecResult.POOR;
        }
        if (!economy.withdraw(uuid, cost)) {
            return SpecService.RespecResult.POOR;
        }
        return null;
    }
}
