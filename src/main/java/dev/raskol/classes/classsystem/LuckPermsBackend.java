// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.classsystem;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.resource.ResourceService;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.event.EventSubscription;
import net.luckperms.api.event.user.UserDataRecalculateEvent;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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
    private final Map<UUID, PlayerClass> lastClass = new ConcurrentHashMap<>();

    private ResourceService resourceService;

    public LuckPermsBackend(ClassProvider provider, RaskolClasses plugin) {
        this.provider = provider;
        this.plugin = plugin;
        this.api = LuckPermsProvider.get(); // бросит, если LP не загружен
        this.subscription = api.getEventBus().subscribe(
                plugin, UserDataRecalculateEvent.class, this::onRecalculate);
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
        return resolveUser(api.getUserManager().getUser(player.getUniqueId()));
    }

    /** Резолв по LP-User (работает и для оффлайн-событий). */
    private PlayerClass resolveUser(User user) {
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
     * Пересчёт данных LP: инвалидируем кэш класса всегда; ресурс сбрасываем
     * ТОЛЬКО при фактической смене класса (prev != now). Первый sighting игрока
     * (prev == null) сбросом не считается.
     */
    private void onRecalculate(UserDataRecalculateEvent event) {
        UUID uuid = event.getUser().getUniqueId();
        PlayerClass now = resolveUser(event.getUser());
        PlayerClass prev = lastClass.put(uuid, now);
        provider.invalidate(uuid);
        if (prev == null || prev == now) {
            return;
        }
        if (resourceService == null) {
            return;
        }
        Player online = plugin.getServer().getPlayer(uuid);
        if (online != null) {
            resourceService.clear(uuid);
        } else {
            resourceService.markResetOnJoin(uuid);
        }
    }

    /** Отписка от EventBus (onDisable плагина). */
    public void unsubscribe() {
        if (subscription != null) {
            subscription.close();
        }
    }
}
