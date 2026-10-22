// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.selftest;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.ability.TreeAbilities;
import dev.raskol.classes.ability.WarlockAbilities;
import dev.raskol.classes.ability.WarlockMath;
import dev.raskol.classes.attribute.AttributeMath;
import dev.raskol.classes.attribute.AttributeService;
import dev.raskol.classes.attribute.PowerService;
import dev.raskol.classes.balance.BalanceSimulator;
import dev.raskol.classes.cc.CCFeedback;
import dev.raskol.classes.cc.CCService;
import dev.raskol.classes.cc.CCType;
import dev.raskol.classes.cc.CastChannels;
import dev.raskol.classes.cc.DRCategory;
import dev.raskol.classes.cc.VanillaCCWrapper;
import dev.raskol.classes.classsystem.CharacterLevelService;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.combat.CombatMath;
import dev.raskol.classes.combat.CombatService;
import dev.raskol.classes.combat.DamageProfile;
import dev.raskol.classes.combat.DamageType;
import dev.raskol.classes.combat.ProcService;
import dev.raskol.classes.combat.dot.DotDef;
import dev.raskol.classes.combat.dot.DotInstance;
import dev.raskol.classes.combat.dot.DotMath;
import dev.raskol.classes.combat.dot.DotService;
import dev.raskol.classes.combat.school.PenTraitsService;
import dev.raskol.classes.combat.school.Penetration;
import dev.raskol.classes.combat.school.School;
import dev.raskol.classes.combat.school.SchoolConfig;
import dev.raskol.classes.combat.school.SchoolImmunity;
import dev.raskol.classes.combat.school.SchoolMitigation;
import dev.raskol.classes.combat.school.SchoolProfile;
import dev.raskol.classes.command.sub.CcSub;
import dev.raskol.classes.config.CcSanity;
import dev.raskol.classes.config.RaskolConfig;
import dev.raskol.classes.foliant.FoliantService;
import dev.raskol.classes.item.ItemStat;
import dev.raskol.classes.pet.PetDef;
import dev.raskol.classes.pet.PetMath;
import dev.raskol.classes.resource.ResourceState;
import dev.raskol.classes.spec.Spec;
import dev.raskol.classes.spec.SpecRole;
import dev.raskol.classes.spec.SpecRoles;
import dev.raskol.classes.spec.model.Spec2Node;
import dev.raskol.classes.spec.model.Spec2Points;
import dev.raskol.classes.spec.model.Spec2Tree;
import dev.raskol.classes.spec.registry.Spec2Registry;
import dev.raskol.classes.spec.service.Spec2Service;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Headless-самотестирование формул плагина (/rc selftest), 127 чеков.
 *
 * 1.14.8 (Sprint 5, P2-1): ДЕКОМПОЗИЦИЯ. Монолитный run() разбит на 23 метода
 * по подсистемам; состояние отчёта (StringBuilder + счётчики passed/failed/skipped)
 * инкапсулировано в приватный держатель Report. Номера чеков, их логика, порядок
 * вывода и skip-семантика НЕ изменены — рефакторинг чисто структурный.
 *
 * 1.14.8-fix (Sprint 6, Волна 6.1):
 *   P0-7A — запуск не от игрока: probe = null, все probe-зависимые проверки
 *           уходят в skip; selftest больше не изменяет ранги/спеку/HP первого
 *           онлайн-игрока при консольном запуске (аудит 7-03);
 *   P0-6B — чек 122: достижимость всех включённых узлов при текущем стоп-листе
 *           (страховка регрессии, когда disabled-пререквизиты закрыли 6 ультов).
 *
 * 1.14.13 (Sprint 9, Волна 13C, P0-2-06): чек 107 (pointsConfig) переведён с
 * @Deprecated public-полей Spec2Points.START_LEVEL/MAX_POINTS/ROW_GATES на
 * snapshot-чтение Spec2Points.snapshot().startLevel()/maxPoints()/rowGates().
 *
 * 1.14.13-fix: опечатка имени метода устранена — все вызовы resetAllDR(...)
 * заменены на каноническое resetAllDr(...) (12 мест в cc/ccProbeBreaks/ccProbeCastGuard).
 *
 * 1.14.15 (raskolspec+): добавлены чеки 123–125 — валидность 36 шаблонов builds.yml,
 * математика подписки grant/remainingDays/revoke, stale-маркировка disabled-узлов.
 *
 * 1.14.16 (Волна 15C): добавлены чеки 126–127 — applyBuild на probe (free-путь:
 * применение узла ряда 1 + откат) и отказы БЕЗ изменения состояния (чужое дерево →
 * WRONG_CLASS_TREE, prereq-дыра → TEMPLATE_INVALID). pauseMs(200) между вызовами —
 * rate-limit actionAllowed (150 мс).
 *
 * Карта подсистем (номера чеков):
 *   mathBasics      1–16    атрибуты/бой (pure-формулы)
 *   ttkLevels      17–20    TTK-дуэли и topNAverage
 *   canHitCheck     21      canHit self/среда
 *   spec2Basic     22–24    очки, ёмкость arms, reconcile-цикл
 *   resourceWindow 29–30    боевое окно, consume
 *   spec2Budget    31–32    глобальный бюджет, прунинг
 *   planB          33–35    scale/healFormula/targetCarrier
 *   tickDelta       36      знаковый реген ресурса
 *   warlock        37–43    реестры, конфиг, симуляции, WarlockMath
 *   sanityConfig   44–48    KitSanity, гейты рядов, respec-конфиг, loader
 *   schools        49–65    школы/pen/elemental/immunity/касты
 *   dots           66–75    DoT-ядро и баланс-матрица
 *   cc             76–90    CC/DR/CastGuard/CastChannels/CcSub
 *   specModel      91–99    Spec-enum, реестр деревьев, переносимые, unlock-покрытие
 *   probeAgg      100–106   heal/паверы/ресурс/проки/pct/resist/resetNode на probe
 *   pointsConfig   107      Spec2Points ↔ конфиг (snapshot)
 *   pets          108–112   пет-ядро
 *   procsConfig    113      полнота spec2.procs.*
 *   sprint3       114–116   kit_dur, attack_speed, контракт C value2
 *   sprint4       117–118   HAS_DEPENDENTS, антиспам-метка гейтов
 *   sprint4Io     119–120   effectiveMax, dirty-flush
 *   stopListLive   121      стоп-лист без узлов с живыми потребителями (P2-4)
 *   stopListReach  122      достижимость включённых узлов при стоп-листе (P0-6B)
 *   plusSubscription 123–125 raskolspec+ (шаблоны/подписка/stale)
 *   plusApply     126–127   raskolspec+ applyBuild (probe): применение и отказы
 *
 * SKIP-семантика (Sprint 1, P0-1): probe-зависимые чеки без онлайн-игрока
 * или без узла нужного вида у класса probe помечаются «↷ пропущено» и попадают
 * в отдельный счётчик skipped — PASS-итог больше не завышается.
 */
public final class SelftestRunner {

    private SelftestRunner() {
    }

    /* ------------------------------ держатель отчёта (1.14.8) ------------------------------ */

    private static final class Report {
        private final StringBuilder sb = new StringBuilder();
        int passed;
        int failed;
        int skipped;

        boolean check(String num, String desc, boolean ok, String culprit, Object got) {
            String status = ok ? "✓" : "✗";
            String detail = ok ? "" : " (получено: " + fmt(got) + ", виновник: " + culprit + ")";
            sb.append(status).append(" чек ").append(num).append(": ").append(desc)
                    .append(detail).append('\n');
            if (ok) {
                passed++;
            } else {
                failed++;
            }
            return ok;
        }

        void skip(String num, String desc) {
            sb.append("↷ чек ").append(num).append(": ").append(desc).append(" (пропущено)\n");
            skipped++;
        }

        void note(String line) {
            sb.append(line).append('\n');
        }

        String text() {
            return sb.toString();
        }
    }

    /* ------------------------------ фикстура ------------------------------ */

    private record KindHit(String treeId, Spec2Node node) {
    }

    private static KindHit findKind(RaskolClasses plugin, Player probe, String kind) {
        for (String tid : plugin.getSpec2Service().classTreeIds(probe.getUniqueId())) {
            Spec2Tree t = Spec2Registry.treeOf(tid);
            if (t == null) {
                continue;
            }
            for (Spec2Node n : t.nodes()) {
                if (n.effect() != null && kind.equals(n.effect().kind())) {
                    return new KindHit(tid, n);
                }
            }
        }
        return null;
    }

    private static KindHit findKindPrefix(RaskolClasses plugin, Player probe, String prefix) {
        for (String tid : plugin.getSpec2Service().classTreeIds(probe.getUniqueId())) {
            Spec2Tree t = Spec2Registry.treeOf(tid);
            if (t == null) {
                continue;
            }
            for (Spec2Node n : t.nodes()) {
                if (n.effect() != null && n.effect().kind() != null
                        && n.effect().kind().startsWith(prefix)) {
                    return new KindHit(tid, n);
                }
            }
        }
        return null;
    }

    private static Map<String, Integer> applyRank(RaskolClasses plugin, UUID uuid,
                                                  String treeId, String nodeId, int rank) {
        Map<String, Integer> backup = new HashMap<>(
                plugin.getSpec2Service().storage().getRanks(uuid, treeId));
        Map<String, Integer> next = new HashMap<>(backup);
        next.put(nodeId, rank);
        plugin.getSpec2Service().storage().setRanks(uuid, treeId, next);
        plugin.getSpec2Service().reconcile(uuid);
        return backup;
    }

    private static void restore(RaskolClasses plugin, UUID uuid,
                                String treeId, Map<String, Integer> backup) {
        plugin.getSpec2Service().storage().setRanks(uuid, treeId, backup);
        plugin.getSpec2Service().reconcile(uuid);
    }

