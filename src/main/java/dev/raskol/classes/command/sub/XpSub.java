// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.command.sub;

import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.character.progression.CharacterExperienceService;
import dev.raskol.classes.character.progression.CharacterProgressionEngine;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/** Административное управление уровнем и опытом персонажа. */
public final class XpSub implements CommandSub {

    private final RaskolClasses plugin;

    public XpSub(RaskolClasses plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "xp";
    }

    @Override
    public String permission() {
        return "raskolclasses.admin.progression";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendUsage(sender);
            return true;
        }

        String action = args[0].toLowerCase(Locale.ROOT);
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(Component.text("Игрок должен быть в сети. Укажите точный ник.", NamedTextColor.RED));
            return true;
        }

        CharacterExperienceService experience = plugin.getCharacterExperience();
        switch (action) {
            case "view" -> {
                if (args.length != 2) {
                    sendUsage(sender);
                    return true;
                }
                sendSnapshot(sender, target, experience.snapshot(target.getUniqueId()));
            }
            case "add" -> {
                if (args.length != 3) {
                    sendUsage(sender);
                    return true;
                }
                Long amount = parseLong(args[2]);
                if (amount == null || amount <= 0L) {
                    sender.sendMessage(Component.text(
                            "Количество XP должно быть положительным целым числом.", NamedTextColor.RED));
                    return true;
                }
                CharacterExperienceService.ProgressSnapshot before = experience.snapshot(target.getUniqueId());
                CharacterProgressionEngine.AwardResult result;
                try {
                    result = experience.award(target.getUniqueId(), amount);
                } catch (ArithmeticException exception) {
                    sender.sendMessage(Component.text(
                            "Слишком большое значение XP: переполнение при начислении.", NamedTextColor.RED));
                    return true;
                }
                CharacterExperienceService.ProgressSnapshot after = experience.snapshot(target.getUniqueId());
                audit(sender, target, "add", amount + " XP; " + before.level() + " -> " + after.level());
                String message = before.level() == CharacterProgressionEngine.MAX_LEVEL
                        ? target.getName() + " уже достиг 60 уровня; XP не сохраняется."
                        : "Начислено " + amount + " XP игроку " + target.getName()
                                + ". Уровень: " + after.level()
                                + (result.levelsGained() > 0 ? " (+" + result.levelsGained() + ")" : "")
                                + (result.unspentExperience() > 0 ? "; остаток на 60 уровне не сохраняется" : "");
                sender.sendMessage(Component.text(message, NamedTextColor.GREEN));
                sendSnapshot(sender, target, after);
            }
            case "set" -> {
                if (args.length != 4) {
                    sendUsage(sender);
                    return true;
                }
                Integer level = parseInt(args[2]);
                Long currentXp = parseLong(args[3]);
                if (level == null || currentXp == null) {
                    sender.sendMessage(Component.text("Уровень и XP должны быть целыми числами.", NamedTextColor.RED));
                    return true;
                }
                try {
                    CharacterExperienceService.ProgressSnapshot before = experience.snapshot(target.getUniqueId());
                    CharacterExperienceService.ProgressSnapshot after =
                            experience.set(target.getUniqueId(), level, currentXp);
                    audit(
                            sender,
                            target,
                            "set",
                            before.level() + "/" + before.currentExperience() + " -> " + after.level() + "/"
                                    + after.currentExperience());
                    sender.sendMessage(Component.text(
                            "Прогресс персонажа " + target.getName() + " обновлён.", NamedTextColor.GREEN));
                    sendSnapshot(sender, target, after);
                } catch (IllegalArgumentException exception) {
                    sender.sendMessage(Component.text(exception.getMessage(), NamedTextColor.RED));
                }
            }
            default -> sendUsage(sender);
        }
        return true;
    }

    @Override
    public List<String> complete(CommandSender sender, String[] fullArgs) {
        if (fullArgs.length == 2) {
            return SubUtil.filter(List.of("view", "add", "set"), fullArgs[1]);
        }
        if (fullArgs.length == 3) {
            return SubUtil.filter(SubUtil.onlinePlayerNames(), fullArgs[2]);
        }
        return List.of();
    }

    private void sendSnapshot(
            CommandSender sender, Player target, CharacterExperienceService.ProgressSnapshot snapshot) {
        String progress = snapshot.experienceToNextLevel().isPresent()
                ? snapshot.currentExperience() + " / "
                        + (snapshot.currentExperience()
                                + snapshot.experienceToNextLevel().getAsLong()) + " XP"
                : "максимальный уровень";
        sender.sendMessage(Component.text(
                target.getName() + ": уровень " + snapshot.level() + "/" + CharacterProgressionEngine.MAX_LEVEL + " · "
                        + progress,
                NamedTextColor.GOLD));
    }

    private void audit(CommandSender sender, Player target, String action, String details) {
        Logger logger = plugin.getLogger();
        String actorId = sender instanceof Player player ? player.getUniqueId().toString() : "console";
        logger.info("PROGRESSION_AUDIT actor=" + sender.getName() + " actorId=" + actorId
                + " action=" + action + " target=" + target.getUniqueId() + " details=\"" + details + "\"");
    }

    private static Long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static Integer parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static void sendUsage(CommandSender sender) {
        sender.sendMessage(Component.text(
                "/rc xp view <игрок> · /rc xp add <игрок> <XP> · /rc xp set <игрок> <уровень> <XP-в-уровне>",
                NamedTextColor.YELLOW));
    }
}
