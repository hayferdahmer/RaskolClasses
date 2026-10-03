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
 * На Reason.CLASS / FACTION → немедленный reconcile spec2/резистов/боевых
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
 * 1.14.0 (Б8.2-fix): legacy TalentService/SpecService удалены;
 *         reconcile идёт через Spec2Service (passive-резисты автоматически
 *         пересчитываются внутри Spec2EffectsApplier при reconcile),
 *         спека читается через Spec.fromId(spec2Service.mainSpec).
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

    /**
     * 1.14.0 (Б8.2-fix): немедленный reconcile через spec2-слой.
     *  - spec2Service.reconcile() пересчитывает все атрибутные/резист-бонусы деревьев;
     *  - Spec2EffectsApplier (зарегистрирован Spec2RoleListener) автоматически
     *    подтягивает passive-резисты спеки — отдельный restorePassiveResists не нужен;
     *  - при CLASS-смене проверяем mismatch класса у основной спеки.
     */
    private void reconcileImmediate(UUID uuid, String reason) {
        // Spec2: полный reconcile (снимает/вешает модификаторы source=spec2,
        // Spec2EffectsApplier в тике подтянет passive-резисты спеки)
        if (plugin.getSpec2Service() != null) {
            plugin.getSpec2Service().reconcile(uuid);
        }

        var player = plugin.getServer().getPlayer(uuid);

        // Проверка mismatch класса у спеки: при CLASS-смене, если основная спека
        // принадлежит другому классу — сбрасываем (mainSpec = null).
        if ("CLASS".equals(reason) && player != null && plugin.getSpec2Service() != null) {
            String main = plugin.getSpec2Service().mainSpec(uuid);
            Spec spec = Spec.fromId(main);
            if (spec != null) {
                dev.raskol.classes.classsystem.PlayerClass current =
                        plugin.getClassProvider().getClassOf(player);
                if (current != null && spec.playerClass() != current) {
                    plugin.getSpec2Service().storage().setMain(uuid, null);
                    plugin.getSpec2Service().reconcile(uuid);
                    plugin.getLogger().info("PassportChange CLASS: reconcile для " + player.getName()
                            + " (спека " + spec.id() + " сброшена из-за mismatch класса)");
                } else {
                    plugin.getLogger().info("PassportChange CLASS: reconcile для " + player.getName()
                            + " (спека " + spec.id() + " сохранена)");
                }
            } else {
                plugin.getLogger().info("PassportChange CLASS: reconcile для " + player.getName()
                        + " (спека не выбрана)");
            }
        } else if (player != null) {
            plugin.getLogger().info("PassportChange FACTION: reconcile для " + player.getName()
                    + " (гейты/резисты обновлены через spec2)");
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
