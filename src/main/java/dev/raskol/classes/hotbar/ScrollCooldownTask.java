// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.hotbar;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.ability.AbilityDef;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.spec.Spec;
import dev.raskol.classes.spec.SpecRegistry;
import io.papermc.paper.datacomponent.DataComponentTypes;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

/**
 * 1.5.6: визуальный кулдаун НА САМОМ СВИТКЕ в хотбаре.
 * Используем vanilla durability-бар через Paper data-components:
 * MAX_DAMAGE = 100, DAMAGE = доля оставшегося КД (красный → зелёный по мере
 * отката; DAMAGE = 0 — полоска исчезает, способность готова).
 * Работает для свитков способностей (PDC ability) и свитков спеков (spec_ability);
 * свитки инсталляций КД не имеют — пропускаются.
 * Гейт: hotbar-bind.cooldown-bar (дефолт true).
 */
public final class ScrollCooldownTask {

    private static final int MAX = 100;
    private static final int[] SLOTS = {0, 1, 2, 3, 4, 5, 6, 7, 8, 40}; // хотбар + оффхенд

    private final RaskolClasses plugin;

    public ScrollCooldownTask(RaskolClasses plugin) {
        this.plugin = plugin;
    }

    public BukkitTask start() {
        return plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 10L, 10L);
    }

    private void tick() {
        if (!plugin.getConfig().getBoolean("hotbar-bind.cooldown-bar", true)) {
            return;
        }
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            Inventory inv = player.getInventory();
            for (int slot : SLOTS) {
                ItemStack item = inv.getItem(slot);
                if (item == null) {
                    continue;
                }
                Long remaining = remainingMillis(player, item);
                Long total = totalMillis(player, item);
                if (remaining == null || total == null || total <= 0L) {
                    continue; // не свиток с КД
                }
                int damage;
                if (remaining <= 0L) {
                    damage = 0;
                } else {
                    damage = (int) Math.max(1L,
                            Math.min(MAX, Math.ceil((double) MAX * remaining / total)));
                }
                applyBar(item, damage);
            }
        }
    }

    /** Остаток КД свитка; null если предмет не свиток с КД. */
    private Long remainingMillis(Player player, ItemStack item) {
        String abilityId = plugin.getTokens().readId(item);
        if (abilityId != null) {
            return plugin.getCooldowns().getRemainingMillis(player.getUniqueId(), abilityId);
        }
        Spec spec = plugin.getSpecToken().readSpec(item);
        if (spec != null) {
            return plugin.getCooldowns().getRemainingMillis(player.getUniqueId(),
                    "spec_" + spec.id());
        }
        return null; // свиток инсталляции или обычный предмет
    }

    /** Полный КД свитка в мс; null если предмет не свиток с КД. */
    private Long totalMillis(Player player, ItemStack item) {
        String abilityId = plugin.getTokens().readId(item);
        if (abilityId != null) {
            PlayerClass pc = plugin.getClassProvider().getClassOf(player);
            if (pc == null) {
                return null;
            }
            AbilityDef def = plugin.getAbilities().findById(pc, abilityId);
            return def != null ? def.cooldownMillis() : null;
        }
        Spec spec = plugin.getSpecToken().readSpec(item);
        if (spec != null) {
            SpecRegistry.SpecDef sdef = plugin.getSpecRegistry().get(spec);
            return sdef != null ? sdef.activeCooldown() * 1000L : null;
        }
        return null;
    }

    /** Ставит MAX_DAMAGE (если нет) и актуальный DAMAGE; пишет только при изменении. */
    private void applyBar(ItemStack item, int damage) {
        Integer max = item.getData(DataComponentTypes.MAX_DAMAGE);
        if (max == null || max != MAX) {
            item.setData(DataComponentTypes.MAX_DAMAGE, MAX);
        }
        Integer current = item.getData(DataComponentTypes.DAMAGE);
        int cur = current == null ? 0 : current;
        if (cur != damage) {
            item.setData(DataComponentTypes.DAMAGE, damage);
        }
    }
}
