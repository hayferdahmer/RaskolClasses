// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.spec;

import dev.raskol.classes.RaskolClasses;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * Визуал ловушки охотника (1.4.0, Пакет 2.3).
 * ItemDisplay «капкан» (tripwire hook ×2.2) на земле + партиклы + звук.
 * Сущность временная (setPersistent(false)), удаляется по таймеру —
 * мир не изменяется, на рестартах не остаётся мусора.
 *
 * FIX 1.4.0.4: масштаб через setTransformation(Transformation) —
 * метода setTransformationScale в paper-api 1.21.4 нет.
 */
public final class TrapVisual {

    private TrapVisual() {
    }

    /** Показать капкан в точке на durationTicks, затем растворить. */
    public static void show(RaskolClasses plugin, Location at, int durationTicks) {
        World world = at.getWorld();
        if (world == null) {
            return;
        }
        Location spot = at.clone().add(0.5, 0.12, 0.5);

        ItemDisplay display = (ItemDisplay) world.spawnEntity(spot, EntityType.ITEM_DISPLAY);
        display.setItemStack(new ItemStack(Material.TRIPWIRE_HOOK));
        // scale ×2.2, без смещения и поворотов
        display.setTransformation(new Transformation(
                new Vector3f(0f, 0f, 0f),
                new Vector3f(2.2f, 2.2f, 2.2f),
                new AxisAngle4f(),
                new AxisAngle4f()));
        display.setViewRange(24f);
        display.setPersistent(false);
        display.setInvulnerable(true);

        world.playSound(spot, Sound.BLOCK_TRIPWIRE_ATTACH, 1.0f, 1.2f);
        world.spawnParticle(Particle.CRIT, spot.clone().add(0.0, 0.2, 0.0),
                10, 0.3, 0.15, 0.3, 0.0);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!display.isDead()) {
                display.remove();
            }
            world.playSound(spot, Sound.BLOCK_TRIPWIRE_DETACH, 1.0f, 0.8f);
            world.spawnParticle(Particle.CLOUD, spot.clone().add(0.0, 0.2, 0.0),
                    6, 0.25, 0.15, 0.25, 0.0);
        }, Math.max(1, durationTicks));
    }
}
