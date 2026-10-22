package dev.raskol.classes.item;

import dev.raskol.classes.RaskolClasses;
import org.bukkit.entity.Player;
import java.io.File;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/** Runtime owner of the validated item registry, factory and equipment snapshots. */
public final class ItemSystem implements ItemStatsProvider {
    private final RaskolClasses plugin;
    private final ItemFactory factory;
    private final ItemHook hook;
    private final SetBonusService setBonuses=new SetBonusService();
    private final Map<UUID,ItemStatSnapshot> snapshots=new ConcurrentHashMap<>();
    private final java.util.Set<String> warnedUnknownIds=ConcurrentHashMap.newKeySet();
    private volatile ItemRegistry registry;
    public ItemSystem(RaskolClasses plugin) { this.plugin=plugin;factory=new ItemFactory(plugin);hook=new ItemHook(plugin,this);reload(); }
    public synchronized boolean reload() {
        File file=new File(plugin.getDataFolder(),"items.yml");
        if(!file.exists()) plugin.saveResource("items.yml",false);
        try { ItemRegistry loaded=ItemConfigLoader.load(file,plugin.getLogger()::warning);registry=loaded;setBonuses.definitions(loaded.sets());plugin.getLogger().info("Загружено определений предметов: "+loaded.size());return true; }
        catch(Exception ex){plugin.getLogger().severe("items.yml отклонён; прежний реестр сохранён: "+ex.getMessage());return false;}
    }
    public ItemRegistry registry(){return registry;}
    public ItemFactory factory(){return factory;}
    public ItemPdc pdc(){return factory.pdc();}
    public ItemHook hook(){return hook;}
    public SetBonusService setBonuses(){return setBonuses;}
    public void start(){hook.start();}
    public void stop(){hook.stop();}
    public ItemDefinition definitionOf(org.bukkit.inventory.ItemStack item){
        if(!pdc().isItem(item))return null;
        String id=pdc().itemId(item);ItemDefinition def=registry.get(id);Integer level=pdc().itemLevel(item);
        if(def==null){if(id!=null&&warnedUnknownIds.add(id))plugin.getLogger().warning("Неизвестный item_id в предмете; предмет инертен: "+id);return null;}
        if(level==null||level<1||level>60||!def.rarity().name().equals(pdc().rarity(item))||!pdc().hasValidUid(item))return null;
        if(level==def.itemLevel())return def;
        double scale=(double)level/def.itemLevel();Map<ItemStat,Double> stats=new java.util.EnumMap<>(ItemStat.class);
        def.baseStats().forEach((stat,value)->stats.put(stat,stat.clamp(value*scale)));
        return new ItemDefinition(def.id(),def.displayName(),def.material(),def.slot(),def.weaponType(),def.rarity(),level,
                def.playerClass(),def.spec(),def.levelRequirement(),stats,def.affixPool(),def.affixCount(),def.setId(),def.lore(),def.glow(),def.customModelData(),def.template());
    }
    public org.bukkit.inventory.ItemStack create(String id,int level,long seed) {
        ItemDefinition def=registry.get(id);if(def==null)throw new IllegalArgumentException("Unknown item: "+id);
        if(level==def.itemLevel()) {
            org.bukkit.inventory.ItemStack created=factory.create(def,seed);
            plugin.getServer().getPluginManager().callEvent(new dev.raskol.classes.api.items.ItemCreatedEvent(created));
            return created;
        }
        Map<ItemStat,Double> stats=new java.util.EnumMap<>(ItemStat.class);double scale=(double)level/def.itemLevel();
        def.baseStats().forEach((stat,value)->stats.put(stat,stat.clamp(value*scale)));
        ItemDefinition adjusted=new ItemDefinition(def.id(),def.displayName(),def.material(),def.slot(),def.weaponType(),def.rarity(),level,
                def.playerClass(),def.spec(),def.levelRequirement(),stats,def.affixPool(),def.affixCount(),def.setId(),def.lore(),def.glow(),def.customModelData(),def.template());
        org.bukkit.inventory.ItemStack created=factory.create(adjusted,seed);
        plugin.getServer().getPluginManager().callEvent(new dev.raskol.classes.api.items.ItemCreatedEvent(created));
        return created;
    }
    public org.bukkit.inventory.ItemStack create(String id,int level){return create(id,level,ThreadLocalRandom.current().nextLong());}
    public void snapshot(UUID id,ItemStatSnapshot snapshot){snapshots.put(id,snapshot);}
    @Override public ItemStatSnapshot snapshot(UUID id){return snapshots.getOrDefault(id,ItemStatSnapshot.EMPTY);}
    public void clear(UUID id){snapshots.remove(id);}
    public void reconcile(Player player){hook.reconcile(player);}
}
