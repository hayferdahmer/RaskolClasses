// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.command;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.ability.AbilityDef;
import dev.raskol.classes.attribute.AttributeService;
import dev.raskol.classes.attribute.AttributeType;
import dev.raskol.classes.balance.BalanceSimulator;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.classsystem.SkillLevelProvider;
import dev.raskol.classes.combat.DamageProfile;
import dev.raskol.classes.combat.ResistService;
import dev.raskol.classes.config.RaskolConfig;
import dev.raskol.classes.effect.EffectType;
import dev.raskol.classes.gui.ClassBook;
import dev.raskol.classes.install.Installation;
import dev.raskol.classes.spec.Spec;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Команды 1.5.4+ /rc (инфо), /rc 1–7, /rc menu, /rc reload, /rc debug, /rc health.
 * 1.6.13: /rc selftest. 1.7.0 пакет 1: блоки атрибутов в /rc и /rc debug.
 * 1.7.5: слот 6 — инфо-сообщение (активки спеков удалены).
 * 1.7.6: /rc debug simulate [classA] [classB] — TTK-харнесс (матрица 5×5 и дуэли).
 * Все строковые литералы однострочные (защита от поломки склеек при копировании).
 */
public final class RaskolCommand implements CommandExecutor, TabCompleter {

    private static final List<String> ROOT_SUGGESTIONS =
            List.of("1", "2", "3", "4", "5", "6", "7", "menu", "reload",
                    "debug", "health", "selftest");

    private final RaskolClasses plugin;

