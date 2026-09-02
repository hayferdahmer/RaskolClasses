// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.spec;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.classsystem.SkillLevelProvider;
import dev.raskol.classes.hook.EconomyHook;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Бизнес-логика спеков (1.4.0): выбор на 40 уровне, платный респец (Пакет 3).
 * Цена респеца = base + level * per-level (конфиг spec.*), деньги сжигаются
 * через economy-контракт Core (money-sink).
 */
public final class SpecService {

    private static final int REQUIRED_LEVEL = 40;
    private static final long PENDING_MILLIS = 30_000L;

    public enum RespecResult { OK, NO_SPEC, NO_ECONOMY, POOR, NOT_PENDING }

    private final RaskolClasses plugin;
    private final SpecStorage storage;
    private final SpecRegistry registry;
    private final EconomyHook economy;
    private final Map<UUID, Long> pendingUntil = new ConcurrentHashMap<>();

    public SpecService(RaskolClasses plugin, SpecStorage storage, SpecRegistry registry) {
        this.plugin = plugin;
        this.storage = storage;
        this.registry = registry;
        this.economy = new EconomyHook(plugin);
    }

    public EconomyHook economy() {
        return economy;
    }

    /** Проверка: может ли игрок выбрать спеку? */
    public boolean canChoose(Player player) {
        PlayerClass pc = plugin.getClassProvider().getClassOf(player);
        if (pc == null) {
            return false;
        }
        if (storage.hasSpec(player.getUniqueId())) {
            return false;
        }
        int level = plugin.getSkillLevels().getLevel(player.getUniqueId(), pc.profileSkillName());
        return level != SkillLevelProvider.NO_SKILL_SYSTEM && level >= REQUIRED_LEVEL;
    }

    /** Выбор спеки. Возвращает true если успешно. */
    public boolean choose(Player player, Spec spec) {
        PlayerClass pc = plugin.getClassProvider().getClassOf(player);
        if (pc == null || pc != spec.playerClass()) {
            player.sendMessage(Component.text(
                    "Эта специализация не для твоего класса",
                    NamedTextColor.RED));
            return false;
        }
        if (!canChoose(player)) {
            player.sendMessage(Component.text(
                    "Требуется уровень " + REQUIRED_LEVEL + " (" + pc.profileSkillName() + ")",
                    NamedTextColor.RED));
            return false;
        }
        storage.set(player.getUniqueId(), spec);
        syncToLuckPerms(player, spec);
        // атрибуты (броня/скорость) — сразу, без релога
        plugin.getSpecEffects().applyAttributes(player, spec);
        player.sendMessage(Component.text("Специализация выбрана: ", NamedTextColor.GREEN)
                .append(Component.text(spec.displayName(), pc.getColor())));
        return true;
    }

    /** Получить выбранную спеку (null если не выбрана). */
    public Spec getSpec(UUID uuid) {
        return storage.get(uuid);
    }

    // --- Пакет 3: платный респец ---

    /** Цена: base + level * per-level. */
    public int respecCost(Player player) {
        PlayerClass pc = plugin.getClassProvider().getClassOf(player);
        int level = pc == null
                ? REQUIRED_LEVEL
                : plugin.getSkillLevels().getLevel(player.getUniqueId(), pc.profileSkillName());
        if (level == SkillLevelProvider.NO_SKILL_SYSTEM || level < 1) {
            level = REQUIRED_LEVEL;
        }
        int base = plugin.getConfig().getInt("spec.respec-base-cost", 250);
        int per = plugin.getConfig().getInt("spec.respec-per-level", 10);
        return base + level * per;
    }

    /** Запрос подтверждения (живёт 30 секунд). */
    public void requestRespec(Player player) {
        pendingUntil.put(player.getUniqueId(), System.currentTimeMillis() + PENDING_MILLIS);
    }

    /** Подтверждение и выполнение респеца. */
    public RespecResult confirmRespec(Player player) {
        UUID uuid = player.getUniqueId();
        Long until = pendingUntil.get(uuid);
        if (until == null || until <= System.currentTimeMillis()) {
            return RespecResult.NOT_PENDING;
        }
        pendingUntil.remove(uuid);

        Spec old = storage.get(uuid);
        if (old == null) {
            return RespecResult.NO_SPEC;
        }

        boolean admin = player.hasPermission("raskolclasses.admin");
        if (!admin) {
            if (!economy.available()) {
                return RespecResult.NO_ECONOMY;
            }
            int cost = respecCost(player);
            if (economy.balance(uuid) < cost) {
                return RespecResult.POOR;
            }
            if (!economy.withdraw(uuid, cost)) {
                return RespecResult.POOR;
            }
        }

        clearSpecFromLuckPerms(player, old);
        storage.remove(uuid);
        plugin.getSpecEffects().removeAttributes(player);
        return RespecResult.OK;
    }

    // --- LP-синхронизация ---

    /** Синхронизация с LP: добавляем permission-ноду для TAB. */
    private void syncToLuckPerms(Player player, Spec spec) {
        if (plugin.getServer().getPluginManager().getPlugin("LuckPerms") == null) {
            return;
        }
        try {
            LuckPerms luckPerms = LuckPermsProvider.get();
            User user = luckPerms.getUserManager().getUser(player.getUniqueId());
            if (user == null) {
                return;
            }
            user.data().add(Node.builder("raskolclasses.spec." + spec.id()).value(true).build());
            luckPerms.getUserManager().saveUser(user);
        } catch (Exception e) {
            plugin.getLogger().warning("Не удалось синхронизировать спеку с LP: " + e.getMessage());
        }
    }

    /** Респец: снимаем permission-ноду старой спеки. */
    private void clearSpecFromLuckPerms(Player player, Spec spec) {
        if (plugin.getServer().getPluginManager().getPlugin("LuckPerms") == null) {
            return;
        }
        try {
            LuckPerms luckPerms = LuckPermsProvider.get();
            User user = luckPerms.getUserManager().getUser(player.getUniqueId());
            if (user == null) {
                return;
            }
            user.data().remove(Node.builder("raskolclasses.spec." + spec.id()).value(true).build());
            luckPerms.getUserManager().saveUser(user);
        } catch (Exception e) {
            plugin.getLogger().warning("Не удалось снять спеку с LP: " + e.getMessage());
        }
    }
}
