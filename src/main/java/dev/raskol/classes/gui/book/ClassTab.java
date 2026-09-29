// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.gui.book;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.attribute.AttributeService;
import dev.raskol.classes.attribute.AttributeType;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.combat.school.School;
import dev.raskol.classes.config.RaskolConfig;
import dev.raskol.classes.gui.ClassBook;
import dev.raskol.classes.util.TextFx;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 1.11.4 (P4b): вкладка «Класс и пассивки»: пассивки, атрибуты, резисты, корона.
 * 1.12.2 (Блок 5): в лоре щита — строки пробития и стихийных резистов.
 */
public final class ClassTab implements BookTabView {

    @Override
    public ClassBook.Tab id() {
        return ClassBook.Tab.CLASS;
    }

    @Override
    public void render(RenderCtx ctx) {
        RaskolClasses plugin = ctx.plugin();
        PlayerClass pc = ctx.pc();
        List<String> passives = RaskolConfig.passiveIds(pc);
        for (int i = 0; i < passives.size() && i < BookSlots.PASSIVE_SLOTS.length; i++) {
            ctx.inv().setItem(BookSlots.PASSIVE_SLOTS[i], passiveItem(plugin, pc, passives.get(i)));
        }
        ctx.inv().setItem(BookSlots.SLOT_ATTRIBUTES, attributesItem(plugin, ctx.player(), pc));
        ctx.inv().setItem(BookSlots.SLOT_RESIST, resistItem(plugin, ctx.player(), pc));
        ctx.inv().setItem(BookSlots.SLOT_CROWN, crownItem(plugin, ctx.player(), pc));
    }

