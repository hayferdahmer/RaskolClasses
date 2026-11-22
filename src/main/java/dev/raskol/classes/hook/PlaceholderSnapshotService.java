// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.hook;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Player;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;

/**
 * Формирует снимки PlaceholderAPI на основном потоке сервера.
 *
 * <p>Асинхронные callback PlaceholderAPI используют UUID как ключ снимка;
 * чтение состояния Player и вызовы внешних интеграций остаются в этом сервисе
 * и выполняются только на основном потоке.</p>
 */
public final class PlaceholderSnapshotService {

    private final Map<UUID, PlaceholderSnapshot> snapshots = new ConcurrentHashMap<>();
    private final Set<UUID> warnedFailures = ConcurrentHashMap.newKeySet();
    /**
     * Возвращает значение из последнего опубликованного снимка.
     */
    public String raskolValue(UUID playerId, String parameter) {
        PlaceholderSnapshot snapshot = snapshots.get(playerId);
        return snapshot == null ? null : snapshot.raskolValue(parameter);
    }

    /**
     * Возвращает значение из последнего опубликованного снимка.
     */
    public String crownValue(UUID playerId, String parameter) {
        PlaceholderSnapshot snapshot = snapshots.get(playerId);
        return snapshot == null ? null : snapshot.crownValue(parameter);
    }

    /** Обновляет снимки всех игроков; вызывается runtime owner на основном потоке. */
    public void refreshOnlinePlayers(RaskolClasses plugin) {
        Set<UUID> onlineIds = new HashSet<>();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            UUID playerId = player.getUniqueId();
            onlineIds.add(playerId);
            refresh(plugin, player, playerId);
        }

        snapshots.keySet().removeIf(playerId -> !onlineIds.contains(playerId));
        warnedFailures.retainAll(onlineIds);
    }

    private void refresh(RaskolClasses plugin, Player player, UUID playerId) {
        try {
            PlayerClass playerClass = plugin.getClassProvider().getClassOf(player);
            Map<String, String> raskol = new HashMap<>();
            Map<String, String> crown = new HashMap<>();

            raskol.put("class", playerClass == null ? "" : playerClass.getDisplayName());
            raskol.put("level", Integer.toString(plugin.getCharacterLevels().characterLevel(playerId)));
            raskol.put("hp", Integer.toString((int) plugin.getHpBarService().currentFormulaHp(player)));
            raskol.put("hp_max", Integer.toString((int) plugin.getHpBarService().formulaMaxHp(playerId)));
            raskol.put("resource", Integer.toString((int) plugin.getResources().getValue(playerId)));
            raskol.put("phys_resist", Integer.toString((int) plugin.getResists().physicalResist(playerId)));
            raskol.put(
                    "magic_resist", Integer.toString((int) plugin.getResists().magicResist(playerId)));

            double[] avoidance = plugin.getAttributes().effectiveAvoidance(playerId);
            raskol.put("dodge", fmt1(avoidance[0]));
            raskol.put("parry", fmt1(avoidance[1]));
            raskol.put("crit_melee", fmt1(plugin.getAttributes().critMeleeChance(playerId)));
            raskol.put("crit_spell", fmt1(plugin.getAttributes().critSpellChance(playerId)));

            String mainSpec = plugin.getSpec2Service().mainSpec(playerId);
            dev.raskol.classes.spec.Spec spec = dev.raskol.classes.spec.Spec.fromId(mainSpec);
            raskol.put("spec", spec == null ? "" : spec.displayName());
            raskol.put(
                    "talent_points", Integer.toString(plugin.getSpec2Service().availablePoints(playerId)));
            raskol.put(
                    "talents",
                    plugin.getSpec2Service().spentGlobal(playerId) + "/"
                            + plugin.getSpec2Service().earnedPoints(playerId));

            if (!plugin.getPlusSubscription().active(playerId)) {
                raskol.put("plus", "expired");
            } else {
                raskol.put("plus", Integer.toString(plugin.getPlusSubscription().remainingDays(playerId)));
            }

            crown.put("crown", safe(plugin.getFlavorService().crownDisplayName(playerId)));
            crown.put(
                    "title",
                    playerClass == null ? "" : safe(plugin.getFlavorService().titleOf(playerId, playerClass)));
            crown.put("faction", safe(plugin.getFactionHook().factionOf(playerId)));

            snapshots.put(playerId, new PlaceholderSnapshot(raskol, crown));
            warnedFailures.remove(playerId);
        } catch (RuntimeException exception) {
            snapshots.remove(playerId);
            if (warnedFailures.add(playerId)) {
                plugin.getLogger()
                        .warning("Не удалось обновить PlaceholderAPI-снимок игрока " + playerId + ": "
                                + exception.getMessage());
            }
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String fmt1(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    /** Очищает опубликованные значения после остановки runtime producer. */
    public void clear() {
        snapshots.clear();
        warnedFailures.clear();
    }
}
