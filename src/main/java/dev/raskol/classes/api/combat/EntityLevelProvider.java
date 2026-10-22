package dev.raskol.classes.api.combat;

import org.bukkit.entity.Entity;

/** Implemented by integrations that assign levels to custom entities. */
public interface EntityLevelProvider { int levelOf(Entity entity); }
