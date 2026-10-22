package dev.raskol.classes.item;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable item-definition registry. */
public final class ItemRegistry {
    private final Map<String, ItemDefinition> definitions;
    private final Map<String,SetDefinition> sets;
    public ItemRegistry(Collection<ItemDefinition> definitions) { this(definitions,Map.of()); }
    public ItemRegistry(Collection<ItemDefinition> definitions,Map<String,SetDefinition> sets) {
        Map<String, ItemDefinition> map = new LinkedHashMap<>();
        for (ItemDefinition d : definitions) if (map.putIfAbsent(d.id(), d) != null)
            throw new IllegalArgumentException("Duplicate item id: " + d.id());
        this.definitions = Map.copyOf(map);
        this.sets=Map.copyOf(sets);
    }
    public ItemDefinition get(String id) { return definitions.get(id); }
    public Collection<ItemDefinition> all() { return definitions.values(); }
    public int size() { return definitions.size(); }
    public Map<String,SetDefinition> sets() { return sets; }
}
