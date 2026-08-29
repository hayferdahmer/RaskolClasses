// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.gui;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.ability.AbilityDef;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.classsystem.SkillLevelProvider;
import dev.raskol.classes.config.RaskolConfig;
import dev.raskol.classes.util.TextFx;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class ClassMenu implements InventoryHolder {

    private static final int SYMBOL_SLOT = 4;
    private static final int FIRST_SLOT = 10;

    private final Inventory inventory;

    private ClassMenu(RaskolClasses plugin, Player player, PlayerClass pc) {
        RaskolConfig.ClassTheme theme = plugin.getRaskolConfig().themeOf(pc);
        this.inventory = Bukkit.createInventory(this, 27,
                TextFx.gradient("Способности: " + pc.getDisplayName(),
                        theme.primary(), theme.secondary()));

        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        filler.editMeta(meta -> meta.displayName(Component.empty()));
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }

        ItemStack emblem = new ItemStack(Material.PAPER);
        emblem.editMeta(meta -> {
            meta.displayName(TextFx.gradient(
                    theme.symbol() + " " + pc.getDisplayName(), theme.primary(), theme.secondary()));
            List<Component> emblemLore = new ArrayList<>();
            for (String passiveId : RaskolConfig.passiveIds(pc)) {
                if (!plugin.getRaskolConfig().passiveEnabled(pc, passiveId)) {
                    continue;
                }
                String passiveName = plugin.getRaskolConfig()
                        .passiveDisplayName(pc, passiveId, passiveId);
                String passiveDesc = plugin.getRaskolConfig()
                        .passiveDescription(pc, passiveId, "");
                emblemLore.add(Component.text("Пассив: " + passiveName
                                + (passiveDesc.isEmpty() ? "" : " — " + passiveDesc),
                        NamedTextColor.GRAY));
            }
            if (!emblemLore.isEmpty()) {
                emblemLore.add(0, separator());
                emblemLore.add(separator());
                meta.lore(emblemLore);
            }
        });
        inventory.setItem(SYMBOL_SLOT, emblem);

        int level = plugin.getSkillLevels().getLevel(player.getUniqueId(), pc.profileSkillName());
        List<AbilityDef> abilities = plugin.getAbilities().getAbilities(pc);
        int slot = FIRST_SLOT;
        for (AbilityDef def : abilities) {
            if (slot > 16) {
                break;
            }
            inventory.setItem(slot, buildItem(plugin, player, pc, def, level, theme));
            slot++;
        }
    }

    public static void open(RaskolClasses plugin, Player player, PlayerClass pc) {
        player.openInventory(new ClassMenu(plugin, player, pc).getInventory());
    }

    private ItemStack buildItem(RaskolClasses plugin, Player player, PlayerClass pc,
                                AbilityDef def, int level, RaskolConfig.ClassTheme theme) {
        long remaining = plugin.getCooldowns().getRemainingMillis(player.getUniqueId(), def.id());
        boolean unlocked = level == SkillLevelProvider.NO_SKILL_SYSTEM
                || level >= def.unlockLevel();

        ItemStack item = new ItemStack(iconOf(def.id()));
        List<Component> lore = new ArrayList<>();
        lore.add(separator());

        // Микро-пакет: описание способности — под разделителем, серым
        String description = plugin.getRaskolConfig()
                .abilityDescription(pc, def.id(), "");
        if (!description.isEmpty()) {
            lore.add(Component.text(description, NamedTextColor.GRAY));
            lore.add(separator());
        }

        lore.add(Component.text("Стоимость: " + def.cost() + " «" + pc.getResourceName() + "»",
                NamedTextColor.GRAY));
        lore.add(Component.text("Перезарядка: " + def.cooldownMillis() / 1000L + "с",
                NamedTextColor.GRAY));

        if (!unlocked) {
            lore.add(Component.text("Закрыто: нужен " + pc.profileSkillName()
                    + " " + def.unlockLevel() + " (у вас " + level + ")", NamedTextColor.RED));
        } else if (remaining > 0L) {
            lore.add(Component.text("Перезарядка: " + (remaining / 1000L + 1L) + "с",
                    NamedTextColor.YELLOW));
        } else {
            lore.add(Component.text("Готова — кликните для каста", NamedTextColor.GREEN));
        }
        lore.add(separator());
        lore.add(Component.text("Слот: /rc " + def.slot(), NamedTextColor.DARK_GRAY));

        item.editMeta(meta -> {
            meta.displayName(TextFx.gradient(def.displayName(), theme.primary(), theme.secondary()));
            meta.lore(lore);
        });
        return item;
    }

    private static Component separator() {
        return Component.text("──────────────", NamedTextColor.DARK_GRAY);
    }

    private static Material iconOf(String abilityId) {
        return switch (abilityId) {
            case "steel_skin" -> Material.SHIELD;
            case "shield_bash" -> Material.IRON_SWORD;
            case "blood_fury" -> Material.NETHERITE_SWORD;
            case "war_god" -> Material.GOLDEN_HELMET;
            case "shield_wall" -> Material.SHIELD;
            case "execute" -> Material.NETHERITE_AXE;
            case "avatar_of_war" -> Material.GOLDEN_HELMET;
            case "aimed_shot" -> Material.BOW;
            case "cheetah_aspect" -> Material.LEATHER_BOOTS;
            case "multi_shot" -> Material.ARROW;
            case "barrage" -> Material.SPECTRAL_ARROW;
            case "lesser_heal" -> Material.APPLE;
            case "flash_heal" -> Material.GOLDEN_APPLE;
            case "pw_shield" -> Material.TOTEM_OF_UNDYING;
            case "circle_of_prayer" -> Material.BOOK;
            case "smite" -> Material.NETHER_STAR;
            case "firebolt" -> Material.FIRE_CHARGE;
            case "blink" -> Material.ENDER_PEARL;
            case "frost_nova" -> Material.BLUE_ICE;
            case "arcane_burst" -> Material.END_CRYSTAL;
            case "stealth" -> Material.BLACK_DYE;
            case "fan_of_knives" -> Material.IRON_NUGGET;
            case "cheap_shot" -> Material.SPIDER_EYE;
            case "evasion" -> Material.FEATHER;
            default -> Material.PAPER;
        };
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public static final class ClickHandler implements Listener {

        private final RaskolClasses plugin;

        public ClickHandler(RaskolClasses plugin) {
            this.plugin = plugin;
        }

        @EventHandler(ignoreCancelled = true)
        public void onInventoryClick(InventoryClickEvent event) {
            if (!(event.getInventory().getHolder() instanceof ClassMenu)) {
                return;
            }
            event.setCancelled(true);

            if (!(event.getWhoClicked() instanceof Player player)) {
                return;
            }
            if (event.getClickedInventory() == null
                    || !(event.getClickedInventory().getHolder() instanceof ClassMenu)) {
                return;
            }

            PlayerClass pc = plugin.getClassProvider().getClassOf(player);
            if (pc == null) {
                return;
            }
            List<AbilityDef> abilities = plugin.getAbilities().getAbilities(pc);
            int index = event.getSlot() - FIRST_SLOT;
            if (index < 0 || index >= abilities.size()) {
                return;
            }

            player.closeInventory();
            plugin.getAbilities().tryCast(player, abilities.get(index));
        }
    }
}
