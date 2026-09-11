// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.command;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.ability.AbilityDef;
import dev.raskol.classes.attribute.AttributeService;
import dev.raskol.classes.attribute.AttributeType;
import dev.raskol.classes.balance.BalanceSimulator;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.combat.CombatService;
import dev.raskol.classes.combat.DamageProfile;
import dev.raskol.classes.combat.ResistService;
import dev.raskol.classes.gui.ClassBook;
import dev.raskol.classes.install.InstallationType;
import dev.raskol.classes.spec.Spec;
import dev.raskol.classes.spec.SpecRegistry;
import dev.raskol.classes.talent.TalentModel;
import dev.raskol.classes.talent.TalentsRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Исполнитель и автодополнение команды /rc (1.4.0 + 1.5.4 + 1.6.4 + 1.6.12
 * + 1.7.6 + 1.8.0 + 1.9.0).
 *
 * Подкоманды:
 *  /rc [1-5]                    — применить способность слота N
 *  /rc menu                     — открыть Книгу класса (вкладка ABILITIES)
 *  /rc reload                   — перезагрузить конфиг (permission raskolclasses.admin)
 *  /rc debug [player]           — диагностика
 *  /rc debug simulate [A] [B] [level] — headless-дуэль в TTK-харнессе
 *  /rc debug simulate matrix [level]  — матрица 5×5 TTK
 *  /rc health                   — здоровье сервера (MSPT/TPS/purge/sound-fx)
 *  /rc selftest                 — headless-проверка формул (28 чеков)
 *  /rc talents [player]         — просмотр очков/купленного дерева (1.9.0)
 *  /rc talents reset [player] [free] — сброс талантов (платный/админ)
 *
 * 1.9.0: блок TALENTS (просмотр/сброс) + selftest чек 24 (reconcile).
 */
public final class RaskolCommand implements CommandExecutor, TabCompleter {

    private static final List<String> ROOT_SUBS = List.of(
            "1", "2", "3", "4", "5", "6", "7", "menu", "reload",
            "debug", "health", "selftest", "talents");
    private static final List<String> DEBUG_SUBS = List.of("simulate", "matrix");

    private final RaskolClasses plugin;

    public RaskolCommand(RaskolClasses plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);

        // /rc [1-5] — применить способность слота
        if (sub.length() == 1 && Character.isDigit(sub.charAt(0)) && sender instanceof Player p) {
            int slot = sub.charAt(0) - '0';
            PlayerClass pc = plugin.getClassProvider().getClassOf(p);
            if (pc == null) {
                p.sendMessage(Component.text(plugin.getRaskolConfig().message(
                        "no-class-cast", "Класс не выбран — способности недоступны"),
                        NamedTextColor.GRAY));
                return true;
            }
            AbilityDef def = plugin.getAbilities().getBySlot(pc, slot);
            if (def == null) {
                p.sendMessage(Component.text("Слот " + slot + " пуст.", NamedTextColor.GRAY));
                return true;
            }
            if (plugin.getAbilities().tryCast(p, def)) {
                plugin.getFx().onAttempt(p, def.id(), def.cooldownMillis());
            }
            return true;
        }

