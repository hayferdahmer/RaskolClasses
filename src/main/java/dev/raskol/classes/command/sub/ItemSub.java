package dev.raskol.classes.command.sub;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.item.ItemDefinition;
import dev.raskol.classes.item.ItemRarity;
import dev.raskol.classes.item.ItemStat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/** Administrative item registry and item issuance commands. */
public final class ItemSub implements CommandSub {
    private final RaskolClasses plugin;
    public ItemSub(RaskolClasses plugin){this.plugin=plugin;}
    @Override public String name(){return "item";}
    @Override public String permission(){return null;}
    @Override public boolean execute(CommandSender sender,String[] args){
        if(args.length==0){sender.sendMessage(Component.text("/rc item list|info|give|roll|stats|browse",NamedTextColor.YELLOW));return true;}
        String action=args[0].toLowerCase(Locale.ROOT);
        if("stats".equals(action)){if(!sender.hasPermission("raskolclasses.debug"))return deny(sender);Player p=args.length>1?Bukkit.getPlayer(args[1]):sender instanceof Player pl?pl:null;if(p==null){sender.sendMessage(Component.text("Игрок не найден.",NamedTextColor.RED));return true;}printStats(sender,p);return true;}
        if(!sender.hasPermission("raskolclasses.admin.item")&&!"give".equals(action)&&!"roll".equals(action))return deny(sender);
        if(("give".equals(action)||"roll".equals(action))&&!sender.hasPermission("raskolclasses.admin.item.give"))return deny(sender);
        switch(action){
            case "list"->{int page=args.length>1?positiveInt(args[1],1):1;List<ItemDefinition> all=plugin.getItems().registry().all().stream().sorted(java.util.Comparator.comparing(ItemDefinition::id)).toList();int from=(page-1)*45;sender.sendMessage(Component.text("Предметы "+Math.min(from+1,all.size())+"–"+Math.min(from+45,all.size())+" / "+all.size(),NamedTextColor.GOLD));for(int i=from;i<Math.min(from+45,all.size());i++)sender.sendMessage(Component.text(all.get(i).id(),NamedTextColor.GRAY));}
            case "info"->{if(args.length<2){usage(sender,"info <id>");return true;}ItemDefinition d=plugin.getItems().registry().get(args[1]);if(d==null){sender.sendMessage(Component.text("Предмет не найден.",NamedTextColor.RED));return true;}sender.sendMessage(Component.text(d.id()+" · "+d.displayName()+" · "+d.rarity()+" · ур. "+d.itemLevel(),NamedTextColor.GOLD));d.baseStats().forEach((s,v)->sender.sendMessage(Component.text("  "+s.id()+": "+v,NamedTextColor.GRAY)));}
            case "give","roll"->{if(args.length<3){usage(sender,action+" <игрок> <id> [кол-во|seed]");return true;}Player target=Bukkit.getPlayer(args[1]);ItemDefinition d=plugin.getItems().registry().get(args[2]);if(target==null||d==null){sender.sendMessage(Component.text(target==null?"Игрок не найден.":"Предмет не найден.",NamedTextColor.RED));return true;}int amount=1;long seed=ThreadLocalRandom.current().nextLong();if("give".equals(action)&&args.length>3)amount=positiveInt(args[3],1);if("roll".equals(action)&&args.length>3)try{seed=Long.parseLong(args[3]);}catch(NumberFormatException ex){sender.sendMessage(Component.text("Seed должен быть целым числом.",NamedTextColor.RED));return true;}if(amount>64){sender.sendMessage(Component.text("За одну выдачу можно передать не больше 64 предметов.",NamedTextColor.RED));return true;}for(int i=0;i<amount;i++){ItemStack item=plugin.getItemsApi().create(d.id(),d.itemLevel(),seed+i);var leftovers=target.getInventory().addItem(item);leftovers.values().forEach(left->target.getWorld().dropItemNaturally(target.getLocation(),left));}plugin.getLogger().info("Item issued by="+sender.getName()+" to="+target.getName()+" id="+d.id()+" amount="+amount+" seed="+seed);sender.sendMessage(Component.text("Выдано: "+d.displayName()+" ×"+amount,NamedTextColor.GREEN));}
            case "browse"->{if(!(sender instanceof Player p)){sender.sendMessage(Component.text("Команда доступна игроку.",NamedTextColor.RED));return true;}if(!sender.hasPermission("raskolclasses.admin.item"))return deny(sender);plugin.getItemBrowser().open(p,0);}
            default->{sender.sendMessage(Component.text("Неизвестная операция. /rc item list|info|give|roll|stats|browse",NamedTextColor.RED));}
        }
        return true;
    }
    private void printStats(CommandSender sender,Player player){var snap=plugin.getItems().snapshot(player.getUniqueId());sender.sendMessage(Component.text("Снаряжение "+player.getName()+":",NamedTextColor.GOLD));for(ItemStat s:ItemStat.values()){double v=snap.value(s);if(v!=0)sender.sendMessage(Component.text(s.id()+": "+String.format(Locale.ROOT,"%.1f",v),NamedTextColor.GRAY));}if(!snap.unmetRequirements().isEmpty())sender.sendMessage(Component.text("Требования: "+snap.unmetRequirements(),NamedTextColor.RED));}
    @Override public List<String> complete(CommandSender sender,String[] args){if(args.length==2&&List.of("info","give","roll").contains(args[1].toLowerCase(Locale.ROOT)))return SubUtil.filter(plugin.getItems().registry().all().stream().map(ItemDefinition::id).toList(),"");if(args.length==2&&List.of("list","stats").contains(args[1]))return SubUtil.filter(SubUtil.onlinePlayerNames(),"");return List.of();}
    private boolean deny(CommandSender sender){sender.sendMessage(Component.text("Недостаточно прав.",NamedTextColor.RED));return true;}
    private void usage(CommandSender sender,String value){sender.sendMessage(Component.text("/rc item "+value,NamedTextColor.YELLOW));}
    private static int positiveInt(String value,int fallback){try{int n=Integer.parseInt(value);return n>0?n:fallback;}catch(NumberFormatException e){return fallback;}}
}
