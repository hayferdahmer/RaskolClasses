// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.fx;

import org.bukkit.Location;
import org.bukkit.Particle;

/**
 * 1.11.4 (P4a): визуал кита Чернокнижника (plain-партиклы ада/глубин).
 * Static-утилиты без состояния: safeFx (с фолбэком на SOUL_FIRE_FLAME,
 * битый ключ не роняет каст), ringFx (кольцо по радиусу), riseFx (восходящие споры/души).
 */
public final class WarlockFx {

    private WarlockFx() {
    }

    /** Облачный всплеск над точкой; IllegalArgumentException (data-партикл) → фолбэк. */
    public static void safeFx(Location loc, Particle particle, int count, double spread) {
        if (loc == null || loc.getWorld() == null) {
            return;
        }
        try {
            loc.getWorld().spawnParticle(particle, loc.clone().add(0.0, 1.0, 0.0),
                    count, spread, spread * 0.6, spread, 0.04);
        } catch (IllegalArgumentException e) {
            loc.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, loc.clone().add(0.0, 1.0, 0.0),
                    Math.max(4, count / 2), spread, spread * 0.6, spread, 0.02);
        }
    }

    /** Кольцо партиклов по радиусу (зоны, печати). */
    public static void ringFx(Location center, double radius, Particle particle, int perPoint) {
        if (center == null || center.getWorld() == null) {
            return;
        }
        try {
            int points = 20;
            for (int i = 0; i < points; i++) {
                double angle = (Math.PI * 2 * i) / points;
                Location p = center.clone().add(Math.cos(angle) * radius, 0.4, Math.sin(angle) * radius);
                center.getWorld().spawnParticle(particle, p, perPoint, 0.0, 0.3, 0.0, 0.01);
            }
        } catch (IllegalArgumentException ignored) {
        }
    }

    /** Восходящие споры/души над точкой («ад дышит»). */
    public static void riseFx(Location loc, Particle particle, int count) {
        if (loc == null || loc.getWorld() == null) {
            return;
        }
        try {
            loc.getWorld().spawnParticle(particle, loc.clone().add(0.0, 0.3, 0.0),
                    count, 0.35, 0.9, 0.35, 0.06);
        } catch (IllegalArgumentException ignored) {
        }
    }
}
