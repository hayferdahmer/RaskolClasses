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
 * Бизнес-логика спеков (1.4.0 → 1.11.4).
 * 1.11.4 (P4e): фасад. Резисты спек — в SpecPassives (генерик, F7),
 * экономика/арм-окно — в SpecEconomy, формулы — в SpecMath.
 * Публичный API сохранён: ClassBook/SpecListener/RaskolClasses/FoliantService не меняются.
 * Legacy-dead: startSpecNotifyTask/clearNotifyState оставлены как заглушки
 * (активки спек удалены в 1.7.5; задача никогда не стартовала).
 */
public final class SpecService {

    private static final int REQUIRED_LEVEL = 40;

    public enum RespecResult { OK, NO_SPEC, NO_ECONOMY, POOR, NOT_PENDING }

    private final RaskolClasses plugin;
    private final SpecStorage storage;
    private final SpecRegistry registry;
    private final SpecPassives passives;
    private final SpecEconomy economyPart;
    private final Map<UUID, Map<String, Long>> notifyPrev = new ConcurrentHashMap<>();

    public SpecService(RaskolClasses plugin, SpecStorage storage, SpecRegistry registry) {
        this.plugin = plugin;
        this.storage = storage;
        this.registry = registry;
        this.passives = new SpecPassives(plugin);
        this.economyPart = new SpecEconomy(plugin, new EconomyHook(plugin));
    }

    public EconomyHook economy() {
        return economyPart.economy();
    }

    public SpecPassives passives() {
        return passives;
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
        passives.applySpecResists(player, spec); // 1.11.4: генерик (F7)
        player.sendMessage(Component.text("Специализация выбрана: ", NamedTextColor.GREEN)
                .append(Component.text(spec.displayName(), pc.getColor())));
        return true;
    }

    /**
     * Выбранная спека с валидацией класса (1.5.1): после админ-смены класса
     * спека отключается сама (storage + LP-нода + атрибуты + резисты).
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
                "Твоя специализация сброшена: класс изменён. Выбери новую: /rc menu",
                NamedTextColor.YELLOW));
        storage.remove(uuid);
        clearSpecFromLuckPerms(player, spec);
        plugin.getSpecEffects().removeAttributes(player);
        passives.removeAllSpecResists(uuid);
        return null;
    }

    /** Восстановление permanent-резистов спек-пассивок на входе. */
    public void restorePassiveResists(Player player) {
        passives.restorePassiveResists(player);
    }

    /** Сверка спек-модификаторов по онлайну (теперь self-scheduled в SpecPassives). */
    public void reconcilePassiveResists() {
        passives.reconcilePassiveResists();
    }

    // --- Платный респец (Пакет 3, 1.4.0) ---

    public int respecCost(Player player) {
        return economyPart.respecCost(player);
    }

    public void requestRespec(Player player) {
        economyPart.requestRespec(player);
    }

    public RespecResult confirmRespec(Player player) {
        UUID uuid = player.getUniqueId();
        if (!economyPart.isPending(uuid)) {
            return RespecResult.NOT_PENDING;
        }
        economyPart.clearPending(uuid);

        Spec old = storage.get(uuid);
        if (old == null) {
            return RespecResult.NO_SPEC;
        }

        RespecResult payFail = economyPart.pay(player);
        if (payFail != null) {
            return payFail;
        }

        clearSpecFromLuckPerms(player, old);
        storage.remove(uuid);
        plugin.getSpecEffects().removeAttributes(player);
        passives.removeAllSpecResists(uuid);
        notifyPrev.remove(uuid);
        int stripped = plugin.getSpecToken().stripScrolls(player, old);
        if (stripped > 0) {
            player.sendMessage(Component.text("Свитки старого пути сгорели: " + stripped,
                    NamedTextColor.GRAY));
        }
        return RespecResult.OK;
    }

    // --- Legacy-dead (1.5.1 ready-notify спек-абилок; активки удалены в 1.7.5) ---

    public BukkitTask startSpecNotifyTask() {
        return null; // задача не стартует: спек-активок нет с 1.7.5
    }

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
