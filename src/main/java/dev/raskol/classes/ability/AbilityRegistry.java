package dev.raskol.classes.ability;

import dev.raskol.classes.model.RClass;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Реестр активных способностей.
 * Регистрация происходит один раз при старте плагина; после этого реестр считается неизменяемым.
 */
public final class AbilityRegistry {

    private final Map<String, AbilityDef> byId = new HashMap<>();
    private final Map<RClass, List<AbilityDef>> byClass = new EnumMap<>(RClass.class);
    private boolean frozen;

    /**
     * Регистрирует способность. Ключ хранится в нижнем регистре для устойчивости к вводу.
     *
     * @throws IllegalStateException    если реестр уже заморожен
     * @throws IllegalArgumentException если id дублируется
     */
    public void register(@NotNull AbilityDef def) {
        if (frozen) {
            throw new IllegalStateException("AbilityRegistry is frozen");
        }
        String key = def.id().toLowerCase(Locale.ROOT);
        if (byId.putIfAbsent(key, def) != null) {
            throw new IllegalArgumentException("Duplicate ability id: " + def.id());
        }
        byClass.computeIfAbsent(def.rClass(), c -> new ArrayList<>()).add(def);
    }

    /** Замораживает реестр и сортирует способности по уровню разблокировки. */
    public void freeze() {
        if (frozen) return;
        for (Map.Entry<RClass, List<AbilityDef>> e : byClass.entrySet()) {
            e.getValue().sort(Comparator.comparingInt(AbilityDef::requiredLevel));
            byClass.put(e.getKey(), Collections.unmodifiableList(e.getValue()));
        }
        frozen = true;
    }

    public @NotNull Optional<AbilityDef> byId(@NotNull String id) {
        return Optional.ofNullable(byId.get(id.toLowerCase(Locale.ROOT)));
    }

    /** Все способности класса, отсортированные по требуемому уровню. */
    public @NotNull @Unmodifiable List<AbilityDef> forClass(@NotNull RClass rClass) {
        return byClass.getOrDefault(rClass, List.of());
    }

    /** Способности класса, разблокированные на данном уровне. */
    public @NotNull @Unmodifiable List<AbilityDef> unlockedAt(@NotNull RClass rClass, int level) {
        List<AbilityDef> all = forClass(rClass);
        List<AbilityDef> out = new ArrayList<>(all.size());
        for (AbilityDef def : all) {
            if (def.unlockedAt(level)) out.add(def);
        }
        return Collections.unmodifiableList(out);
    }

    /** Ближайшая ещё не разблокированная способность (для подсказок). */
    public @NotNull Optional<AbilityDef> nextLocked(@NotNull RClass rClass, int level) {
        for (AbilityDef def : forClass(rClass)) {
            if (!def.unlockedAt(level)) return Optional.of(def);
        }
        return Optional.empty();
    }

    /** Все зарегистрированные способности (для отладки/аудита). */
    public @NotNull @Unmodifiable Collection<AbilityDef> all() {
        return Collections.unmodifiableCollection(byId.values());
    }
}
