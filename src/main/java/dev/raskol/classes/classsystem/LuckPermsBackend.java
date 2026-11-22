// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.classsystem;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Player;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.resource.ResourceService;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.event.EventSubscription;
import net.luckperms.api.event.user.UserDataRecalculateEvent;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.InheritanceNode;
import net.luckperms.api.node.types.WeightNode;

/**
 * LP-бэкенд определения класса: группа LuckPerms вида class_<NAME> → PlayerClass.
 * Конструктор бросает IllegalStateException/NoClassDefFoundError, если LuckPerms
 * не загружен — ClassProvider ловит это и живёт без бэкенда.
 *
 * Подписка на UserDataRecalculateEvent: инвалидация кэша класса всегда;
 * сброс ресурса — ТОЛЬКО при фактической смене класса.
 *
 * 1.15.5 (аудит 2.6-78): ранее resourceService.clear(uuid) вызывался на ЛЮБОЙ
 * пересчёт данных LP (vault-синхронизация, выдача несвязанной группы, временная
 * нода) — шкала ресурса обнулялась посреди боя. Теперь бэкенд держит lastClass
 * и сбрасывает ресурс только при prev != now. Оффлайн-смена класса помечается
 * resetOnJoin: ResourceService обнулит шкалу при входе вместо восстановления старой.
 */
public final class LuckPermsBackend {

    private final ClassProvider provider;
    private final RaskolClasses plugin;
    private final LuckPerms api;
    private final EventSubscription<UserDataRecalculateEvent> subscription;

    private record ClassState(PlayerClass playerClass) {}

    private final Map<UUID, ClassState> lastClass = new ConcurrentHashMap<>();
    private final Set<UUID> conflictWarnings = ConcurrentHashMap.newKeySet();

    private ResourceService resourceService;

    public LuckPermsBackend(ClassProvider provider, RaskolClasses plugin) {
        this.provider = provider;
        this.plugin = plugin;
        this.api = LuckPermsProvider.get(); // бросит, если LP не загружен
        this.subscription = api.getEventBus().subscribe(plugin, UserDataRecalculateEvent.class, this::onRecalculate);
    }

    /** Ресурс-сервис для сброса шкалы при смене класса. */
    public void setResourceService(ResourceService resourceService) {
        this.resourceService = resourceService;
    }

    /**
     * Класс определяется только primary-группой class_<NAME>.
     * Несколько эффективных class_* групп или class_* не в primary — конфликт,
     * а не повод выбирать случайную наследуемую группу.
     */
    public PlayerClass resolve(Player player) {
        return resolveDetailed(player).playerClass();
    }

    public ClassGroupResolver.Resolution resolveDetailed(Player player) {
        if (api == null || player == null) {
            return ClassGroupResolver.resolve(null, List.of());
        }
        return resolveUser(api.getUserManager().getUser(player.getUniqueId()));
    }

    /** Резолв по LP-User; работает и для офлайн-событий. */
    private ClassGroupResolver.Resolution resolveUser(User user) {
        if (user == null) {
            return ClassGroupResolver.resolve(null, List.of());
        }
        List<String> inheritedNames = user.getInheritedGroups(user.getQueryOptions()).stream()
                .map(Group::getName)
                .toList();
        return ClassGroupResolver.resolve(user.getPrimaryGroup(), inheritedNames);
    }

    /**
     * Атомарно заменяет единственную class_* группу и выставляет её primary.
     * При невозможности подтвердить итоговую primary-группу исходные узлы
     * восстанавливаются, а переход отклоняется.
     */
    public boolean replaceClassGroup(UUID uuid, PlayerClass targetClass) {
        if (api == null || uuid == null || targetClass == null) {
            return false;
        }

        String targetGroup = "class_" + targetClass.name().toLowerCase(java.util.Locale.ROOT);
        User user = null;
        List<InheritanceNode> previousClassNodes = new ArrayList<>();
        String previousPrimary = null;
        boolean snapshotCaptured = false;
        try {
            ensureClassGroup(targetGroup);
            user = api.getUserManager().loadUser(uuid).join();
            if (user == null) {
                return false;
            }

            previousPrimary = user.getPrimaryGroup();
            for (InheritanceNode node : user.getNodes(NodeType.INHERITANCE)) {
                if (isClassGroup(node.getGroupName())) {
                    previousClassNodes.add(node);
                }
            }
            snapshotCaptured = true;

            for (InheritanceNode node : previousClassNodes) {
                user.data().remove(node);
            }
            user.data().add(InheritanceNode.builder(targetGroup).build());

            if (!user.setPrimaryGroup(targetGroup).wasSuccessful()) {
                restoreClassGroups(user, previousClassNodes, previousPrimary);
                return false;
            }

            api.getUserManager().saveUser(user).join();
            ClassGroupResolver.Resolution resolution = resolveUser(user);
            if (resolution.status() != ClassGroupResolver.Status.RESOLVED || resolution.playerClass() != targetClass) {
                restoreClassGroups(user, previousClassNodes, previousPrimary);
                return false;
            }

            conflictWarnings.remove(uuid);
            provider.invalidate(uuid);
            return true;
        } catch (Exception exception) {
            if (user != null && snapshotCaptured) {
                restoreClassGroups(user, previousClassNodes, previousPrimary);
            }
            plugin.getLogger()
                    .warning("Не удалось заменить class-группу игрока " + uuid + ": " + exception.getMessage());
            return false;
        }
    }

