// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.fx;

import dev.raskol.classes.RaskolClasses;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Движок звука/партиклов (1.5.0, Пакет 1).
 * FIX 1.5.0.1: каталог дефолтов вшит в код — звуки/партиклы работают
 * БЕЗ секции vfx в конфиге; конфиг (vfx.<id>.cast-sound/cast-particle)
 * только ПЕРЕОПРЕДЕЛЯЕТ дефолты.
 * FIX 1.5.0.2: резолв через Registry.SOUNDS / Registry.PARTICLE_TYPE
 * (без deprecated Sound.valueOf / Particle.valueOf — иначе deprecation-гейт
 * CI валит ран).
 */
public final class FxService {

    private static final Map<String, String[]> DEFAULT
