// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.install;

import dev.raskol.classes.RaskolClasses;
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

/**
 * Свиток постановки инсталляции (1.5.x → 1.9.0-fix4).
 * 1.10.4: лор Пентаграммы (бывш. Круг Хулы) + понятная строка лимита.
 */
public final class InstallToken {

    private final RaskolClasses plugin;
    private final NamespacedKey typeKey;

    public InstallToken(RaskolClasses plugin) {
        this.plugin = plugin;
        this.typeKey = new NamespacedKey(plugin, "install_type");
    }

    public ItemStack create(InstallationType type, PlayerClass pc) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("◈ " + type.displayName(), NamedTextColor.LIGHT_PURPLE));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Инсталляция класса: " + pc.getDisplayName(), NamedTextColor.GRAY));
        lore.add(Component.text(""));
        for (String line : description(type)) {
            lore.add(Component.text(line, NamedTextColor.WHITE));
        }
        lore.add(Component.text(""));
        lore.add(Component.text("ПКМ в хотбаре — установить", NamedTextColor.GREEN));
        lore.add(Component.text("Лимит: 2 активных инсталляции на игрока", NamedTextColor.GRAY));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(typeKey, PersistentDataType.STRING, type.name());
        item.setItemMeta(meta);
        return item;
    }

    /** 1.10.4: покрыт HERESY_CIRCLE = «Пентаграмма». */
    private List<String> description(InstallationType type) {
        return switch (type) {
            case WAR_BANNER -> List.of(
                    "Знамя войны: аура Resistance I союзникам",
                    "в радиусе 6 блоков на 8 секунд.");
            case BEAR_TRAP -> List.of(
                    "Капкан: враг, шагнувший в радиус 1.2,",
                    "получает 3 урона и Slowness VI на 2 с.",
                    "Капкан расходуется при срабатывании.");
            case LIGHT_WARD -> List.of(
                    "Световой ward: +2 HP/с союзникам",
                    "в радиусе 4 блоков, длится 60 с.");
            case FROST_RUNE -> List.of(
                    "Ледяная руна-зона радиусом 8 блоков на 30 с.",
                    "Враги внутри: урон 4/с, +1/с за каждую секунду",
                    "пребывания (кап 12/с); замедление растёт",
                    "каждые 5 с пребывания (до Slowness IV).",
                    "Магу внутри: +3 маны/с и ИНТ ×2.",
                    "Одна руна за раз; следующая через 60 с.");
            case SMOKE_BOMB -> List.of(
                    "Дымовая шашка: враг в радиусе 3 слепнет",
                    "на 2 с; владелец получает Speed I на 3 с.",
                    "Расходуется при срабатывании.");
            case HERESY_CIRCLE -> List.of(
                    "Пентаграмма: осквернённый круг радиусом 6",
                    "блоков на 25 секунд. Видима всем: кольцо",
                    "огней душ и пятилучевая звезда спор.",
                    "Враги внутри: маг-урон и запрет лечения.",
                    "Чернокнижнику внутри: +3 Скверны/с.",
                    "В аду урон пентаграммы умножается на шесть.",
                    "Постановка: звук призыва визора.");
        };
    }

    /** Тип инсталляции из свитка; null если предмет не свиток инсталляции. */
    public InstallationType readType(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        String name = item.getItemMeta().getPersistentDataContainer().get(typeKey, PersistentDataType.STRING);
        if (name == null) {
            return null;
        }
        try {
            return InstallationType.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** true, если предмет — свиток инсталляции (для ScrollSanitizer). */
    public boolean isInstallScroll(ItemStack item) {
        return readType(item) != null;
    }
}
