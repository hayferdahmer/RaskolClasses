// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.hotbar;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.ability.AbilityDef;
import dev.raskol.classes.classsystem.PlayerClass;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Свиток способности в хотбаре (1.5.x → 1.9.0-fix3).
 * 1.9.0-fix3: лора свитка теперь честная и полная: описание способности,
 * формула урона/хила с числами (base + coeff% от Силы), строка силы класса,
 * цена/КД/слот и подсказка ПКМ. Игрок видит, ЧТО именно он кастует.
 */
public final class AbilityToken {

    private final RaskolClasses plugin;
    private final NamespacedKey abilityKey;

    public AbilityToken(RaskolClasses plugin) {
        this.plugin = plugin;
        this.abilityKey = new NamespacedKey(plugin, "ability_id");
    }

    public ItemStack create(AbilityDef def, PlayerClass pc) {
        ItemStack item = new ItemStack(Material.AMETHYST_SHARD);
        item.setItemMeta(buildMeta(def, pc));
        return item;
    }

    private ItemMeta buildMeta(AbilityDef def, PlayerClass pc) {
        ItemStack probe = new ItemStack(Material.AMETHYST_SHARD);
        ItemMeta meta = probe.getItemMeta();
        meta.displayName(Component.text("«" + def.displayName() + "»", NamedTextColor.LIGHT_PURPLE));

        List<Component> lore = new ArrayList<>();
        String desc = plugin.getRaskolConfig().abilityDescription(pc, def.id(), "");
        if (!desc.isEmpty()) {
            lore.add(Component.text(desc, NamedTextColor.WHITE));
        }
        // формула урона/хила с числами
        String power = plugin.getConfig().getString(
                "classes." + pc.name() + ".abilities." + def.id() + ".power", "");
        double base = plugin.getConfig().getDouble(
                "classes." + pc.name() + ".abilities." + def.id() + ".base", 0.0);
        double coeff = plugin.getConfig().getDouble(
                "classes." + pc.name() + ".abilities." + def.id() + ".coeff", 0.0);
        if (coeff > 0.0) {
            String powerName = switch (power) {
                case "wp" -> "Силы оружия";
                case "hpow" -> "Силы исцеления";
                default -> "Силы заклинаний";
            };
            lore.add(Component.text(String.format(Locale.ROOT,
                    "Эффект: %.0f + %.0f%% от %s", base, coeff * 100.0, powerName),
                    NamedTextColor.AQUA));
            String powerFormula = switch (power) {
                case "wp" -> "Сила оружия = base + СИЛА×1.5 + ЛОВК×0.5";
                case "hpow" -> "Сила исцеления = base + ИНТ×1.4";
                default -> "Сила заклинаний = base + ИНТ×1.5";
            };
            lore.add(Component.text(powerFormula, NamedTextColor.DARK_AQUA));
        }
        lore.add(Component.text("Цена: " + def.cost() + " · КД: "
                + (def.cooldownMillis() / 1000L) + " с · Слот: " + def.slot(),
                NamedTextColor.GRAY));
        lore.add(Component.text("ПКМ — свиток в хотбар", NamedTextColor.YELLOW));

        meta.lore(lore);
        meta.getPersistentDataContainer().set(abilityKey, PersistentDataType.STRING, def.id());
        return meta;
    }

    /** id способности из свитка; null если предмет не свиток. */
    public String readId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(abilityKey, PersistentDataType.STRING);
    }
}
