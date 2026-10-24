// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.item.service;

import dev.raskol.classes.item.model.ItemDefinition;
import dev.raskol.classes.item.model.ItemStat;
import dev.raskol.classes.item.model.SetDefinition;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Builds set contributions and publishes immutable active-set snapshots.
 * 1.15.1-D: apply() БОЛЬШЕ НЕ мутирует входную map статов — возвращает новую
 * (item-статы + бонусы достигнутых порогов). Side-effect устранён (аудит C):
 * вызывающий код явно видит, откуда взялся итоговый набор.
 */
public final class SetBonusService {

    public record ActiveSet(String id, String displayName, int pieces, List<Integer> activeThresholds) {
        public ActiveSet {
            activeThresholds = List.copyOf(activeThresholds);
        }
    }

    private volatile Map<String, SetDefinition> definitions = Map.of();
    private final Map<UUID, List<ActiveSet>> active = new ConcurrentHashMap<>();

    public void definitions(Map<String, SetDefinition> definitions) {
        this.definitions = Map.copyOf(definitions);
    }

    public List<ActiveSet> active(UUID player) {
        return active.getOrDefault(player, List.of());
    }

    public void clear(UUID player) {
        active.remove(player);
    }

    /**
     * Считает бонусы сетов по надетым предметам и возвращает НОВУЮ map:
     * itemStats + бонусы всех достигнутых порогов (2..6). Вход не изменяется.
     * Публикует снапшот активных сетов для GUI/дамп-команд.
     */
    public Map<ItemStat, Double> apply(UUID player, List<ItemDefinition> items,
                                       Map<ItemStat, Double> itemStats) {
        Map<ItemStat, Double> result = new EnumMap<>(ItemStat.class);
        result.putAll(itemStats);

        Map<String, Integer> counts = new HashMap<>();
        for (ItemDefinition item : items) {
            if (item.setId() != null) {
                counts.merge(item.setId(), 1, Integer::sum);
            }
        }

        List<ActiveSet> resultSets = new ArrayList<>();
        counts.forEach((id, pieces) -> {
            SetDefinition set = definitions.get(id);
            if (set == null) {
                return;
            }
            List<Integer> thresholds = new ArrayList<>();
            set.bonuses().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> {
                        if (pieces >= entry.getKey()) {
                            thresholds.add(entry.getKey());
                            entry.getValue().stats().forEach((stat, value) ->
                                    result.merge(stat, stat.clamp(value), Double::sum));
                        }
                    });
            resultSets.add(new ActiveSet(id, set.displayName(), pieces, thresholds));
        });
        active.put(player, List.copyOf(resultSets));
        return result;
    }
}
