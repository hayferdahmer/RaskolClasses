// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.gui.book;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.gui.ClassBook;
import dev.raskol.classes.spec.Spec;
import dev.raskol.classes.spec.SpecRole;
import dev.raskol.classes.spec.SpecRoles;
import dev.raskol.classes.spec.model.Spec2Tree;
import dev.raskol.classes.spec.registry.Spec2Registry;
import dev.raskol.classes.spec.service.Spec2Service;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 1.11.4 (P4b) → 1.14.0 (Б3): вкладка «Специализации» на Spec2.
 * Карточка спеки: роль (SpecRoles), превью дерева (Spec2Tree: ёмкость и вершина),
 * выбор = spec2Service.chooseMain (один раз, бесплатно, с 15 уровня).
 *
 * 1.15.8 (Волна 8e, аудит 2.12-16): сообщения о смене пути синхронизированы с
 * текущим spec2-контрактом — упоминание legacy-команды /rc spec respec заменено
 * на актуальную процедуру (полный сброс дерева через GUI-кристалл во вкладке
 * «Деревья путей»). Добавлены пассивы ролей из config.yml spec2.role-passives:
 * FIGHTER +2% урона · TANK +5% ccResist и +5% получаемого лечения · HEALER +5%
 * исходящего лечения.
 */
public final class SpecsTab implements BookTabView {

    @Override
    public ClassBook.Tab id() {
        return ClassBook.Tab.SPECS;
    }

    @Override
    public void render(RenderCtx ctx) {
        List<Spec> specs = specsFor(ctx.pc());
        for (int i = 0; i < specs.size() && i < BookSlots.SPEC_SLOTS.length; i++) {
            ctx.inv().setItem(BookSlots.SPEC_SLOTS[i],
                    specItem(ctx.plugin(), ctx.player(), specs.get(i)));
        }
        // 1.14.0 (Б3): кнопка «сброс пути» временно отключена до Б5 —
        // респек в Spec2 идёт через GUI-кристалл во вкладке «Деревья путей».
    }

    @Override
    public void onClick(RenderCtx ctx, int slot, boolean left, boolean right) {
        RaskolClasses plugin = ctx.plugin();
        Player player = ctx.player();
        if (!right) {
            return;
        }
        int idx = BookSlots.indexOf(BookSlots.SPEC_SLOTS, slot);
        if (idx < 0) {
            return;
        }
        List<Spec> specs = specsFor(ctx.pc());
        if (idx >= specs.size()) {
            return;
        }
        Spec spec = specs.get(idx);
        Spec2Service svc = plugin.getSpec2Service();
        String main = svc.mainSpec(player.getUniqueId());
        if (main != null) {
            if (main.equals(spec.id())) {
                player.sendMessage(Component.text(
                        "Этот путь уже выбран тобой — основные способности активны.",
                        NamedTextColor.GRAY));
            } else {
                // 1.15.8 (2.12-16): актуальная процедура смены пути
                player.sendMessage(Component.text(
                        "Ты уже идёшь путём «" + displayNameOf(main)
                                + "». Смена пути: полный сброс дерева во вкладке"
                                + " «Деревья путей» (ПКМ по кристаллу), затем"
                                + " повторный выбор спеки здесь.",
                        NamedTextColor.RED));
            }
            return;
        }
        if (plugin.getCharacterLevels().characterLevel(player.getUniqueId()) < 15) {
            player.sendMessage(Component.text(
                    "Путь откроется на 15 уровне персонажа.",
                    NamedTextColor.GRAY));
            return;
        }
        boolean ok = svc.chooseMain(player, spec.id());
        if (ok) {
            player.sendMessage(Component.text(
                    "Ты вступаешь на путь «" + spec.displayName()
                            + "» — основные способности теперь твои.",
                    NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text(
                    "Путь недоступен. Проверь уровень (≥15) и отсутствие выбранной спеки.",
                    NamedTextColor.RED));
        }
    }

    private static List<Spec> specsFor(PlayerClass pc) {
        return Arrays.asList(Spec.forClass(pc));
    }

    private static String displayNameOf(String specId) {
        try {
            return Spec.valueOf(specId.toUpperCase(java.util.Locale.ROOT)).displayName();
        } catch (IllegalArgumentException ex) {
            return specId;
        }
    }

    private ItemStack specItem(RaskolClasses plugin, Player player, Spec spec) {
        Spec current = specOfMain(plugin, player);
        Spec2Tree tree = Spec2Registry.treeOf(spec.id());
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        item.editMeta(meta -> {
            boolean chosen = current != null && current == spec;
            meta.displayName(Component.text(spec.displayName(),
                    chosen ? NamedTextColor.GOLD : NamedTextColor.GRAY));
            List<Component> lore = new ArrayList<>();
            SpecRole role = SpecRoles.roleOf(spec.id());
            lore.add(Component.text("Роль: " + roleName(role), roleColor(role)));
            // 1.15.8 (2.12-16): пассивы ролей из config.yml spec2.role-passives
            String rolePassive = rolePassive(role);
            if (rolePassive != null) {
                lore.add(Component.text(rolePassive, NamedTextColor.YELLOW));
            }
            if (tree != null) {
                lore.add(Component.text("Путь: " + tree.nodes().size() + " узлов · ёмкость "
                        + tree.capacity() + " ранг", NamedTextColor.DARK_AQUA));
            } else {
                lore.add(Component.text("Путь будет доступен в будущем батче.",
                        NamedTextColor.DARK_GRAY));
            }
            lore.add(Component.empty());
            if (chosen) {
                lore.add(Component.text("✔ Это твой путь", NamedTextColor.GREEN));
            } else if (current != null) {
                // 1.15.8 (2.12-16): актуальная процедура смены пути
                lore.add(Component.text("Выбран другой путь: «"
                        + current.displayName() + "»", NamedTextColor.RED));
                lore.add(Component.text("Смена: сброс дерева → повторный выбор",
                        NamedTextColor.DARK_GRAY));
            } else {
                lore.add(Component.text("Не выбран · ПКМ — вступить (уровень 15+)",
                        NamedTextColor.YELLOW));
            }
            meta.lore(lore);
            if (chosen) {
                meta.addEnchant(Enchantment.LURE, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
        });
        return item;
    }

    /**
     * 1.15.8 (2.12-16): пассив роли из config.yml spec2.role-passives.
     * FIGHTER: +2% урона · TANK: +5% ccResist, +5% получаемого лечения ·
     * HEALER: +5% исходящего лечения.
     */
    private static String rolePassive(SpecRole role) {
        if (role == null) {
            return null;
        }
        return switch (role) {
            case FIGHTER -> "Пассив: +2% урона";
            case TANK -> "Пассив: +5% ccResist, +5% получаемого лечения";
            case HEALER -> "Пассив: +5% исходящего лечения";
        };
    }

    private static Spec specOfMain(RaskolClasses plugin, Player player) {
        String main = plugin.getSpec2Service().mainSpec(player.getUniqueId());
        if (main == null) {
            return null;
        }
        try {
            return Spec.valueOf(main.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String roleName(SpecRole role) {
        if (role == null) {
            return "—";
        }
        return switch (role) {
            case FIGHTER -> "ДД · Боец";
            case TANK -> "Танк · Защита";
            case HEALER -> "Лекарь · Поддержка";
        };
    }

    private static NamedTextColor roleColor(SpecRole role) {
        if (role == null) {
            return NamedTextColor.GRAY;
        }
        return switch (role) {
            case TANK -> NamedTextColor.GREEN;
            case HEALER -> NamedTextColor.WHITE;
            case FIGHTER -> NamedTextColor.RED;
        };
    }
}
