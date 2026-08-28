// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.classsystem;

import dev.raskol.classes.config.RaskolConfig;
import dev.raskol.classes.resource.ResourceService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.event.EventSubscription;
import net.luckperms.api.event.node.NodeMutateEvent;
import net.luckperms.api.model.user.User;
import net.luckperms.api.model.user.UserManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Изолированный мост к LuckPerms API: класс грузится только при установленном
 * LuckPerms (проверка в ClassProvider), иначе NoClassDefFoundError не коснётся плагина.
 */
public final class LuckPermsBackend {

    private final LuckPerms luckPerms;
    private final ClassProvider classProvider;
    private final Plugin plugin;
    private EventSubscription<NodeMutateEvent> subscription;
    private ResourceService resourceService;
    private RaskolConfig config;

    /** Флаг «раз в сессию»: игроки, которым уже показан титр принятия. */
    private final Set<UUID> greetedInSession = ConcurrentHashMap.newKeySet();

    public LuckPermsBackend(ClassProvider classProvider, Plugin plugin) {
        this.classProvider = classProvider;
        this.plugin = plugin;
        this.luckPerms = LuckPermsProvider.get();
        this.subscription = luckPerms.getEventBus()
                .subscribe(NodeMutateEvent.class, this::onNodeMutate);
    }

    public void setResourceService(ResourceService resourceService) {
        this.resourceService = resourceService;
    }

    public void setConfig(RaskolConfig config) {
        this.config = config;
    }

    /** Primary group → класс; группа не class_* → null. */
    public PlayerClass resolve(Player player) {
        UserManager userManager = luckPerms.getUserManager();
        User user = userManager.getUser(player.getUniqueId());
        if (user == null) {
            user = userManager.loadUser(player.getUniqueId()).join();
        }
        if (user == null) {
            return null;
        }
        return PlayerClass.fromLuckPermsGroup(user.getPrimaryGroup());
    }

    /**
     * При смене primary group: запоминаем старый класс, инвалидируем кэш,
     * сбрасываем ресурс при реальной смене и показываем титр принятия.
     */
    private void onNodeMutate(NodeMutateEvent event) {
        if (!(event.getTarget() instanceof User user)) {
            return;
        }
        UUID uuid = user.getUniqueId();

        PlayerClass oldClass = classProvider.getCachedClass(uuid);
        classProvider.invalidate(uuid);

        if (resourceService == null) {
            return;
        }
        Player online = Bukkit.getPlayer(uuid);
        if (online == null) {
            return;
        }
        PlayerClass newClass = classProvider.getClassOf(online);

        if (oldClass != null && newClass != null && oldClass != newClass) {
            resourceService.reset(uuid);
        }

        // V1: титр при первом принятии класса (oldClass == null) ИЛИ при смене класса.
        if (newClass != null && (oldClass == null || oldClass != newClass)
                && greetedInSession.add(uuid)) {
            showClassAcceptTitle(online, newClass);
        }
    }

    /**
     * V1: титр «⚔ Воин» градиентом темы + партиклы + звук.
     * Обёрнуто в runTask: NodeMutateEvent может прийти с асинхронного потока
     * (LP Web-редактор), а партиклы/титры безопасны только с main-thread.
     */
    private void showClassAcceptTitle(Player player, PlayerClass pc) {
        if (config == null || !config.isClassAcceptEnabled()) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            RaskolConfig.ClassTheme theme = config.themeOf(pc);
            String titleText = String.format("<gradient:%s:%s>%s %s</gradient>",
                    theme.primary().asHexString(), theme.secondary().asHexString(),
                    theme.symbol(), pc.getDisplayName());
            Component title = MiniMessage.miniMessage().deserialize(titleText);
            Component subtitle = Component.text(config.classAcceptSubtitle(),
                    TextColor.color(0xAAAAAA));

            player.showTitle(Title.title(title, subtitle, Title.Times.times(
                    Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofMillis(500))));

            Location loc = player.getLocation().add(0, 1, 0);
            player.getWorld().spawnParticle(theme.particle(), loc, 30, 0.8, 0.8, 0.8, 0.05);
            player.playSound(player.getLocation(), theme.sound(), 1.0f, 1.0f);
        });
    }

    /** Отписка в onDisable — без утечки подписок на event bus. */
    public void unsubscribe() {
        if (subscription != null) {
            subscription.close();
            subscription = null;
        }
    }
}
