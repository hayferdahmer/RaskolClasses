package dev.raskol.classes.api.combat;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Registration point for profiles owned by integrations such as RaskolMobs. */
public final class CombatProfileRegistry {
    private final Map<UUID,CombatProfile> profiles=new ConcurrentHashMap<>();
    public void register(UUID entityId,CombatProfile profile){profiles.put(java.util.Objects.requireNonNull(entityId),java.util.Objects.requireNonNull(profile));}
    public void unregister(UUID entityId){profiles.remove(entityId);}
    public CombatProfile profileOf(UUID entityId){return profiles.get(entityId);}
}
