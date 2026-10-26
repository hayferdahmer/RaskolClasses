// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.item.craft;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.item.gui.ItemHover;
import dev.raskol.classes.item.model.ItemDefinition;
import dev.raskol.classes.item.model.ItemRarity;
import dev.raskol.classes.item.model.ItemSlot;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 1.15.4: разбор (salvage) предметов RaskolClasses на фрагменты.
 *
 * Математика (pure, selftest 136):
 *  - вид осколка: MAIN_HAND/OFF_HAND → WEAPON, иначе ARMOR;
 *  - тир: COMMON/UNCOMMON → COMMON, RARE → RARE, EPIC/LEGENDARY/MYTHIC → EPIC;
 *  - количество осколков: itemLevel / 20 (20→1, 40→2, 60→3);
 *  - эссенция класса: 0 (COMMON-тир) / 1 (RARE) / 2 (EPIC+), только если предмет классовый;
 *  - реликвия: +1 для LEGENDARY/MYTHIC.
 *
 * Экономика: разбор убыточен относительно стоимости предмета (~30%) — монетный сток;
 * дневной лимит item.salvage.daily-limit (дефолт 20) — анти-ферма фрагментов.
 * Лимит хранится in-memory (день/счёт на игрока): потеря счётчика при рестарте
 * осознанно приемлема (лимит анти-фармовый, не балансный).
 *
 * 1.15.4-A-fix: карта фрагментов — LinkedHashMap (Fragment это record, не enum).
 * 1.15.5-B: сообщение результата несёт hover-превью разобранного предмета
 * (клон стака снимается ДО потребления — предмет уже израсходован к моменту отправки).
 */
public final class SalvageService {

    public enum SalvageResult { OK, DISABLED, EMPTY_HAND, NOT_OURS, DAILY_LIMIT }

    private final RaskolClasses plugin;
    private final FragmentRegistry fragments;
    private final Map<UUID, long[]> daily = new ConcurrentHashMap<>();

    public SalvageService(RaskolClasses plugin, FragmentRegistry fragments) {
        this.plugin = plugin;
        this.fragments = fragments;
    }

    /** Pure: карта фрагментов за разбор определения (без состояния). */
    public static Map<Fragment, Integer> fragmentsFor(FragmentRegistry registry, ItemDefinition def) {
        Map<Fragment, Integer> out = new LinkedHashMap<>();
        boolean weapon = def.slot() == ItemSlot.MAIN_HAND || def.slot() == ItemSlot.OFF_HAND;
        ItemRarity tier = tierOf(def.rarity());
        Fragment shard = registry.byId((weapon ? "frag_weapon_" : "frag_armor_")
                + tier.name().toLowerCase(java.util.Locale.ROOT));
        if (shard != null) {
            out.merge(shard, Math.max(1, def.itemLevel() / 20), Integer::sum);
        }
        if (def.playerClass() != null) {
            Fragment essence = registry.byId("frag_essence_"
                    + def.playerClass().name().toLowerCase(java.util.Locale.ROOT));
            int essenceCount = tier == ItemRarity.COMMON ? 0 : tier == ItemRarity.RARE ? 1 : 2;
            if (essence != null && essenceCount > 0) {
                out.merge(essence, essenceCount, Integer::sum);
            }
        }
        if (def.rarity() == ItemRarity.LEGENDARY || def.rarity() == ItemRarity.MYTHIC) {
            Fragment relic = registry.byId("frag_relic");
            if (relic != null) {
                out.merge(relic, 1, Integer::sum);
            }
        }
        return out;
    }

    private static ItemRarity tierOf(ItemRarity rarity) {
        return switch (rarity) {
            case COMMON, UNCOMMON -> ItemRarity.COMMON;
            case RARE -> ItemRarity.RARE;
            default -> ItemRarity.EPIC;
        };
    }

    /** Разобрать предмет в основной руке. */
    public SalvageResult salvage(Player player) {
        if (!plugin.getConfig().getBoolean("item.salvage.enabled", true)) {
            return SalvageResult.DISABLED;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) {
            return SalvageResult.EMPTY_HAND;
        }
        ItemDefinition def = plugin.getItems().definitionOf(hand);
        if (def == null) {
            return SalvageResult.NOT_OURS;
        }
        UUID uuid = player.getUniqueId();
        long today = LocalDate.now().toEpochDay();
        long[] counter = daily.compute(uuid, (k, v) -> (v == null || v[0] != today)
                ? new long[]{today, 0} : v);
        int limit = Math.max(1, plugin.getConfig().getInt("item.salvage.daily-limit", 20));
        if (counter[1] >= limit) {
            return SalvageResult.DAILY_LIMIT;
        }
        counter[1]++;

        // 1.15.5-B: превью ДО потребления стака (hover в сообщении результата)
        ItemStack preview = hand.clone();

        Map<Fragment, Integer> gained = fragmentsFor(fragments, def);
        hand.setAmount(hand.getAmount() - 1);
        if (hand.getAmount() <= 0) {
            player.getInventory().setItemInMainHand(null);
        }
        fragments.addFragments(player, gained);

        StringBuilder list = new StringBuilder();
        gained.forEach((fragment, count) -> list.append(fragment.displayName())
                .append(" ×").append(count).append(", "));
        String pretty = list.length() > 0
                ? list.substring(0, list.length() - 2) : "";
        player.sendMessage(ItemHover.withPreview(Component.text(plugin.getRaskolConfig()
                .message("item.salvage-ok", "♻ Разобрано: {item} → {fragments}")
                .replace("{item}", def.displayName())
                .replace("{fragments}", pretty), NamedTextColor.GREEN), preview));
        return SalvageResult.OK;
    }

    /** Сообщение об отказе для UI/команды. */
    public void notifyFailure(Player player, SalvageResult result) {
        String key;
        String fallback;
        switch (result) {
            case DISABLED -> { key = "item.salvage-disabled"; fallback = "♻ Разбор отключён"; }
            case EMPTY_HAND -> { key = "item.salvage-empty"; fallback = "♻ В руке нет предмета для разбора"; }
            case NOT_OURS -> { key = "item.salvage-not-ours"; fallback = "♻ Этот предмет не разобрать: он не из RaskolClasses"; }
            case DAILY_LIMIT -> { key = "item.salvage-limit"; fallback = "♻ Дневной лимит разбора исчерпан ({limit})"; }
            default -> { return; }
        }
        player.sendMessage(Component.text(plugin.getRaskolConfig()
                .message(key, fallback)
                .replace("{limit}", String.valueOf(
                        plugin.getConfig().getInt("item.salvage.daily-limit", 20))),
                NamedTextColor.RED));
    }
}
