// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.command;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.ability.AbilityDef;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.classsystem.SkillLevelProvider;
import dev.raskol.classes.combat.ResistService;
import dev.raskol.classes.config.RaskolConfig;
import dev.raskol.classes.effect.EffectType;
import dev.raskol.classes.gui.ClassBook;
import dev.raskol.classes.install.Installation;
import dev.raskol.classes.spec.Spec;
import dev.raskol.classes.spec.SpecRegistry;
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
import java.util.Map;
import java.util.UUID;

public final class RaskolCommand implements CommandExecutor, TabCompleter {

    private static final List<String> ROOT_SUGGESTIONS =
            List.of("1", "2", "3", "4", "5", "6", "7", "menu", "reload", "debug");

    private final RaskolClasses plugin;

    public RaskolCommand(RaskolClasses plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        RaskolConfig cfg = plugin.getRaskolConfig();
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text(
                        "Информация о классе доступна только игрокам", NamedTextColor.GRAY));
                return true;
            }
            sendInfo(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                if (!sender.hasPermission("raskolclasses.admin")) {
                    sender.sendMessage(Component.text(
                            cfg.message("no-permission", "Недостаточно прав"),
                            NamedTextColor.RED));
                    return true;
                }
                plugin.reloadPlugin();
                sender.sendMessage(Component.text("RaskolClasses: конфигурация перезагружена",
                        NamedTextColor.GREEN));
            }
            case "menu" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Книга класса — только для игроков",
                            NamedTextColor.GRAY));
                    return true;
                }
                ClassBook.open(plugin, player, ClassBook.Tab.ABILITIES);
            }
            case "6" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Каст доступен только игрокам",
                            NamedTextColor.GRAY));
                    return true;
                }
                Spec spec = plugin.getSpecService().getSpec(player.getUniqueId());
                if (spec == null) {
                    player.sendMessage(Component.text(
                            "Специализация не выбрана — открой Книгу класса: /rc menu",
                            NamedTextColor.GRAY));
                    return true;
                }
                plugin.getSpecCaster().tryCast(player, spec);
            }
            case "7" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Установка доступна только игрокам",
                            NamedTextColor.GRAY));
                    return true;
                }
                plugin.getInstallations().tryPlace(player);
            }
            case "1", "2", "3", "4", "5" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Каст доступен только игрокам",
                            NamedTextColor.GRAY));
                    return true;
                }
                PlayerClass pc = plugin.getClassProvider().getClassOf(player);
                if (pc == null) {
                    player.sendMessage(Component.text(
                            cfg.message("no-class", "Класс не выбран — посетите герольда"),
                            NamedTextColor.GRAY));
                    return true;
                }
                int slot = Integer.parseInt(args[0]);
                AbilityDef def = plugin.getAbilities().getBySlot(pc, slot);
                if (def == null) {
                    player.sendMessage(Component.text("У класса " + pc.getDisplayName()
                            + " нет способности в слоте " + slot, NamedTextColor.GRAY));
                    return true;
                }
                plugin.getAbilities().tryCast(player, def);
                plugin.getFx().onAttempt(player, def.id(), def.cooldownMillis());
            }
            case "debug" -> {
                if (!sender.hasPermission("raskolclasses.debug")) {
                    sender.sendMessage(Component.text(
                            cfg.message("no-permission", "Недостаточно прав"),
                            NamedTextColor.RED));
                    return true;
                }
                Player target = sender instanceof Player p ? p : null;
                if (args.length > 1) {
                    target = Bukkit.getPlayer(args[1]);
                    if (target == null) {
                        sender.sendMessage(Component.text(
                                "Игрок «" + args[1] + "» не найден",
                                NamedTextColor.RED));
                        return true;
                    }
                }
                if (target == null) {
                    sender.sendMessage(Component.text(
                            "Укажите игрока или выполните команду в игре",
                            NamedTextColor.RED));
                    return true;
                }
                sendDebug(sender, target);
            }
            default -> sender.sendMessage(Component.text(
                    "Использование: /rc [1-7|menu|reload|debug]",
                    NamedTextColor.GRAY));
        }
        return true;
    }

    private void sendInfo(Player player) {
        RaskolConfig cfg = plugin.getRaskolConfig();
        PlayerClass pc = plugin.getClassProvider().getClassOf(player);
        if (pc == null) {
            player.sendMessage(Component.text(
                    cfg.message("no-class", "Класс не выбран — посетите герольда"),
                    NamedTextColor.GRAY));
            return;
        }
        int level = plugin.getSkillLevels().getLevel(player.getUniqueId(), pc.profileSkillName());

        player.sendMessage(Component.text("Класс: ", NamedTextColor.GRAY)
                .append(Component.text(pc.getDisplayName(), pc.getColor())));
        String levelText = level == SkillLevelProvider.NO_SKILL_SYSTEM
                ? "AuraSkills не подключён"
                : pc.profileSkillName() + " " + level;
        player.sendMessage(Component.text("Уровень: " + levelText, NamedTextColor.GRAY));
        player.sendMessage(Component.text(pc.getResourceName() + ": "
                + (int) plugin.getResources().getValue(player.getUniqueId()) + "/100",
                pc.getColor()));

        UUID uuid = player.getUniqueId();

        // 1.6.0: итоговые резисты класса (+ модификаторы)
        player.sendMessage(Component.text("Резисты: ", NamedTextColor.GRAY)
                .append(Component.text("физ " + (int) plugin.getResists().physicalResist(uuid)
                        + "% · маг " + (int) plugin.getResists().magicResist(uuid) + "%",
                        NamedTextColor.AQUA)));

        player.sendMessage(Component.text("Корона: ", NamedTextColor.GRAY)
                .append(Component.text(
                        plugin.getFlavorService().crownDisplayName(uuid),
                        NamedTextColor.GOLD)));
        String title = plugin.getFlavorService().titleOf(uuid, pc);
        if (!title.isEmpty()) {
            player.sendMessage(Component.text("Титул: ", NamedTextColor.GRAY)
                    .append(Component.text(title, pc.getColor())));
        }

        Spec spec = plugin.getSpecService().getSpec(uuid);
        if (spec != null) {
            player.sendMessage(Component.text("Специализация: ", NamedTextColor.GRAY)
                    .append(Component.text(spec.displayName(), pc.getColor())));
        } else {
            String specStatus = plugin.getSpecService().canChoose(player)
                    ? "доступна — Книга класса (/rc menu)"
                    : "откроется на 40 уровне";
            player.sendMessage(Component.text("Специализация: " + specStatus,
                    NamedTextColor.DARK_GRAY));
        }

        player.sendMessage(Component.text("Инсталляции: ", NamedTextColor.GRAY)
                .append(Component.text(plugin.getInstallations().countOf(uuid) + "/2 активных",
                        NamedTextColor.DARK_GRAY)));

        for (AbilityDef def : plugin.getAbilities().getAbilities(pc)) {
            String desc = cfg.abilityDescription(pc, def.id(), "");
            Component descComp = desc.isEmpty()
                    ? Component.empty()
                    : Component.text(" — " + desc, NamedTextColor.GRAY);
            player.sendMessage(Component.text("[" + def.slot() + "] ", NamedTextColor.DARK_GRAY)
                    .append(Component.text(def.displayName(), pc.getColor()))
                    .append(descComp)
                    .append(Component.text(" · " + def.cost() + " рес. · "
                                    + def.cooldownMillis() / 1000L + "с кд · "
                                    + statusOf(player, def, level),
                            NamedTextColor.GRAY)));
        }

        for (String passiveId : RaskolConfig.passiveIds(pc)) {
            if (!cfg.passiveEnabled(pc, passiveId)) {
                continue;
            }
            String passiveName = cfg.passiveDisplayName(pc, passiveId, passiveId);
            String passiveDesc = cfg.passiveDescription(pc, passiveId, "");
            player.sendMessage(Component.text("Пассив: ", NamedTextColor.DARK_GRAY)
                    .append(Component.text(passiveName, pc.getColor()))
                    .append(Component.text(passiveDesc.isEmpty() ? "" : " — " + passiveDesc,
                            NamedTextColor.GRAY)));
        }

        player.sendMessage(Component.text("Книга класса: /rc menu · Каст: /rc 1–7",
                NamedTextColor.DARK_GRAY));
    }

    private void sendDebug(CommandSender sender, Player target) {
        UUID uuid = target.getUniqueId();
        RaskolConfig cfg = plugin.getRaskolConfig();
        PlayerClass pc = plugin.getClassProvider().getClassOf(target);

        sender.sendMessage(Component.text("--- RaskolClasses Debug ---",
                NamedTextColor.GOLD));
        sender.sendMessage(Component.text("Версия: ", NamedTextColor.GRAY)
                .append(Component.text(plugin.getPluginMeta().getVersion(),
                        NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("Цель: ", NamedTextColor.GRAY)
                .append(Component.text(target.getName(), NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("Источник класса: ", NamedTextColor.GRAY)
                .append(Component.text(plugin.getClassProvider().sourceOf(),
                        NamedTextColor.AQUA)));

        if (pc == null) {
            sender.sendMessage(Component.text("Класс: не выбран",
                    NamedTextColor.RED));
            return;
        }

        sender.sendMessage(Component.text("Класс: ", NamedTextColor.GRAY)
                .append(Component.text(pc.getDisplayName() + " (" + pc.name() + ")",
                        pc.getColor())));
        sender.sendMessage(Component.text(
                "Ресурс (" + pc.getResourceName() + "): ", NamedTextColor.GRAY)
                .append(Component.text(
                        (int) plugin.getResources().getValue(uuid) + "/100",
                        pc.getColor())));

        // 1.6.0: разбивка резистов — база класса + активные модификаторы
        ResistService.Breakdown rb = plugin.getResists().breakdown(uuid);
        sender.sendMessage(Component.text("Резисты: ", NamedTextColor.GRAY)
                .append(Component.text("физ " + (int) rb.physicalTotal() + "% (база "
                        + (int) rb.basePhysical() + ") · маг " + (int) rb.magicTotal()
                        + "% (база " + (int) rb.baseMagic() + ")", NamedTextColor.AQUA)));
        for (ResistService.Modifier m : rb.active()) {
            sender.sendMessage(Component.text("  • модификатор " + m.source()
                    + ": физ " + (int) m.physicalPct() + "% · маг " + (int) m.magicPct() + "%",
                    NamedTextColor.GRAY));
        }

        sender.sendMessage(Component.text("Корона: ", NamedTextColor.GRAY)
                .append(Component.text(
                        plugin.getFlavorService().crownDisplayName(uuid),
                        NamedTextColor.GOLD)));
        String title = plugin.getFlavorService().titleOf(uuid, pc);
        sender.sendMessage(Component.text("Титул: ", NamedTextColor.GRAY)
                .append(Component.text(title.isEmpty() ? "—" : title,
                        NamedTextColor.WHITE)));

        Spec spec = plugin.getSpecService().getSpec(uuid);
        if (spec != null) {
            sender.sendMessage(Component.text("Специализация: ", NamedTextColor.GRAY)
                    .append(Component.text(spec.displayName(), NamedTextColor.GREEN)));
        } else {
            sender.sendMessage(Component.text("Специализация: не выбрана",
                    NamedTextColor.GRAY));
        }

        sender.sendMessage(Component.text("Инсталляции: ", NamedTextColor.GRAY)
                .append(Component.text(plugin.getInstallations().countOf(uuid) + "/2",
                        NamedTextColor.WHITE)));

        int maxGlobal = plugin.getConfig().getInt("installations.max-global", 200);
        sender.sendMessage(Component.text("Инсталляции на сервере: ", NamedTextColor.GRAY)
                .append(Component.text(plugin.getInstallations().countGlobal() + "/" + maxGlobal,
                        NamedTextColor.WHITE)));
        long now = System.currentTimeMillis();
        for (Installation inst : plugin.getInstallations().snapshot()) {
            if (!inst.getOwner().equals(uuid)) {
                continue;
            }
            long remain = Math.max(0L, inst.getExpiresAt() - now);
            Location loc = inst.getLocation();
            sender.sendMessage(Component.text("  • " + inst.getType().displayName()
                            + " — " + (remain / 1000L) + "с ("
                            + loc.getBlockX() + "/" + loc.getBlockY() + "/" + loc.getBlockZ() + ")",
                    NamedTextColor.GRAY));
        }

        boolean hasCooldowns = false;
        for (AbilityDef def : plugin.getAbilities().getAbilities(pc)) {
            long remaining = plugin.getCooldowns().getRemainingMillis(uuid, def.id());
            if (remaining > 0L) {
                if (!hasCooldowns) {
                    sender.sendMessage(Component.text("Активные КД:",
                            NamedTextColor.YELLOW));
                    hasCooldowns = true;
                }
                sender.sendMessage(Component.text("  • " + def.displayName()
                        + " — " + (remaining / 1000L + 1L) + "с",
                        NamedTextColor.GRAY));
            }
        }
        long specRemaining = spec != null
                ? plugin.getCooldowns().getRemainingMillis(uuid, "spec_" + spec.id())
                : 0L;
        if (specRemaining > 0L) {
            sender.sendMessage(Component.text("  • Спека — "
                    + (specRemaining / 1000L + 1L) + "с", NamedTextColor.YELLOW));
        }
        if (!hasCooldowns && specRemaining <= 0L) {
            sender.sendMessage(Component.text("Активные КД: нет",
                    NamedTextColor.GRAY));
        }

        Map<EffectType, Long> effects = plugin.getEffects().getActiveEffects(uuid);
        if (effects.isEmpty()) {
            sender.sendMessage(Component.text("Активные эффекты: нет",
                    NamedTextColor.GRAY));
        } else {
            sender.sendMessage(Component.text("Активные эффекты:",
                    NamedTextColor.LIGHT_PURPLE));
            for (Map.Entry<EffectType, Long> entry : effects.entrySet()) {
                long exp = entry.getValue();
                String suffix = exp == Long.MAX_VALUE ? "∞"
                        : ((exp - now) / 1000L) + "с";
                sender.sendMessage(Component.text("  • " + entry.getKey().name()
                        + " — " + suffix, NamedTextColor.LIGHT_PURPLE));
            }
        }

        sender.sendMessage(Component.text("Пассивки:", NamedTextColor.AQUA));
        for (String passiveId : RaskolConfig.passiveIds(pc)) {
            boolean enabled = cfg.passiveEnabled(pc, passiveId);
            String passiveName = cfg.passiveDisplayName(pc, passiveId, passiveId);
            sender.sendMessage(Component.text("  • " + passiveName
                            + (enabled ? "" : " [выкл]") + " — "
                            + passiveNumbers(pc, passiveId),
                    NamedTextColor.GRAY));
        }

        List<String> fxIds = new ArrayList<>();
        for (AbilityDef def : plugin.getAbilities().getAbilities(pc)) {
            fxIds.add(def.id());
        }
        if (spec != null) {
            SpecRegistry.SpecDef sdef = plugin.getSpecRegistry().get(spec);
            if (sdef != null) {
                fxIds.add(sdef.activeId());
            }
        }
        plugin.getFx().appendDebug(sender, fxIds);

        int level = plugin.getSkillLevels().getLevel(uuid, pc.profileSkillName());
        String levelText = level == SkillLevelProvider.NO_SKILL_SYSTEM
                ? "AuraSkills не подключён"
                : String.valueOf(level);
        sender.sendMessage(Component.text(
                "Уровень " + pc.profileSkillName() + ": ", NamedTextColor.GRAY)
                .append(Component.text(levelText, NamedTextColor.WHITE)));

        boolean visible = plugin.getHud().isVisible(target);
        sender.sendMessage(Component.text("HUD: ", NamedTextColor.GRAY)
                .append(Component.text(visible ? "включён" : "выключен",
                        visible ? NamedTextColor.GREEN : NamedTextColor.RED)));
    }

    private String passiveNumbers(PlayerClass pc, String id) {
        RaskolConfig cfg = plugin.getRaskolConfig();
        return switch (id) {
            case "execute_passive" -> "chance " + percent(cfg.passiveDouble(pc, id, "chance", 0.20))
                    + " · ×" + cfg.passiveDouble(pc, id, "multiplier", 3.0)
                    + " · threshold " + percent(cfg.passiveDouble(pc, id, "threshold", 0.20))
                    + " · КД " + cfg.passiveInt(pc, id, "cooldown-seconds", 6) + "с";
            case "predator" -> "threshold " + percent(cfg.passiveDouble(pc, id, "threshold", 0.80))
                    + " · ×" + cfg.passiveDouble(pc, id, "multiplier", 1.20);
            case "grace" -> "×" + cfg.passiveDouble(pc, id, "multiplier", 1.15);
            case "mana_soaked" -> "threshold " + (int) cfg.passiveDouble(pc, id, "threshold", 50.0)
                    + " маны · −" + percent(cfg.passiveDouble(pc, id, "reduction", 0.15));
            case "poisoned_blades" -> "chance " + percent(cfg.passiveDouble(pc, id, "chance", 0.30))
                    + " · " + cfg.passiveInt(pc, id, "duration-seconds", 2) + "с"
                    + " · КД " + cfg.passiveInt(pc, id, "cooldown-seconds", 3) + "с";
            case "sadism" -> "+" + cfg.passiveDouble(pc, id, "bonus", 3.0)
                    + " · КД " + cfg.passiveInt(pc, id, "cooldown-seconds", 2) + "с";
            default -> id;
        };
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
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase();
        return ROOT_SUGGESTIONS.stream()
                .filter(suggestion -> suggestion.startsWith(prefix))
                .filter(suggestion -> !"reload".equals(suggestion)
                        || sender.hasPermission("raskolclasses.admin"))
                .filter(suggestion -> !"debug".equals(suggestion)
                        || sender.hasPermission("raskolclasses.debug"))
                .toList();
    }
}
