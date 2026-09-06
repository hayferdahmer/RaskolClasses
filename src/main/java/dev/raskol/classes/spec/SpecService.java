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
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Бизнес-логика спеков (1.4.0 + 1.5.1).
 * FIX 1.5.1:
 *  - getSpec(uuid) теперь валидирует спеку против текущего класса:
 *    после админ-смены класса старая спека отключается сама (storage + LP-нода),
 *    игрок получает одноразовое сообщение;
 *  - респец сжигает свитки старой спеки из инвентаря;
 *  - ready-notify для спек-активки (actionbar + звук), независимо от
 *    CooldownManager; гейт performance.ready-notify.spec-enabled (default true).
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
    /** previous remaining для ready-notify спек-активки. */
    private final Map<UUID, Map<String, Long>> notifyPrev = new ConcurrentHashMap<>();

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
        if (player.hasPermission("raskolclasses.admin")) {
            return true;
        }
        int level = plugin.getSkillLevels().getLevel(player.getUniqueId(), pc.profileSkillName());
        if (level == SkillLevelProvider.NO_SKILL_SYSTEM) {
            return true;
        }
        return level >= REQUIRED_LEVEL;
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
        plugin.getSpecEffects().applyAttributes(player, spec);
        player.sendMessage(Component.text("Специализация выбрана: ", NamedTextColor.GREEN)
                .append(Component.text(spec.displayName(), pc.getColor())));
        return true;
    }

    /**
     * Выбранная спека. FIX 1.5.1: если класс игрока сменился (админ/LP/Core)
     * и не совпадает с классом спеки — спека отключается: storage чистится,
     * LP-нода снимается, игрок получает одноразовое сообщение.
     * Оффлайн-игроки не валидируются (пассивки оффлайн и не работают).
     */
    public Spec getSpec(UUID uuid) {
        Spec spec = storage.get(uuid);
        if (spec == null) {
            return null;
        }
        Player player = plugin.getServer().getPlayer(uuid);
        if (player == null) {
            return spec;
        }
        PlayerClass pc = plugin.getClassProvider().getClassOf(player);
        if (pc == null || spec.playerClass() == pc) {
            return spec;
        }
        plugin.getLogger().warning("RaskolClasses: спека " + spec.id() + " игрока "
                + player.getName() + " не соответствует классу " + pc.name()
                + " — спека сброшена");
        player.sendMessage(Component.text(
                "Твоя специализация сброшена: класс изменён. Выбери новую: /rc spec",
                NamedTextColor.YELLOW));
        storage.remove(uuid);
        clearSpecFromLuckPerms(player, spec);
        plugin.getSpecEffects().removeAttributes(player);
        return null;
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
        notifyPrev.remove(uuid);
        // FIX 1.5.1: свитки старого пути сгорают при отречении
        int stripped = plugin.getSpecToken().stripScrolls(player, old);
        if (stripped > 0) {
            player.sendMessage(Component.text("Свитки старого пути сгорели: " + stripped,
                    NamedTextColor.GRAY));
        }
        return RespecResult.OK;
    }

    // --- FIX 1.5.1: ready-notify спек-активки ---

    /**
     * Таск раз в 20 тиков: когда кулдаун спек-активки (>= min-cooldown-seconds)
     * истекает — actionbar + звук. Не трогает CooldownManager.
     * Отключается: performance.ready-notify.spec-enabled: false.
     */
    public BukkitTask startSpecNotifyTask() {
        return plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!plugin.getRaskolConfig().isReadyNotifyEnabled()) {
                return;
            }
            if (!plugin.getConfig().getBoolean("performance.ready-notify.spec-enabled", true)) {
                return;
            }
            long minMillis = plugin.getRaskolConfig().readyNotifyMinCooldownSeconds() * 1000L;
            String soundKey = plugin.getRaskolConfig().readyNotifySoundKey();
            String template = plugin.getRaskolConfig().readyNotifyMessage();
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                UUID uuid = player.getUniqueId();
                Spec spec = getSpec(uuid);
                Map<String, Long> prev = notifyPrev.computeIfAbsent(uuid, k -> new HashMap<>());
                if (spec == null) {
                    prev.clear();
                    continue;
                }
                String cdId = "spec_" + spec.id();
                prev.keySet().removeIf(k -> !k.equals(cdId)); // респец/смена спеки
                long remaining = plugin.getCooldowns().getRemainingMillis(uuid, cdId);
                Long before = prev.get(cdId);
                if (before != null && before > 0 && remaining <= 0 && before >= minMillis) {
                    SpecRegistry.SpecDef def = registry.get(spec);
                    String name = def != null ? def.activeDescription() : spec.displayName();
                    Sound sound = plugin.getFx().resolveSound(soundKey);
                    if (sound != null) {
                        plugin.getFx().playSound(player.getLocation(), sound, 0.6f, 1.0f);
                    }
                    player.sendActionBar(Component.text(
                            template.replace("{ability}", name), NamedTextColor.GREEN));
                }
                prev.put(cdId, remaining);
            }
        }, 20L, 20L);
    }

    /** Чистка состояния notify на выход игрока. */
    public void clearNotifyState(UUID uuid) {
        notifyPrev.remove(uuid);
    }

    // --- LP-синхронизация ---

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