    private void ensureClassGroup(String groupName) {
        Group group = api.getGroupManager().getGroup(groupName);
        if (group == null) {
            group = api.getGroupManager().createAndLoadGroup(groupName).join();
        }

        int requiredWeight = 15;
        for (PlayerClass playerClass : PlayerClass.values()) {
            String otherGroupName = "class_" + playerClass.name().toLowerCase(java.util.Locale.ROOT);
            if (groupName.equalsIgnoreCase(otherGroupName)) {
                continue;
            }
            Group existing = api.getGroupManager().getGroup(otherGroupName);
            if (existing != null && existing.getWeight().isPresent()) {
                requiredWeight = Math.max(requiredWeight, existing.getWeight().getAsInt());
            }
        }

        if (group.getWeight().isEmpty() || group.getWeight().getAsInt() < requiredWeight) {
            for (WeightNode node : group.getNodes(NodeType.WEIGHT)) {
                group.data().remove(node);
            }
            group.data().add(WeightNode.builder(requiredWeight).build());
            api.getGroupManager().saveGroup(group).join();
        }
    }

    private static boolean isClassGroup(String groupName) {
        return groupName != null && groupName.toLowerCase(java.util.Locale.ROOT).startsWith("class_");
    }

    private void restoreClassGroups(User user, List<InheritanceNode> previousClassNodes, String previousPrimary) {
        try {
            for (InheritanceNode node : user.getNodes(NodeType.INHERITANCE)) {
                if (isClassGroup(node.getGroupName())) {
                    user.data().remove(node);
                }
            }
            for (InheritanceNode node : previousClassNodes) {
                user.data().add(node);
            }
            if (previousPrimary != null && !previousPrimary.isBlank()) {
                user.setPrimaryGroup(previousPrimary);
            }
            api.getUserManager().saveUser(user).join();
            provider.invalidate(user.getUniqueId());
        } catch (Exception exception) {
            plugin.getLogger()
                    .severe("Не удалось откатить class-группы игрока " + user.getUniqueId() + ": "
                            + exception.getMessage());
        }
    }

    /**
     * Пересчёт данных LP: инвалидируем кэш класса всегда; ресурс сбрасываем
     * ТОЛЬКО при фактической смене класса (prev != now). Первый sighting игрока
     * (prev == null) сбросом не считается.
     */
    private void onRecalculate(UserDataRecalculateEvent event) {
        UUID uuid = event.getUser().getUniqueId();
        ClassGroupResolver.Resolution resolution = resolveUser(event.getUser());
        PlayerClass now = resolution.playerClass();
        if (resolution.status() == ClassGroupResolver.Status.CONFLICT) {
            if (conflictWarnings.add(uuid)) {
                plugin.getLogger()
                        .warning("Конфликт class-групп LuckPerms у "
                                + uuid
                                + ": "
                                + resolution.classGroups()
                                + "; класс не назначен, требуется оставить одну primary-группу class_<NAME>.");
            }
        } else {
            conflictWarnings.remove(uuid);
        }
        ClassState previousState = lastClass.put(uuid, new ClassState(now));
        provider.invalidate(uuid);
        boolean firstSighting = previousState == null;
        boolean classChanged = firstSighting || previousState.playerClass() != now;
        if (!classChanged) {
            return;
        }

        Player online = plugin.getServer().getPlayer(uuid);
        if (online != null) {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (plugin.isEnabled()
                        && plugin.getServer().getPlayer(uuid) != null
                        && plugin.getSpec2Service() != null) {
                    plugin.getSpec2Service().reconcile(uuid);
                }
            });
        }

        // Первое состояние нужно для reconcile, но не считается сменой класса
        // и не должно сбрасывать ресурс игрока.
        if (firstSighting || resourceService == null) {
            return;
        }
        if (online != null) {
            resourceService.clear(uuid);
        } else {
            resourceService.markResetOnJoin(uuid);
        }
    }

    /** Удаляет временное состояние игрока при выходе. */
    public void forget(UUID uuid) {
        lastClass.remove(uuid);
        conflictWarnings.remove(uuid);
    }

    /** Отписка от EventBus (onDisable плагина). */
    public void unsubscribe() {
        if (subscription != null) {
            subscription.close();
        }
        lastClass.clear();
        conflictWarnings.clear();
    }
}