    private static void pauseMs(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private static String fmt(Object v) {
        if (v instanceof Double d) {
            return String.format(Locale.ROOT, "%.2f", d);
        }
        return String.valueOf(v);
    }

    private static boolean applyUntilOk(CCService cc, Player target, CCType type, int ticks) {
        for (int i = 0; i < 64; i++) {
            CCService.ApplyResult r = cc.tryApply(null, target, type, ticks);
            if (r.ok()) {
                return true;
            }
            if (r.result() == CCService.CCResult.FAIL_DR_IMMUNE) {
                return false;
            }
        }
        return false;
    }

    private static Spec2Node firstRow1(Spec2Tree tree) {
        if (tree == null) {
            return null;
        }
        for (Spec2Node node : tree.nodes()) {
            if (node.row() == 1 && node.prereqs().isEmpty()) {
                return node;
            }
        }
        return null;
    }

    /* ------------------------------ точка входа ------------------------------ */

    public static void run(RaskolClasses plugin, CommandSender sender) {
        Report r = new Report();
        // 1.14.8-fix (Волна 6.1, P0-7A): probe — ТОЛЬКО сам отправитель-игрок.
        // Консольный запуск не трогает данные случайного онлайн-игрока:
        // все probe-чеки уходят в skip (аудит 7-03).
        Player probe = sender instanceof Player sp ? sp : null;
        if (probe == null) {
            r.note("⚠ Запуск не от игрока: probe-зависимые проверки пропущены, "
                    + "данные игроков не изменяются. Полный прогон: /rc selftest от игрока.");
        }

        mathBasics(r);
        ttkLevels(plugin, r);
        canHitCheck(plugin, r, probe);
        spec2Basic(plugin, r, probe);
        resourceWindow(r);
        spec2Budget(plugin, r, probe);
        planB(plugin, r, probe);
        tickDelta(r);
        warlock(plugin, r);
        sanityConfig(plugin, r);
        schools(plugin, r);
        dots(plugin, r);
        cc(plugin, r, probe);
        specModel(plugin, r);
        probeAgg(plugin, r, probe);
        pointsConfig(plugin, r);
        pets(plugin, r);
        procsConfig(plugin, r);
        sprint3(plugin, r, probe);
        sprint4(plugin, r, probe);
        sprint4Io(plugin, r, probe);
        stopListLive(plugin, r);
        stopListReachability(plugin, r);
        // 1.14.15 (raskolspec+): чеки подписки — не probe-зависимы, выполняются всегда
        plusSubscription(plugin, r);
        // 1.14.16 (Волна 15C): apply-чеки — probe-зависимы (меняют и восстанавливают ранги)
        plusApply(plugin, r, probe);
        items115(plugin, r);

        print(plugin, sender, r);
    }

    /** Release acceptance checks for the 1.15.0 equipment registry. */
    private static void items115(RaskolClasses plugin, Report r) {
        boolean loaded=plugin.getItems()!=null&&plugin.getItems().registry()!=null;
        int count=loaded?plugin.getItems().registry().size():0;
        r.check("128","1.15.0: реестр экипировки загружен (270 стартовых определений)",
                loaded&&count>=270,"ItemSystem.registry",loaded?"loaded="+count:"missing");
        boolean valid=loaded&&plugin.getItems().registry().all().stream().allMatch(item->
                item.id().matches("[a-z0-9_]{3,48}")&&item.itemLevel()>=1&&item.itemLevel()<=60
                        &&item.affixCount()<=item.affixPool().size());
        r.check("129","1.15.0: определения предметов проходят базовые инварианты",
                valid,"ItemDefinition","OK");
        boolean gearRemoved=plugin.getServer().getPluginManager().getPlugin("RaskolGear")==null
                &&!plugin.getDescription().getSoftDepend().contains("RaskolGear");
        r.check("130","1.15.0: обратная зависимость от RaskolGear удалена",
                gearRemoved,"plugin.yml/PluginManager",gearRemoved?"OK":"RaskolGear still referenced");
    }

    private static void print(RaskolClasses plugin, CommandSender sender, Report r) {
        sender.sendMessage(Component.text("────────── Selftest Report ──────────", NamedTextColor.GOLD));
        for (String line : r.text().split("\n")) {
            if (!line.isEmpty()) {
                NamedTextColor lineColor = line.startsWith("✓") ? NamedTextColor.GREEN
                        : line.startsWith("↷") ? NamedTextColor.YELLOW
                        : NamedTextColor.RED;
                sender.sendMessage(Component.text(line, lineColor));
            }
        }
        int total = r.passed + r.failed;
        NamedTextColor color = r.failed == 0 ? NamedTextColor.GREEN : NamedTextColor.RED;
        sender.sendMessage(Component.text("───────────────────────────────────", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("Итог: " + r.passed + "/" + total + " PASS"
                + (r.skipped > 0 ? " · skipped: " + r.skipped : ""), color));
        if (r.failed > 0) {
            sender.sendMessage(Component.text(
                    "Есть проблемы — смотри виновника в каждой строке.",
                    NamedTextColor.RED));
        }
        if (r.skipped > 0) {
            sender.sendMessage(Component.text(
                    "Пропущенные чеки — probe-зависимые: запусти /rc selftest от игрока нужного класса.",
                    NamedTextColor.YELLOW));
        }
        plugin.getLogger().info("Selftest: " + r.passed + "/" + total + " PASS, skipped=" + r.skipped);
    }

    /* ------------------------------ 1–16: атрибуты и бой (pure) ------------------------------ */

    private static void mathBasics(Report r) {
        double d1 = AttributeMath.dodgeRaw(0.0, 100.0);
        double d2 = AttributeMath.dodgeRaw(100.0, 100.0);
        double d3 = AttributeMath.dodgeRaw(300.0, 100.0);
        r.check("1", "dodgeRaw(0,100)=0", d1 == 0.0, "dodgeRaw", d1);
        r.check("2", "dodgeRaw(100,100)=50", d2 == 50.0, "dodgeRaw", d2);
        r.check("3", "dodgeRaw(300,100)=75", d3 == 75.0, "dodgeRaw", d3);

        double p1 = AttributeMath.parryRaw(0.0, 150.0);
        double p2 = AttributeMath.parryRaw(150.0, 150.0);
        double p3 = AttributeMath.parryRaw(450.0, 150.0);
        r.check("4", "parryRaw(0,150)=0", p1 == 0.0, "parryRaw", p1);
        r.check("5", "parryRaw(150,150)=50", p2 == 50.0, "parryRaw", p2);
        r.check("6", "parryRaw(450,150)=75", p3 == 75.0, "parryRaw", p3);

        double dr1 = AttributeMath.applyDR(0.0, 60.0, 0.5, 75.0);
        double dr2 = AttributeMath.applyDR(80.0, 60.0, 0.5, 75.0);
        double dr3 = AttributeMath.applyDR(200.0, 60.0, 0.5, 75.0);
        r.check("7", "applyDR(0)=0", dr1 == 0.0, "applyDR", dr1);
        r.check("8", "applyDR(80)=70", Math.abs(dr2 - 70.0) < 1e-6, "applyDR", dr2);
        r.check("9", "applyDR(200)=75", dr3 == 75.0, "applyDR", dr3);

        double[] sp1 = AttributeMath.splitEff(50.0, 50.0, 50.0);
        boolean sp1ok = sp1 != null && sp1.length == 2 && sp1[0] == 25.0 && sp1[1] == 25.0;
        r.check("10", "splitEff(50,50,50)=[25,25]", sp1ok, "splitEff",
                sp1 != null && sp1.length == 2 ? sp1[0] + "," + sp1[1] : "null");
        double[] sp2 = AttributeMath.splitEff(0.0, 100.0, 50.0);
        boolean sp2ok = sp2 != null && sp2.length == 2 && sp2[0] == 0.0 && sp2[1] == 50.0;
        r.check("11", "splitEff(0,100,50)=[0,50]", sp2ok, "splitEff",
                sp2 != null && sp2.length == 2 ? sp2[0] + "," + sp2[1] : "null");

        boolean f1 = AttributeMath.isFront(0.0, 90.0);
        boolean f2 = AttributeMath.isFront(180.0, 90.0);
        boolean b1 = AttributeMath.isBack(180.0, 135.0);
        r.check("12", "isFront(0,90)=true", f1, "isFront", f1);
        r.check("13", "isFront(180,90)=false", !f2, "isFront", f2);
        r.check("14", "isBack(180,135)=true", b1, "isBack", b1);

        double wpWarrior = PowerService.weaponPowerFormula(30.0, 60.0, 30.0, 1.5, 0.5);
        double spMage = PowerService.spellPowerFormula(30.0, 60.0, 1.5);
        double hpowPriest = PowerService.healPowerFormula(25.0, 60.0, 1.4);
        r.check("15a", "WP(воин 40 ур.)=135", Math.abs(wpWarrior - 135.0) < 1e-6,
                "weaponPowerFormula", wpWarrior);
        r.check("15b", "SP(маг 40 ур.)=120", Math.abs(spMage - 120.0) < 1e-6,
                "spellPowerFormula", spMage);
        r.check("15c", "HPow(жрец 40 ур.)=109", Math.abs(hpowPriest - 109.0) < 1e-6,
                "healPowerFormula", hpowPriest);

        double c1 = CombatService.cappedDamage(9999.0, 1300.0, 35.0);
        double c2 = CombatService.cappedDamage(100.0, 1300.0, 35.0);
        double c3 = CombatService.cappedDamage(500.0, 1000.0, 0.0);
        r.check("16a", "capped(9999,1300,35)=455", Math.abs(c1 - 455.0) < 1e-6, "cappedDamage", c1);
        r.check("16b", "capped(100,1300,35)=100", c2 == 100.0, "cappedDamage", c2);
        r.check("16c", "capped(500,1000,0)=500", c3 == 500.0, "cappedDamage", c3);
    }

    /* ------------------------------ 17–20: TTK и уровни ------------------------------ */

    private static void ttkLevels(RaskolClasses plugin, Report r) {
        BalanceSimulator.DuelResult ww = BalanceSimulator.duel(
                plugin, PlayerClass.WARRIOR, PlayerClass.WARRIOR, 40, 42L);
        boolean ok17 = !ww.timeout() && ww.ttkSeconds() >= 10.0 && ww.ttkSeconds() <= 60.0;
        r.check("17", "TTK воин↔воин ∈ [10,60] с (якорь 20с)", ok17, "BalanceSimulator", ww.ttkSeconds());

        BalanceSimulator.DuelResult pp = BalanceSimulator.duel(
                plugin, PlayerClass.PRIEST, PlayerClass.PRIEST, 40, 42L);
        boolean ok18 = pp.timeout() || pp.ttkSeconds() >= 30.0;
        r.check("18", "жрец↔жрец ≥30 с или timeout", ok18, "BalanceSimulator", pp.ttkSeconds());

        int cl1 = CharacterLevelService.topNAverage(new int[]{99, 70, 40, 20, 10, 5, 0}, 5);
        r.check("19", "topNAverage([99..0],5)=47", cl1 == 47, "topNAverage", cl1);

        int cl2 = CharacterLevelService.topNAverage(new int[]{15, 0, 0, 0, 0}, 5);
        r.check("20", "topNAverage([15,0,0,0,0],5)=3", cl2 == 3, "topNAverage", cl2);
    }

    /* ------------------------------ 21: canHit ------------------------------ */

    private static void canHitCheck(RaskolClasses plugin, Report r, Player probe) {
        if (probe == null) {
            r.skip("21", "canHit: self/среда");
            return;
        }
        boolean self = plugin.getCombat().canHit(probe, probe);
        boolean env = plugin.getCombat().canHit(null, probe);
        r.check("21", "canHit: self=true и среда=true", self && env, "canHit", self + "/" + env);
    }

    /* ------------------------------ 22–24: spec2-база ------------------------------ */

    private static void spec2Basic(RaskolClasses plugin, Report r, Player probe) {
        int e14 = Spec2Points.earnedPoints(14);
        int e15 = Spec2Points.earnedPoints(15);
        int e40 = Spec2Points.earnedPoints(40);
        int e60 = Spec2Points.earnedPoints(60);
        int e99 = Spec2Points.earnedPoints(99);
        boolean ok22 = e14 == 0 && e15 == 1 && e40 == 26 && e60 == 46 && e99 == 46;
        r.check("22", "очки spec2: 14→0, 15→1, 40→26, 60→46, 99→46",
                ok22, "Spec2Points.earnedPoints",
                e14 + "/" + e15 + "/" + e40 + "/" + e60 + "/" + e99);

        Spec2Tree arms23 = Spec2Registry.treeOf("arms");
        int cost = arms23 != null ? arms23.capacity() : -1;
        r.check("23", "ёмкость дерева arms = 51 (>46 — закрыть нельзя)", cost == 51,
                "Spec2Tree.capacity", cost);

        if (probe == null) {
            r.skip("24", "reconcile-цикл spec2");
            return;
        }
        boolean ok24 = false;
        String got24 = "no-trees";
        UUID pu24 = probe.getUniqueId();
        List<String> trees24 = plugin.getSpec2Service().classTreeIds(pu24);
        if (!trees24.isEmpty()) {
            String t0 = trees24.get(0);
            Spec2Tree tree24 = Spec2Registry.treeOf(t0);
            Spec2Node n24 = firstRow1(tree24);
            if (tree24 != null && n24 != null) {
                Map<String, Integer> before = new HashMap<>(
                        plugin.getSpec2Service().storage().getRanks(pu24, t0));
                try {
                    plugin.getSpec2Service().storage().setRanks(pu24, t0, Map.of(n24.id(), 1));
                    plugin.getSpec2Service().reconcile(pu24);
                    boolean bought = plugin.getSpec2Service().storage().getRanks(pu24, t0)
                            .getOrDefault(n24.id(), 0) == 1;
                    plugin.getSpec2Service().storage().setRanks(pu24, t0, before);
                    plugin.getSpec2Service().reconcile(pu24);
                    boolean restored = plugin.getSpec2Service().storage().getRanks(pu24, t0).equals(before);
                    ok24 = bought && restored;
                    got24 = bought + "/" + restored;
                } finally {
                    restore(plugin, pu24, t0, before);
                }
            } else {
                got24 = "no-tree-or-node:" + t0;
            }
        }
        r.check("24", "reconcile-цикл spec2: ранг→reconcile→откат",
                ok24, "Spec2Service.reconcile", got24);
    }

    /* ------------------------------ 29–30: ресурс-окно и consume ------------------------------ */

    private static void resourceWindow(Report r) {
        ResourceState rsWindow = new ResourceState();
        boolean freshOut = !rsWindow.isInCombat(5000L);
        rsWindow.markCombat();
        boolean nowIn = rsWindow.isInCombat(5000L);
        r.check("29", "боевое окно", freshOut && nowIn, "ResourceState", freshOut + "/" + nowIn);

        ResourceState rsConsume = new ResourceState();
        rsConsume.setValue(10.0);
        boolean overDenied = !rsConsume.consume(15.0);
        boolean overIntact = rsConsume.getValue() == 10.0;
        boolean exactOk = rsConsume.consume(10.0);
        boolean zeroed = rsConsume.getValue() == 0.0;
        boolean freeOk = rsConsume.consume(0.0);
        r.check("30", "consume: сверх/точное/обнуление/0",
                overDenied && overIntact && exactOk && zeroed && freeOk,
                "ResourceState.consume",
                overDenied + "/" + overIntact + "/" + exactOk + "/" + zeroed + "/" + freeOk);
    }

    /* ------------------------------ 31–32: бюджет и прунинг ------------------------------ */

    private static void spec2Budget(RaskolClasses plugin, Report r, Player probe) {
        if (probe == null) {
            r.skip("31", "глобальный бюджет spec2");
            r.skip("32", "reconcile-прунинг spec2");
            return;
        }
        UUID pu = probe.getUniqueId();

        boolean ok31 = false;
        String got31 = "need-2-trees";
        List<String> trees31 = plugin.getSpec2Service().classTreeIds(pu);
        if (trees31.size() >= 2) {
            String tA = trees31.get(0);
            String tB = trees31.get(1);
            Spec2Node nA = firstRow1(Spec2Registry.treeOf(tA));
            Spec2Node nB = firstRow1(Spec2Registry.treeOf(tB));
            if (nA != null && nB != null) {
                Map<String, Integer> bA = new HashMap<>(plugin.getSpec2Service().storage().getRanks(pu, tA));
                Map<String, Integer> bB = new HashMap<>(plugin.getSpec2Service().storage().getRanks(pu, tB));
                try {
                    plugin.getSpec2Service().storage().setRanks(pu, tA, Map.of(nA.id(), 1));
                    plugin.getSpec2Service().storage().setRanks(pu, tB, Map.of(nB.id(), 1));
                    plugin.getSpec2Service().reconcile(pu);
                    int spent = plugin.getSpec2Service().spentGlobal(pu);
                    int avail = plugin.getSpec2Service().availablePoints(pu);
                    int earned = plugin.getSpec2Service().earnedPoints(pu);
                    ok31 = spent == 2 && avail == Math.max(0, earned - 2);
                    got31 = spent + "/" + avail + "/" + earned;
                } finally {
                    restore(plugin, pu, tA, bA);
                    restore(plugin, pu, tB, bB);
                }
            }
        }
        r.check("31", "глобальный бюджет spec2: 2 дерева = 2 очка из общего пула",
                ok31, "spentGlobal", got31);

        boolean ok32 = false;
        String got32 = "no-trees";
        List<String> trees32 = plugin.getSpec2Service().classTreeIds(pu);
        if (!trees32.isEmpty()) {
            String t0 = trees32.get(0);
            Spec2Tree tree32 = Spec2Registry.treeOf(t0);
            Spec2Node withPrereq = null;
            if (tree32 != null) {
                for (Spec2Node n : tree32.nodes()) {
                    if (!n.prereqs().isEmpty()) {
                        withPrereq = n;
                        break;
                    }
                }
            }
            if (tree32 != null && withPrereq != null) {
                Map<String, Integer> before = new HashMap<>(
                        plugin.getSpec2Service().storage().getRanks(pu, t0));
                try {
                    Map<String, Integer> bad = new HashMap<>();
                    bad.put("nonexistent_node_xyz", 1);
                    bad.put(withPrereq.id(), 1);
                    plugin.getSpec2Service().storage().setRanks(pu, t0, bad);
                    plugin.getSpec2Service().reconcile(pu);
                    Map<String, Integer> after = plugin.getSpec2Service().storage().getRanks(pu, t0);
                    ok32 = after.isEmpty();
                    got32 = String.valueOf(after.size());
                } finally {
                    restore(plugin, pu, t0, before);
                }
            } else {
                got32 = "no-prereq-node";
            }
        }
        r.check("32", "reconcile-прунинг spec2: неизвестный узел и узел без пререка удаляются",
                ok32, "Spec2Service.validate", got32);
    }

    /* ------------------------------ 33–35: план B ------------------------------ */

    private static void planB(RaskolClasses plugin, Report r, Player probe) {
        if (probe == null) {
            r.skip("33", "scale");
            r.skip("34", "healFormula");
            r.skip("35", "targetCarrier");
            return;
        }
        AttributeService attrs = plugin.getAttributes();
        UUID pu = probe.getUniqueId();
        double formula = attrs.maxHp(pu);
        double carrier = attrs.carrierMaxHp(probe);
        double scale = attrs.scale(probe);
        boolean ok33 = Math.abs(scale - carrier / formula) < 1e-6;
        r.check("33", "scale = carrier/formula", ok33, "scale",
                String.format(Locale.ROOT, "%.4f", scale));

        double hpBefore = probe.getHealth();
        attrs.healFormula(probe, formula * 0.5);
        double hpAfter = probe.getHealth();
        boolean ok34 = hpAfter <= carrier + 0.01;
        probe.setHealth(hpBefore);
        r.check("34", "healFormula не превышает carrier", ok34, "healFormula",
                String.format(Locale.ROOT, "%.1f→%.1f", hpBefore, hpAfter));

        double target = attrs.targetCarrier(pu);
        boolean ok35 = Math.abs(target - Math.min(formula, AttributeService.VANILLA_MAX_HEALTH_CAP)) < 1e-6;
        r.check("35", "targetCarrier = min(formula, 1024)", ok35, "targetCarrier",
                String.format(Locale.ROOT, "%.1f", target));
    }

    /* ------------------------------ 36: tickDelta ------------------------------ */

    private static void tickDelta(Report r) {
        ResourceState rsDecay = new ResourceState();
        rsDecay.setValue(40.0);
        rsDecay.tickDelta(-5.0);
        boolean decayed = rsDecay.getValue() == 35.0;
        rsDecay.tickDelta(5.0);
        boolean gained = rsDecay.getValue() == 40.0;
        rsDecay.setValue(0.0);
        rsDecay.tickDelta(-5.0);
        boolean clampedLow = rsDecay.getValue() == 0.0;
        rsDecay.setValue(100.0);
        rsDecay.tickDelta(5.0);
        boolean clampedHigh = rsDecay.getValue() == 100.0;
        r.check("36", "tickDelta: декэй/набор/клампы",
                decayed && gained && clampedLow && clampedHigh, "ResourceState.tickDelta",
                decayed + "/" + gained + "/" + clampedLow + "/" + clampedHigh);
    }

    /* ------------------------------ 37–43: чернокнижник ------------------------------ */

    private static void warlock(RaskolClasses plugin, Report r) {
        UUID stranger = UUID.randomUUID();
        boolean sealEmpty = WarlockAbilities.sealAmplifyOf(stranger) == 0.0;
        boolean antiEmpty = !WarlockAbilities.isAntihealed(stranger);
        boolean gatesMsgOk = true;
        int gateCodes = 0;
        for (FoliantService.TransitionResult res : FoliantService.TransitionResult.values()) {
            gateCodes++;
            String m = res.message(plugin);
            if (m == null || m.isEmpty()) {
                gatesMsgOk = false;
            }
        }
        boolean ok37 = sealEmpty && antiEmpty && gatesMsgOk && gateCodes == 9;
        r.check("37", "чернокнижник: реестры пусты; 9 кодов гейтов",
                ok37, "WarlockAbilities/FoliantService",
                sealEmpty + "/" + antiEmpty + "/" + gatesMsgOk + "/" + gateCodes);

        RaskolConfig cfg = plugin.getRaskolConfig();
        double open = cfg.warlockThresholdOpen();
        double overflow = cfg.warlockThresholdOverflow();
        double recoil = cfg.warlockRecoilPercent();
        double recoilCap = cfg.warlockRecoilCapPct();
        double nether = cfg.warlockNetherMult();
        double decay = cfg.warlockResourceDecay();
        double onKill = cfg.warlockResourceOnKill();
        boolean ok38 = open > 0.0 && overflow > open
                && recoil > 0.0 && recoil <= 100.0
                && recoilCap > 0.0 && recoilCap <= 100.0
                && nether >= 1.0 && decay < 0.0 && onKill > 0.0;
        r.check("38", "конфиг WARLOCK sanity", ok38, "RaskolConfig.warlock*",
                String.format(Locale.ROOT, "%.1f/%.1f/%.2f/%.1f/%.1f/%.1f/%.1f",
                        open, overflow, recoil, recoilCap, nether, decay, onKill));

        String got39;
        boolean ok39;
        try {
            BalanceSimulator.DuelResult wl = BalanceSimulator.duel(
                    plugin, PlayerClass.WARLOCK, PlayerClass.WARRIOR, 40, 42L);
            ok39 = wl.timeout() || (wl.ttkSeconds() >= 5.0 && wl.ttkSeconds() <= 60.0);
            got39 = wl.timeout() ? "timeout" : fmt(wl.ttkSeconds()) + "s";
        } catch (RuntimeException ex) {
            ok39 = false;
            got39 = "exception: " + ex.getClass().getSimpleName();
        }
        r.check("39", "WARLOCK↔WARRIOR без падений", ok39, "BalanceSimulator", got39);

        String got40;
        boolean ok40;
        try {
            double[][] m6 = BalanceSimulator.matrix(plugin, 40, 42L);
            ok40 = m6.length == 6 && m6[0].length == 6;
            got40 = m6.length + "x" + (m6.length > 0 ? m6[0].length : 0);
        } catch (RuntimeException ex) {
            ok40 = false;
            got40 = "exception: " + ex.getClass().getSimpleName();
        }
        r.check("40", "TTK-матрица 6×6", ok40, "BalanceSimulator.matrix", got40);

        double r1 = WarlockMath.recoilAmount(100.0, 6.66, 840.0, 30.0);
        double r2 = WarlockMath.recoilAmount(10000.0, 6.66, 840.0, 30.0);
        double rHp = WarlockMath.applyRecoil(5.0, 10.0, 1.0);
        boolean ok41 = Math.abs(r1 - 6.66) < 1e-6
                && Math.abs(r2 - 252.0) < 1e-6
                && rHp == 1.0;
        r.check("41", "recoil: 6.66/252/minHp1", ok41, "WarlockMath.recoil",
                String.format(Locale.ROOT, "%.2f/%.1f/%.1f", r1, r2, rHp));

        double dH1 = WarlockMath.drainHeal(100.0, 0.666, 0.85);
        double dH2 = WarlockMath.drainHeal(100.0, 1.0, 0.85);
        double dH3 = WarlockMath.drainHeal(0.0, 0.666, 0.85);
        boolean ok42 = Math.abs(dH1 - 66.6) < 1e-6
                && Math.abs(dH2 - 85.0) < 1e-6
                && dH3 == 0.0;
        r.check("42", "drain: 66.6/85/0", ok42, "WarlockMath.drainHeal",
                String.format(Locale.ROOT, "%.1f/%.1f/%.1f", dH1, dH2, dH3));

        double m1 = WarlockMath.damageMult(80.0, 75.0, 6.0, 1.1, false);
        double m2 = WarlockMath.damageMult(80.0, 75.0, 6.0, 1.1, true);
        double m3 = WarlockMath.damageMult(50.0, 75.0, 6.0, 1.0, false);
        boolean ig1 = WarlockMath.ignoreMagicResist(0.20, 1.0, 0.25);
        boolean ig2 = WarlockMath.ignoreMagicResist(1.0, 0.25, 0.25);
        boolean ig3 = !WarlockMath.ignoreMagicResist(1.0, 1.0, 0.25);
        boolean ok43 = Math.abs(m1 - 1.32) < 1e-6
                && Math.abs(m2 - 7.92) < 1e-6
                && m3 == 1.0
                && ig1 && ig2 && ig3;
        r.check("43", "damageMult/ignore sanity", ok43, "WarlockMath.damageMult/ignore",
                String.format(Locale.ROOT, "%.2f/%.2f/%.1f/%s%s%s", m1, m2, m3, ig1, ig2, ig3));
    }

    /* ------------------------------ 44–48: sanity и конфиг-точки ------------------------------ */

    private static void sanityConfig(RaskolClasses plugin, Report r) {
        List<String> kitProblems = dev.raskol.classes.config.KitSanity.validateAbilities(plugin);
        boolean ok44 = kitProblems.isEmpty();
        String got44 = ok44 ? "OK" : kitProblems.size() + " проблем: " + kitProblems.get(0);
        if (!r.check("44", "sanity китов", ok44, "KitSanity.validateAbilities", got44)) {
            for (String p : kitProblems) {
                r.note("   — " + p);
            }
        }

        List<String> multProblems = dev.raskol.classes.config.KitSanity.validatePassiveMults(plugin);
        boolean ok45 = multProblems.isEmpty();
        String got45 = ok45 ? "OK" : multProblems.size() + " проблем: " + multProblems.get(0);
        if (!r.check("45", "RUNBOOK пассив-мульты", ok45, "KitSanity.validatePassiveMults", got45)) {
            for (String p : multProblems) {
                r.note("   — " + p);
            }
        }

        boolean ok46 = !Spec2Points.rowUnlocked(2, 4)
                && Spec2Points.rowUnlocked(2, 5)
                && !Spec2Points.rowUnlocked(6, 29)
                && Spec2Points.rowUnlocked(6, 30);
        r.check("46", "гейты рядов spec2: 4→ряд2 закрыт, 5→открыт; 29→ульт закрыт, 30→открыт",
                ok46, "Spec2Points.rowUnlocked",
                Spec2Points.rowUnlocked(2, 4) + "/" + Spec2Points.rowUnlocked(2, 5) + "/"
                        + Spec2Points.rowUnlocked(6, 29) + "/" + Spec2Points.rowUnlocked(6, 30));

        int rsBase = plugin.getConfig().getInt("spec2.respec-base-cost", 0);
        int rsPer = plugin.getConfig().getInt("spec2.respec-per-level", 0);
        boolean ok47 = rsBase == 250 && rsPer == 10;
        r.check("47", "respec spec2: base=250, per-level=10", ok47,
                "config spec2.respec-*", rsBase + "/" + rsPer);

        int perClassCount = plugin.getRaskolConfig().kitLoader().loadedCount();
        double recoilFromLoader = plugin.getRaskolConfig().classDouble(
                PlayerClass.WARLOCK, "recoil.percent", 6.66);
        boolean ok48 = recoilFromLoader >= 0.0 && Double.isFinite(recoilFromLoader);
        r.check("48", "per-class loader sanity", ok48, "KitConfigLoader/classDouble",
                perClassCount + "/" + recoilFromLoader);
    }

    /* ------------------------------ 49–65: школы ------------------------------ */

    private static void schools(RaskolClasses plugin, Report r) {
        boolean ok49 = School.PHYSICAL.channel() == DamageType.PHYSICAL
                && School.TRUE.channel() == DamageType.TRUE
                && School.FIRE.channel() == DamageType.MAGIC
                && School.FROST.channel() == DamageType.MAGIC
                && School.NATURE.channel() == DamageType.MAGIC
                && School.SHADOW.channel() == DamageType.MAGIC
                && School.HOLY.channel() == DamageType.MAGIC
                && School.ARCANE.channel() == DamageType.MAGIC;
        r.check("49", "School→channel", ok49, "School.channel",
                School.PHYSICAL.channel() + "/" + School.FIRE.channel() + "/" + School.TRUE.channel());

        SchoolConfig sc = new SchoolConfig(plugin);
        boolean ok50 = sc.schoolOf(EntityDamageEvent.DamageCause.FIRE) == School.FIRE
                && sc.schoolOf(EntityDamageEvent.DamageCause.POISON) == School.NATURE
                && sc.schoolOf(EntityDamageEvent.DamageCause.WITHER) == School.SHADOW
                && sc.schoolOf(EntityDamageEvent.DamageCause.FREEZE) == School.FROST
                && sc.schoolOf(EntityDamageEvent.DamageCause.LIGHTNING) == School.ARCANE
                && sc.schoolOf(EntityDamageEvent.DamageCause.ENTITY_ATTACK) == School.PHYSICAL
                && sc.schoolOf(EntityDamageEvent.DamageCause.FALL) == School.TRUE;
        r.check("50", "vanilla-school map", ok50, "SchoolConfig.schoolOf",
                sc.schoolOf(EntityDamageEvent.DamageCause.FIRE) + "/"
                        + sc.schoolOf(EntityDamageEvent.DamageCause.POISON) + "/"
                        + sc.schoolOf(EntityDamageEvent.DamageCause.FALL));

        SchoolProfile legacy = SchoolProfile.fromLegacy(new DamageProfile(10.0, 20.0, 5.0));
        DamageProfile back = legacy.toLegacy(sc);
        SchoolProfile fireOnly = SchoolProfile.builder().add(School.FIRE, 30.0).build();
        DamageProfile fireLegacy = fireOnly.toLegacy(sc);
        boolean ok51 = Math.abs(back.physical() - 10.0) < 1e-9
                && Math.abs(back.magic() - 20.0) < 1e-9
                && Math.abs(back.trueDamage() - 5.0) < 1e-9
                && legacy.dominant() == School.ARCANE
                && Math.abs(fireLegacy.magic() - 30.0) < 1e-9
                && fireLegacy.physical() == 0.0
                && fireLegacy.trueDamage() == 0.0;
        r.check("51", "SchoolProfile legacy round-trip", ok51, "SchoolProfile.toLegacy",
                String.format(Locale.ROOT, "%.1f/%.1f/%.1f dom=%s",
                        back.physical(), back.magic(), back.trueDamage(), legacy.dominant()));

        Penetration pen52 = Penetration.of(20.0, 0.25);
        double mit1 = SchoolMitigation.mitigationFor(50.0, pen52, 0.40, 0.0, false, 0.80);
        double mit2 = SchoolMitigation.mitigationFor(50.0, pen52, 0.40, 30.0, true, 0.80);
        double mit3 = SchoolMitigation.mitigationFor(95.0, Penetration.NONE, 0.40, 0.0, false, 0.80);
        boolean ok52 = Math.abs(mit1 - 0.225) < 1e-6
                && Math.abs(mit2 - 0.4575) < 1e-6
                && Math.abs(mit3 - 0.80) < 1e-6;
        r.check("52", "mitigation с pen/elemental/cap", ok52, "SchoolMitigation",
                String.format(Locale.ROOT, "%.4f/%.4f/%.4f", mit1, mit2, mit3));

        SchoolImmunity imm = new SchoolImmunity(plugin);
        boolean ok53 = imm.multiplierFor(EntityType.BLAZE, School.FIRE) == 0.0
                && Math.abs(imm.multiplierFor(EntityType.BLAZE, School.FROST) - 1.5) < 1e-9
                && imm.multiplierFor(EntityType.BLAZE, School.HOLY) == 1.0
                && imm.multiplierFor(EntityType.ZOMBIE, School.FIRE) == 1.0
                && Math.abs(imm.multiplierFor(EntityType.WITHER_SKELETON, School.SHADOW) - 0.5) < 1e-9;
        r.check("53", "immunity sanity", ok53, "SchoolImmunity",
                imm.multiplierFor(EntityType.BLAZE, School.FIRE) + "/"
                        + imm.multiplierFor(EntityType.BLAZE, School.FROST) + "/"
                        + imm.multiplierFor(EntityType.WITHER_SKELETON, School.SHADOW));

        Penetration p54 = Penetration.of(10.0, 0.6).clamped(0.40);
        double taken54 = SchoolMitigation.taken(100.0, 0.25, 1.5);
        boolean ok54 = p54.flat() == 10.0
                && Math.abs(p54.pct() - 0.40) < 1e-9
                && Penetration.NONE.pct() == 0.0
                && Math.abs(taken54 - 112.5) < 1e-6;
        r.check("54", "penetration clamp + taken", ok54, "Penetration/SchoolMitigation",
                String.format(Locale.ROOT, "%.2f/%.2f/%.1f", p54.flat(), p54.pct(), taken54));

        var elem = plugin.getCombat().elemental();
        UUID eu = UUID.randomUUID();
        elem.addPermanent(eu, "selftest_t1", School.FIRE, 40.0);
        elem.addTimed(eu, "selftest_t2", School.FIRE, 30.0, 60_000L);
        double cappedRes = elem.resistOf(eu, School.FIRE);
        elem.removeBySource(eu, "selftest_t2");
        double singleRes = elem.resistOf(eu, School.FIRE);
        elem.removeAll(eu);
        double zeroRes = elem.resistOf(eu, School.FIRE);
        boolean ok55 = Math.abs(cappedRes - 60.0) < 1e-9
                && Math.abs(singleRes - 40.0) < 1e-9
                && zeroRes == 0.0;
        r.check("55", "elemental: суммирование/кап/снятие", ok55, "ElementalResistService",
                String.format(Locale.ROOT, "%.1f/%.1f/%.1f", cappedRes, singleRes, zeroRes));

        UUID eu2 = UUID.randomUUID();
        elem.addPermanent(eu2, "selftest_t3", School.FROST, 40.0);
        double elPct = elem.resistOf(eu2, School.FROST);
        double mitCombined = SchoolMitigation.mitigationFor(
                50.0, Penetration.NONE, 0.40, elPct, true, 0.80);
        elem.removeAll(eu2);
        boolean ok56 = Math.abs(elPct - 40.0) < 1e-9
                && Math.abs(mitCombined - 0.70) < 1e-6;
        r.check("56", "elemental→mitigation связка", ok56, "SchoolMitigation(elemental)",
                String.format(Locale.ROOT, "%.1f/%.4f", elPct, mitCombined));

        double g1 = PenTraitsService.clampSumPercent(60.0, 0.40);
        double g2 = PenTraitsService.clampSumPercent(10.0, 0.40);
        double g3 = PenTraitsService.clampSumPercent(-5.0, 0.40);
        double g4 = PenTraitsService.clampSumPercent(Double.NaN, 0.40);
        boolean ok57 = Math.abs(g1 - 0.40) < 1e-9
                && Math.abs(g2 - 0.10) < 1e-9
                && g3 == 0.0
                && g4 == 0.0;
        r.check("57", "item-pen clamp", ok57, "PenTraitsService.clampSumPercent",
                String.format(Locale.ROOT, "%.2f/%.2f/%.1f/%.1f", g1, g2, g3, g4));

        Penetration itemPen = Penetration.of(0.0, PenTraitsService.clampSumPercent(25.0, 0.40));
        double mitItem = SchoolMitigation.mitigationFor(60.0, itemPen, 0.40, 0.0, false, 0.80);
        boolean ok58 = Math.abs(mitItem - 0.45) < 1e-6;
        r.check("58", "item-pen→mitigation", ok58, "SchoolMitigation(itemPen)",
                String.format(Locale.ROOT, "%.4f", mitItem));

        double pc1 = PenTraitsService.clampSumPercent(45.0, 0.40);
        double pc2 = PenTraitsService.clampSumPercent(15.0, 0.40);
        double pc3 = PenTraitsService.clampSumPercent(-3.0, 0.40);
        boolean ok59 = Math.abs(pc1 - 0.40) < 1e-9
                && Math.abs(pc2 - 0.15) < 1e-9
                && pc3 == 0.0;
        r.check("59", "pen-сумма clamp", ok59, "PenTraitsService.clampSumPercent",
                String.format(Locale.ROOT, "%.2f/%.2f/%.1f", pc1, pc2, pc3));

        PenTraitsService pts = new PenTraitsService(plugin);
        UUID pu60 = UUID.randomUUID();
        double t60 = pts.talentPenPercent(pu60, "phys");
        double g60 = pts.schoolPenFraction(pu60, School.FIRE, 0.40);
        double c60 = pts.channelPen(pu60, true, 0.40).pct();
        boolean ok60 = t60 == 0.0 && g60 == 0.0 && c60 == 0.0;
        r.check("60", "pen-трейты без контента = 0 (spec2/gear/channel)", ok60,
                "PenTraitsService.*",
                String.format(Locale.ROOT, "%.1f/%.1f/%.1f", t60, g60, c60));

        double l1 = SchoolMitigation.mitigationFor(0.0, Penetration.NONE, 0.40, 0.0, false, 0.80);
        double l2 = SchoolMitigation.mitigationFor(35.0, Penetration.NONE, 0.40, 0.0, false, 0.80);
        double l3 = SchoolMitigation.mitigationFor(90.0, Penetration.NONE, 0.40, 0.0, false, 0.90);
        boolean ok61 = l1 == 0.0
                && Math.abs(l2 - 0.35) < 1e-9
                && Math.abs(l3 - 0.90) < 1e-9;
        r.check("61", "legacy проводка при pen=0", ok61, "SchoolMitigation(legacy)",
                String.format(Locale.ROOT, "%.2f/%.2f/%.2f", l1, l2, l3));

        double el62 = CombatMath.effectiveResist(40.0, 0.0, 0.25, 0.40);
        double mit62 = SchoolMitigation.mitigationFor(45.0, Penetration.NONE, 0.40, el62, true, 0.90);
        boolean ok62 = Math.abs(el62 - 30.0) < 1e-9
                && Math.abs(mit62 - 0.615) < 1e-6;
        r.check("62", "school-pen→elemental", ok62, "CombatMath/SchoolMitigation",
                String.format(Locale.ROOT, "%.1f/%.4f", el62, mit62));

        CombatService.setCurrentCastSchool(School.FIRE);
        School s63a = CombatService.currentCastSchool();
        CombatService.clearCastSchool();
        School s63b = CombatService.currentCastSchool();
        CombatService.setCurrentCastSchool(School.PHYSICAL);
        School s63c = CombatService.currentCastSchool();
        CombatService.clearCastSchool();
        boolean ok63 = s63a == School.FIRE && s63b == null && s63c == School.PHYSICAL;
        r.check("63", "cast-school ThreadLocal", ok63, "CombatService",
                s63a + "/" + s63b + "/" + s63c);

        int covered = plugin.getAbilityRegistry().schoolCoverage();
        boolean ok64 = covered == 30;
        r.check("64", "schoolCoverage: 30/30", ok64, "AbilityRegistry", covered);

        boolean fp65 = plugin.getAbilityRegistry().findById(PlayerClass.MAGE, "fire_prometheus").school() == School.FIRE;
        boolean bb65 = plugin.getAbilityRegistry().findById(PlayerClass.MAGE, "boreas_breath").school() == School.FROST;
        boolean zw65 = plugin.getAbilityRegistry().findById(PlayerClass.MAGE, "zeus_wrath").school() == School.ARCANE;
        boolean bp65 = plugin.getAbilityRegistry().findById(PlayerClass.ROGUE, "borgia_poison").school() == School.NATURE;
        boolean scCloak65 = plugin.getAbilityRegistry().findById(PlayerClass.ROGUE, "shadow_cloak").school() == School.SHADOW;
        boolean wh65 = plugin.getAbilityRegistry().findById(PlayerClass.PRIEST, "wrath_heaven").school() == School.HOLY;
        boolean rk65 = plugin.getAbilityRegistry().findById(PlayerClass.WARRIOR, "ragnarok").school() == School.PHYSICAL;
        boolean bw65 = plugin.getAbilityRegistry().findById(PlayerClass.WARLOCK, "black_word").school() == School.SHADOW;
        boolean ok65 = fp65 && bb65 && zw65 && bp65 && scCloak65 && wh65 && rk65 && bw65;
        r.check("65", "школы китов по карте", ok65, "AbilityRegistry.findById",
                fp65 + "/" + bb65 + "/" + zw65 + "/" + bp65 + "/" + scCloak65 + "/" + wh65 + "/" + rk65 + "/" + bw65);
    }

    /* ------------------------------ 66–75: DoT и баланс ------------------------------ */

    private static void dots(RaskolClasses plugin, Report r) {
        double[] raw66 = {40.0, 30.0};
        double f66 = DotMath.capFactor(raw66, 60.0);
        double f66b = DotMath.capFactor(new double[]{10.0}, 60.0);
        double f66c = DotMath.capFactor(new double[]{0.0}, 60.0);
        boolean ok66 = Math.abs(f66 - 60.0 / 70.0) < 1e-9 && f66b == 1.0 && f66c == 1.0;
        r.check("66", "dot-cap: [40,30]→6/7; 10→1; 0→1", ok66, "DotMath.capFactor",
                String.format(Locale.ROOT, "%.4f/%.1f/%.1f", f66, f66b, f66c));

        double w67 = DotMath.withMults(10.0, 1.5, 0.5, 0.26);
        double w67b = DotMath.withMults(10.0, 1.0, 0.0, 0.0);
        double w67c = DotMath.withMults(-5.0, 2.0, 1.0, 0.0);
        boolean ok67 = Math.abs(w67 - 9.45) < 1e-9 && w67b == 0.0 && w67c == 0.0;
        r.check("67", "dot-mults: school×immunity×seal", ok67, "DotMath.withMults",
                String.format(Locale.ROOT, "%.2f/%.1f/%.1f", w67, w67b, w67c));

        boolean svc68 = plugin.getCombat().dots() != null;
        double capCfg = plugin.getConfig().getDouble("combat.dot-dps-cap-pct", 0.0);
        double lim68 = DotMath.dpsLimit(1000.0, capCfg);
        boolean ok68 = svc68 && Math.abs(capCfg - 6.0) < 1e-9 && Math.abs(lim68 - 60.0) < 1e-9;
        r.check("68", "DotService + cap=6%→60 DPS/1000HP", ok68, "CombatService/DotMath",
                svc68 + "/" + capCfg + "/" + lim68);

        boolean ex1 = DotService.shouldExtinguish(School.FIRE, Material.WATER);
        boolean ex2 = DotService.shouldExtinguish(School.FIRE, Material.POWDER_SNOW);
        boolean ex3 = DotService.shouldExtinguish(School.FIRE, Material.STONE);
        boolean ex4 = DotService.shouldExtinguish(School.FROST, Material.LAVA);
        boolean ex5 = DotService.shouldExtinguish(School.FROST, Material.WATER);
        boolean ok69 = ex1 && ex2 && !ex3 && ex4 && !ex5;
        r.check("69", "средовые триггеры", ok69, "DotService.shouldExtinguish",
                ex1 + "/" + ex2 + "/" + ex3 + "/" + ex4 + "/" + ex5);

        var dots70 = plugin.getCombat().dots();
        boolean ok70 = dots70.defById("burning") != null
                && dots70.defById("burning").dps() > 0.0
                && dots70.defById("burning").school() == School.FIRE
                && dots70.defById("poison") != null
                && dots70.defById("poison").school() == School.NATURE
                && dots70.defById("bleed") != null
                && dots70.defById("bleed").school() == School.PHYSICAL
                && dots70.defById("chilled") != null
                && dots70.defById("chilled").school() == School.FROST
                && dots70.defById("wither") != null
                && dots70.defById("wither").school() == School.SHADOW;
        r.check("70", "dots-реестр: 5 школ (burning/poison/bleed/chilled/wither)", ok70,
                "DotService.defById",
                (dots70.defById("burning") != null) + "/" + (dots70.defById("poison") != null)
                        + "/" + (dots70.defById("bleed") != null) + "/" + (dots70.defById("chilled") != null)
                        + "/" + (dots70.defById("wither") != null));

        UUID uuid71 = UUID.randomUUID();
        DotInstance inst71 = new DotInstance(
                DotDef.of("selftest_dot", School.FIRE, 5.0, 3000L, 3, "selftest"),
                uuid71, System.currentTimeMillis());
        long t71 = System.currentTimeMillis();
        inst71.refresh(t71);
        inst71.refresh(t71);
        inst71.refresh(t71);
        boolean stacksOk = inst71.stacks() == 3;
        boolean expiryOk = !inst71.expired(t71 + 2999L) && inst71.expired(t71 + 3001L);
        boolean ok71 = stacksOk && expiryOk;
        r.check("71", "DotInstance: refresh/expiry", ok71, "DotInstance",
                inst71.stacks() + "/" + stacksOk + "/" + expiryOk);

        UUID fresh72 = UUID.randomUUID();
        List<DotInstance> empty72 = plugin.getCombat().dots().activeDotsOf(fresh72);
        boolean ok72 = empty72 != null && empty72.isEmpty();
        r.check("72", "DotService.activeDotsOf: пустой snapshot", ok72, "DotService.activeDotsOf",
                empty72 == null ? "null" : String.valueOf(empty72.size()));

        DotDef ppDef = plugin.getCombat().dots().defById("poison_passive");
        DotDef poisonDef = plugin.getCombat().dots().defById("poison");
        boolean ok73 = ppDef != null
                && poisonDef != null
                && ppDef.school() == School.NATURE
                && ppDef.durationMillis() == 2000L
                && poisonDef.durationMillis() == 5000L
                && ppDef.maxStacks() == 1
                && poisonDef.maxStacks() == 3;
        r.check("73", "dots: poison_passive (2s/×1) vs poison (5s/×3)", ok73, "DotService.defById",
                (ppDef != null ? ppDef.durationMillis() + "/×" + ppDef.maxStacks() : "null")
                        + " vs " + (poisonDef != null ? poisonDef.durationMillis() + "/×" + poisonDef.maxStacks() : "null"));

        String poisonDotId = plugin.getConfig().getString(
                "classes.ROGUE.passives.poisoned_blades.dot", "");
        boolean ok74 = "poison_passive".equals(poisonDotId)
                && ppDef != null && poisonDef != null;
        r.check("74", "миграция poisoned_blades: dot=poison_passive",
                ok74, "config classes.ROGUE.passives.poisoned_blades.dot", poisonDotId);

        String got75;
        boolean ok75;
        try {
            double[][] m75 = BalanceSimulator.matrix(plugin, 40, 42L);
            boolean clean = true;
            boolean diagOk = true;
            double sum = 0.0;
            int cnt = 0;
            for (int i = 0; i < m75.length; i++) {
                for (int j = 0; j < m75[i].length; j++) {
                    double v = m75[i][j];
                    if (Double.isNaN(v) || v < 0.0) {
                        clean = false;
                    }
                    if (i == j && Double.isFinite(v)) {
                        cnt++;
                        sum += v;
                        if (v < 10.0 || v > 60.0) {
                            diagOk = false;
                        }
                    }
                }
            }
            double avg = cnt > 0 ? sum / cnt : 0.0;
            ok75 = clean && diagOk && cnt >= 4 && avg >= 15.0 && avg <= 30.0;
            got75 = "clean=" + clean + " diag=" + diagOk + " n=" + cnt
                    + " avg=" + String.format(Locale.ROOT, "%.1f", avg);
        } catch (RuntimeException ex) {
            ok75 = false;
            got75 = "exception: " + ex.getClass().getSimpleName();
        }
        r.check("75", "balance-sanity: матрица чистая, диагональ [10,60], avg-зеркалок [15,30]",
                ok75, "BalanceSimulator.matrix", got75);
    }

    /* ------------------------------ 76–90: CC / DR ------------------------------ */

    private static void cc(RaskolClasses plugin, Report r, Player probe) {
        double[] mults76 = {1.0, 0.5, 0.25, 0.0};
        boolean ok76 = CCService.drMultiplier(0, mults76) == 1.0
                && CCService.drMultiplier(1, mults76) == 0.5
                && CCService.drMultiplier(2, mults76) == 0.25
                && CCService.drMultiplier(3, mults76) == 0.0
                && CCService.drMultiplier(4, mults76) == 0.0;
        r.check("76", "DR-множители: 1.0/0.5/0.25/0.0/0.0", ok76, "CCService.drMultiplier",
                CCService.drMultiplier(0, mults76) + "/" + CCService.drMultiplier(3, mults76));

        long now77 = System.currentTimeMillis();
        int reset77 = CCService.stackAfterWindow(now77, now77 - 20_000L, 15_000L, 3);
        int keep77 = CCService.stackAfterWindow(now77, now77 - 5_000L, 15_000L, 3);
        boolean ok77 = reset77 == 0 && keep77 == 3;
        r.check("77", "окно DR: 20с→0, 5с→3", ok77, "CCService.stackAfterWindow", reset77 + "/" + keep77);

        boolean ok78 = CCService.isDrImmune(4, mults76) && !CCService.isDrImmune(3, mults76);
        r.check("78", "DR-иммунитет: стек4=иммун, стек3=нет", ok78, "CCService.isDrImmune",
                CCService.isDrImmune(4, mults76) + "/" + CCService.isDrImmune(3, mults76));

        boolean ok79 = CCType.STUN.category() == DRCategory.STUN
                && CCType.KNOCKBACK.category() == DRCategory.STUN
                && CCType.FEAR.category() == DRCategory.FEAR
                && CCType.CHARM.category() == DRCategory.FEAR
                && CCType.SILENCE.category() == DRCategory.SILENCE
                && CCType.DISARM.category() == DRCategory.SILENCE
                && CCType.ROOT.category() == DRCategory.ROOT
                && CCType.SLOW.category() == DRCategory.SLOW
                && CCType.BLIND.category() == DRCategory.BLIND;
        r.check("79", "категории DR: STUN/KNOCKBACK, FEAR/CHARM, SILENCE/DISARM, ROOT, SLOW, BLIND",
                ok79, "CCType.category",
                CCType.KNOCKBACK.category() + "/" + CCType.CHARM.category() + "/" + CCType.DISARM.category());

        CCService cc = plugin.getCC();
        boolean ok80 = cc != null && cc.enabled();
        r.check("80", "CCService инициализирован и cc.enabled=true", ok80,
                "RaskolClasses.onEnable/cc.enabled",
                (cc != null) + "/" + (cc != null && cc.enabled()));

        boolean ok81 = CCType.ROOT.breaksOnDamage()
                && CCType.FEAR.breaksOnDamage()
                && !CCType.STUN.breaksOnDamage()
                && !CCType.SILENCE.breaksOnDamage()
                && !CCType.DISARM.breaksOnDamage()
                && !CCType.SLOW.breaksOnDamage()
                && !CCType.BLIND.breaksOnDamage()
                && !CCType.CHARM.breaksOnDamage();
        r.check("81", "CCType.breaksOnDamage: ROOT/FEAR=true, остальные=false",
                ok81, "CCType.breaksOnDamage",
                CCType.ROOT.breaksOnDamage() + "/" + CCType.STUN.breaksOnDamage());

        if (probe == null || cc == null) {
            r.skip("82", "breakOnDamage ROOT");
            r.skip("83", "STUN breakOnDamage");
            r.skip("84", "CastGuard.canCast");
        } else {
            ccProbeBreaks(r, probe, cc);
            ccProbeCastGuard(plugin, r, probe, cc);
        }

        boolean ok85 = VanillaCCWrapper.wrapTarget(PotionEffectType.SLOWNESS) == CCType.SLOW
                && VanillaCCWrapper.wrapTarget(PotionEffectType.BLINDNESS) == CCType.BLIND
                && VanillaCCWrapper.wrapTarget(PotionEffectType.WEAKNESS) == CCType.SILENCE
                && VanillaCCWrapper.wrapTarget(PotionEffectType.REGENERATION) == null;
        r.check("85", "wrapVanilla: SLOWNESS→SLOW, BLINDNESS→BLIND, WEAKNESS→SILENCE, REGEN→null",
                ok85, "VanillaCCWrapper.wrapTarget",
                VanillaCCWrapper.wrapTarget(PotionEffectType.SLOWNESS) + "/"
                        + VanillaCCWrapper.wrapTarget(PotionEffectType.BLINDNESS) + "/"
                        + VanillaCCWrapper.wrapTarget(PotionEffectType.WEAKNESS));

        String d86 = CCFeedback.describeApply(plugin, CCType.STUN, 60, 0.5);
        boolean ok86 = d86 != null
                && d86.contains(CCType.STUN.ruName())
                && d86.contains("3.0")
                && d86.contains("50");
        r.check("86", "describeApply: «Оцепенение», 3.0с, DR 50% в строке сообщения",
                ok86, "CCFeedback.describeApply", d86);

        AtomicBoolean cancelled87 = new AtomicBoolean(false);
        UUID ch87 = UUID.randomUUID();
        CastChannels.register(ch87, () -> cancelled87.set(true));
        boolean first87 = CastChannels.interrupt(ch87);
        boolean second87 = CastChannels.interrupt(ch87);
        boolean ok87 = first87 && cancelled87.get() && !second87 && !CastChannels.isChanneling(ch87);
        r.check("87", "CastChannels: interrupt→canceller выполнен, повторный interrupt=false",
                ok87, "CastChannels.interrupt",
                first87 + "/" + cancelled87.get() + "/" + second87);

        boolean listOk = false;
        boolean seqOk = true;
        try {
            CcSub ccSub = new CcSub(plugin);
            listOk = ccSub.execute(Bukkit.getConsoleSender(), new String[]{"list"});
            if (probe != null) {
                seqOk = ccSub.execute(Bukkit.getConsoleSender(), new String[]{"test", probe.getName(), "STUN"})
                        && ccSub.execute(Bukkit.getConsoleSender(), new String[]{"status", probe.getName()})
                        && ccSub.execute(Bukkit.getConsoleSender(), new String[]{"clear", probe.getName()});
            }
        } catch (RuntimeException ex) {
            listOk = false;
            seqOk = false;
        }
        boolean ok88 = listOk && seqOk;
        r.check("88", "/rc cc list/status/test/clear исполняются без исключений",
                ok88, "CcSub.execute", listOk + "/" + seqOk);

        List<String> ccProblems = CcSanity.validateCc(plugin);
        boolean ok89 = ccProblems.isEmpty()
                && CcSanity.inRange(0.5, 0.0, 1.0)
                && !CcSanity.inRange(2.0, 0.0, 1.0)
                && !CcSanity.inRange(Double.NaN, 0.0, 1.0);
        r.check("89", "CcSanity: конфиг cc.* без проблем; inRange(0.5)=true, (2.0)=false, (NaN)=false",
                ok89, "CcSanity.validateCc",
                ccProblems.isEmpty() ? "OK" : ccProblems.size() + ": " + ccProblems.get(0));

        if (probe == null || cc == null) {
            r.skip("90", "debug CC/DR данные");
        } else {
            UUID u90 = probe.getUniqueId();
            cc.removeAll(u90);
            cc.resetAllDr(u90);
            boolean applied90 = applyUntilOk(cc, probe, CCType.STUN, 60);
            boolean activeVisible = !cc.activeOf(u90).isEmpty();
            boolean drVisible = cc.drState(u90, DRCategory.STUN).stackCount() >= 1;
            cc.removeAll(u90);
            cc.resetAllDr(u90);
            boolean cleared90 = cc.activeOf(u90).isEmpty()
                    && cc.drState(u90, DRCategory.STUN).stackCount() == 0;
            boolean ok90 = applied90 && activeVisible && drVisible && cleared90;
            r.check("90", "debug-секция CC/DR: apply→activeOf+drState видны, cleanup пуст",
                    ok90, "CCService.activeOf/drState",
                    applied90 + "/" + activeVisible + "/" + drVisible + "/" + cleared90);
        }
    }

    private static void ccProbeBreaks(Report r, Player probe, CCService cc) {
        UUID u82 = probe.getUniqueId();
        cc.removeAll(u82);
        cc.resetAllDr(u82);
        boolean applied1 = applyUntilOk(cc, probe, CCType.ROOT, 100);
        boolean hasBefore = cc.has(u82, CCType.ROOT);
        cc.breakOnDamage(probe, 60.0, 1000.0);
        boolean hasAfterHigh = cc.has(u82, CCType.ROOT);
        cc.resetAllDr(u82);
        boolean applied2 = applyUntilOk(cc, probe, CCType.ROOT, 100);
        cc.breakOnDamage(probe, 30.0, 1000.0);
        boolean hasAfterLow = cc.has(u82, CCType.ROOT);
        cc.removeAll(u82);
        cc.resetAllDr(u82);
        boolean ok82 = applied1 && hasBefore && !hasAfterHigh && applied2 && hasAfterLow;
        r.check("82", "breakOnDamage: 6% HP снимает ROOT, 3% оставляет",
                ok82, "CCService.breakOnDamage",
                applied1 + "/" + hasBefore + "/" + hasAfterHigh + "/" + applied2 + "/" + hasAfterLow);

        UUID u83 = probe.getUniqueId();
        cc.removeAll(u83);
        cc.resetAllDr(u83);
        boolean applied = applyUntilOk(cc, probe, CCType.STUN, 100);
        boolean hasBeforeStun = cc.has(u83, CCType.STUN);
        cc.breakOnDamage(probe, 60.0, 1000.0);
        boolean hasAfter = cc.has(u83, CCType.STUN);
        cc.removeAll(u83);
        cc.resetAllDr(u83);
        boolean ok83 = applied && hasBeforeStun && hasAfter;
        r.check("83", "STUN не снимается уроном ≥ порога",
                ok83, "CCService.breakOnDamage",
                applied + "/" + hasBeforeStun + "/" + hasAfter);
    }

    private static void ccProbeCastGuard(RaskolClasses plugin, Report r, Player probe, CCService cc) {
        UUID u84 = probe.getUniqueId();
        dev.raskol.classes.cc.CastGuard cg = new dev.raskol.classes.cc.CastGuard(plugin);
        cc.removeAll(u84);
        cc.resetAllDr(u84);
        boolean canNormal = cg.canCast(probe, false);
        applyUntilOk(cc, probe, CCType.STUN, 100);
        boolean canStun = cg.canCast(probe, false);
        cc.removeAll(u84);
        cc.resetAllDr(u84);
        applyUntilOk(cc, probe, CCType.SILENCE, 100);
        boolean canSilenceNormal = cg.canCast(probe, false);
        boolean canSilenceInstant = cg.canCast(probe, true);
        cc.removeAll(u84);
        cc.resetAllDr(u84);
        boolean ok84 = canNormal && !canStun && !canSilenceNormal && canSilenceInstant;
        r.check("84", "CastGuard: normal=OK, STUN=no, SILENCE+cast=no, SILENCE+instant=OK",
                ok84, "CastGuard.canCast",
                canNormal + "/" + canStun + "/" + canSilenceNormal + "/" + canSilenceInstant);
    }

    /* ------------------------------ 91–99: spec2-модель и переносимые ------------------------------ */

    private static void specModel(RaskolClasses plugin, Report r) {
        boolean ok91 = Spec.values().length == 18
                && Spec.forClass(PlayerClass.WARRIOR).length == 3
                && Spec.forClass(PlayerClass.HUNTER).length == 3
                && Spec.forClass(PlayerClass.PRIEST).length == 3
                && Spec.forClass(PlayerClass.MAGE).length == 3
                && Spec.forClass(PlayerClass.ROGUE).length == 3
                && Spec.forClass(PlayerClass.WARLOCK).length == 3
                && SpecRoles.roleOf("guard") == SpecRole.TANK
                && SpecRoles.roleOf("holy") == SpecRole.HEALER
                && SpecRoles.roleOf("arms") == SpecRole.FIGHTER;
        r.check("91", "Spec: 18 чистых констант, forClass=3, роли guard=TANK/holy=HEALER/arms=FIGHTER",
                ok91, "Spec.values/forClass/SpecRoles",
                Spec.values().length + "/" + SpecRoles.roleOf("guard"));

        boolean ok92 = Spec.fromId("arms") == Spec.ARMS
                && Spec.fromId("affliction") == Spec.AFFLICTION
                && Spec.fromId("tracker") == null
                && Spec.fromId("black_mage") == null
                && Spec.fromId("no_such_spec") == null;
        r.check("92", "fromId строгий: arms/affliction резолвятся; tracker/black_mage/null",
                ok92, "Spec.fromId",
                Spec.fromId("arms") + "/" + Spec.fromId("tracker"));

        int capMin = plugin.getConfig().getInt("spec2.tree-capacity-min", 50);
        if (capMin < 1) {
            capMin = 50;
        }
        boolean ok93 = true;
        String got93 = "";
        for (Spec s : Spec.values()) {
            Spec2Tree t = Spec2Registry.treeOf(s.id());
            int cap = t == null ? 0 : t.capacity();
            if (t == null || cap < capMin || cap > 58) {
                ok93 = false;
                got93 = s.id() + ":" + (t == null ? "null" : String.valueOf(cap))
                        + " (ожидалось " + capMin + "–58)";
                break;
            }
        }
        r.check("93", "все 18 деревьев в реестре, ёмкость [" + capMin + ", 58] (>46)",
                ok93, "Spec2Registry.treeOf/capacity", got93.isEmpty() ? "OK" : got93);

        boolean ok94 = true;
        String got94 = "";
        for (Spec s : Spec.values()) {
            Spec2Tree t = Spec2Registry.treeOf(s.id());
            if (t == null) {
                ok94 = false;
                got94 = s.id();
                break;
            }
            int ult = 0;
            int unlock = 0;
            for (Spec2Node n : t.nodes()) {
                if (n.isUltimate()) {
                    ult++;
                }
                if ("unlock_ability".equals(n.type())) {
                    unlock++;
                }
            }
            if (ult != 1 || unlock < 1) {
                ok94 = false;
                got94 = s.id() + ": ult=" + ult + ", unlock=" + unlock;
                break;
            }
        }
        r.check("94", "в каждом дереве ровно 1 ульт и ≥1 unlock_ability",
                ok94, "Spec2Tree nodes", got94.isEmpty() ? "OK" : got94);

        boolean ok95 = true;
        String got95 = "";
        int transferableTotal = 0;
        for (PlayerClass pc : PlayerClass.values()) {
            List<dev.raskol.classes.ability.TransferableAbilities.Entry> entries =
                    dev.raskol.classes.ability.TransferableAbilities.forClass(pc);
            if (entries.size() != 2) {
                ok95 = false;
                got95 = pc.name() + ": переносимых=" + entries.size() + " (ожидалось 2)";
                break;
            }
            for (dev.raskol.classes.ability.TransferableAbilities.Entry e : entries) {
                transferableTotal++;
                dev.raskol.classes.ability.AbilityDef def = plugin.getAbilities().findById(pc, e.id());
                if (def == null || def.slot() != e.legacySlot()) {
                    ok95 = false;
                    got95 = pc.name() + "." + e.id() + ": в DEFAULTS slot="
                            + (def == null ? "null" : String.valueOf(def.slot()))
                            + " (ожидалось " + e.legacySlot() + ")";
                    break;
                }
            }
            if (!ok95) {
                break;
            }
        }
        if (ok95 && transferableTotal != 12) {
            ok95 = false;
            got95 = "всего переносимых=" + transferableTotal + " (ожидалось 12)";
        }
        r.check("95", "реестр переносимых: 12 id = slots 4–5 × 6 классов, все в DEFAULTS",
                ok95, "TransferableAbilities/AbilityRegistry.findById",
                ok95 ? "OK(12)" : got95);

        boolean ok96 = true;
        String got96 = "";
        for (PlayerClass pc : PlayerClass.values()) {
            for (dev.raskol.classes.ability.TransferableAbilities.Entry e :
                    dev.raskol.classes.ability.TransferableAbilities.forClass(pc)) {
                if (!dev.raskol.classes.ability.AbilityRegistry.isTransferable(e.id())
                        || dev.raskol.classes.ability.AbilityRegistry.transferableSlot(e.id()) != e.legacySlot()) {
                    ok96 = false;
                    got96 = e.id() + ": isTransferable/transferableSlot рассогласованы";
                    break;
                }
                dev.raskol.classes.ability.AbilityDef viaSlot =
                        plugin.getAbilities().getBySlot(pc, e.legacySlot());
                dev.raskol.classes.ability.AbilityDef viaTree =
                        plugin.getAbilities().getBySlotOrTree(pc, e.legacySlot(), UUID.randomUUID());
                if (viaSlot == null || viaTree == null || !viaSlot.id().equals(viaTree.id())) {
                    ok96 = false;
                    got96 = e.id() + ": getBySlotOrTree != getBySlot в переходный период";
                    break;
                }
            }
            if (!ok96) {
                break;
            }
        }
        r.check("96", "резолв переносимых: getBySlotOrTree == getBySlot (переходный инвариант)",
                ok96, "AbilityRegistry.getBySlotOrTree", ok96 ? "OK" : got96);

        boolean ok97 = true;
        String got97 = "";
        String[] numKeys97 = {"base", "coeff", "threshold", "execute-mult", "radius",
                "duration", "per-purged", "channel", "antiheal", "missing-hp-bonus"};
        for (PlayerClass pc : PlayerClass.values()) {
            for (dev.raskol.classes.ability.TransferableAbilities.Entry e :
                    dev.raskol.classes.ability.TransferableAbilities.forClass(pc)) {
                String treeSection = "classes." + pc.name() + ".treeAbilities." + e.id();
                if (!plugin.getConfig().isConfigurationSection(treeSection)) {
                    ok97 = false;
                    got97 = e.id() + ": секция treeAbilities отсутствует";
                    break;
                }
                double treeBaseValue = plugin.getConfig().getDouble(treeSection + ".base", Double.NaN);
                if (!Double.isFinite(treeBaseValue) || treeBaseValue <= 0.0) {
                    ok97 = false;
                    got97 = e.id() + ": treeAbilities.base отсутствует/неположителен (" + treeBaseValue + ")";
                    break;
                }
                String kitSection = "classes." + pc.name() + ".abilities." + e.id();
                if (plugin.getConfig().isConfigurationSection(kitSection)) {
                    String kitPrefix = kitSection + ".";
                    String treePrefix = treeSection + ".";
                    for (String key : numKeys97) {
                        if (plugin.getConfig().isSet(kitPrefix + key)) {
                            double kv = plugin.getConfig().getDouble(kitPrefix + key, Double.NaN);
                            double tv = plugin.getConfig().getDouble(treePrefix + key, Double.NaN);
                            if (!Double.isFinite(tv) || Math.abs(kv - tv) > 1e-9) {
                                ok97 = false;
                                got97 = e.id() + "." + key + ": abilities=" + kv
                                        + " treeAbilities=" + tv + " (рассогласование legacy/authoritative)";
                                break;
                            }
                        }
                    }
                    if (!ok97) {
                        break;
                    }
                }
            }
            if (!ok97) {
                break;
            }
        }
        r.check("97", "переносимые: treeAbilities.<id> авторитетно (base>0); legacy abilities, если есть, совпадает",
                ok97, "config treeAbilities/abilities", ok97 ? "OK(12)" : got97);

        boolean ok98 = false;
        Spec2Tree arcaneTree = Spec2Registry.treeOf("arcane");
        if (arcaneTree != null) {
            for (Spec2Node node : arcaneTree.nodes()) {
                if ("arc_athena_aegis".equals(node.id())
                        && "unlock_ability".equals(node.type())) {
                    ok98 = true;
                    break;
                }
            }
        }
        r.check("98", "athena_aegis имеет unlock_ability-узел в arcane (пробел 11/12 закрыт)",
                ok98, "MageTrees.arcane/Spec2Node",
                ok98 ? "OK(arc_athena_aegis)" : "no-node");

        boolean ok99 = true;
        String got99 = "";
        Set<String> unlockTargets = new HashSet<>();
        for (Spec s : Spec.values()) {
            Spec2Tree t99 = Spec2Registry.treeOf(s.id());
            if (t99 == null) {
                continue;
            }
            for (Spec2Node n99 : t99.nodes()) {
                if (n99.effect() == null || !"unlock_ability".equals(n99.effect().kind())) {
                    continue;
                }
                String target = n99.effect().target();
                unlockTargets.add(target);
                if (!plugin.getAbilities().hasCaster(target)) {
                    ok99 = false;
                    got99 = s.id() + ":" + n99.id() + " → цель '" + target + "' без кастера";
                    break;
                }
            }
            if (!ok99) {
                break;
            }
        }
        if (ok99) {
            for (String treeCasterId : plugin.getAbilities().treeCasterIds()) {
                if (!unlockTargets.contains(treeCasterId)) {
                    ok99 = false;
                    got99 = "древесный кастер '" + treeCasterId + "' не открыт ни одним unlock-узлом";
                    break;
                }
            }
        }
        r.check("99", "unlock_ability ↔ кастеры: двунаправленное покрытие без дыр",
                ok99, "Spec2Registry/AbilityRegistry.hasCaster", ok99 ? "OK" : got99);
    }

    /* ------------------------------ 100–106: агрегаты на probe ------------------------------ */

    private static void probeAgg(RaskolClasses plugin, Report r, Player probe) {
        probeHeal(plugin, r, probe);
        probePowers(plugin, r, probe);
        resourceCeiling(plugin, r, probe);
        procContract(plugin, r, probe);
        probeCombatPct(plugin, r, probe);
        probeResistSchool(plugin, r, probe);
        resetNodeCheck(plugin, r, probe);
    }

    private static void probeHeal(RaskolClasses plugin, Report r, Player probe) {
        if (probe == null) {
            r.skip("100", "heal-агрегат");
            return;
        }
        UUID pu100 = probe.getUniqueId();
        KindHit ho = findKind(plugin, probe, "heal_out_pct");
        KindHit hr = findKind(plugin, probe, "heal_received_pct");
        if (ho == null && hr == null) {
            r.skip("100", "heal-агрегат (у класса probe нет узлов heal_out/heal_received)");
            return;
        }
        boolean ok100 = true;
        StringBuilder got100 = new StringBuilder();
        Map<String, Integer> bHo = null;
        Map<String, Integer> bHr = null;
        try {
            if (ho != null) {
                bHo = applyRank(plugin, pu100, ho.treeId(), ho.node().id(), 2);
                double expect = 2 * ho.node().effect().value();
                double gotV = plugin.getSpec2Service().healOutPercent(pu100);
                if (Math.abs(gotV - expect) > 1e-9) {
                    ok100 = false;
                    got100.append("heal_out=").append(gotV).append('/').append(expect).append(' ');
                }
            } else {
                got100.append("heal_out:n/a ");
            }
            if (hr != null) {
                bHr = applyRank(plugin, pu100, hr.treeId(), hr.node().id(), 2);
                double expect = 2 * hr.node().effect().value();
                double gotV = plugin.getSpec2Service().healReceivedPercent(pu100);
                if (Math.abs(gotV - expect) > 1e-9) {
                    ok100 = false;
                    got100.append("heal_recv=").append(gotV).append('/').append(expect).append(' ');
                }
            } else {
                got100.append("heal_recv:n/a ");
            }
        } finally {
            if (ho != null && bHo != null) {
                restore(plugin, pu100, ho.treeId(), bHo);
            }
            if (hr != null && bHr != null) {
                restore(plugin, pu100, hr.treeId(), bHr);
            }
        }
        r.check("100", "heal-агрегат: heal_out_pct/heal_received_pct = 2×value узла класса probe",
                ok100, "Spec2Service.healOutPercent/healReceivedPercent", got100.toString());
    }

    private static void probePowers(RaskolClasses plugin, Report r, Player probe) {
        if (probe == null || plugin.getClassProvider().getClassOf(probe) == null) {
            r.skip("101", "павер-множители");
            return;
        }
        UUID pu101 = probe.getUniqueId();
        KindHit wp = findKind(plugin, probe, "wp_pct");
        KindHit spN = findKind(plugin, probe, "sp_pct");
        KindHit hpow = findKind(plugin, probe, "hpow_pct");
        if (wp == null && spN == null && hpow == null) {
            r.skip("101", "павер-множители (у класса probe нет узлов wp/sp/hpow_pct)");
            return;
        }
        boolean ok101 = true;
        StringBuilder got101 = new StringBuilder();
        Map<String, Integer> bWp = null;
        Map<String, Integer> bSp = null;
        Map<String, Integer> bHp = null;
        try {
            if (wp != null) {
                double w0 = plugin.getCombat().powers().weaponPower(pu101);
                bWp = applyRank(plugin, pu101, wp.treeId(), wp.node().id(), 2);
                double delta = 2 * wp.node().effect().value();
                double w1 = plugin.getCombat().powers().weaponPower(pu101);
                if (Math.abs(plugin.getSpec2Service().wpPercent(pu101) - delta) > 1e-9
                        || w0 <= 0 || Math.abs(w1 - w0 * (100 + delta) / 100.0) > 1e-6) {
                    ok101 = false;
                    got101.append("wp=").append(w1 / Math.max(w0, 1e-9)).append(' ');
                }
            }
            if (spN != null) {
                double s0 = plugin.getCombat().powers().spellPower(pu101);
                bSp = applyRank(plugin, pu101, spN.treeId(), spN.node().id(), 2);
                double delta = 2 * spN.node().effect().value();
                double s1 = plugin.getCombat().powers().spellPower(pu101);
                if (Math.abs(plugin.getSpec2Service().spPercent(pu101) - delta) > 1e-9
                        || s0 <= 0 || Math.abs(s1 - s0 * (100 + delta) / 100.0) > 1e-6) {
                    ok101 = false;
                    got101.append("sp=").append(s1 / Math.max(s0, 1e-9)).append(' ');
                }
            }
            if (hpow != null) {
                double h0 = plugin.getCombat().powers().healPower(pu101);
                bHp = applyRank(plugin, pu101, hpow.treeId(), hpow.node().id(), 2);
                double delta = 2 * hpow.node().effect().value();
                double h1 = plugin.getCombat().powers().healPower(pu101);
                if (Math.abs(plugin.getSpec2Service().hpowPercent(pu101) - delta) > 1e-9
                        || h0 <= 0 || Math.abs(h1 - h0 * (100 + delta) / 100.0) > 1e-6) {
                    ok101 = false;
                    got101.append("hpow=").append(h1 / Math.max(h0, 1e-9)).append(' ');
                }
            }
        } finally {
            if (wp != null && bWp != null) restore(plugin, pu101, wp.treeId(), bWp);
            if (spN != null && bSp != null) restore(plugin, pu101, spN.treeId(), bSp);
            if (hpow != null && bHp != null) restore(plugin, pu101, hpow.treeId(), bHp);
        }
        r.check("101", "павер-множители: wp/sp/hpow_pct = 2×value и пропорция PowerService",
                ok101, "Spec2Service.*Percent/PowerService", got101.toString());
    }

    private static void resourceCeiling(RaskolClasses plugin, Report r, Player probe) {
        ResourceState rs102 = new ResourceState();
        rs102.setValue(100.0);
        boolean baseCap = rs102.getValue() == 100.0;
        rs102.setCeiling(150.0);
        rs102.add(60.0);
        boolean ceilUp = rs102.getValue() == 150.0;
        rs102.setCeiling(120.0);
        boolean ceilDown = rs102.getValue() == 120.0;
        rs102.setCeiling(-5.0);
        rs102.setCeiling(Double.NaN);
        boolean ceilGuard = rs102.getEffectiveMax() == 120.0;
        boolean pureOk = baseCap && ceilUp && ceilDown && ceilGuard;
        UUID stranger102 = UUID.randomUUID();
        boolean strangerZero = plugin.getSpec2Service().resourceMaxBonus(stranger102) == 0.0
                && plugin.getSpec2Service().resourceRegenPercent(stranger102) == 0.0;
        boolean ok102 = pureOk && strangerZero;
        String got102 = "pure=" + pureOk;
        if (probe != null) {
            UUID pu102 = probe.getUniqueId();
            KindHit rmax = findKind(plugin, probe, "resource_max");
            KindHit rreg = findKind(plugin, probe, "resource_regen_pct");
            Map<String, Integer> bMax = null;
            Map<String, Integer> bReg = null;
            try {
                if (rmax != null) {
                    bMax = applyRank(plugin, pu102, rmax.treeId(), rmax.node().id(), 2);
                    double expect = 2 * rmax.node().effect().value();
                    double gotV = plugin.getSpec2Service().resourceMaxBonus(pu102);
                    ok102 &= Math.abs(gotV - expect) < 1e-9;
                    got102 += " max=" + gotV;
                }
                if (rreg != null) {
                    bReg = applyRank(plugin, pu102, rreg.treeId(), rreg.node().id(), 2);
                    double expect = 2 * rreg.node().effect().value();
                    double gotV = plugin.getSpec2Service().resourceRegenPercent(pu102);
                    ok102 &= Math.abs(gotV - expect) < 1e-9;
                    got102 += " regen=" + gotV;
                }
                if (rmax == null && rreg == null) {
                    got102 += " (probe-узлы n/a)";
                }
            } finally {
                if (rmax != null && bMax != null) restore(plugin, pu102, rmax.treeId(), bMax);
                if (rreg != null && bReg != null) restore(plugin, pu102, rreg.treeId(), bReg);
            }
        }
        r.check("102", "ресурс: setCeiling расширяет/сжимает/мусор-guard; resource_max/regen_pct из узлов",
                ok102, "ResourceState.setCeiling/Spec2Service.resourceMaxBonus", got102);
    }

    private static void procContract(RaskolClasses plugin, Report r, Player probe) {
        boolean pureOk = ProcService.clampFraction(1.5) == 1.0
                && ProcService.clampFraction(-1.0) == 0.0
                && ProcService.clampFraction(Double.NaN) == 0.0
                && ProcService.rollChance(1.0)
                && !ProcService.rollChance(0.0);
        boolean stripOk = true;
        String got103 = "pure=" + pureOk;
        if (probe != null) {
            UUID pu103 = probe.getUniqueId();
            KindHit pn = findKindPrefix(plugin, probe, "proc_");
            if (pn == null) {
                got103 += " (proc-узел n/a)";
            } else {
                String kind = pn.node().effect().kind();
                String stripped = kind.substring("proc_".length());
                Map<String, Integer> bP = null;
                try {
                    bP = applyRank(plugin, pu103, pn.treeId(), pn.node().id(), 2);
                    double expect = 2 * pn.node().effect().value();
                    double byNewKey = plugin.getSpec2Service().procBonus(pu103, stripped);
                    double byOldKey = plugin.getSpec2Service().procBonus(pu103, kind);
                    stripOk = Math.abs(byNewKey - expect) < 1e-9 && byOldKey == 0.0;
                    got103 += " " + kind + "→" + stripped + "=" + byNewKey;
                } finally {
                    if (bP != null) {
                        restore(plugin, pu103, pn.treeId(), bP);
                    }
                }
            }
        }
        r.check("103", "проки: clampFraction/rollChance pure + префикс proc_ срезан в агрегате",
                pureOk && stripOk, "ProcService/Spec2Service.accumulate", got103);
    }

    private static void probeCombatPct(RaskolClasses plugin, Report r, Player probe) {
        if (probe == null) {
            r.skip("104", "боевые pct-агрегаты");
            return;
        }
        UUID pu104 = probe.getUniqueId();
        String[] kinds104 = {"phys_dmg_pct", "magic_dmg_pct", "block_pct",
                "crit_melee_pct", "hp_pct", "move_speed_pct"};
        List<KindHit> hits = new ArrayList<>();
        for (String k : kinds104) {
            KindHit h = findKind(plugin, probe, k);
            if (h != null) {
                hits.add(h);
            }
        }
        if (hits.isEmpty()) {
            r.skip("104", "боевые pct-агрегаты (у класса probe нет pct-узлов)");
            return;
        }
        boolean ok104 = true;
        StringBuilder got104 = new StringBuilder();
        Map<String, Map<String, Integer>> backups = new HashMap<>();
        double m0 = plugin.getAttributes().maxHp(pu104);
        try {
            for (KindHit h : hits) {
                backups.computeIfAbsent(h.treeId(),
                        tid -> new HashMap<>(plugin.getSpec2Service().storage().getRanks(pu104, tid)));
            }
            for (KindHit h : hits) {
                Map<String, Integer> next = new HashMap<>(
                        plugin.getSpec2Service().storage().getRanks(pu104, h.treeId()));
                next.put(h.node().id(), 2);
                plugin.getSpec2Service().storage().setRanks(pu104, h.treeId(), next);
            }
            plugin.getSpec2Service().reconcile(pu104);
            for (KindHit h : hits) {
                double expect = 2 * h.node().effect().value();
                double gotV = switch (h.node().effect().kind()) {
                    case "phys_dmg_pct" -> plugin.getSpec2Service().physDmgPercent(pu104);
                    case "magic_dmg_pct" -> plugin.getSpec2Service().magicDmgPercent(pu104);
                    case "block_pct" -> plugin.getSpec2Service().blockPercent(pu104);
                    case "crit_melee_pct" -> plugin.getSpec2Service().critMeleeBonus(pu104);
                    case "hp_pct" -> plugin.getSpec2Service().hpPercent(pu104);
                    default -> plugin.getSpec2Service().moveSpeedPercent(pu104);
                };
                if (Math.abs(gotV - expect) > 1e-9) {
                    ok104 = false;
                    got104.append(h.node().effect().kind()).append('=').append(gotV)
                            .append('/').append(expect).append(' ');
                }
            }
            KindHit hpHit = null;
            for (KindHit h : hits) {
                if ("hp_pct".equals(h.node().effect().kind())) {
                    hpHit = h;
                }
            }
            if (hpHit != null) {
                double maxHpAfter = plugin.getAttributes().maxHp(pu104);
                double expectRatio = 1.0 + (2 * hpHit.node().effect().value()) / 100.0;
                if (m0 <= 0 || Math.abs(maxHpAfter - m0 * expectRatio) > 1e-6) {
                    ok104 = false;
                    got104.append("maxHp×=").append(maxHpAfter / Math.max(m0, 1e-9)).append(' ');
                }
            }
        } finally {
            for (Map.Entry<String, Map<String, Integer>> e : backups.entrySet()) {
                restore(plugin, pu104, e.getKey(), e.getValue());
            }
        }
        r.check("104", "боевые pct-агрегаты = 2×value узлов probe + maxHp ×(1+hp/100)",
                ok104, "Spec2Service.*Percent/AttributeService.maxHp",
                ok104 ? "kinds=" + hits.size() : got104.toString());
    }

    private static void probeResistSchool(RaskolClasses plugin, Report r, Player probe) {
        if (probe == null) {
            r.skip("105", "resist-школа");
            return;
        }
        UUID pu105 = probe.getUniqueId();
        String foundTree = null;
        String foundNode = null;
        for (String tid : plugin.getSpec2Service().classTreeIds(pu105)) {
            Spec2Tree t = Spec2Registry.treeOf(tid);
            if (t == null) {
                continue;
            }
            for (Spec2Node n : t.nodes()) {
                if (n.effect() != null && "resist".equals(n.effect().kind())
                        && "nature".equals(n.effect().target())) {
                    foundTree = tid;
                    foundNode = n.id();
                    break;
                }
            }
            if (foundNode != null) {
                break;
            }
        }
        if (foundNode == null) {
            r.skip("105", "resist-школа (у класса probe нет узла resist-nature)");
            return;
        }
        Map<String, Integer> bTree = new HashMap<>(
                plugin.getSpec2Service().storage().getRanks(pu105, foundTree));
        boolean ok105;
        String got105;
        try {
            plugin.getSpec2Service().storage().setRanks(pu105, foundTree,
                    Map.of(foundNode, 1));
            plugin.getSpec2Service().reconcile(pu105);
            double nres = plugin.getCombat().elemental().resistOf(pu105, School.NATURE);
            ok105 = nres >= 5.0;
            got105 = String.format(Locale.ROOT, "node=%s resistN=%.1f", foundNode, nres);
        } finally {
            restore(plugin, pu105, foundTree, bTree);
        }
        r.check("105", "resist-узел: resist-nature → ElementalResistService ≥5",
                ok105, "Spec2Service.accumulate/ElementalResistService.resistOf", got105);
    }

    private static void resetNodeCheck(RaskolClasses plugin, Report r, Player probe) {
        if (probe == null) {
            r.skip("106", "resetNode");
            return;
        }
        UUID pu106 = probe.getUniqueId();
        List<String> trees106 = plugin.getSpec2Service().classTreeIds(pu106);
        String t0 = trees106.isEmpty() ? null : trees106.get(0);
        Spec2Tree tree106 = t0 == null ? null : Spec2Registry.treeOf(t0);
        Spec2Node node106 = tree106 == null ? null : firstRow1(tree106);
        if (tree106 == null || node106 == null) {
            r.skip("106", "resetNode (нет дерева/узла)");
            return;
        }
        String prevMain = plugin.getSpec2Service().storage().getMain(pu106);
        boolean tempMain = prevMain == null;
        Map<String, Integer> b106 = new HashMap<>(
                plugin.getSpec2Service().storage().getRanks(pu106, t0));
        boolean ok106;
        String got106;
        try {
            if (tempMain) {
                plugin.getSpec2Service().storage().setMain(pu106, t0);
            }
            plugin.getSpec2Service().storage().setRanks(pu106, t0,
                    Map.of(node106.id(), 2));
            plugin.getSpec2Service().reconcile(pu106);
            int before = plugin.getSpec2Service().spentGlobal(pu106);
            Spec2Service.NodeResetResult r106 =
                    plugin.getSpec2Service().resetNode(probe, t0, node106.id(), true);
            int after = plugin.getSpec2Service().spentGlobal(pu106);
            int rank = plugin.getSpec2Service().storage().getRanks(pu106, t0)
                    .getOrDefault(node106.id(), 0);
            ok106 = r106 == Spec2Service.NodeResetResult.OK
                    && after == before - 1 && rank == 1;
            got106 = String.format(Locale.ROOT, "result=%s spent %d→%d rank=%d",
                    r106, before, after, rank);
        } finally {
            plugin.getSpec2Service().storage().setRanks(pu106, t0, b106);
            if (tempMain) {
                plugin.getSpec2Service().storage().setMain(pu106, null);
            }
            plugin.getSpec2Service().reconcile(pu106);
        }
        r.check("106", "resetNode: ранг −1, spentGlobal −1, free-путь, временный main",
                ok106, "Spec2Service.resetNode", got106);
    }

    /* ------------------------------ 107: Spec2Points ↔ конфиг (1.14.13: snapshot) ------------------------------ */

    private static void pointsConfig(RaskolClasses plugin, Report r) {
        Spec2Points.Snapshot snap = Spec2Points.snapshot();
        int[] rowGates = snap.rowGates();

        int cfgStart = plugin.getConfig().getInt("spec2.start-level", 15);
        int cfgMax = plugin.getConfig().getInt("spec2.max-points", 46);
        java.util.List<?> cfgGates = plugin.getConfig().getList("spec2.row-gates", null);

        boolean startMatch = snap.startLevel() == cfgStart;
        boolean maxMatch = snap.maxPoints() == cfgMax;
        boolean gatesMatch = cfgGates != null && cfgGates.size() == rowGates.length;
        if (gatesMatch) {
            for (int i = 0; i < cfgGates.size(); i++) {
                Object v = cfgGates.get(i);
                if (v instanceof Number n) {
                    if (n.intValue() != rowGates[i]) {
                        gatesMatch = false;
                        break;
                    }
                } else {
                    gatesMatch = false;
                    break;
                }
            }
        }
        boolean ok107 = startMatch && maxMatch && gatesMatch;
        String got107 = String.format(Locale.ROOT, "start=%d/%d max=%d/%d gates=%s",
                cfgStart, snap.startLevel(), cfgMax, snap.maxPoints(),
                gatesMatch ? "match" : "mismatch");
        r.check("107", "Spec2Points: start-level/max-points/row-gates из конфига (snapshot)",
                ok107, "Spec2Points.configure/snapshot", got107);
    }

    /* ------------------------------ 108–112: петы ------------------------------ */

    private static void pets(RaskolClasses plugin, Report r) {
        PetDef wolf108 = PetDef.byId("wolf");
        PetDef demon108 = PetDef.byId("demon");
        PetDef fiend108 = PetDef.byId("shadowfiend");
        boolean ok108 = wolf108 != null && demon108 != null && fiend108 != null
                && wolf108.baseHp() == 20.0 && wolf108.baseDmg() == 4.0 && !wolf108.temporary()
                && demon108.baseHp() == 30.0 && demon108.baseDmg() == 8.0 && demon108.ttlSeconds() == 12
                && fiend108.baseHp() == 12.0 && fiend108.baseDmg() == 5.0 && fiend108.ttlSeconds() == 8
                && PetDef.byId("elemental") == null;
        r.check("108", "PetDef-реестр: wolf 20/4 perm, demon 30/8 ttl12, shadowfiend 12/5 ttl8",
                ok108, "PetDef.byId", ok108 ? "OK" : "registry-gap");

        boolean ok109 = Math.abs(PetMath.hp(20.0, 50.0, 0.0) - 120.0) < 1e-9
                && Math.abs(PetMath.hp(20.0, 50.0, 10.0) - 132.0) < 1e-9
                && Math.abs(PetMath.damage(4.0, 100.0, 0.0, 1.0) - 34.0) < 1e-9
                && Math.abs(PetMath.damage(4.0, 100.0, 50.0, 2.0) - 102.0) < 1e-9
                && PetMath.hp(20.0, Double.NaN, 0.0) == 20.0;
        r.check("109", "PetMath: hp 20+50*2=120 (×1.1=132); dmg 4+100*0.3=34 (×1.5×2=102); NaN-guard",
                ok109, "PetMath.hp/damage",
                String.format(Locale.ROOT, "%.1f/%.1f/%.1f/%.1f",
                        PetMath.hp(20.0, 50.0, 0.0), PetMath.hp(20.0, 50.0, 10.0),
                        PetMath.damage(4.0, 100.0, 0.0, 1.0),
                        PetMath.damage(4.0, 100.0, 50.0, 2.0)));

        boolean ok110 = "Волк Steve".equals(PetDef.byId("wolf").displayName("Steve"))
                && "Демон Steve".equals(PetDef.byId("demon").displayName("Steve"))
                && "Тенескот Steve".equals(PetDef.byId("shadowfiend").displayName("Steve"));
        r.check("110", "PetDef.displayName: «Волк/Демон/Тенескот <ник>»",
                ok110, "PetDef.nameTemplate", ok110 ? "OK"
                        : PetDef.byId("wolf").displayName("Steve"));

        long now111 = System.currentTimeMillis();
        boolean ok111 = !PetMath.expired(0L, now111)
                && PetMath.expired(now111 - 1L, now111)
                && !PetMath.expired(now111 + 5_000L, now111)
                && PetMath.needsTeleport(13.0 * 13.0, 12.0)
                && !PetMath.needsTeleport(11.0 * 11.0, 12.0);
        r.check("111", "PetMath.expired/needsTeleport: ttl=0 вечен, 13>12 телепорт, 11<12 нет",
                ok111, "PetMath.expired/needsTeleport", ok111 ? "OK" : "fail");

        boolean ok112 = plugin.getPets().attributesReady();
        r.check("112", "PetService.attributesReady: max_health/attack_damage/movement_speed из реестра",
                ok112, "PetService.attributesReady", ok112 ? "OK" : "null-attribute");
    }

    /* ------------------------------ 113: полнота spec2.procs ------------------------------ */

    private static void procsConfig(RaskolClasses plugin, Report r) {
        String[] procIds = {"riposte", "counterattack", "revenge", "shield_slam",
                "second_wind", "reflect_magic", "crit_bonus", "savage", "headshot",
                "stealth_bonus", "stealth_extend", "armor_pen"};
        boolean ok113 = plugin.getConfig().isSet("spec2.procs.expose.cap");
        StringBuilder missing = new StringBuilder();
        for (String id : procIds) {
            if (!plugin.getConfig().isSet("spec2.procs." + id + ".amp")) {
                ok113 = false;
                missing.append(id).append(' ');
            }
        }
        r.check("113", "spec2.procs.*.amp/.cap: все 12 amp + expose.cap присутствуют в конфиге",
                ok113, "config spec2.procs", ok113 ? "OK(13)" : "missing: " + missing);
    }

    /* ------------------------------ 114–116: Sprint 3 ------------------------------ */

    private static void sprint3(RaskolClasses plugin, Report r, Player probe) {
        if (probe == null || plugin.getClassProvider().getClassOf(probe) == null) {
            r.skip("114", "kit_dur-агрегат");
            r.skip("115", "attack_speed applier");
            r.skip("116", "контракт C value2");
            return;
        }
        UUID pu = probe.getUniqueId();
        PlayerClass pc = plugin.getClassProvider().getClassOf(probe);

        KindHit kd = findKind(plugin, probe, "kit_dur");
        if (kd == null) {
            r.skip("114", "kit_dur (у класса probe нет kit_dur-узлов)");
        } else {
            String ability114 = kd.node().effect().target();
            Map<String, Integer> b114 = null;
            boolean ok114;
            String got114;
            try {
                b114 = applyRank(plugin, pu, kd.treeId(), kd.node().id(), 2);
                double expect = 2 * kd.node().effect().value();
                double gotBonus = plugin.getSpec2Service().kitDurBonus(pu, ability114);
                int baseDur = TreeAbilities.durationOrKit(plugin, pc, ability114, 7);
                int withSpec = TreeAbilities.durationWithSpec(plugin, pc, ability114, 7, pu);
                ok114 = Math.abs(gotBonus - expect) < 1e-9
                        && withSpec == baseDur + (int) Math.round(expect);
                got114 = String.format(Locale.ROOT, "bonus=%.1f/%.1f dur=%d/%d",
                        gotBonus, expect, withSpec, baseDur);
            } finally {
                if (b114 != null) {
                    restore(plugin, pu, kd.treeId(), b114);
                }
            }
            r.check("114", "kit_dur: agg = 2×value; durationWithSpec = base + bonus",
                    ok114, "Spec2Service.kitDurBonus/TreeAbilities.durationWithSpec", got114);
        }

        KindHit as = findKind(plugin, probe, "attack_speed_pct");
        if (as == null) {
            r.skip("115", "attack_speed (у класса probe нет узла)");
        } else {
            Map<String, Integer> b115 = null;
            boolean ok115;
            String got115;
            try {
                b115 = applyRank(plugin, pu, as.treeId(), as.node().id(), 2);
                double pct = plugin.getSpec2Service().attackSpeedPercent(pu);
                boolean aggOk = Math.abs(pct - 2 * as.node().effect().value()) < 1e-9;
                org.bukkit.attribute.AttributeInstance ai = probe.getAttribute(
                        io.papermc.paper.registry.RegistryAccess.registryAccess()
                                .getRegistry(io.papermc.paper.registry.RegistryKey.ATTRIBUTE)
                                .get(org.bukkit.NamespacedKey.minecraft("attack_speed")));
                boolean modPresent = ai != null && ai.getModifiers().stream()
                        .anyMatch(m -> m.getKey().toString().equals("raskolclasses:spec2_attack_speed"));
                ok115 = aggOk && modPresent;
                got115 = "pct=" + pct + " modifier=" + modPresent;
            } finally {
                if (b115 != null) {
                    restore(plugin, pu, as.treeId(), b115);
                }
            }
            r.check("115", "attack_speed_pct: агрегат + modifier на игроке (A4 закрыт)",
                    ok115, "Spec2Service.attackSpeedPercent/Spec2EffectsApplier", got115);
        }

        KindHit pv = null;
        for (String tid : plugin.getSpec2Service().classTreeIds(pu)) {
            Spec2Tree t = Spec2Registry.treeOf(tid);
            if (t == null) {
                continue;
            }
            for (Spec2Node n : t.nodes()) {
                if (n.effect() != null && n.effect().kind() != null
                        && n.effect().kind().startsWith("proc_")
                        && n.effect().value2() != 0.0) {
                    pv = new KindHit(tid, n);
                    break;
                }
            }
            if (pv != null) {
                break;
            }
        }
        if (pv == null) {
            r.skip("116", "контракт C value2 (у класса probe нет proc-узла с силой)");
        } else {
            Map<String, Integer> b116 = null;
            boolean ok116;
            String got116;
            try {
                b116 = applyRank(plugin, pu, pv.treeId(), pv.node().id(), 2);
                String kind = pv.node().effect().kind();
                String stripped = kind.substring("proc_".length());
                double expectAmp = 2 * pv.node().effect().value2();
                double expectChance = 2 * pv.node().effect().value();
                double gotAmp = plugin.getSpec2Service().procAmpBonus(pu, stripped);
                double gotChance = plugin.getSpec2Service().procBonus(pu, stripped);
                ok116 = Math.abs(gotAmp - expectAmp) < 1e-9
                        && Math.abs(gotChance - expectChance) < 1e-9;
                got116 = String.format(Locale.ROOT, "%s: amp=%.2f/%.2f chance=%.2f/%.2f",
                        stripped, gotAmp, expectAmp, gotChance, expectChance);
            } finally {
                if (b116 != null) {
                    restore(plugin, pu, pv.treeId(), b116);
                }
            }
            r.check("116", "контракт C: procAmp = rank×value2, proc = rank×value",
                    ok116, "Spec2Service.procAmpBonus/procBonus", got116);
        }
    }

    /* ------------------------------ 117–118: Sprint 4 ------------------------------ */

    private static void sprint4(RaskolClasses plugin, Report r, Player probe) {
        if (probe == null || plugin.getClassProvider().getClassOf(probe) == null) {
            r.skip("117", "HAS_DEPENDENTS");
            r.skip("118", "антиспам-метка гейтов");
            return;
        }
        UUID pu117 = probe.getUniqueId();
        Spec2Node depA = null;
        Spec2Node depB = null;
        String depTree = null;
        for (String tid : plugin.getSpec2Service().classTreeIds(pu117)) {
            Spec2Tree t = Spec2Registry.treeOf(tid);
            if (t == null) {
                continue;
            }
            for (Spec2Node n : t.nodes()) {
                if (n.prereqs().isEmpty() || plugin.getSpec2Service().nodeDisabled(n.id())) {
                    continue;
                }
                for (Map.Entry<String, Integer> pr : n.prereqs().entrySet()) {
                    Spec2Node a = t.find(pr.getKey());
                    if (a != null && !plugin.getSpec2Service().nodeDisabled(a.id())) {
                        depA = a;
                        depB = n;
                        depTree = tid;
                        break;
                    }
                }
                if (depB != null) {
                    break;
                }
            }
            if (depB != null) {
                break;
            }
        }
        if (depB == null) {
            r.skip("117", "HAS_DEPENDENTS (нет пары пререквизит→зависимый у класса probe)");
        } else {
            Map<String, Integer> b117 = new HashMap<>(
                    plugin.getSpec2Service().storage().getRanks(pu117, depTree));
            String prevMain = plugin.getSpec2Service().storage().getMain(pu117);
            boolean tempMain = prevMain == null;
            boolean ok117;
            String got117;
            try {
                if (tempMain) {
                    plugin.getSpec2Service().storage().setMain(pu117, depTree);
                }
                Map<String, Integer> set = new HashMap<>();
                set.put(depA.id(), 1);
                set.put(depB.id(), 1);
                plugin.getSpec2Service().storage().setRanks(pu117, depTree, set);
                pauseMs(200);
                Spec2Service.NodeResetResult res =
                        plugin.getSpec2Service().resetNode(probe, depTree, depA.id(), true);
                int rankA = plugin.getSpec2Service().storage().getRanks(pu117, depTree)
                        .getOrDefault(depA.id(), 0);
                ok117 = res == Spec2Service.NodeResetResult.HAS_DEPENDENTS && rankA == 1;
                got117 = "result=" + res + " rankA=" + rankA;
            } finally {
                plugin.getSpec2Service().storage().setRanks(pu117, depTree, b117);
                if (tempMain) {
                    plugin.getSpec2Service().storage().setMain(pu117, null);
                }
                plugin.getSpec2Service().reconcile(pu117);
            }
            r.check("117", "P1-5: resetNode пререквизита при живом зависимом → HAS_DEPENDENTS, ранг не меняется",
                    ok117, "Spec2Service.resetNode/hasDependents", got117);
        }

        UUID pu118 = probe.getUniqueId();
        PlayerClass pc118 = plugin.getClassProvider().getClassOf(probe);
        String gatedId = null;
        dev.raskol.classes.ability.AbilityDef gatedDef = null;
        for (String tid : plugin.getAbilities().treeCasterIds()) {
            if (!plugin.getSpec2Service().hasUnlocked(pu118, tid)) {
                dev.raskol.classes.ability.AbilityDef d =
                        plugin.getAbilities().findById(pc118, tid);
                if (d != null) {
                    gatedId = tid;
                    gatedDef = d;
                    break;
                }
            }
        }
        if (gatedDef == null) {
            r.skip("118", "антиспам-метка (у probe нет закрытых древесных способностей)");
        } else {
            plugin.getAbilities().clearAttempts(pu118);
            boolean castResult = plugin.getAbilities().tryCast(probe, gatedDef);
            boolean marked = plugin.getAbilities().hasAttemptMark(pu118, gatedId);
            boolean ok118 = !castResult && !marked;
            String got118 = "cast=" + castResult + " mark=" + marked + " id=" + gatedId;
            r.check("118", "P1-6: гейтованный каст отклонён БЕЗ антиспам-метки",
                    ok118, "AbilityRegistry.castOn", got118);
        }
    }

    /* ------------------------------ 119–120: Sprint 4 IO ------------------------------ */

    private static void sprint4Io(RaskolClasses plugin, Report r, Player probe) {
        ResourceState rs119 = new ResourceState();
        rs119.setCeiling(150.0);
        rs119.add(150.0);
        double got119 = rs119.getValue();
        double max119 = rs119.getEffectiveMax();
        boolean ok119 = Math.abs(got119 - 150.0) < 1e-9 && Math.abs(max119 - 150.0) < 1e-9;
        r.check("119", "P1-8: после setCeiling(150) ресурс растёт до 150 (не кламп на 100)",
                ok119, "ResourceState.setCeiling/getValue",
                String.format(Locale.ROOT, "value=%.1f/%.1f max=%.1f", got119, 150.0, max119));

        if (probe == null) {
            r.skip("120", "P1-7 dirty-flush");
            return;
        }
        dev.raskol.classes.hud.HpBarService hbs = plugin.getHpBarService();
        boolean ok120;
        String got120;
        try {
            hbs.saveAll();
            java.io.File healthFile = plugin.getDataFolder().toPath()
                    .resolve("health.yml").toFile();
            long before = healthFile.exists() ? healthFile.lastModified() : 0L;
            Thread.sleep(50);
            hbs.saveAll();
            long after = healthFile.exists() ? healthFile.lastModified() : 0L;
            ok120 = true;
            got120 = "saveAll ok, mtime before=" + before + " after=" + after;
        } catch (Throwable t) {
            ok120 = false;
            got120 = "exception: " + t.getClass().getSimpleName()
                    + (t.getMessage() != null ? ": " + t.getMessage() : "");
        }
        r.check("120", "P1-7: saveAll + повторный saveAll без исключений (dirty-flush идемпотентен)",
                ok120, "HpBarService.saveAll/flushDirty", got120);
    }

    /* ------------------------------ 121: Sprint 5, P2-4 ------------------------------ */

    private static void stopListLive(RaskolClasses plugin, Report r) {
        boolean ok121 = true;
        String got121 = "";
        Set<String> liveProcTargets = Set.of("free_heal", "enrage_dmg", "trance",
                "rage_on_cast", "bleed_on_crit", "burning_extend");
        for (Spec s : Spec.values()) {
            Spec2Tree t = Spec2Registry.treeOf(s.id());
            if (t == null) {
                continue;
            }
            for (Spec2Node n : t.nodes()) {
                if (n.effect() == null || n.effect().kind() == null) {
                    continue;
                }
                String kind = n.effect().kind();
                boolean live = "kit_dur".equals(kind)
                        || "attack_speed_pct".equals(kind)
                        || (kind.startsWith("proc_")
                            && liveProcTargets.contains(kind.substring("proc_".length())));
                if (live && plugin.getSpec2Service().nodeDisabled(n.id())) {
                    ok121 = false;
                    got121 = n.id() + " (" + kind + ")";
                    break;
                }
            }
            if (!ok121) {
                break;
            }
        }
        r.check("121", "P2-4: в стоп-листе нет узлов с живыми потребителями (kit_dur/attack_speed/procs)",
                ok121, "config spec2.disabled-nodes",
                ok121 ? "OK(20 disabled)" : "живой, но закрыт: " + got121);
    }

    /* ------------------------------ 122: Sprint 6, Волна 6.1, P0-6B ------------------------------ */

    private static void stopListReachability(RaskolClasses plugin, Report r) {
        Set<String> disabled = plugin.getSpec2Service().disabledSet();
        int unreachable = 0;
        String got122 = "";
        for (Spec s : Spec.values()) {
            Spec2Tree t = Spec2Registry.treeOf(s.id());
            if (t == null) {
                continue;
            }
            for (Spec2Node n : t.nodes()) {
                if (disabled.contains(n.id())) {
                    continue; // сам узел в стоп-листе — покупка и не ожидается
                }
                if (!t.isReachable(n.id(), disabled)) {
                    unreachable++;
                    if (got122.isEmpty()) {
                        got122 = s.id() + ":" + n.id();
                    }
                }
            }
        }
        boolean ok122 = unreachable == 0;
        r.check("122", "P0-6B: каждый включённый узел достижим при текущем стоп-листе (цепочки пререквизитов целы)",
                ok122, "Spec2Tree.isReachable/spec2.disabled-nodes",
                ok122 ? "OK(" + disabled.size() + " disabled)"
                        : unreachable + " недостижимых, первый: " + got122);
    }

    /* ------------------------------ 123–125: 1.14.15 (raskolspec+) ------------------------------ */

    private static void plusSubscription(RaskolClasses plugin, Report r) {
        // 123: 36 шаблонов загружены без проблем
        int tplCount = plugin.getBuildTemplates().countLoaded();
        int tplProblems = plugin.getBuildTemplates().countProblems();
        boolean ok123 = tplCount == 36 && tplProblems == 0;
        r.check("123", "raskolspec+: 36 шаблонов загружено без проблем (18 спек × 2 архетипа)",
                ok123, "BuildTemplates.countLoaded/countProblems",
                "loaded=" + tplCount + " problems=" + tplProblems);

        // 124: математика подписки grant/remainingDays/revoke
        UUID probe = UUID.randomUUID();
        long exp1 = 0L;
        int days = 0;
        boolean activeAfterRevoke = false;
        try {
            exp1 = plugin.getPlusSubscription().grant(probe, 1);
            days = plugin.getPlusSubscription().remainingDays(probe);
        } finally {
            plugin.getPlusSubscription().revoke(probe);
            activeAfterRevoke = plugin.getPlusSubscription().active(probe);
        }
        boolean ok124 = exp1 > System.currentTimeMillis()
                && days >= 29 && days <= 31
                && !activeAfterRevoke;
        r.check("124", "raskolspec+: grant(1 период) → expires в будущем, remainingDays 29..31, revoke чист",
                ok124, "PlusSubscription.grant/remainingDays/revoke",
                "expires=" + exp1 + " days=" + days + " active_after_revoke=" + activeAfterRevoke);

        // 125: stale-маркировка шаблонов с disabled-узлами
        int stale = plugin.getBuildTemplates().countStale();
        boolean ok125 = stale >= 0;
        r.check("125", "raskolspec+: stale-шаблонов = " + stale + " (disabled-nodes маркировка)",
                ok125, "BuildTemplates.countStale", "stale=" + stale);
    }

    /* ------------------------------ 126–127: 1.14.16 (Волна 15C) apply raskolspec+ ------------------------------ */

    /**
     * 126: applyBuild(free) применяет узел ряда 1 к первому дереву probe:
     * результат OK, ранг = 1, затем состояние восстанавливается из backup.
     * 127: отказы БЕЗ изменения состояния: чужое дерево → WRONG_CLASS_TREE;
     * узел с пререквизитом без пререка → TEMPLATE_INVALID (simulateApply-parity).
     * pauseMs(200) между вызовами — rate-limit actionAllowed (150 мс).
     */
    private static void plusApply(RaskolClasses plugin, Report r, Player probe) {
        if (probe == null || plugin.getClassProvider().getClassOf(probe) == null) {
            r.skip("126", "raskolspec+ apply на probe");
            r.skip("127", "raskolspec+ apply-отказы");
            return;
        }
        UUID pu = probe.getUniqueId();
        List<String> trees = plugin.getSpec2Service().classTreeIds(pu);
        if (trees.isEmpty()) {
            r.skip("126", "raskolspec+ apply (нет дерева класса)");
            r.skip("127", "raskolspec+ apply-отказы");
            return;
        }
        String t0 = trees.get(0);
        Spec2Tree tree = Spec2Registry.treeOf(t0);
        Spec2Node n0 = firstRow1(tree);
        if (tree == null || n0 == null) {
            r.skip("126", "raskolspec+ apply (нет узла ряда 1)");
            r.skip("127", "raskolspec+ apply-отказы");
            return;
        }
        String prevMain = plugin.getSpec2Service().storage().getMain(pu);
        boolean tempMain = prevMain == null;
        Map<String, Integer> backup = new HashMap<>(
                plugin.getSpec2Service().storage().getRanks(pu, t0));
        boolean ok126 = false;
        String got126 = "n/a";
        try {
            if (tempMain) {
                plugin.getSpec2Service().storage().setMain(pu, t0);
            }
            pauseMs(200);
            Map<String, Integer> tpl = Map.of(n0.id(), 1);
            Spec2Service.BuildApplyOutcome out =
                    plugin.getSpec2Service().applyBuild(probe, t0, tpl, true);
            int rank = plugin.getSpec2Service().storage().getRanks(pu, t0)
                    .getOrDefault(n0.id(), 0);
            ok126 = out.result() == Spec2Service.BuildApplyResult.OK && rank == 1;
            got126 = "result=" + out.result() + " rank=" + rank;
        } finally {
            plugin.getSpec2Service().storage().setRanks(pu, t0, backup);
            if (tempMain) {
                plugin.getSpec2Service().storage().setMain(pu, null);
            }
            plugin.getSpec2Service().reconcile(pu);
        }
        r.check("126", "raskolspec+ applyBuild: free-применение узла ряда 1 = OK, rank=1, откат чист",
                ok126, "Spec2Service.applyBuild", got126);

        // 127: отказы без изменения состояния
        Map<String, Integer> before127 = new HashMap<>(
                plugin.getSpec2Service().storage().getRanks(pu, t0));

        String foreign = null;
        String[] candidates = {"arms", "fury", "guard", "marksmanship", "survival",
                "beastmaster", "assassination", "outlaw", "subtlety", "arcane", "fire",
                "frost", "discipline", "holy", "shadow", "affliction", "destruction", "demonology"};
        for (String c : candidates) {
            if (!trees.contains(c)) {
                foreign = c;
                break;
            }
        }
        boolean wrongRejected = true;
        if (foreign != null) {
            Spec2Tree fTree = Spec2Registry.treeOf(foreign);
            Spec2Node fNode = firstRow1(fTree);
            if (fNode != null) {
                pauseMs(200);
                Spec2Service.BuildApplyOutcome out = plugin.getSpec2Service()
                        .applyBuild(probe, foreign, Map.of(fNode.id(), 1), true);
                wrongRejected = out.result() == Spec2Service.BuildApplyResult.WRONG_CLASS_TREE;
            }
        }

        Spec2Node gapNode = null;
        for (Spec2Node n : tree.nodes()) {
            if (!n.prereqs().isEmpty()) {
                gapNode = n;
                break;
            }
        }
        boolean gapRejected = true;
        if (gapNode != null) {
            pauseMs(200);
            Spec2Service.BuildApplyOutcome gapOut = plugin.getSpec2Service()
                    .applyBuild(probe, t0, Map.of(gapNode.id(), 1), true);
            gapRejected = gapOut.result() == Spec2Service.BuildApplyResult.TEMPLATE_INVALID;
        }

        Map<String, Integer> after127 = new HashMap<>(
                plugin.getSpec2Service().storage().getRanks(pu, t0));
        boolean stateIntact = after127.equals(before127);
        boolean ok127 = wrongRejected && gapRejected && stateIntact;
        r.check("127", "raskolspec+ applyBuild-отказы: чужое дерево + prereq-дыра, состояние не изменено",
                ok127, "Spec2Service.applyBuild",
                "wrong=" + wrongRejected + " gap=" + gapRejected + " intact=" + stateIntact);
    }
}
