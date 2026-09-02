// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.spec;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.classsystem.SkillLevelProvider;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Бизнес-логика спеков (1.4.0, Пакет 1).
 * Проверка уровня, выбор, синхронизация с LP (для TAB).
 */
public final class SpecService {

    private static final int REQUIRED_LEVEL = 40;

    private final RaskolClasses plugin;
    private final SpecStorage storage;
    private final SpecRegistry registry;

    public SpecService(RaskolClasses plugin, SpecStorage storage, SpecRegistry registry) {
        this.plugin = plugin;
        this.storage = storage;
        this.registry = registry;
    }

    /** Проверка: может ли игрок выбрать спеку? */
    public boolean canChoose(Player player) {
        PlayerClass pc = plugin.getClassProvider().getClassOf(player);
        if (pc == null) {
            return false;
        }
        if (storage.hasSpec(player.getUniqueId())) {
            return false; // уже выбрана
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
        player.sendMessage(Component.text("Специализация выбрана: ", NamedTextColor.GREEN)
                .append(Component.text(spec.displayName(), pc.getColor())));
        return true;
    }

    /** Получить выбранную спеку (null если не выбрана). */
    public Spec getSpec(UUID uuid) {
        return storage.get(uuid);
    }

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
            Node node = Node.builder("raskolclasses.spec." + spec.id()).value(true).build();
            user.data().add(node);
            luckPerms.getUserManager().saveUser(user);
        } catch (Exception e) {
            plugin.getLogger().warning("Не удалось синхронизировать спеку с LP: " + e.getMessage());
        }
    }
}
