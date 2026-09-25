// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.command;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.attribute.AttributeService;
import dev.raskol.classes.attribute.AttributeType;
import dev.raskol.classes.balance.BalanceSimulator;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.classsystem.SkillLevelProvider;
import dev.raskol.classes.combat.DamageProfile;
import dev.raskol.classes.combat.ResistService;
import dev.raskol.classes.gui.ClassBook;
import dev.raskol.classes.hook.GearHook;
import dev.raskol.classes.hook.SetBonusService;
import dev.raskol.classes.spec.Spec;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
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
 * Исполнитель и автодополнение /rc (1.4.0 → 1.10.0).
 * 1.10.0: shortName покрывает WARLOCK для TTK-матрицы.
 */
public final class RaskolCommand implements CommandExecutor, TabCompleter {

    private static final List<String> ROOT_SUBS = List.of(
            "menu", "reload", "debug", "health", "selftest", "gear");

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
                sender.sendMessage(Component.text("RaskolClasses: конфигурация перезагружена.",
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
            case "gear" -> handleGear(sender, args.length > 1 ? args[1] : null);
            default -> sendHelp(sender);
        }
        return true;
    }

    /* ------------------------------ GEAR ------------------------------ */

    private void handleGear(CommandSender sender, String targetName) {
        if (!sender.hasPermission("raskolclasses.debug")) {
            sender.sendMessage(Component.text(plugin.getRaskolConfig().message(
                    "no-permission", "Недостаточно прав"), NamedTextColor.RED));
            return;
        }
        Player target = targetName != null ? Bukkit.getPlayer(targetName)
                : (sender instanceof Player p ? p : null);
        if (target == null) {
            sender.sendMessage(Component.text("Игрок не найден.", NamedTextColor.RED));
            return;
        }
        printGearInfo(sender, target);
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

    /* ------------------------------ DEBUG ------------------------------ */

    private void handleDebug(CommandSender sender, String[] args) {
        if (!sender.hasPermission("raskolclasses.debug")) {
            sender.sendMessage(Component.text(plugin.getRaskolConfig().message(
                    "no-permission", "Недостаточно прав"), NamedTextColor.RED));
            return;
        }
        if (args.length >= 1 && "simulate".equalsIgnoreCase(args[0])) {
            if (args.length >= 2 && "matrix".equalsIgnoreCase(args[1])) {
                int level = parseLevel(args, 2);
                sender.sendMessage(Component.text("=== TTK-матрица (seed 42, уровень "
                        + level + ") ===", NamedTextColor.GOLD));
                sender.sendMessage(Component.text("Якорь balance.target-ttk-seconds = "
                        + fmt1(plugin.getConfig().getDouble("balance.target-ttk-seconds", 20.0))
                        + " с · «—» = не убивает за 60 с", NamedTextColor.GRAY));
                printMatrix(sender, level);
                return;
            }
            PlayerClass a = null;
            PlayerClass b = null;
            int level = 40;
            for (String tok : Arrays.copyOfRange(args, 1, args.length)) {
                PlayerClass pc = parseClass(tok.toUpperCase(Locale.ROOT));
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
                    ? "Таймаут 60 с (лидер: " + (r.winner() == null ? "ничья" : r.winner().name()) + ")"
                    : "TTK: " + fmt1(r.ttkSeconds()) + " с, победил " + r.winner().name(),
                    NamedTextColor.AQUA));
            sender.sendMessage(Component.text("Касты: " + r.castsA() + " / " + r.castsB()
                    + " · Уклонения: " + r.dodgesA() + " / " + r.dodgesB(),
                    NamedTextColor.GRAY));
            return;
        }
        if (args.length >= 1 && "matrix".equalsIgnoreCase(args[0])) {
            int level = parseLevel(args, 1);
            sender.sendMessage(Component.text("=== TTK-матрица (seed 42, уровень "
                    + level + ") ===", NamedTextColor.GOLD));
            printMatrix(sender, level);
            return;
        }
        Player target = args.length > 0 ? Bukkit.getPlayer(args[0])
                : (sender instanceof Player p ? p : null);
        if (target == null) {
            sender.sendMessage(Component.text("Игрок не найден.", NamedTextColor.RED));
            return;
        }
        printPlayerDebug(sender, target);
    }

