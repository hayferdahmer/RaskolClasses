// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.command.sub;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.attribute.AttributeService;
import dev.raskol.classes.attribute.AttributeType;
import dev.raskol.classes.balance.BalanceSimulator;
import dev.raskol.classes.cc.CCInstance;
import dev.raskol.classes.cc.CCService;
import dev.raskol.classes.cc.DRCategory;
import dev.raskol.classes.cc.DRState;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.classsystem.SkillLevelProvider;
import dev.raskol.classes.combat.DamageProfile;
import dev.raskol.classes.combat.ResistService;
import dev.raskol.classes.combat.school.School;
import dev.raskol.classes.spec.Spec;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 1.11.4 (P4d): /rc debug [player] · /rc debug simulate [A] [B] [level] ·
 * /rc debug simulate matrix [level] · /rc debug matrix [level].
 * 1.12.2 (Блок 5): в дампе игрока — строки пробития (pen) и стихийных резистов.
 * 1.13.0 (Б4): в дампе игрока — секция контроля: активные CC с остатком
 *         и DR-стеки с таймером до сброса окна.
 */
public final class DebugSub implements CommandSub {

    private final RaskolClasses plugin;

    public DebugSub(RaskolClasses plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "debug";
    }

    @Override
    public String permission() {
        return "raskolclasses.debug";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length >= 1 && "simulate".equalsIgnoreCase(args[0])) {
            if (args.length >= 2 && "matrix".equalsIgnoreCase(args[1])) {
                int level = SubUtil.parseLevel(args, 2);
                sender.sendMessage(Component.text("=== TTK-матрица (seed 42, уровень "
                        + level + ") ===", NamedTextColor.GOLD));
                sender.sendMessage(Component.text("Якорь balance.target-ttk-seconds = "
                        + SubUtil.fmt1(plugin.getConfig().getDouble("balance.target-ttk-seconds", 20.0))
                        + " с · «—» = не убивает за 60 с", NamedTextColor.GRAY));
                printMatrix(sender, level);
                return true;
            }
            PlayerClass a = null;
            PlayerClass b = null;
            int level = 40;
            for (String tok : Arrays.copyOfRange(args, 1, args.length)) {
                PlayerClass pc = SubUtil.parseClass(tok.toUpperCase(Locale.ROOT));
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
                    : "TTK: " + SubUtil.fmt1(r.ttkSeconds()) + " с, победил " + r.winner().name(),
                    NamedTextColor.AQUA));
            sender.sendMessage(Component.text("Касты: " + r.castsA() + " / " + r.castsB()
                    + " · Уклонения: " + r.dodgesA() + " / " + r.dodgesB(),
                    NamedTextColor.GRAY));
            return true;
        }
        if (args.length >= 1 && "matrix".equalsIgnoreCase(args[0])) {
            int level = SubUtil.parseLevel(args, 1);
            sender.sendMessage(Component.text("=== TTK-матрица (seed 42, уровень "
                    + level + ") ===", NamedTextColor.GOLD));
            sender.sendMessage(Component.text("Якорь balance.target-ttk-seconds = "
                    + SubUtil.fmt1(plugin.getConfig().getDouble("balance.target-ttk-seconds", 20.0))
                    + " с · «—» = не убивает за 60 с", NamedTextColor.GRAY));
            printMatrix(sender, level);
            return true;
        }
        Player target = args.length > 0 ? Bukkit.getPlayer(args[0])
                : (sender instanceof Player p ? p : null);
        if (target == null) {
            sender.sendMessage(Component.text("Игрок не найден.", NamedTextColor.RED));
            return true;
        }
        printPlayerDebug(sender, target);
        return true;
    }

    @Override
    public List<String> complete(CommandSender sender, String[] fullArgs) {
        if (fullArgs.length == 2) {
            List<String> out = new ArrayList<>(List.of("simulate", "matrix"));
            out.addAll(SubUtil.onlinePlayerNames());
            return SubUtil.filter(out, fullArgs[1]);
        }
        if (fullArgs.length == 3 && ("simulate".equalsIgnoreCase(fullArgs[1])
                || "matrix".equalsIgnoreCase(fullArgs[1]))) {
            List<String> out = new ArrayList<>();
            for (PlayerClass pc : PlayerClass.values()) {
                out.add(pc.name());
            }
            return SubUtil.filter(out, fullArgs[2]);
        }
        if (fullArgs.length == 4 && "simulate".equalsIgnoreCase(fullArgs[1])) {
            List<String> out = new ArrayList<>();
            for (PlayerClass pc : PlayerClass.values()) {
                out.add(pc.name());
            }
            out.addAll(List.of("10", "20", "30", "40", "50", "60"));
            return SubUtil.filter(out, fullArgs[3]);
        }
        if (fullArgs.length == 5 && "simulate".equalsIgnoreCase(fullArgs[1])) {
            return SubUtil.filter(List.of("10", "20", "30", "40", "50", "60"), fullArgs[4]);
        }
        return List.of();
    }

    /* ------------------------------ вывод ------------------------------ */

    private void printMatrix(CommandSender sender, int level) {
        double[][] m = BalanceSimulator.matrix(plugin, level, 42L);
        PlayerClass[] pcs = PlayerClass.values();
        StringBuilder header = new StringBuilder("атак\\защ ");
        for (PlayerClass pc : pcs) {
            header.append(String.format(Locale.ROOT, "%8s", SubUtil.shortName(pc)));
        }
        sender.sendMessage(Component.text(header.toString(), NamedTextColor.DARK_GRAY));
        for (int i = 0; i < pcs.length; i++) {
            StringBuilder row = new StringBuilder(String.format(Locale.ROOT, "%-8s", SubUtil.shortName(pcs[i])));
            for (int j = 0; j < pcs.length; j++) {
                double v = m[i][j];
                row.append(Double.isFinite(v)
                        ? String.format(Locale.ROOT, "%8s", SubUtil.fmt1(v))
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
        sender.sendMessage(Component.text("maxHP " + (int) max + " · WP " + SubUtil.fmt1(wp)
                + " · SP " + SubUtil.fmt1(sp) + " · HPow " + SubUtil.fmt1(hpow), NamedTextColor.WHITE));
        sender.sendMessage(Component.text("Уклонение: " + SubUtil.fmt1(eff[0]) + "% · Парирование: "
                + SubUtil.fmt1(eff[1]) + "%", NamedTextColor.GREEN));
        sender.sendMessage(Component.text("Крит мили: " + SubUtil.fmt1(attrs.critMeleeChance(uuid))
                + "% · Крит магии: " + SubUtil.fmt1(attrs.critSpellChance(uuid)) + "%",
                NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("Резист физ: " + (int) rb.physicalTotal()
                + "% · маг: " + (int) rb.magicTotal() + "%", NamedTextColor.LIGHT_PURPLE));
        for (ResistService.Modifier m : rb.active()) {
            sender.sendMessage(Component.text("  • " + m.source() + ": +" + (int) m.physicalPct()
                    + " физ / +" + (int) m.magicPct() + " маг", NamedTextColor.GRAY));
        }

        var penTraits = plugin.getCombat().penTraits();
        double penPhys = penTraits.totalPenPercent(uuid, "phys", plugin.getGearHook());
        double penMagic = penTraits.totalPenPercent(uuid, "magic", plugin.getGearHook());
        double penCapPct = plugin.getConfig().getDouble("schools.pen-pct-cap", 0.40) * 100.0;
        sender.sendMessage(Component.text("Пробитие: физ " + SubUtil.fmt1(penPhys)
                + "% · маг " + SubUtil.fmt1(penMagic) + "% (кап " + SubUtil.fmt1(penCapPct) + "%)",
                NamedTextColor.DARK_AQUA));
        var elem = plugin.getCombat().elemental();
        StringBuilder elLine = new StringBuilder();
        for (School school : School.values()) {
            double r = elem.resistOf(uuid, school);
            if (r > 0.0) {
                elLine.append(school.id()).append(" ").append((int) r).append("%  ");
            }
        }
        sender.sendMessage(Component.text("Стихии: "
                + (elLine.length() > 0 ? elLine.toString().trim() : "—"),
                NamedTextColor.DARK_AQUA));

        // 1.13.0 (Б4): секция контроля — активные CC и DR-стеки с таймером окна
        CCService ccSvc = plugin.getCC();
        List<CCInstance> ccs = ccSvc.activeOf(uuid);
        if (ccs.isEmpty()) {
            sender.sendMessage(Component.text("Контроль: —", NamedTextColor.GRAY));
        } else {
            long now = System.currentTimeMillis();
            StringBuilder ccLine = new StringBuilder();
            for (CCInstance inst : ccs) {
                ccLine.append(inst.type().ruName()).append(' ')
                        .append(inst.remainingTicks(now) / 20L).append("с  ");
            }
            sender.sendMessage(Component.text("Контроль: " + ccLine.toString().trim(),
                    NamedTextColor.RED));
        }
        long windowMs = (long) (plugin.getConfig().getDouble("cc.window-seconds", 15.0) * 1000.0);
        StringBuilder drLine = new StringBuilder();
        for (DRCategory cat : DRCategory.values()) {
            DRState st = ccSvc.drState(uuid, cat);
            if (st.stackCount() <= 0) {
                continue;
            }
            long left = Math.max(0L, windowMs - (System.currentTimeMillis() - st.lastAppliedAt()));
            drLine.append(cat.id()).append('=').append(st.stackCount())
                    .append(" (сброс ").append(left / 1000L).append("с)  ");
        }
        sender.sendMessage(Component.text("DR-стеки: "
                + (drLine.length() > 0 ? drLine.toString().trim() : "—"),
                NamedTextColor.DARK_AQUA));

        var gearHook = plugin.getGearHook();
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
                + SubUtil.fmt1(taken) + " из 35", NamedTextColor.GRAY));
        int level = plugin.getSkillLevels().getLevel(uuid, pc.profileSkillName());
        sender.sendMessage(Component.text("Уровень " + pc.profileSkillName() + ": "
                + (level == SkillLevelProvider.NO_SKILL_SYSTEM ? "AuraSkills не подключён" : level),
                NamedTextColor.GRAY));
    }
}
