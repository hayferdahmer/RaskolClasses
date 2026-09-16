// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.hook;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.spec.Spec;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Слушатель RaskolPassportChangeEvent из RaskolCore (1.9.2).
 *
 * Рефлексия по двум причинам:
 *  1) не тащим raskol-core в pom (нет публичного Maven-репо);
 *  2) на рантайме классы Core видны через softdepend в plugin.yml.
 *
 * На Reason.CLASS / FACTION → немедленный reconcile талантов/резистов/боевых
 * кэшей + снятие спеки при mismatch класса. Закрывает 5-секундное окно
 * эксплойта «сменил группу → 5 секунд старых гейтов/резистов/спеки» (TTL кэша Core).
 *
 * Регистрация через pluginManager.registerEvent(Class, Listener, Priority,
 * EventExecutor, Plugin) — позволяет слушать событие без compile-time
 * зависимости. Без Core слушатель молча отключается.
 *
 * 1.9.2-fix: generics — класс события приводится к Class<? extends Event>
 * через asSubclass(Event.class) после проверки isAssignableFrom, иначе
 * registerEvent не компилируется (Class<capture#1 of ?>).
 */
public final class PassportChangeListener implements Listener {

    private final RaskolClasses plugin;
    private final boolean active;
    private final Class<? extends Event> eventClass;
    private final Method getUuid;
    private final Method getReason;
    private final Method reasonName;

    public PassportChangeListener(RaskolClasses plugin) {
        this.plugin = plugin;
        Class<? extends Event> clazz = null;
        Method mUuid = null;
        Method mReason = null;
        Method mName = null;
        try {
            Class<?> raw = Class.forName("dev.raskol.core.event.RaskolPassportChangeEvent");
            if (Event.class.isAssignableFrom(raw)) {
                clazz = raw.asSubclass(Event.class);
            }
            mUuid = raw.getMethod("getUuid");
            mReason = raw.getMethod("getReason");
            Class<?> reasonEnum = Class.forName(
                    "dev.raskol.core.event.RaskolPassportChangeEvent$Reason");
            mName = reasonEnum.getMethod("name");
        } catch (Throwable t) {
            plugin.getLogger().info("PassportChangeListener: RaskolCore не найден — "
                    + "мгновенный reconcile на смену паспорта отключён");
        }
        this.active = clazz != null && mUuid != null && mReason != null && mName != null;
        this.eventClass = clazz;
        this.getUuid = mUuid;
        this.getReason = mReason;
        this.reasonName = mName;
    }

    public boolean isActive() {
        return active;
    }

    public Class<? extends Event> getEventClass() {
        return eventClass;
    }

    /**
     * Вызывается из EventExecutor. Читает uuid и reason через рефлексию,
     * на CLASS/FACTION запускает немедленный reconcile.
     */
    public void onEvent(Event event) {
        if (!active || eventClass == null || !eventClass.isInstance(event)) {
            return;
        }
        try {
            UUID uuid = (UUID) getUuid.invoke(event);
            Object reasonObj = getReason.invoke(event);
            String reason = (String) reasonName.invoke(reasonObj);
            if (uuid == null || reason == null) {
                return;
            }
            if (!"CLASS".equals(reason) && !"FACTION".equals(reason)) {
                return; // TOWN/NATION/REFRESH не требуют reconcile боевых гейтов
            }
            reconcileImmediate(uuid, reason);
        } catch (Throwable t) {
            plugin.getLogger().warning("PassportChangeListener: ошибка обработки события: "
                    + t.getMessage());
        }
    }

    private void reconcileImmediate(UUID uuid, String reason) {
        // Таланты: немедленный reconcile (снимает/вешает модификаторы source=talents)
        plugin.getTalentService().reconcile(uuid);

        // Резисты: пересборка спек-резистов (снимает старые, ставит новые)
        var player = plugin.getServer().getPlayer(uuid);
        if (player != null) {
            plugin.getSpecService().restorePassiveResists(player);
        }

        // Проверка mismatch класса у спеки: если CLASS-смена привела к другому классу,
        // спека автоматически сбрасывается (SpecService.getSpec сам это делает)
        if ("CLASS".equals(reason) && player != null) {
            Spec spec = plugin.getSpecService().getSpec(uuid);
            if (spec != null) {
                plugin.getLogger().info("PassportChange CLASS: reconcile для " + player.getName()
                        + " (спека " + spec.id() + " сохранена)");
            } else {
                plugin.getLogger().info("PassportChange CLASS: reconcile для " + player.getName()
                        + " (спека сброшена из-за mismatch класса)");
            }
        } else if (player != null) {
            plugin.getLogger().info("PassportChange FACTION: reconcile для " + player.getName()
                    + " (гейты/резисты обновлены)");
        }
    }

    /**
     * Регистрация слушателя. Вызывается из RaskolClasses.onEnable.
     * Без Core — тихий выход.
     */
    public void register() {
        if (!active || eventClass == null) {
            return;
        }
        plugin.getServer().getPluginManager().registerEvent(
                eventClass,
                this,
                EventPriority.MONITOR,
                (listener, event) -> {
                    if (listener instanceof PassportChangeListener pcl) {
                        pcl.onEvent(event);
                    }
                },
                plugin
        );
        plugin.getLogger().info("PassportChangeListener: зарегистрирован — "
                + "мгновенный reconcile на смену паспорта активен");
    }

    public void unregister() {
        if (active) {
            HandlerList.unregisterAll(this);
        }
    }
}
