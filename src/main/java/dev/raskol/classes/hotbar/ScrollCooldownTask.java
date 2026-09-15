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
 * Индикация перезарядки на свитках (1.5.6 → 1.9.0-fix10, v10).
 *
 * РЕЖИМЫ (hotbar-bind.cooldown-mode), дефолт = legacy (выбор владельца):
 *  - legacy  : ТО САМОЕ «КАК РАНЬШЕ». Мутація меты живого ItemStack БЕЗ пакета
 *              set slot: рука НЕ дёргается никогда; строка «⏳ N с» видна на свитке
 *              при открытии инвентаря (E) и ресинке клиента. Босс-бара нет.
 *  - lore    : строка «⏳ N с» обновляется пакетом каждую секунду везде,
 *              включая предмет в руке (рука дёргается раз в секунду).
 *  - bossbar : живой счётчик босс-баром над хотбаром; свиток в руке не трогается;
 *              цифры на свитке — когда он не в руке.
 *  - both    : босс-бар живой + цифры на свитке, когда он НЕ в руке; в руке свиток
 *              обновляется только 2 раза за каст (старт и готовность).
 *
 * Техническая база legacy: CraftInventoryPlayer.getItem отдаёт write-through mirror —
 * setItemMeta на нём пишет в серверное состояние без пакета клиенту, поэтому
 * дёргания нет, а при открытии окна инвентаря клиент забирает актуальную лору.
 *
 * Маркер деплоя: при старте печатает «ScrollCooldownTask v10 active (mode=…)».
 */
public final class ScrollCooldownTask {

    private static final String CD_PREFIX = "⏳";

    private final RaskolClasses plugin;
    /** playerUUID → (slot → последние отображённые секунды). */
    private final Map<UUID, Map<Integer, Long>> shown = new ConcurrentHashMap<>();
    /** playerUUID → босс-бар кулдауна свитка в руке (только режимы bossbar/both). */
    private final Map<UUID, BossBar> bars = new ConcurrentHashMap<>();

    public ScrollCooldownTask(RaskolClasses plugin) {
        this.plugin = plugin;
    }

    public BukkitTask start() {
        plugin.getLogger().info("ScrollCooldownTask v10 active (mode=" + mode() + ")");
        return plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 10L, 10L);
    }

    private String mode() {
        String m = plugin.getConfig().getString("hotbar-bind.cooldown-mode", "legacy");
        return m == null ? "legacy" : m.toLowerCase(Locale.ROOT);
    }

    private void tick() {
        String mode = mode();
        boolean barOn = mode.equals("bossbar") || mode.equals("both");

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

                switch (mode) {
                    case "legacy" ->
                        // мутация меты без пакета: рука не дёргается, клиент заберёт при ресинке
                            applyLore(item, seconds);
                    case "lore" -> {
                        applyLore(item, seconds);
                        inv.setItem(slot, item);
                    }
                    case "bossbar" -> {
                        if (!heldSlot) {
                            applyLore(item, seconds);
                            inv.setItem(slot, item);
                        }
                    }
                    case "both" -> {
                        if (!heldSlot || seconds == 0L || lastVal == -1L) {
                            applyLore(item, seconds);
                            inv.setItem(slot, item);
                        }
                    }
                    default -> applyLore(item, seconds);
                }
            }

            // босс-бар только в режимах bossbar/both
            if (barOn) {
                BossBar bar = bars.get(uuid);
                if (heldRemaining > 0L && heldName != null && heldTotal > 0L) {
                    long secs = heldRemaining / 1000L + 1L;
                    float progress = (float) Math.max(0.0, Math.min(1.0, (double) heldRemaining / heldTotal));
                    Component title = Component.text(CD_PREFIX + " " + heldName + " — " + secs + " с",
                            NamedTextColor.AQUA);
                    if (bar == null) {
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