    private ItemStack passiveItem(RaskolClasses plugin, PlayerClass pc, String id) {
        RaskolConfig cfg = plugin.getRaskolConfig();
        ItemStack item = new ItemStack(Material.EXPERIENCE_BOTTLE);
        item.editMeta(meta -> {
            meta.displayName(Component.text(cfg.passiveDisplayName(pc, id, id),
                    NamedTextColor.GOLD));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(cfg.passiveDescription(pc, id, ""), NamedTextColor.GRAY));
            lore.add(Component.text(passiveNumbers(id), NamedTextColor.WHITE));
            meta.lore(lore);
        });
        return item;
    }

    private static String passiveNumbers(String id) {
        return switch (id) {
            case "execute_passive" -> "20% шанс · ×3 · порог HP 20% · КД 6 с";
            case "predator" -> "порог HP 80% · ×1.2";
            case "grace" -> "×1.15 к исходящему лечению";
            case "mana_soaked" -> "порог маны 50 · −15% входящего урона";
            case "poisoned_blades" -> "30% шанс · Яд I 2 с · КД 3 с";
            case "sadism" -> "+3 урона со спины · КД 2 с";
            case "black_mass" -> "6.66% lifesteal · 6.66% рефлекта чистым уроном в r8";
            default -> "";
        };
    }

    private ItemStack attributesItem(RaskolClasses plugin, Player player, PlayerClass pc) {
        UUID uuid = player.getUniqueId();
        AttributeService attrs = plugin.getAttributes();
        AttributeType main = attrs.mainOf(pc);
        double str = attrs.value(uuid, AttributeType.STR);
        double agi = attrs.value(uuid, AttributeType.AGI);
        double intel = attrs.value(uuid, AttributeType.INT);
        double[] eff = attrs.effectiveAvoidance(uuid);
        int charLevel = plugin.getCharacterLevels().characterLevel(uuid);
        int topN = Math.max(1, plugin.getConfig().getInt("character-level.top-n", 5));
        int cap = (int) plugin.getConfig().getDouble("attributes.level-cap", 60.0);
        ItemStack item = new ItemStack(Material.ENCHANTED_BOOK);
        item.editMeta(meta -> {
            meta.displayName(TextFx.gradient(
                    BookItems.msg(plugin, "book.attributes.title", "Атрибуты класса"),
                    plugin.getRaskolConfig().themeOf(pc).primary(),
                    plugin.getRaskolConfig().themeOf(pc).secondary()));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(BookItems.msg(plugin, "book.attributes.char-level",
                    "Уровень персонажа: {value} (топ-{n} скиллов, кап {cap})")
                    .replace("{value}", String.valueOf(charLevel))
                    .replace("{n}", String.valueOf(topN))
                    .replace("{cap}", String.valueOf(cap)), NamedTextColor.WHITE));
            lore.add(Component.empty());
            lore.add(Component.text((main == AttributeType.STR ? "★ " : "  ")
                    + BookItems.msg(plugin, "book.attributes.str", "СИЛА: {value}")
                    .replace("{value}", String.valueOf((int) str)), NamedTextColor.WHITE));
            lore.add(Component.text((main == AttributeType.AGI ? "★ " : "  ")
                    + BookItems.msg(plugin, "book.attributes.agi", "ЛОВКОСТЬ: {value}")
                    .replace("{value}", String.valueOf((int) agi)), NamedTextColor.WHITE));
            lore.add(Component.text((main == AttributeType.INT ? "★ " : "  ")
                    + BookItems.msg(plugin, "book.attributes.int", "ИНТЕЛЛЕКТ: {value}")
                    .replace("{value}", String.valueOf((int) intel)), NamedTextColor.WHITE));
            lore.add(Component.empty());
            lore.add(Component.text(BookItems.msg(plugin, "book.attributes.hp", "Макс. HP: {value}")
                    .replace("{value}", String.valueOf((int) attrs.maxHp(uuid))),
                    NamedTextColor.WHITE));
            lore.add(Component.text(BookItems.msg(plugin, "book.attributes.dodge", "Уклонение: {value}%")
                    .replace("{value}", String.format(Locale.ROOT, "%.1f", eff[0])),
                    NamedTextColor.WHITE));
            lore.add(Component.text(BookItems.msg(plugin, "book.attributes.parry", "Парирование: {value}%")
                    .replace("{value}", String.format(Locale.ROOT, "%.1f", eff[1])),
                    NamedTextColor.WHITE));
            lore.add(Component.text(BookItems.msg(plugin, "book.attributes.crit-melee", "Крит мили: {value}%")
                    .replace("{value}", String.format(Locale.ROOT, "%.1f",
                            attrs.critMeleeChance(uuid))), NamedTextColor.WHITE));
            lore.add(Component.text(BookItems.msg(plugin, "book.attributes.crit-spell", "Крит магии: {value}%")
                    .replace("{value}", String.format(Locale.ROOT, "%.1f",
                            attrs.critSpellChance(uuid))), NamedTextColor.WHITE));
            meta.lore(lore);
        });
        return item;
    }

    private ItemStack resistItem(RaskolClasses plugin, Player player, PlayerClass pc) {
        UUID uuid = player.getUniqueId();
        var rb = plugin.getResists().breakdown(uuid);
        ItemStack item = new ItemStack(Material.SHIELD);
        item.editMeta(meta -> {
            meta.displayName(TextFx.gradient(BookItems.msg(plugin, "book.resist.title", "Сопротивления"),
                    plugin.getRaskolConfig().themeOf(pc).primary(),
                    plugin.getRaskolConfig().themeOf(pc).secondary()));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(BookItems.msg(plugin, "book.resist.phys", "Физ: {total}% (база {base}%)")
                    .replace("{total}", String.valueOf((int) rb.physicalTotal()))
                    .replace("{base}", String.valueOf((int) rb.basePhysical())),
                    NamedTextColor.WHITE));
            lore.add(Component.text(BookItems.msg(plugin, "book.resist.magic", "Маг: {total}% (база {base}%)")
                    .replace("{total}", String.valueOf((int) rb.magicTotal()))
                    .replace("{base}", String.valueOf((int) rb.baseMagic())),
                    NamedTextColor.WHITE));
            lore.add(Component.empty());
            if (rb.active().isEmpty()) {
                lore.add(Component.text(BookItems.msg(plugin, "book.resist.none",
                        "Активных модификаторов нет"), NamedTextColor.DARK_GRAY));
            } else {
                for (var m : rb.active()) {
                    lore.add(Component.text(BookItems.msg(plugin, "book.resist.mod",
                            "• {source}: +{phys} физ / +{magic} маг")
                            .replace("{source}", m.source())
                            .replace("{phys}", String.valueOf((int) m.physicalPct()))
                            .replace("{magic}", String.valueOf((int) m.magicPct())),
                            NamedTextColor.GRAY));
                }
            }
            // 1.12.2 (Блок 5): пробитие и стихийный слой
            var penTraits = plugin.getCombat().penTraits();
            double penPhys = penTraits.totalPenPercent(uuid, "phys", plugin.getGearHook());
            double penMagic = penTraits.totalPenPercent(uuid, "magic", plugin.getGearHook());
            lore.add(Component.text("Пробитие: физ " + (int) penPhys + "% · маг " + (int) penMagic + "%",
                    NamedTextColor.DARK_AQUA));
            var elem = plugin.getCombat().elemental();
            StringBuilder elLine = new StringBuilder();
            for (School school : School.values()) {
                double r = elem.resistOf(uuid, school);
                if (r > 0.0) {
                    elLine.append(school.id()).append(" ").append((int) r).append("%  ");
                }
            }
            lore.add(Component.text("Стихии: " + (elLine.length() > 0 ? elLine.toString().trim() : "—"),
                    NamedTextColor.DARK_AQUA));
            lore.add(Component.text(BookItems.msg(plugin, "book.resist.cap", "Кап: {cap}%")
                    .replace("{cap}", String.valueOf((int) plugin.getResists().cap())),
                    NamedTextColor.DARK_GRAY));
            meta.lore(lore);
        });
        return item;
    }

    private ItemStack crownItem(RaskolClasses plugin, Player player, PlayerClass pc) {
        UUID uuid = player.getUniqueId();
        String title = plugin.getFlavorService().titleOf(uuid, pc);
        ItemStack item = new ItemStack(Material.GOLDEN_HELMET);
        item.editMeta(meta -> {
            meta.displayName(Component.text(BookItems.msg(plugin, "book.crown.title", "Корона и титул"),
                    NamedTextColor.GOLD));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(Component.text(BookItems.msg(plugin, "book.crown.crown", "Корона: {name}")
                    .replace("{name}", plugin.getFlavorService().crownDisplayName(uuid)),
                    NamedTextColor.WHITE));
            lore.add(Component.text(BookItems.msg(plugin, "book.crown.titleline", "Титул: {name}")
                    .replace("{name}", title.isEmpty() ? "—" : title), NamedTextColor.WHITE));
            lore.add(Component.text(BookItems.msg(plugin, "book.crown.aura",
                    "Аура-партикл видна союзникам и врагам"), NamedTextColor.DARK_GRAY));
            meta.lore(lore);
        });
        return item;
    }
}
