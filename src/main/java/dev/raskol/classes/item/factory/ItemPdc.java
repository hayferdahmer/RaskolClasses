// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.item.factory;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.item.model.Affix;
import dev.raskol.classes.item.model.ItemDefinition;
import dev.raskol.classes.item.model.ItemStat;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/** Versioned, plugin-owned PDC format for generated items. */
public final class ItemPdc {
    public static final int VERSION = 1;
    private final NamespacedKey version, uid, id, level, rarity, seed, affixes, shotDamage, shotType,
            socketGems, upgrade, enchants, craftedBy;
    public ItemPdc(RaskolClasses plugin) {
        version = key(plugin,"item_v"); uid = key(plugin,"item_uid"); id = key(plugin,"item_id");
        level = key(plugin,"item_ilvl"); rarity = key(plugin,"item_rarity"); seed = key(plugin,"roll_seed");
        affixes = key(plugin,"affix_vals");
        shotDamage=key(plugin,"shot_attack_damage");shotType=key(plugin,"shot_type");
        socketGems=key(plugin,"socket_gems");upgrade=key(plugin,"upgrade");
        enchants=key(plugin,"enchants");craftedBy=key(plugin,"crafted_by");
    }
    private static NamespacedKey key(RaskolClasses plugin,String key) { return new NamespacedKey(plugin,key); }
    public void write(ItemStack item, ItemDefinition def, long rollSeed, Map<ItemStat,Double> vals) {
        var meta=item.getItemMeta(); if(meta==null) return;
        var p=meta.getPersistentDataContainer();
        p.set(version,PersistentDataType.INTEGER,VERSION); p.set(uid,PersistentDataType.STRING,UUID.randomUUID().toString());
        p.set(id,PersistentDataType.STRING,def.id()); p.set(level,PersistentDataType.INTEGER,def.itemLevel());
        p.set(rarity,PersistentDataType.STRING,def.rarity().name()); p.set(seed,PersistentDataType.LONG,rollSeed);
        p.set(socketGems,PersistentDataType.STRING,"");p.set(upgrade,PersistentDataType.INTEGER,0);
        p.set(enchants,PersistentDataType.STRING,"");
        StringBuilder b=new StringBuilder(); vals.forEach((s,v)->{if(b.length()>0)b.append(';');b.append(s.id()).append(':').append(v);});
        p.set(affixes,PersistentDataType.STRING,b.toString()); item.setItemMeta(meta);
    }
    public boolean isItem(ItemStack item) { var p=container(item); return p!=null && Integer.valueOf(VERSION).equals(p.get(version,PersistentDataType.INTEGER)); }
    public boolean hasValidUid(ItemStack item) { try { UUID.fromString(uid(item));return true; } catch(RuntimeException ex) { return false; } }
    public String itemId(ItemStack item) { var p=container(item); return p==null?null:p.get(id,PersistentDataType.STRING); }
    public Integer itemLevel(ItemStack item) { var p=container(item); return p==null?null:p.get(level,PersistentDataType.INTEGER); }
    public String rarity(ItemStack item) { var p=container(item); return p==null?null:p.get(rarity,PersistentDataType.STRING); }
    public void writeShot(org.bukkit.entity.Projectile projectile,double damage,String type) {
        projectile.getPersistentDataContainer().set(shotDamage,PersistentDataType.DOUBLE,damage);
        projectile.getPersistentDataContainer().set(shotType,PersistentDataType.STRING,type);
    }
    public Double shotDamage(org.bukkit.entity.Projectile projectile) { return projectile.getPersistentDataContainer().get(shotDamage,PersistentDataType.DOUBLE); }
    public String shotType(org.bukkit.entity.Projectile projectile) { return projectile.getPersistentDataContainer().get(shotType,PersistentDataType.STRING); }
    public String uid(ItemStack item) { var p=container(item); return p==null?null:p.get(uid,PersistentDataType.STRING); }
    /** Writes the creator UUID for future crafting integrations. */
    public void markCraftedBy(ItemStack item,UUID creator){var meta=item.getItemMeta();if(meta==null)return;
        meta.getPersistentDataContainer().set(craftedBy,PersistentDataType.STRING,creator.toString());item.setItemMeta(meta);}
    /** Reads the optional creator UUID reserved for the crafting patch. */
    public UUID craftedBy(ItemStack item){var p=container(item);if(p==null)return null;
        try{return UUID.fromString(p.get(craftedBy,PersistentDataType.STRING));}catch(RuntimeException ex){return null;}}
    /** Reads reserved socket data without interpreting it in 1.15.0. */
    public String socketData(ItemStack item){var p=container(item);return p==null?null:p.get(socketGems,PersistentDataType.STRING);}
    /** Reads reserved upgrade level; fresh items start at zero. */
    public Integer upgradeLevel(ItemStack item){var p=container(item);return p==null?null:p.get(upgrade,PersistentDataType.INTEGER);}
    /** Reads reserved enchant data without interpreting it in 1.15.0. */
    public String enchantData(ItemStack item){var p=container(item);return p==null?null:p.get(enchants,PersistentDataType.STRING);}
    public Map<ItemStat,Double> affixes(ItemStack item, ItemDefinition def) {
        PersistentDataContainer p=container(item); if(p==null) return Map.of();
        String raw=p.get(affixes,PersistentDataType.STRING); if(raw==null || raw.length()>2048) return Map.of();
        Map<ItemStat,Double> values=new EnumMap<>(ItemStat.class);
        for(String part:raw.split(";")) { String[] pair=part.split(":",2); if(pair.length!=2) continue;
            ItemStat stat=ItemStat.fromId(pair[0]); if(stat==null || values.containsKey(stat)) continue;
            Affix range=def.affixPool().stream().filter(a->a.stat()==stat).findFirst().orElse(null); if(range==null) continue;
            try { double value=Double.parseDouble(pair[1]); if(Double.isFinite(value)&&values.size()<def.affixCount()) values.put(stat, Math.max(range.min(),Math.min(range.max(),value))); }
            catch(NumberFormatException ignored) { }
        }
        return Map.copyOf(values);
    }
    private static PersistentDataContainer container(ItemStack item) { if(item==null||item.getType().isAir()||!item.hasItemMeta())return null; return item.getItemMeta().getPersistentDataContainer(); }
}