    public RaskolCommand(RaskolClasses plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        RaskolConfig cfg = plugin.getRaskolConfig();
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("Информация о классе доступна только игрокам", NamedTextColor.GRAY));
                return true;
            }
            sendInfo(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                if (!sender.hasPermission("raskolclasses.admin")) {
                    sender.sendMessage(Component.text(cfg.message("no-permission", "Недостаточно прав"), NamedTextColor.RED));
                    return true;
                }
                plugin.reloadPlugin();
                sender.sendMessage(Component.text("RaskolClasses: конфигурация перезагружена", NamedTextColor.GREEN));
            }
            case "menu" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Книга класса — только для игроков", NamedTextColor.GRAY));
                    return true;
                }
                ClassBook.open(plugin, player, ClassBook.Tab.ABILITIES);
            }
            case "6" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Каст доступен только игрокам", NamedTextColor.GRAY));
                    return true;
                }
                player.sendMessage(Component.text("Активки спеков удалены в 1.7.5: спека — пассивная идентичность. Таланты придут в 1.8.0.", NamedTextColor.GRAY));
            }
            case "7" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Установка доступна только игрокам", NamedTextColor.GRAY));
                    return true;
                }
                plugin.getInstallations().tryPlace(player);
            }
            case "1", "2", "3", "4", "5" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Каст доступен только игрокам", NamedTextColor.GRAY));
                    return true;
                }
                PlayerClass pc = plugin.getClassProvider().getClassOf(player);
                if (pc == null) {
                    player.sendMessage(Component.text(cfg.message("no-class", "Класс не выбран — посетите герольда"), NamedTextColor.GRAY));
                    return true;
                }
                int slot = Integer.parseInt(args[0]);
                AbilityDef def = plugin.getAbilities().getBySlot(pc, slot);
                if (def == null) {
                    player.sendMessage(Component.text("У класса " + pc.getDisplayName() + " нет способности в слоте " + slot, NamedTextColor.GRAY));
                    return true;
                }
                plugin.getAbilities().tryCast(player, def);
                plugin.getFx().onAttempt(player, def.id(), def.cooldownMillis());
            }
            case "health" -> {
                if (!sender.hasPermission("raskolclasses.debug")) {
                    sender.sendMessage(Component.text(cfg.message("no-permission", "Недостаточно прав"), NamedTextColor.RED));
                    return true;
                }
                sendHealth(sender);
            }
            case "selftest" -> {
                if (!sender.hasPermission("raskolclasses.debug")) {
                    sender.sendMessage(Component.text(cfg.message("no-permission", "Недостаточно прав"), NamedTextColor.RED));
                    return true;
                }
                dev.raskol.classes.selftest.SelftestRunner.run(plugin, sender);
            }
            case "debug" -> {
                if (!sender.hasPermission("raskolclasses.debug")) {
                    sender.sendMessage(Component.text(cfg.message("no-permission", "Недостаточно прав"), NamedTextColor.RED));
                    return true;
                }
                // 1.7.6: симулятор баланса
                if (args.length > 1 && args[1].equalsIgnoreCase("simulate")) {
                    handleSimulate(sender, args);
                    return true;
                }
                Player target = sender instanceof Player p ? p : null;
                if (args.length > 1) {
                    target = Bukkit.getPlayer(args[1]);
                    if (target == null) {
                        sender.sendMessage(Component.text("Игрок " + args[1] + " не найден", NamedTextColor.RED));
                        return true;
                    }
                }
                if (target == null) {
                    sender.sendMessage(Component.text("Укажите игрока или выполните команду в игре", NamedTextColor.RED));
                    return true;
                }
                sendDebug(sender, target);
            }
            default -> sender.sendMessage(Component.text("Использование: /rc [1-7|menu|reload|debug|health|selftest]", NamedTextColor.GRAY));
        }
        return true;
    }

    /* ------------------------- 1.7.6: TTK-харнесс ------------------------- */

    /**
     * /rc debug simulate            → матрица 5×5 TTK (строка = атакующий).
     * /rc debug simulate <A>        → дуэль класса отправителя (или WARRIOR из консоли) против A.
     * /rc debug simulate <A> <B>    → дуэль A против B.
     * Уровень 40, seed 42 (детерминированно). Подсветка: зелёный = коридор
     * anchor ±30%, жёлтый = вне коридора, красный = не убивает за 60 с.
     */
    private void handleSimulate(CommandSender sender, String[] args) {
        int level = 40;
        long seed = 42L;
        double anchor = plugin.getRaskolConfig().targetTtkSeconds();

        if (args.length == 1) {
            double[][] m = BalanceSimulator.matrix(plugin, level, seed);
            PlayerClass[] pcs = PlayerClass.values();
            sender.sendMessage(Component.text("─── TTK-матрица (сек), уровень " + level
                    + ", якорь " + fmt1(anchor) + " с ───", NamedTextColor.GOLD));
            Component header = Component.text("атак\\защ          |", NamedTextColor.GRAY);
            for (PlayerClass pc : pcs) {
                header = header.append(Component.text(String.format(Locale.ROOT, " %8s", shortName(pc)),
                        NamedTextColor.DARK_GRAY));
            }
            sender.sendMessage(header);
            for (int i = 0; i < pcs.length; i++) {
                Component row = Component.text(String.format(Locale.ROOT, "%-17s |", shortName(pcs[i])),
                        NamedTextColor.GRAY);
                for (int j = 0; j < pcs.length; j++) {
                    double v = m[i][j];
                    String cell = Double.isFinite(v)
                            ? String.format(Locale.ROOT, " %8s", fmt1(v))
                            : String.format(Locale.ROOT, " %8s", "—");
                    row = row.append(Component.text(cell, cellColor(v, anchor)));
                }
                sender.sendMessage(row);
            }
            sender.sendMessage(Component.text("Зелёный = якорь ±30% · жёлтый = вне коридора · — = не убивает за 60 с",
                    NamedTextColor.DARK_GRAY));
            sender.sendMessage(Component.text("Детали пары: /rc debug simulate <A> <B>", NamedTextColor.DARK_GRAY));
            return;
        }

        PlayerClass a = parseClass(args[1]);
        PlayerClass b;
        if (args.length > 2) {
            b = parseClass(args[2]);
        } else if (sender instanceof Player p && plugin.getClassProvider().getClassOf(p) != null) {
            b = a;
            a = plugin.getClassProvider().getClassOf(p);
        } else {
            b = a;
            a = PlayerClass.WARRIOR;
        }
        if (a == null || b == null) {
            sender.sendMessage(Component.text("Классы: WARRIOR, HUNTER, PRIEST, MAGE, ROGUE", NamedTextColor.RED));
            return;
        }
        BalanceSimulator.DuelResult r = BalanceSimulator.duel(plugin, a, b, level, seed);
        sender.sendMessage(Component.text("─── Дуэль: " + shortName(a) + " vs " + shortName(b)
                + " (уровень " + level + ", seed " + seed + ") ───", NamedTextColor.GOLD));
        if (r.timeout()) {
            String leader = r.winner() == null ? "ничья по HP" : "лидер по HP: " + shortName(r.winner());
            sender.sendMessage(Component.text("Итог: timeout 60 с (" + leader + ")", NamedTextColor.YELLOW));
        } else {
            sender.sendMessage(Component.text("Итог: " + shortName(r.winner()) + " победил за "
                    + fmt1(r.ttkSeconds()) + " с", NamedTextColor.GREEN));
            double dev = (r.ttkSeconds() - anchor) / anchor * 100.0;
            NamedTextColor devColor = Math.abs(dev) <= 30.0 ? NamedTextColor.GREEN : NamedTextColor.YELLOW;
            sender.sendMessage(Component.text("Отклонение от якоря " + fmt1(anchor) + " с: "
                    + (dev >= 0 ? "+" : "") + fmt1(dev) + "%", devColor));
        }
        sender.sendMessage(Component.text("Касты: " + shortName(a) + " " + r.castsA()
                + " · " + shortName(b) + " " + r.castsB(), NamedTextColor.GRAY));
        sender.sendMessage(Component.text("Уклонения/парирования: " + shortName(a) + " " + r.dodgesA()
                + " · " + shortName(b) + " " + r.dodgesB(), NamedTextColor.GRAY));
    }

    private static PlayerClass parseClass(String s) {
        if (s == null) {
            return null;
        }
        try {
            return PlayerClass.valueOf(s.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String shortName(PlayerClass pc) {
        return switch (pc) {
            case WARRIOR -> "воин";
            case HUNTER -> "охотник";
            case PRIEST -> "жрец";
            case MAGE -> "маг";
            case ROGUE -> "разбойник";
        };
    }

    private static NamedTextColor cellColor(double v, double anchor) {
        if (!Double.isFinite(v)) {
            return NamedTextColor.RED;
        }
        return Math.abs(v - anchor) <= anchor * 0.3 ? NamedTextColor.GREEN : NamedTextColor.YELLOW;
    }

    private static String fmt1(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }

    /* ------------------------- /rc health (1.6.12) ------------------------- */

    private void sendHealth(CommandSender sender) {
        sender.sendMessage(Component.text("--- RaskolClasses Health ---", NamedTextColor.GOLD));

        double mspt = plugin.getServer().getAverageTickTime();
        double[] tpsArr = plugin.getServer().getTPS();
        double tps = tpsArr != null && tpsArr.length > 0 ? tpsArr[0] : 0.0;
        sender.sendMessage(Component.text("MSPT: " + fmt2(mspt) + " ms · TPS: " + fmt2(tps),
                mspt <= 50.0 ? NamedTextColor.GREEN : NamedTextColor.RED));

        sender.sendMessage(Component.text("Модификаторы резиста: игроков " + plugin.getResists().trackedPlayers()
                + " · записей " + plugin.getResists().totalModifiers()
                + " · кэш факторов " + plugin.getResists().factorCacheSize(), NamedTextColor.GRAY));

        sender.sendMessage(Component.text("Кулдауны: игроков " + plugin.getCooldowns().trackedPlayers()
                + " · записей " + plugin.getCooldowns().totalEntries(), NamedTextColor.GRAY));

        int maxGlobal = plugin.getConfig().getInt("installations.max-global", 200);
        sender.sendMessage(Component.text("Инсталляции: активных " + plugin.getInstallations().countGlobal()
                + "/" + maxGlobal, NamedTextColor.GRAY));

        sender.sendMessage(Component.text("Proc-кулдауны Fx: игроков " + plugin.getFx().procVisualCdSize(), NamedTextColor.GRAY));

        sender.sendMessage(Component.text("Звуковой бюджет: сброшено с прошлого /rc health: "
                + plugin.getFx().pollDroppedSounds(), NamedTextColor.GRAY));

        long sincePurge = Math.max(0L, (System.currentTimeMillis() - plugin.getLastPurgeMillis()) / 1000L);
        sender.sendMessage(Component.text("Последний purge: " + sincePurge + " с назад", NamedTextColor.GRAY));

        long uptime = Math.max(0L, (System.currentTimeMillis() - plugin.getEnabledAtMillis()) / 1000L);
        sender.sendMessage(Component.text("Аптайм плагина: " + uptime / 60L + " мин " + uptime % 60L + " с", NamedTextColor.GRAY));
    }

    private static String fmt2(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    /* ------------------------- /rc (инфо) ------------------------- */

    private void sendInfo(Player player) {
        RaskolConfig cfg = plugin.getRaskolConfig();
        PlayerClass pc = plugin.getClassProvider().getClassOf(player);
        if (pc == null) {
            player.sendMessage(Component.text(cfg.message("no-class", "Класс не выбран — посетите герольда"), NamedTextColor.GRAY));
            return;
        }
        int level = plugin.getSkillLevels().getLevel(player.getUniqueId(), pc.profileSkillName());

        player.sendMessage(Component.text("Класс: ", NamedTextColor.GRAY).append(Component.text(pc.getDisplayName(), pc.getColor())));
        String levelText = level == SkillLevelProvider.NO_SKILL_SYSTEM ? "AuraSkills не подключён" : pc.profileSkillName() + " " + level;
        player.sendMessage(Component.text("Уровень: " + levelText, NamedTextColor.GRAY));
        player.sendMessage(Component.text(pc.getResourceName() + ": " + (int) plugin.getResources().getValue(player.getUniqueId()) + "/100", pc.getColor()));

        UUID uuid = player.getUniqueId();

        AttributeService attrs = plugin.getAttributes();
        player.sendMessage(Component.text("Атрибуты: СИЛА " + (int) attrs.value(uuid, AttributeType.STR)
                + " · ЛОВКОСТЬ " + (int) attrs.value(uuid, AttributeType.AGI)
                + " · ИНТЕЛЛЕКТ " + (int) attrs.value(uuid, AttributeType.INT)
                + " (осн. " + attrs.mainOf(pc).displayName() + ")", NamedTextColor.AQUA));
        double[] eff = attrs.effectiveAvoidance(uuid);
        player.sendMessage(Component.text("HP: " + (int) player.getHealth() + "/" + (int) attrs.maxHp(uuid)
                + " · Уклонение " + fmt1(eff[0]) + "% · Парирование " + fmt1(eff[1]) + "%", NamedTextColor.GRAY));

        player.sendMessage(Component.text("Резисты: физ " + (int) plugin.getResists().physicalResist(uuid)
                + "% · маг " + (int) plugin.getResists().magicResist(uuid) + "%", NamedTextColor.AQUA));

        player.sendMessage(Component.text("Корона: ", NamedTextColor.GRAY).append(Component.text(plugin.getFlavorService().crownDisplayName(uuid), NamedTextColor.GOLD)));
        String title = plugin.getFlavorService().titleOf(uuid, pc);
        if (!title.isEmpty()) {
            player.sendMessage(Component.text("Титул: ", NamedTextColor.GRAY).append(Component.text(title, pc.getColor())));
        }

        Spec spec = plugin.getSpecService().getSpec(uuid);
        if (spec != null) {
            player.sendMessage(Component.text("Специализация: " + spec.displayName() + " (пассивная идентичность)", pc.getColor()));
        } else {
            String specStatus = plugin.getSpecService().canChoose(player) ? "доступна — Книга класса (/rc menu)" : "откроется на 40 уровне";
            player.sendMessage(Component.text("Специализация: " + specStatus, NamedTextColor.DARK_GRAY));
        }

        player.sendMessage(Component.text("Инсталляции: " + plugin.getInstallations().countOf(uuid) + "/2 активных", NamedTextColor.DARK_GRAY));

        for (AbilityDef def : plugin.getAbilities().getAbilities(pc)) {
            String desc = cfg.abilityDescription(pc, def.id(), "");
            Component descComp = desc.isEmpty() ? Component.empty() : Component.text(" — " + desc, NamedTextColor.GRAY);
            player.sendMessage(Component.text("[" + def.slot() + "] ", NamedTextColor.DARK_GRAY)
                    .append(Component.text(def.displayName(), pc.getColor()))
                    .append(descComp)
                    .append(Component.text(" · " + def.cost() + " рес. · " + def.cooldownMillis() / 1000L + "с кд · " + statusOf(player, def, level), NamedTextColor.GRAY)));
        }

        for (String passiveId : RaskolConfig.passiveIds(pc)) {
            if (!cfg.passiveEnabled(pc, passiveId)) {
                continue;
            }
            String passiveName = cfg.passiveDisplayName(pc, passiveId, passiveId);
            String passiveDesc = cfg.passiveDescription(pc, passiveId, "");
            player.sendMessage(Component.text("Пассив: ", NamedTextColor.DARK_GRAY)
                    .append(Component.text(passiveName, pc.getColor()))
                    .append(Component.text(passiveDesc.isEmpty() ? "" : " — " + passiveDesc, NamedTextColor.GRAY)));
        }

        player.sendMessage(Component.text("Книга класса: /rc menu · Каст: /rc 1–5", NamedTextColor.DARK_GRAY));
    }

    /* ------------------------- /rc debug ------------------------- */

    private void sendDebug(CommandSender sender, Player target) {
        UUID uuid = target.getUniqueId();
        RaskolConfig cfg = plugin.getRaskolConfig();
        PlayerClass pc = plugin.getClassProvider().getClassOf(target);

        sender.sendMessage(Component.text("--- RaskolClasses Debug ---", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("Версия: ", NamedTextColor.GRAY).append(Component.text(plugin.getPluginMeta().getVersion(), NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("Цель: ", NamedTextColor.GRAY).append(Component.text(target.getName(), NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("Источник класса: ", NamedTextColor.GRAY).append(Component.text(plugin.getClassProvider().sourceOf(), NamedTextColor.AQUA)));

        if (pc == null) {
            sender.sendMessage(Component.text("Класс: не выбран", NamedTextColor.RED));
            return;
        }

        sender.sendMessage(Component.text("Класс: ", NamedTextColor.GRAY).append(Component.text(pc.getDisplayName() + " (" + pc.name() + ")", pc.getColor())));
        sender.sendMessage(Component.text("Ресурс (" + pc.getResourceName() + "): ", NamedTextColor.GRAY)
                .append(Component.text((int) plugin.getResources().getValue(uuid) + "/100", pc.getColor())));

        AttributeService attrs = plugin.getAttributes();
        sender.sendMessage(Component.text("Атрибуты:", NamedTextColor.AQUA));
        for (AttributeType t : AttributeType.values()) {
            sender.sendMessage(Component.text("  • " + t.displayName() + ": " + fmt1(attrs.value(uuid, t))
                    + (attrs.mainOf(pc) == t ? " (основной)" : ""), NamedTextColor.GRAY));
        }
        sender.sendMessage(Component.text("  • maxHP по формуле: " + fmt1(attrs.maxHp(uuid)), NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  • крит мили: " + fmt1(attrs.critMeleeChance(uuid)) + "% · крит магии: "
                + fmt1(attrs.critSpellChance(uuid)) + "%", NamedTextColor.GRAY));
        double dodgeRaw = attrs.dodgeChance(uuid);
        double parryRaw = attrs.parryChance(uuid);
        double[] eff = attrs.effectiveAvoidance(uuid);
        sender.sendMessage(Component.text("  • уклонение raw " + fmt1(dodgeRaw) + "% → eff " + fmt1(eff[0])
                + "% · парирование raw " + fmt1(parryRaw) + "% → eff " + fmt1(eff[1]) + "% (при фронт+мили)", NamedTextColor.GRAY));

        ResistService.Breakdown rb = plugin.getResists().breakdown(uuid);
        sender.sendMessage(Component.text("Резисты: физ " + (int) rb.physicalTotal() + "% (база " + (int) rb.basePhysical()
                + ") · маг " + (int) rb.magicTotal() + "% (база " + (int) rb.baseMagic() + ")", NamedTextColor.AQUA));
        for (ResistService.Modifier m : rb.active()) {
            sender.sendMessage(Component.text("  • модификатор " + m.source() + ": физ " + (int) m.physicalPct()
                    + "% · маг " + (int) m.magicPct() + "%", NamedTextColor.GRAY));
        }

        sender.sendMessage(Component.text("Симулятор урона по цели:", NamedTextColor.AQUA));
        sender.sendMessage(Component.text("  • физ 100 → дойдёт "
                + fmt1(plugin.getCombat().simulateTaken(target, DamageProfile.physical(100))), NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  • маг 100 → дойдёт "
                + fmt1(plugin.getCombat().simulateTaken(target, DamageProfile.magic(100))), NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  • гибрид 50/50 → дойдёт "
                + fmt1(plugin.getCombat().simulateTaken(target, DamageProfile.hybrid(50, 50))), NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  • чистый 100 → дойдёт "
                + fmt1(plugin.getCombat().simulateTaken(target, DamageProfile.trueDmg(100))), NamedTextColor.GRAY));

        sender.sendMessage(Component.text("Корона: ", NamedTextColor.GRAY).append(Component.text(plugin.getFlavorService().crownDisplayName(uuid), NamedTextColor.GOLD)));
        String title = plugin.getFlavorService().titleOf(uuid, pc);
        sender.sendMessage(Component.text("Титул: ", NamedTextColor.GRAY).append(Component.text(title.isEmpty() ? "—" : title, NamedTextColor.WHITE)));

        Spec spec = plugin.getSpecService().getSpec(uuid);
        if (spec != null) {
            sender.sendMessage(Component.text("Специализация: " + spec.displayName() + " (пассивная)", NamedTextColor.GREEN));
        } else {
            sender.sendMessage(Component.text("Специализация: не выбрана", NamedTextColor.GRAY));
        }

        sender.sendMessage(Component.text("Инсталляции: ", NamedTextColor.GRAY).append(Component.text(plugin.getInstallations().countOf(uuid) + "/2", NamedTextColor.WHITE)));

        int maxGlobal = plugin.getConfig().getInt("installations.max-global", 200);
        sender.sendMessage(Component.text("Инсталляции на сервере: ", NamedTextColor.GRAY).append(Component.text(plugin.getInstallations().countGlobal() + "/" + maxGlobal, NamedTextColor.WHITE)));
        long now = System.currentTimeMillis();
        for (Installation inst : plugin.getInstallations().snapshot()) {
            if (!inst.getOwner().equals(uuid)) {
                continue;
            }
            long remain = Math.max(0L, inst.getExpiresAt() - now);
            Location loc = inst.getLocation();
            sender.sendMessage(Component.text("  • " + inst.getType().displayName() + " — " + (remain / 1000L) + "с ("
                    + loc.getBlockX() + "/" + loc.getBlockY() + "/" + loc.getBlockZ() + ")", NamedTextColor.GRAY));
        }

        boolean hasCooldowns = false;
        for (AbilityDef def : plugin.getAbilities().getAbilities(pc)) {
            long remaining = plugin.getCooldowns().getRemainingMillis(uuid, def.id());
            if (remaining > 0L) {
                if (!hasCooldowns) {
                    sender.sendMessage(Component.text("Активные КД:", NamedTextColor.YELLOW));
                    hasCooldowns = true;
                }
                sender.sendMessage(Component.text("  • " + def.displayName() + " — " + (remaining / 1000L + 1L) + "с", NamedTextColor.GRAY));
            }
        }
        if (!hasCooldowns) {
            sender.sendMessage(Component.text("Активные КД: нет", NamedTextColor.GRAY));
        }

        Map<EffectType, Long> effects = plugin.getEffects().getActiveEffects(uuid);
        if (effects.isEmpty()) {
            sender.sendMessage(Component.text("Активные эффекты: нет", NamedTextColor.GRAY));
        } else {
            sender.sendMessage(Component.text("Активные эффекты:", NamedTextColor.LIGHT_PURPLE));
            for (Map.Entry<EffectType, Long> entry : effects.entrySet()) {
                long exp = entry.getValue();
                String suffix = exp == Long.MAX_VALUE ? "∞" : ((exp - now) / 1000L) + "с";
                sender.sendMessage(Component.text("  • " + entry.getKey().name() + " — " + suffix, NamedTextColor.LIGHT_PURPLE));
            }
        }

        sender.sendMessage(Component.text("Пассивки:", NamedTextColor.AQUA));
        for (String passiveId : RaskolConfig.passiveIds(pc)) {
            boolean enabled = cfg.passiveEnabled(pc, passiveId);
            String passiveName = cfg.passiveDisplayName(pc, passiveId, passiveId);
            sender.sendMessage(Component.text("  • " + passiveName + (enabled ? "" : " [выкл]") + " — "
                    + passiveNumbers(pc, passiveId), NamedTextColor.GRAY));
        }

        List<String> fxIds = new ArrayList<>();
        for (AbilityDef def : plugin.getAbilities().getAbilities(pc)) {
            fxIds.add(def.id());
        }
        plugin.getFx().appendDebug(sender, fxIds);

        int level = plugin.getSkillLevels().getLevel(uuid, pc.profileSkillName());
        String levelText = level == SkillLevelProvider.NO_SKILL_SYSTEM ? "AuraSkills не подключён" : String.valueOf(level);
        sender.sendMessage(Component.text("Уровень " + pc.profileSkillName() + ": ", NamedTextColor.GRAY).append(Component.text(levelText, NamedTextColor.WHITE)));

        boolean visible = plugin.getHud().isVisible(target);
        sender.sendMessage(Component.text("HUD: ", NamedTextColor.GRAY).append(Component.text(visible ? "включён" : "выключен",
                visible ? NamedTextColor.GREEN : NamedTextColor.RED)));
        sender.sendMessage(Component.text("TTK-харнесс: /rc debug simulate [A] [B]", NamedTextColor.DARK_GRAY));
    }

    private String passiveNumbers(PlayerClass pc, String id) {
        RaskolConfig cfg = plugin.getRaskolConfig();
        if (id.equals("execute_passive")) {
            String chance = percent(cfg.passiveDouble(pc, id, "chance", 0.20));
            double mult = cfg.passiveDouble(pc, id, "multiplier", 3.0);
            String thr = percent(cfg.passiveDouble(pc, id, "threshold", 0.20));
            int cd = cfg.passiveInt(pc, id, "cooldown-seconds", 6);
            return "chance " + chance + " · x" + mult + " · порог " + thr + " · КД " + cd + " с";
        }
        if (id.equals("predator")) {
            String thr = percent(cfg.passiveDouble(pc, id, "threshold", 0.80));
            double mult = cfg.passiveDouble(pc, id, "multiplier", 1.20);
            return "порог " + thr + " · x" + mult;
        }
        if (id.equals("grace")) {
            return "x" + cfg.passiveDouble(pc, id, "multiplier", 1.15);
        }
        if (id.equals("mana_soaked")) {
            int thr = (int) cfg.passiveDouble(pc, id, "threshold", 50.0);
            String red = percent(cfg.passiveDouble(pc, id, "reduction", 0.15));
            return "порог маны " + thr + " · -" + red + " входящего урона";
        }
        if (id.equals("poisoned_blades")) {
            String chance = percent(cfg.passiveDouble(pc, id, "chance", 0.30));
            int dur = cfg.passiveInt(pc, id, "duration-seconds", 2);
            int cd = cfg.passiveInt(pc, id, "cooldown-seconds", 3);
            return chance + " шанс · Яд I " + dur + " с · КД " + cd + " с";
        }
        if (id.equals("sadism")) {
            double bonus = cfg.passiveDouble(pc, id, "bonus", 3.0);
            int cd = cfg.passiveInt(pc, id, "cooldown-seconds", 2);
            return "+" + bonus + " урона со спины · КД " + cd + " с";
        }
        return id;
    }

    private static String percent(double v) {
        return (int) Math.round(v * 100.0) + "%";
    }

    private String statusOf(Player player, AbilityDef def, int level) {
        if (level != SkillLevelProvider.NO_SKILL_SYSTEM && level < def.unlockLevel()) {
            return "закрыто (нужен уровень " + def.unlockLevel() + ")";
        }
        long remaining = plugin.getCooldowns().getRemainingMillis(player.getUniqueId(), def.id());
        if (remaining > 0L) {
            return "перезарядка " + (remaining / 1000L + 1L) + "с";
        }
        double value = plugin.getResources().getValue(player.getUniqueId());
        if (value < def.cost()) {
            return "мало ресурса (" + (int) value + "/" + def.cost() + ")";
        }
        return "готова";
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase();
        return ROOT_SUGGESTIONS.stream()
                .filter(suggestion -> suggestion.startsWith(prefix))
                .filter(suggestion -> !"reload".equals(suggestion) || sender.hasPermission("raskolclasses.admin"))
                .filter(suggestion -> !"debug".equals(suggestion) || sender.hasPermission("raskolclasses.debug"))
                .filter(suggestion -> !"health".equals(suggestion) || sender.hasPermission("raskolclasses.debug"))
                .filter(suggestion -> !"selftest".equals(suggestion) || sender.hasPermission("raskolclasses.debug"))
                .toList();
    }
}
