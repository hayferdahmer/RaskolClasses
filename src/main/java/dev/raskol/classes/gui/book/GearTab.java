// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.gui.book;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.gui.ClassBook;
import dev.raskol.classes.hook.GearHook;
import dev.raskol.classes.hook.SetBonusService;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 1.11.4 (P4b): вкладка «Шмот и сеты» (информационная, кликов нет). */
public final class GearTab implements BookTabView {

    @Override
    public ClassBook.Tab id() {
        return ClassBook.Tab.GEAR;
    }

    @Override
    public void render(RenderCtx ctx) {
        RaskolClasses plugin = ctx.plugin();
        Player player = ctx.player();
        GearHook gear = plugin.getGearHook();
        if (gear == null || !gear.isAvailable()) {
            ctx.inv().setItem(BookSlots.SLOT_GEAR_STATS, BookItems.infoItem(Material.BARRIER,
                    "RaskolGear не установлен", List.of(
                            "Плагин снаряжения не найден.",
                            "Статы и сеты недоступны.")));
            return;
        }
        UUID uuid = player.getUniqueId();

        GearHook.EquippedItem weapon = gear.getEquippedWeapon(player);
        if (weapon != null) {
            ctx.inv().setItem(BookSlots.SLOT_GEAR_WEAPON, BookItems.gearItem(weapon.item(),
                    weapon.className(), weapon.rarity(), "оружие"));
        } else {
            ctx.inv().setItem(BookSlots.SLOT_GEAR_WEAPON, BookItems.infoItem(Material.WOODEN_SWORD,
                    "Оружие: нет", List.of("Возьмите оружие класса в руку.")));
        }

        List<GearHook.EquippedItem> armor = gear.getEquippedArmor(player);
        for (int i = 0; i < BookSlots.GEAR_ARMOR_SLOTS.length; i++) {
            if (i < armor.size()) {
                GearHook.EquippedItem a = armor.get(i);
                ctx.inv().setItem(BookSlots.GEAR_ARMOR_SLOTS[i], BookItems.gearItem(a.item(),
                        a.className(), a.rarity(), a.slot()));
            } else {
                ctx.inv().setItem(BookSlots.GEAR_ARMOR_SLOTS[i],
                        BookItems.infoItem(Material.GRAY_STAINED_GLASS_PANE,
                                "Слот пуст", List.of("Наденьте предмет сета.")));
            }
        }

        ctx.inv().setItem(BookSlots.SLOT_GEAR_STATS, BookItems.infoItem(Material.NETHERITE_INGOT,
                "Статы шмота", List.of(
                        "Физ. резист: +" + (int) gear.physResist(uuid) + "%",
                        "Маг. резист: +" + (int) gear.magicResist(uuid) + "%",
                        "Здоровье: +" + (int) gear.hpBonus(uuid),
                        gear.reflect(uuid) > 0
                                ? "Шипы: " + (int) gear.reflect(uuid) + "% (полный сет)"
                                : "Шипы: —")));

        SetBonusService sets = plugin.getSetBonusService();
        List<SetBonusService.ActiveSet> active = sets.getActiveSets(uuid);
        for (int i = 0; i < BookSlots.GEAR_SET_SLOTS.length && i < active.size(); i++) {
            SetBonusService.ActiveSet s = active.get(i);
            List<String> lore = new ArrayList<>();
            lore.add("Предметов: " + s.count() + "/4");
            if (s.full()) {
                lore.add("✔ Сет активен — бонусы применены");
            } else {
                lore.add("✘ Неполный: нужно ещё " + (4 - s.count()));
            }
            ctx.inv().setItem(BookSlots.GEAR_SET_SLOTS[i], BookItems.infoItem(
                    s.full() ? Material.NETHERITE_CHESTPLATE : Material.IRON_CHESTPLATE,
                    "Сет: " + s.className() + " " + s.rarity(), lore));
        }
    }
}
