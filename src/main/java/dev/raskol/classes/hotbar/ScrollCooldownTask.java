// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.hotbar;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.ability.AbilityDef;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.install.InstallationType;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Индикация перезарядки на свитках (1.5.6 → 1.9.0-fix9, v9).
 *
 * РЕЖИМЫ (hotbar-bind.cooldown-mode):
 *  - lore    : как раньше — строка «⏳ N с» на свитке обновляется каждую секунду,
 *              включая предмет в руке (рука дёргается раз в секунду);
 *  - bossbar : живой счётчик босс-баром над хотбаром; свиток в руке не трогается
 *              вообще (рука НЕ дёргается); цифры на свитке — когда он не в руке;
 *  - both    : босс-бар живой + цифры на свитке, когда он НЕ в руке; в руке свиток
 *              обновляется только 2 раза за каст (старт и готовность).
 *
 * Ванильное ограничение: живое число на предмете В РУКЕ невозможно без
 * пересылки предмета клиенту (= дёргание модели). Режимы дают выбор компромисса.
 *
 * Маркер деплоя: при старте печатает «ScrollCooldownTask v9 active (mode=…)» —
 * по этой строке в логе старта видно, какой код реально стоит на сервере.
 */
public final class ScrollCooldownTask {

    private static final String CD_PREFIX = "⏳";

    private final RaskolClasses plugin;
    /** playerUUID → (slot → последние отображённые секунды). */
    private final Map<UUID, Map<Integer, Long>> shown = new ConcurrentHashMap<>();
    /** playerUUID → босс-бар кулдауна свитка в руке. */
    private final Map<UUID, BossBar> bars = new ConcurrentHashMap<>();

    public ScrollCooldownTask(RaskolClasses plugin) {
        this.plugin = plugin;
    }

    public BukkitTask start() {
        plugin.getLogger().info("ScrollCooldownTask v9 active (mode=" + mode() + ")");
        return plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 10L, 10L);
    }

    private String mode() {
        String m = plugin.getConfig().getString("hotbar-bind.cooldown-mode", "both");
        return m == null ? "both" : m.toLowerCase(Locale.ROOT);
    }

    private void tick() {
        String mode = mode();
        boolean loreOn = !mode.equals("bossbar");
        boolean barOn = !mode.equals("lore");

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            Map<Integer, Long> slots = shown.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());
            PlayerClass pc = plugin.getClassProvider().getClassOf(player);
            var inv = player.getInventory();
            int held = inv.getHeldItemSlot();

            long heldRemaining = -1L;
            long heldTotal = -1L;
            String heldName = null;

            for (int slot = 0; slot < 36; slot++) {
                ItemStack item = inv.getItem(slot);
                if (item == null) {
                    continue;
                }
                long remaining = -1L;
                long total = -1L;
                String name = null;
                String abilityId = plugin.getTokens().readId(item);
                if (abilityId != null && pc != null) {
                    AbilityDef def = plugin.getAbilities().findById(pc, abilityId);
                    if (def != null) {
                        remaining = plugin.getCooldowns().getRemainingMillis(uuid, abilityId);
                        total = def.cooldownMillis();
                        name = def.displayName();
                    }
                } else {
                    InstallationType type = plugin.getInstallToken().readType(item);
                    if (type != null) {
                        remaining = plugin.getInstallations().placeCooldownRemaining(uuid, type);
                        total = plugin.getInstallations().placeCooldownTotalMillis(type);
                        name = type.displayName();
                    }
                }
                if (remaining < 0L || total <= 0L) {
                    continue;
                }
                boolean heldSlot = slot == held;
                if (heldSlot) {
                    heldRemaining = remaining;
                    heldTotal = total;
                    heldName = name;
                }

                long seconds = remaining > 0L ? (remaining / 1000L) + 1L : 0L;
                Long last = slots.get(slot);
                long lastVal = last == null ? -1L : last;
                if (lastVal == seconds) {
                    continue;
                }
                slots.put(slot, seconds);

                boolean updateLore;
                if (!heldSlot) {
                    updateLore = loreOn;
                } else {
                    updateLore = mode.equals("lore") || seconds == 0L || lastVal == -1L;
                }
                if (updateLore) {
                    applyLore(item, seconds);
                    inv.setItem(slot, item);
                }
            }

            // босс-бар для свитка в руке
            if (barOn) {
                BossBar bar = bars.get(uuid);
                if (heldRemaining > 0L && heldName != null && heldTotal > 0L) {
                    long secs = heldRemaining / 1000L + 1L;
                    float progress = (float) Math.max(0.0, Math.min(1.0, (double) heldRemaining / heldTotal));
                    Component title = Component.text(CD_PREFIX + " " + heldName + " — " + secs + " с",
                            NamedTextColor.AQUA);
                    if (bar == null) {
                        // 1.9.0-fix10: BossBar.Overlay.PROGRESS (не ProgressStyle)
                        bar = BossBar.bossBar(title, progress, BossBar.Color.BLUE,
                                BossBar.Overlay.PROGRESS);
                        bars.put(uuid, bar);
                        player.showBossBar(bar);
                    } else {
                        bar.name(title);
                        bar.progress(progress);
                    }
                } else if (bar != null) {
                    player.hideBossBar(bar);
                    bars.remove(uuid);
                }
            } else {
                BossBar bar = bars.remove(uuid);
                if (bar != null) {
                    player.hideBossBar(bar);
                }
            }

            slots.keySet().removeIf(slot -> {
                ItemStack it = inv.getItem(slot);
                if (it == null) {
                    return true;
                }
                return plugin.getTokens().readId(it) == null
                        && plugin.getInstallToken().readType(it) == null;
            });
        }

        shown.keySet().removeIf(uuid -> plugin.getServer().getPlayer(uuid) == null);
        bars.keySet().removeIf(uuid -> plugin.getServer().getPlayer(uuid) == null);
    }

    private void applyLore(ItemStack item, long seconds) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        boolean had = lore.removeIf(ScrollCooldownTask::isCooldownLine);
        if (seconds > 0) {
            lore.add(Component.text(CD_PREFIX + " Перезарядка: " + seconds + " с", NamedTextColor.RED));
            meta.lore(lore);
            item.setItemMeta(meta);
            return;
        }
        if (had) {
            meta.lore(lore);
            item.setItemMeta(meta);
        }
    }

    private static boolean isCooldownLine(Component line) {
        String plain = PlainTextComponentSerializer.plainText().serialize(line);
        return plain.startsWith(CD_PREFIX);
    }
}