        switch (sub) {
            case "menu" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(Component.text("Только для игроков.", NamedTextColor.GRAY));
                    return true;
                }
                ClassBook.open(plugin, p, ClassBook.Tab.ABILITIES);
            }
            case "reload" -> {
                if (!sender.hasPermission("raskolclasses.admin")) {
                    sender.sendMessage(Component.text(plugin.getRaskolConfig().message(
                            "no-permission", "Недостаточно прав"), NamedTextColor.RED));
                    return true;
                }
                plugin.reloadPlugin();
                sender.sendMessage(Component.text("RaskolClasses: конфиг перезагружен.",
                        NamedTextColor.GREEN));
            }
            case "debug" -> handleDebug(sender, Arrays.copyOfRange(args, 1, args.length));
            case "health" -> handleHealth(sender);
            case "selftest" -> {
                if (!sender.hasPermission("raskolclasses.debug")) {
                    sender.sendMessage(Component.text(plugin.getRaskolConfig().message(
                            "no-permission", "Недостаточно прав"), NamedTextColor.RED));
                    return true;
                }
                dev.raskol.classes.selftest.SelftestRunner.run(plugin, sender);
            }
            case "talents" -> handleTalents(sender, Arrays.copyOfRange(args, 1, args.length));
            default -> sendHelp(sender);
        }
        return true;
    }

    /* ------------------------------ 1.9.0: TALENTS ------------------------------ */

    private void handleTalents(CommandSender sender, String[] args) {
        // /rc talents reset [player] [free]
        if (args.length >= 1 && "reset".equalsIgnoreCase(args[0])) {
            handleTalentsReset(sender, Arrays.copyOfRange(args, 1, args.length));
            return;
        }
        // /rc talents [player] — просмотр
        UUID targetUuid;
        String targetName;
        if (args.length == 0) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage(Component.text("Только для игроков (укажи ник).",
                        NamedTextColor.GRAY));
                return;
            }
            targetUuid = p.getUniqueId();
            targetName = p.getName();
        } else {
            if (!sender.hasPermission("raskolclasses.debug")) {
                sender.sendMessage(Component.text(plugin.getRaskolConfig().message(
                        "no-permission", "Недостаточно прав"), NamedTextColor.RED));
                return;
            }
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage(Component.text("Игрок «" + args[0] + "» не онлайн.",
                        NamedTextColor.RED));
                return;
            }
            targetUuid = target.getUniqueId();
            targetName = target.getName();
        }

        int charLevel = plugin.getCharacterLevels().characterLevel(targetUuid);
        int earned = plugin.getTalentService().earnedPoints(targetUuid);
        Spec spec = plugin.getSpecService().getSpec(targetUuid);
        if (spec == null) {
            sender.sendMessage(Component.text("=== Таланты: " + targetName + " ===", NamedTextColor.GOLD));
            sender.sendMessage(Component.text("Уровень персонажа: " + charLevel, NamedTextColor.AQUA));
            sender.sendMessage(Component.text("Очков заработано: " + earned, NamedTextColor.GRAY));
            sender.sendMessage(Component.text("Спека не выбрана — дерево недоступно.",
                    NamedTextColor.RED));
            return;
        }
        int spent = plugin.getTalentService().spentPoints(targetUuid, spec.id());
        int available = earned - spent;
        List<String> owned = plugin.getTalentService().purchased(targetUuid, spec.id());
        TalentModel.TalentTree tree = TalentsRegistry.treeOf(spec.id());
        int totalCost = tree != null ? tree.totalCost() : 21;

        sender.sendMessage(Component.text("=== Таланты: " + targetName + " ("
                + spec.displayName() + ") ===", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("Уровень персонажа: " + charLevel, NamedTextColor.AQUA));
        sender.sendMessage(Component.text("Очки: " + available + " доступно / "
                + spent + " потрачено / " + earned + " заработано (кап " + totalCost + ")",
                NamedTextColor.GREEN));
        if (owned.isEmpty()) {
            sender.sendMessage(Component.text("Дерево пустое — узлы не изучены.",
                    NamedTextColor.GRAY));
            return;
        }
        sender.sendMessage(Component.text("Изучено (" + owned.size() + "):", NamedTextColor.YELLOW));
        if (tree != null) {
            for (String nodeId : owned) {
                TalentModel.TalentNode n = tree.find(nodeId);
                if (n != null) {
                    sender.sendMessage(Component.text("  • [" + n.tier() + "/" + n.branch() + "] "
                            + n.name() + " — " + describeEffectShort(n.effect()), NamedTextColor.WHITE));
                }
            }
        }
        // суммарные бонусы (для админа/дебага)
        if (sender.hasPermission("raskolclasses.debug")) {
            sender.sendMessage(Component.text("Сводные бонусы:", NamedTextColor.DARK_GRAY));
            double[] av = plugin.getTalentService().avoidBonus(targetUuid);
            double rg = plugin.getTalentService().regenBonus(targetUuid);
            sender.sendMessage(Component.text("  avoid: dodge +" + fmt1(av[0]) + "%, parry +"
                    + fmt1(av[1]) + "%  |  regen +" + fmt1(rg) + "/с", NamedTextColor.DARK_GRAY));
        }
    }

    private void handleTalentsReset(CommandSender sender, String[] args) {
        boolean isAdmin = sender.hasPermission("raskolclasses.admin");
        Player target;
        boolean free;
        if (args.length == 0) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage(Component.text("Укажи ник (/rc talents reset <player>).",
                        NamedTextColor.GRAY));
                return;
            }
            target = p;
            free = isAdmin;
        } else {
            target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage(Component.text("Игрок «" + args[0] + "» не онлайн.",
                        NamedTextColor.RED));
                return;
            }
            // /rc talents reset <player> free — бесплатно для админа;
            // без free и без raskolclasses.admin — платно для самого игрока
            free = isAdmin && (args.length >= 2 && "free".equalsIgnoreCase(args[1]));
            if (!isAdmin && !target.getUniqueId().equals(((Player) sender).getUniqueId())) {
                sender.sendMessage(Component.text(plugin.getRaskolConfig().message(
                        "no-permission", "Недостаточно прав"), NamedTextColor.RED));
                return;
            }
        }

        var result = plugin.getTalentService().reset(target, free);
        String msg = switch (result) {
            case OK -> "Дерево талантов сброшено" + (free ? " (админ, бесплатно)." : ".");
            case NO_SPEC -> "Спека не выбрана — сбрасывать нечего.";
            case NO_PURCHASED -> "В дереве нет купленных узлов.";
            case POOR -> "Не хватает монет на сброс талантов.";
            case NO_ECONOMY -> "Экономика недоступна — сброс отключён.";
        };
        NamedTextColor color = result == dev.raskol.classes.talent.TalentService.ResetResult.OK
                ? NamedTextColor.GREEN : NamedTextColor.RED;
        sender.sendMessage(Component.text(msg, color));
        if (sender != target && result == dev.raskol.classes.talent.TalentService.ResetResult.OK) {
            target.sendMessage(Component.text(
                    "Администратор сбросил твоё дерево талантов. Очки возвращены в пул.",
                    NamedTextColor.YELLOW));
        }
    }

    private static String describeEffectShort(TalentModel.TalentEffect e) {
        if (e == null) {
            return "";
        }
        return switch (e.kind()) {
            case "attr" -> "+" + (int) e.value() + " " + e.target().toUpperCase();
            case "resist" -> "both".equals(e.target())
                    ? "+" + (int) e.value() + "/+" + (int) e.value2() + "% phys/magic"
                    : "+" + (int) e.value() + "% " + e.target();
            case "kit_base" -> "+" + (int) e.value() + " base " + e.target();
            case "kit_mult" -> "+" + (int) Math.round(e.value() * 100) + "% mult " + e.target();
            case "cd" -> "−" + (int) Math.round(e.value() * 100) + "% cd " + e.target();
            case "regen" -> "+" + (int) e.value() + "/s";
            case "avoid" -> "+" + (int) e.value() + "% " + e.target();
            case "proc" -> "+" + e.value() + " " + e.target();
            default -> e.kind();
        };
    }

    private static String fmt1(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }

    /* ------------------------------ DEBUG ------------------------------ */

    private void handleDebug(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player) && args.length == 0) {
            sender.sendMessage(Component.text("Использование: /rc debug [player|simulate|matrix|health]",
                    NamedTextColor.GRAY));
            return;
        }
        // /rc debug simulate [A] [B] [level] | /rc debug simulate matrix [level]
        if (args.length >= 1 && "simulate".equalsIgnoreCase(args[0])) {
            if (!sender.hasPermission("raskolclasses.debug")) {
                sender.sendMessage(Component.text(plugin.getRaskolConfig().message(
                        "no-permission", "Недостаточно прав"), NamedTextColor.RED));
                return;
            }
            if (args.length >= 2 && "matrix".equalsIgnoreCase(args[1])) {
                int level = 40;
                if (args.length >= 3) {
                    try {
                        level = Math.max(1, Math.min(100, Integer.parseInt(args[2])));
                    } catch (NumberFormatException ignored) {
                        // оставим 40
                    }
                }
                sender.sendMessage(Component.text("=== TTK-матрица (seed 42, уровень "
                        + level + ") ===", NamedTextColor.GOLD));
                sender.sendMessage(Component.text("Якорь balance.target-ttk-seconds = "
                        + plugin.getConfig().getDouble("balance.target-ttk-seconds", 20.0) + " с",
                        NamedTextColor.GRAY));
                BalanceSimulator.printMatrix(plugin, sender, level);
                return;
            }
            PlayerClass a = null;
            PlayerClass b = null;
            int level = 40;
            for (int i = 1; i < args.length; i++) {
                String tok = args[i].toUpperCase(Locale.ROOT);
                PlayerClass pc = parseClass(tok);
                if (pc != null) {
                    if (a == null) {
                        a = pc;
                    } else if (b == null) {
                        b = pc;
                    }
                } else {
                    try {
                        int v = Integer.parseInt(tok);
                        if (v >= 1 && v <= 100) {
                            level = v;
                        }
                    } catch (NumberFormatException ignored) {
                        // пропустить
                    }
                }
            }
            if (a == null) {
                a = PlayerClass.WARRIOR;
            }
            if (b == null) {
                b = a;
            }
            BalanceSimulator.DuelResult r = BalanceSimulator.duel(plugin, a, b, level, 42L);
            sender.sendMessage(Component.text("=== Симуляция " + a.name() + " ↔ "
                    + b.name() + " (уровень " + level + ", seed 42) ===", NamedTextColor.GOLD));
            sender.sendMessage(Component.text(r.timeout()
                    ? "Таймаут: " + r.ttkSeconds() + " с"
                    : "TTK: " + String.format(Locale.ROOT, "%.1f", r.ttkSeconds()) + " с",
                    NamedTextColor.AQUA));
            sender.sendMessage(Component.text("Победитель: " + r.winner().name()
                    + " (HP осталось: " + (int) r.winnerHpLeft() + ")", NamedTextColor.GREEN));
            return;
        }
        // /rc debug matrix [level]
        if (args.length >= 1 && "matrix".equalsIgnoreCase(args[0])) {
            if (!sender.hasPermission("raskolclasses.debug")) {
                sender.sendMessage(Component.text(plugin.getRaskolConfig().message(
                        "no-permission", "Недостаточно прав"), NamedTextColor.RED));
                return;
            }
            int level = 40;
            if (args.length >= 2) {
                try {
                    level = Math.max(1, Math.min(100, Integer.parseInt(args[1])));
                } catch (NumberFormatException ignored) {
                    // 40
                }
            }
            sender.sendMessage(Component.text("=== TTK-матрица (seed 42, уровень "
                    + level + ") ===", NamedTextColor.GOLD));
            BalanceSimulator.printMatrix(plugin, sender, level);
            return;
        }
        if (!sender.hasPermission("raskolclasses.debug")) {
            sender.sendMessage(Component.text(plugin.getRaskolConfig().message(
                    "no-permission", "Недостаточно прав"), NamedTextColor.RED));
            return;
        }
        Player target = args.length > 0 ? Bukkit.getPlayer(args[0]) : (Player) sender;
        if (target == null) {
            sender.sendMessage(Component.text("Игрок не найден.", NamedTextColor.RED));
            return;
        }
        UUID uuid = target.getUniqueId();
        PlayerClass pc = plugin.getClassProvider().getClassOf(target);
        AttributeService attrs = plugin.getAttributes();
        ResistService.Breakdown rb = plugin.getResists().breakdown(uuid);
        CombatService combat = plugin.getCombat();
        double physFactor = rb.physicalTotalFactor();
        double magicFactor = rb.magicTotalFactor();
        int charLevel = plugin.getCharacterLevels().characterLevel(uuid);
        int topN = Math.max(1, plugin.getConfig().getInt("character-level.top-n", 5));
        int cap = (int) plugin.getConfig().getDouble("attributes.level-cap", 60.0);
        Spec spec = plugin.getSpecService().getSpec(uuid);
        int talentsEarned = plugin.getTalentService().earnedPoints(uuid);
        int talentsSpent = spec != null ? plugin.getTalentService().spentPoints(uuid, spec.id()) : 0;

        sender.sendMessage(Component.text("=== " + target.getName() + " ===", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("Класс: " + (pc != null ? pc.getDisplayName() : "—")
                + " (source=" + plugin.getConfig().getString("attributes.level-source", "character") + ")",
                NamedTextColor.AQUA));
        sender.sendMessage(Component.text("Спека: " + (spec != null ? spec.displayName() : "—"),
                NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("Уровень персонажа: " + charLevel
                + " (топ-" + topN + " скиллов, кап " + cap + ")", NamedTextColor.AQUA));
        sender.sendMessage(Component.text("Таланты: " + talentsSpent + " потрачено / "
                + talentsEarned + " заработано", NamedTextColor.YELLOW));
        if (pc == null) {
            return;
        }
        double str = attrs.value(uuid, AttributeType.STR);
        double agi = attrs.value(uuid, AttributeType.AGI);
        double intel = attrs.value(uuid, AttributeType.INT);
        double max = attrs.maxHp(uuid);
        double wp = combat.powers().weaponPower(uuid);
        double sp = combat.powers().spellPower(uuid);
        double hpow = combat.powers().healPower(uuid);
        double[] eff = attrs.effectiveAvoidance(uuid);

        sender.sendMessage(Component.text("STR " + (int) str + " · AGI " + (int) agi
                + " · INT " + (int) intel, NamedTextColor.WHITE));
        sender.sendMessage(Component.text("maxHP " + (int) max + " · WP " + fmt1(wp)
                + " · SP " + fmt1(sp) + " · HPow " + fmt1(hpow), NamedTextColor.WHITE));
        sender.sendMessage(Component.text("Уклонение: " + fmt1(eff[0]) + "% · Парирование: "
                + fmt1(eff[1]) + "%", NamedTextColor.GREEN));
        sender.sendMessage(Component.text("Крит мили: " + fmt1(attrs.critMeleeChance(uuid))
                + "% · Крит магии: " + fmt1(attrs.critSpellChance(uuid)) + "%",
                NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("Резист физ: " + (int) rb.physicalTotal() + "% (×"
                + fmt1(physFactor) + ") · маг: " + (int) rb.magicTotal() + "% (×"
                + fmt1(magicFactor) + ")", NamedTextColor.LIGHT_PURPLE));
        for (ResistService.Modifier m : rb.active()) {
            sender.sendMessage(Component.text("  • " + m.source() + ": +" + (int) m.physicalPct()
                    + " физ / +" + (int) m.magicPct() + " маг", NamedTextColor.GRAY));
        }
        sender.sendMessage(Component.text("Ресурс: " + (int) plugin.getResources().getValue(uuid)
                + "/100", NamedTextColor.AQUA));

        // симуляция урона по профилю 20 физ / 10 маг / 5 чист
        DamageProfile profile = new DamageProfile(20.0, 10.0, 5.0);
        double taken = combat.simulateTaken(target, profile);
        sender.sendMessage(Component.text("Симул. урона (20/10/5): дойдёт "
                + fmt1(taken) + " из 35", NamedTextColor.GRAY));
    }

    private static PlayerClass parseClass(String tok) {
        try {
            return PlayerClass.valueOf(tok);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /* ------------------------------ HEALTH ------------------------------ */

    private void handleHealth(CommandSender sender) {
        if (!sender.hasPermission("raskolclasses.debug")) {
            sender.sendMessage(Component.text(plugin.getRaskolConfig().message(
                    "no-permission", "Недостаточно прав"), NamedTextColor.RED));
            return;
        }
        double mspt = Bukkit.getAverageTickTime();
        double tps = Math.min(20.0, 1000.0 / Math.max(1.0, mspt));
        long purgeAge = System.currentTimeMillis() - plugin.getLastPurgeMillis();
        int fxStale = plugin.getFx().staleCount();
        int fxActive = plugin.getFx().activeCount();
        long uptimeSec = (System.currentTimeMillis() - plugin.getEnabledAtMillis()) / 1000L;
        NamedTextColor color = mspt < 50.0 ? NamedTextColor.GREEN
                : mspt < 55.0 ? NamedTextColor.YELLOW : NamedTextColor.RED;
        sender.sendMessage(Component.text("=== Здоровье RaskolClasses ===", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("MSPT: " + fmt1(mspt) + " (TPS ≈ "
                + fmt1(tps) + ")", color));
        sender.sendMessage(Component.text("Аптайм плагина: " + uptimeSec + " с", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("Последний purge: " + (purgeAge / 1000L) + " с назад",
                NamedTextColor.GRAY));
        sender.sendMessage(Component.text("FxService: активных " + fxActive + ", просроченных "
                + fxStale, NamedTextColor.GRAY));
        sender.sendMessage(Component.text("Онлайн: " + Bukkit.getOnlinePlayers().size() + " игроков",
                NamedTextColor.GRAY));
        int cooldownSize = plugin.getCooldowns().size();
        int attemptsSize = plugin.getAbilities().staleAttemptCount();
        sender.sendMessage(Component.text("Cooldowns: " + cooldownSize + ", Stale attempts: "
                + attemptsSize, NamedTextColor.GRAY));
        int brokenSounds = plugin.getFx().brokenSoundCount();
        if (brokenSounds > 0) {
            sender.sendMessage(Component.text("Сброшенных звуков: " + brokenSounds
                    + " (проверь vfx.*.cast-sound в конфиге)", NamedTextColor.RED));
        }
        List<String> problems = plugin.getConfigValidator().problems();
        if (problems.isEmpty()) {
            sender.sendMessage(Component.text("ConfigValidator: проблем не найдено",
                    NamedTextColor.GREEN));
        } else {
            sender.sendMessage(Component.text("ConfigValidator: " + problems.size() + " проблем(ы)",
                    NamedTextColor.RED));
            for (String p : problems) {
                sender.sendMessage(Component.text("  • " + p, NamedTextColor.RED));
            }
        }
    }

    /* ------------------------------ HELP ------------------------------ */

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("=== /rc ===", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("/rc [1-5] — применить способность слота",
                NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/rc menu — открыть Книгу класса", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/rc talents [player] — просмотр дерева талантов",
                NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/rc talents reset [player] [free] — сброс дерева",
                NamedTextColor.GRAY));
        if (sender.hasPermission("raskolclasses.debug")) {
            sender.sendMessage(Component.text("/rc debug [player] — диагностика",
                    NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("/rc debug simulate [A] [B] [level] — дуэль",
                    NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("/rc debug simulate matrix [level] — TTK-матрица",
                    NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("/rc health — MSPT/TPS/purge", NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("/rc selftest — 28 headless-чеков формул",
                    NamedTextColor.YELLOW));
        }
        if (sender.hasPermission("raskolclasses.admin")) {
            sender.sendMessage(Component.text("/rc reload — перезагрузить конфиг",
                    NamedTextColor.RED));
        }
    }

    /* ------------------------------ TAB ------------------------------ */

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(ROOT_SUBS, args[0]);
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if ("debug".equals(sub)) {
                List<String> out = new ArrayList<>(DEBUG_SUBS);
                for (Player p : Bukkit.getOnlinePlayers()) {
                    out.add(p.getName());
                }
                return filter(out, args[1]);
            }
            if ("talents".equals(sub)) {
                List<String> out = new ArrayList<>();
                out.add("reset");
                for (Player p : Bukkit.getOnlinePlayers()) {
                    out.add(p.getName());
                }
                return filter(out, args[1]);
            }
        }
        if (args.length == 3) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if ("debug".equals(sub) && ("simulate".equalsIgnoreCase(args[1])
                    || "matrix".equalsIgnoreCase(args[1]))) {
                List<String> out = new ArrayList<>();
                for (PlayerClass pc : PlayerClass.values()) {
                    out.add(pc.name());
                }
                return filter(out, args[2]);
            }
            if ("talents".equals(sub) && "reset".equalsIgnoreCase(args[1])) {
                List<String> out = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    out.add(p.getName());
                }
                return filter(out, args[2]);
            }
        }
        if (args.length == 4) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if ("debug".equals(sub) && "simulate".equalsIgnoreCase(args[1])) {
                List<String> out = new ArrayList<>();
                for (PlayerClass pc : PlayerClass.values()) {
                    out.add(pc.name());
                }
                out.addAll(List.of("10", "20", "30", "40", "50", "60"));
                return filter(out, args[3]);
            }
            if ("talents".equals(sub) && "reset".equalsIgnoreCase(args[1])
                    && sender.hasPermission("raskolclasses.admin")) {
                return filter(List.of("free"), args[3]);
            }
        }
        if (args.length == 5) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if ("debug".equals(sub) && "simulate".equalsIgnoreCase(args[1])) {
                return filter(List.of("10", "20", "30", "40", "50", "60"), args[4]);
            }
        }
        return Collections.emptyList();
    }

    private static List<String> filter(List<String> options, String prefix) {
        String p = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String s : options) {
            if (s.toLowerCase(Locale.ROOT).startsWith(p)) {
                out.add(s);
            }
        }
        return out;
    }
}