    private void printMatrix(CommandSender sender, int level) {
        double[][] m = BalanceSimulator.matrix(plugin, level, 42L);
        PlayerClass[] pcs = PlayerClass.values();
        StringBuilder header = new StringBuilder("атак\\защ ");
        for (PlayerClass pc : pcs) {
            header.append(String.format(Locale.ROOT, "%8s", shortName(pc)));
        }
        sender.sendMessage(Component.text(header.toString(), NamedTextColor.DARK_GRAY));
        for (int i = 0; i < pcs.length; i++) {
            StringBuilder row = new StringBuilder(String.format(Locale.ROOT, "%-8s", shortName(pcs[i])));
            for (int j = 0; j < pcs.length; j++) {
                double v = m[i][j];
                row.append(Double.isFinite(v)
                        ? String.format(Locale.ROOT, "%8s", fmt1(v))
                        : String.format(Locale.ROOT, "%8s", "—"));
            }
            sender.sendMessage(Component.text(row.toString(), NamedTextColor.WHITE));
        }
    }

    private void printPlayerDebug(CommandSender sender, Player target) {
        UUID uuid = target.getUniqueId();
        PlayerClass pc = plugin.getClassProvider().getClassOf(target);
        AttributeService attrs = plugin.getAttributes();
        ResistService.Breakdown rb = plugin.getResists().breakdown(uuid);
        int charLevel = plugin.getCharacterLevels().characterLevel(uuid);
        int topN = Math.max(1, plugin.getConfig().getInt("character-level.top-n", 5));
        int cap = (int) plugin.getConfig().getDouble("attributes.level-cap", 60.0);
        Spec spec = plugin.getSpecService().getSpec(uuid);

        sender.sendMessage(Component.text("=== " + target.getName() + " ===", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("Класс: " + (pc != null ? pc.getDisplayName() : "—")
                + " (source=" + plugin.getConfig().getString("attributes.level-source", "character") + ")",
                NamedTextColor.AQUA));
        sender.sendMessage(Component.text("Спека: " + (spec != null ? spec.displayName() : "—")
                + " · таланты и сброс — в Книге класса", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("Уровень персонажа: " + charLevel
                + " (топ-" + topN + " скиллов, кап " + cap + ")", NamedTextColor.AQUA));
        if (pc == null) {
            return;
        }
        double str = attrs.value(uuid, AttributeType.STR);
        double agi = attrs.value(uuid, AttributeType.AGI);
        double intel = attrs.value(uuid, AttributeType.INT);
        double max = attrs.maxHp(uuid);
        double wp = plugin.getCombat().powers().weaponPower(uuid);
        double sp = plugin.getCombat().powers().spellPower(uuid);
        double hpow = plugin.getCombat().powers().healPower(uuid);
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
        sender.sendMessage(Component.text("Резист физ: " + (int) rb.physicalTotal()
                + "% · маг: " + (int) rb.magicTotal() + "%", NamedTextColor.LIGHT_PURPLE));
        for (ResistService.Modifier m : rb.active()) {
            sender.sendMessage(Component.text("  • " + m.source() + ": +" + (int) m.physicalPct()
                    + " физ / +" + (int) m.magicPct() + " маг", NamedTextColor.GRAY));
        }

        GearHook gearHook = plugin.getGearHook();
        if (gearHook != null && gearHook.isAvailable() && gearHook.hasGear(uuid)) {
            StringBuilder gearLine = new StringBuilder("Шмот RaskolGear: +")
                    .append((int) gearHook.physResist(uuid)).append(" физ / +")
                    .append((int) gearHook.magicResist(uuid)).append(" маг / +")
                    .append((int) gearHook.hpBonus(uuid)).append(" HP");
            if (gearHook.reflect(uuid) > 0.0) {
                gearLine.append(" / шипы ").append((int) gearHook.reflect(uuid)).append("%");
            }
            sender.sendMessage(Component.text(gearLine.toString(), NamedTextColor.DARK_AQUA));
        }

        sender.sendMessage(Component.text("Ресурс: " + (int) plugin.getResources().getValue(uuid)
                + "/100", NamedTextColor.AQUA));

        DamageProfile profile = new DamageProfile(20.0, 10.0, 5.0);
        double taken = plugin.getCombat().simulateTaken(target, profile);
        sender.sendMessage(Component.text("Симул. урона (20/10/5): дойдёт "
                + fmt1(taken) + " из 35", NamedTextColor.GRAY));
        int level = plugin.getSkillLevels().getLevel(uuid, pc.profileSkillName());
        sender.sendMessage(Component.text("Уровень " + pc.profileSkillName() + ": "
                + (level == SkillLevelProvider.NO_SKILL_SYSTEM ? "AuraSkills не подключён" : level),
                NamedTextColor.GRAY));
    }

    private static int parseLevel(String[] args, int idx) {
        if (idx < args.length) {
            try {
                int v = Integer.parseInt(args[idx]);
                if (v >= 1 && v <= 100) {
                    return v;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return 40;
    }

    private static PlayerClass parseClass(String tok) {
        try {
            return PlayerClass.valueOf(tok);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** 1.10.0: покрыт WARLOCK. */
    private static String shortName(PlayerClass pc) {
        return switch (pc) {
            case WARRIOR -> "воин";
            case HUNTER -> "охотник";
            case PRIEST -> "жрец";
            case MAGE -> "маг";
            case ROGUE -> "разбойник";
            case WARLOCK -> "чернокн.";
        };
    }

    private static String fmt1(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }

    /* ------------------------------ HEALTH ------------------------------ */

    private void handleHealth(CommandSender sender) {
        if (!sender.hasPermission("raskolclasses.debug")) {
            sender.sendMessage(Component.text(plugin.getRaskolConfig().message(
                    "no-permission", "Недостаточно прав"), NamedTextColor.RED));
            return;
        }
        double mspt = plugin.getServer().getAverageTickTime();
        double[] tpsArr = plugin.getServer().getTPS();
        double tps = tpsArr != null && tpsArr.length > 0 ? tpsArr[0] : 0.0;
        long purgeAge = System.currentTimeMillis() - plugin.getLastPurgeMillis();
        long uptimeSec = (System.currentTimeMillis() - plugin.getEnabledAtMillis()) / 1000L;
        NamedTextColor color = mspt < 50.0 ? NamedTextColor.GREEN
                : mspt < 55.0 ? NamedTextColor.YELLOW : NamedTextColor.RED;
        sender.sendMessage(Component.text("=== Здоровье RaskolClasses ===", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("MSPT: " + fmt1(mspt) + " · TPS: " + fmt1(tps), color));
        sender.sendMessage(Component.text("Аптайм плагина: " + uptimeSec + " с", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("Последний purge: " + (purgeAge / 1000L) + " с назад",
                NamedTextColor.GRAY));
        sender.sendMessage(Component.text("Онлайн: " + Bukkit.getOnlinePlayers().size() + " игроков",
                NamedTextColor.GRAY));
        sender.sendMessage(Component.text("Fx: активных " + plugin.getFx().activeCount()
                + ", битых ключей " + plugin.getFx().brokenSoundCount(), NamedTextColor.GRAY));
    }

    /* ------------------------------ HELP ------------------------------ */

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("=== /rc ===", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("/rc — сводка: класс, уровень персонажа, ресурс, резисты",
                NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/rc menu — Книга класса (способности, спеки, класс, таланты, шмот)",
                NamedTextColor.GRAY));
        sender.sendMessage(Component.text("Способности 1–5 и инсталляции — только свитками в хотбаре",
                NamedTextColor.GRAY));
        if (sender.hasPermission("raskolclasses.debug")) {
            sender.sendMessage(Component.text("/rc debug [player] — диагностика",
                    NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("/rc debug simulate [A] [B] [level] — дуэль TTK",
                    NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("/rc debug simulate matrix [level] — матрица 5×5",
                    NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("/rc gear [player] — экипировка и сеты",
                    NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("/rc health — MSPT/TPS/purge", NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("/rc selftest — headless-чеки формул",
                    NamedTextColor.YELLOW));
        }
        if (sender.hasPermission("raskolclasses.admin")) {
            sender.sendMessage(Component.text("/rc reload — перезагрузить конфигурацию",
                    NamedTextColor.RED));
        }
    }

    /* ------------------------------ TAB ------------------------------ */

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(ROOT_SUBS, args[0]);
        }
        if (args.length == 2 && "debug".equalsIgnoreCase(args[0])) {
            List<String> out = new ArrayList<>(List.of("simulate", "matrix"));
            for (Player p : Bukkit.getOnlinePlayers()) {
                out.add(p.getName());
            }
            return filter(out, args[1]);
        }
        if (args.length == 3 && "debug".equalsIgnoreCase(args[0])
                && ("simulate".equalsIgnoreCase(args[1]) || "matrix".equalsIgnoreCase(args[1]))) {
            List<String> out = new ArrayList<>();
            for (PlayerClass pc : PlayerClass.values()) {
                out.add(pc.name());
            }
            return filter(out, args[2]);
        }
        if (args.length == 4 && "debug".equalsIgnoreCase(args[0])
                && "simulate".equalsIgnoreCase(args[1])) {
            List<String> out = new ArrayList<>();
            for (PlayerClass pc : PlayerClass.values()) {
                out.add(pc.name());
            }
            out.addAll(List.of("10", "20", "30", "40", "50", "60"));
            return filter(out, args[3]);
        }
        if (args.length == 5 && "debug".equalsIgnoreCase(args[0])
                && "simulate".equalsIgnoreCase(args[1])) {
            return filter(List.of("10", "20", "30", "40", "50", "60"), args[4]);
        }
        if (args.length == 2 && "gear".equalsIgnoreCase(args[0])) {
            List<String> out = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                out.add(p.getName());
            }
            return filter(out, args[1]);
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
