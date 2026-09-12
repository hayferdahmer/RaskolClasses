// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.hotbar;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.ability.AbilityDef;
import dev.raskol.classes.classsystem.PlayerClass;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Слушатель свитков в хотбаре (1.5.x → 1.9.0-fix2).
 *
 * 1.9.0-fix2 (причина «способности не применяются»):
 *  - убран ignoreCancelled=true: правый клик по блоку в WG-регионе/на спавне
 *    отменяется другими плагинами как block-interact, но каст свитком —
 *    не взаимодействие с блоком, поэтому событие обрабатываем даже отменённым;
 *  - ПКМ — каст в себя (конвейер tryCast: гейты, списание ресурса, кулдаун, VFX);
 *  - ЛКМ по живой цели под прицелом — точечный каст (жрец и прочие targeted-абилки);
 *    если свиток не targeted или цель не живая — ЛКМ не перехватываем (обычная атака/лом);
 *  - событие гасим только когда реально перехватили каст.
 */
public final class BindListener implements Listener {

    private static final double TARGET_RANGE = 20.0;

    private final RaskolClasses plugin;
    private final AbilityToken tokens;

    public BindListener(RaskolClasses plugin, AbilityToken tokens) {
        this.plugin = plugin;
        this.tokens = tokens;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        boolean right = action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
        boolean left = action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK;
        if (!right && !left) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null) {
            return;
        }
        String abilityId = tokens.readId(item);
        if (abilityId == null) {
            return; // не свиток способности — не трогаем
        }
        Player player = event.getPlayer();
        PlayerClass pc = plugin.getClassProvider().getClassOf(player);
        if (pc == null) {
            player.sendMessage(Component.text(plugin.getRaskolConfig().message(
                    "no-class-cast", "Класс не выбран — способности недоступны"), NamedTextColor.GRAY));
            return;
        }
        AbilityDef def = plugin.getAbilities().findById(pc, abilityId);
        if (def == null) {
            player.sendMessage(Component.text("Способность не найдена: " + abilityId, NamedTextColor.RED));
            return;
        }

        if (right) {
            // ПКМ — каст в себя через конвейер (ресурс, кулдаун, гейты, VFX)
            event.setCancelled(true);
            if (plugin.getAbilities().tryCast(player, def)) {
                plugin.getFx().onAttempt(player, def.id(), def.cooldownMillis());
            }
            return;
        }

        // ЛКМ — точечный каст только если абилка targeted и под прицелом живая цель
        if (!plugin.getAbilities().isTargeted(def.id())) {
            return; // обычная атака/лом блока свитком в руке
        }
        Entity target = player.getTargetEntity((int) TARGET_RANGE);
        if (!(target instanceof LivingEntity living)) {
            return; // цели нет — не перехватываем, пусть идёт обычная атака
        }
        event.setCancelled(true);
        if (plugin.getAbilities().tryCastTargeted(player, living, def)) {
            plugin.getFx().onAttempt(player, def.id(), def.cooldownMillis());
        }
    }
}
