// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.classsystem;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.config.RaskolConfig;
import dev.raskol.classes.resource.ResourceService;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.event.EventSubscription;
import net.luckperms.api.event.user.UserDataRecalculateEvent;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * LP-бэкенд определения класса: группа LuckPerms вида class_<NAME> → PlayerClass.
 * Конструктор бросает IllegalStateException/NoClassDefFoundError, если LuckPerms
 * не загружен — ClassProvider ловит это и живёт без бэкенда (sourceOf() = "off"/"core").
 *
 * Подписка на UserDataRecalculateEvent: при пересчёте данных игрока (смена группы)
 * инвалидируем кэш класса в ClassProvider и сбрасываем ресурс игрока, чтобы новый
 * класс начинал с чистой шкалы.
 *
 * 1.7.4.1 фикс 4 (сборка): вызов resourceService.reset(uuid) заменён на
 * resourceService.clear(uuid) — единственное публичное имя метода в ResourceService.
 */
public final class LuckPermsBackend {

    private final ClassProvider provider;
    private final RaskolClasses plugin;
    private final LuckPerms api;
    private final EventSubscription<UserDataRecalculateEvent> subscription;

    private RaskolConfig config;
    private ResourceService resourceService;

    public LuckPermsBackend(ClassProvider provider, RaskolClasses plugin) {
        this.provider = provider;
        this.plugin = plugin;
        this.api = LuckPermsProvider.get(); // бросит, если LP не загружен
        this.subscription = api.getEventBus().subscribe(
                plugin, UserDataRecalculateEvent.class, this::onRecalculate);
    }

    /** Конфиг-слой (совместимость с порядком инициализации ClassProvider). */
    public void setConfig(RaskolConfig config) {
        this.config = config;
    }

    /** Ресурс-сервис для сброса шкалы при смене класса. */
    public void setResourceService(ResourceService resourceService) {
        this.resourceService = resourceService;
    }

    /**
     * Класс игрока: primary-группа, затем наследованные группы;
     * первая группа вида class_<NAME> побеждает; иначе null.
     */
    public PlayerClass resolve(Player player) {
        if (api == null || player == null) {
            return null;
        }
        User user = api.getUserManager().getUser(player.getUniqueId());
        if (user == null) {
            return null;
        }
        PlayerClass pc = PlayerClass.fromLuckPermsGroup(user.getPrimaryGroup());
        if (pc != null) {
            return pc;
        }
        for (Group group : user.getInheritedGroups(user.getQueryOptions())) {
            pc = PlayerClass.fromLuckPermsGroup(group.getName());
            if (pc != null) {
                return pc;
            }
        }
        return null;
    }

    /**
     * Пересчёт данных LP (смена группы/класса): сбрасываем кэш класса и ресурс,
     * чтобы игрок не тащил шкалу старого класса в новый.
     */
    private void onRecalculate(UserDataRecalculateEvent event) {
        UUID uuid = event.getUser().getUniqueId();
        provider.invalidate(uuid);
        if (resourceService != null) {
            resourceService.clear(uuid); // 1.7.4.1: было reset(uuid) — метода нет
        }
    }

    /** Отписка от EventBus (onDisable плагина). */
    public void unsubscribe() {
        if (subscription != null) {
            subscription.close();
        }
    }
}
