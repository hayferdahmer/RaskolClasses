// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.command.sub;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.hook.GearHook;
import dev.raskol.classes.hook.SetBonusService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/** 1.11.4 (P4d): /rc gear [player] — экипировка, статы шмота, сеты N/4. */
public final class GearSub implements CommandSub {

    private final RaskolClasses plugin;

    public GearSub(RaskolClasses plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "gear";
    }

    @Override
    public String permission() {
        return "raskolclasses.debug";
    }

    @Override
    public boolean execute(CommandSender sender, String[] tailArgs) {
        Player target = tailArgs.length > 0 ? Bukkit.getPlayer(tailArgs[0])
                : (sender instanceof Player p ? p : null);
        if (target == null) {
            sender.sendMessage(Component.text("Игрок не найден.", NamedTextColor.RED));
            return true;
        }
        printGearInfo(sender, target);
        return true;
    }

    @Override
    public List<String> complete(CommandSender sender, String[] fullArgs) {
        if (fullArgs.length == 2) {
            return SubUtil.filter(SubUtil.onlinePlayerNames(), fullArgs[1]);
        }
        return List.of();
    }

    private void printGearInfo(CommandSender sender, Player target) {
        UUID uuid = target.getUniqueId();
        GearHook gearHook = plugin.getGearHook();
        SetBonusService setBonusService = plugin.getSetBonusService();

        sender.sendMessage(Component.text("=== Экипировка " + target.getName() + " ===", NamedTextColor.GOLD));

        if (gearHook == null || !gearHook.isAvailable()) {
            sender.sendMessage(Component.text("RaskolGear не установлен.", NamedTextColor.RED));
            return;
        }

        GearHook.EquippedItem weapon = gearHook.getEquippedWeapon(target);
        if (weapon != null) {
            sender.sendMessage(Component.text("Оружие: " + weapon.className() + " " + weapon.rarity(),
                    NamedTextColor.AQUA));
        } else {
            sender.sendMessage(Component.text("Оружие: нет", NamedTextColor.GRAY));
        }

        List<GearHook.EquippedItem> armor = gearHook.getEquippedArmor(target);
        if (!armor.isEmpty()) {
            sender.sendMessage(Component.text("Броня:", NamedTextColor.AQUA));
            for (GearHook.EquippedItem item : armor) {
                sender.sendMessage(Component.text("  • " + item.slot() + ": "
                        + item.className() + " " + item.rarity(), NamedTextColor.GRAY));
            }
        }

        List<SetBonusService.ActiveSet> sets = setBonusService.getActiveSets(uuid);
        if (!sets.isEmpty()) {
            sender.sendMessage(Component.text("Активные сеты:", NamedTextColor.YELLOW));
            for (SetBonusService.ActiveSet set : sets) {
                String status = set.full() ? "✔ активен" : "✘ неполный";
                sender.sendMessage(Component.text("  • " + set.className() + " " + set.rarity()
                        + " (" + set.count() + "/4) — " + status,
                        set.full() ? NamedTextColor.GREEN : NamedTextColor.RED));
            }
        }

        sender.sendMessage(Component.text("Статы шмота:", NamedTextColor.LIGHT_PURPLE));
        sender.sendMessage(Component.text("  Физ. резист: +" + (int) gearHook.physResist(uuid) + "%",
                NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  Маг. резист: +" + (int) gearHook.magicResist(uuid) + "%",
                NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  HP: +" + (int) gearHook.hpBonus(uuid),
                NamedTextColor.GRAY));
        double reflect = gearHook.reflect(uuid);
        if (reflect > 0.0) {
            sender.sendMessage(Component.text("  Шипы: " + (int) reflect + "%",
                    NamedTextColor.GRAY));
        }
    }
}
