package dev.raskol.classes.command.sub;

import dev.raskol.classes.RaskolClasses;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import java.util.List;

/** Set command surface, populated by set definitions in items.yml. */
public final class SetSub implements CommandSub {
    private final RaskolClasses plugin;
    public SetSub(RaskolClasses plugin){this.plugin=plugin;}
    @Override public String name(){return "set";}
    @Override public String permission(){return "raskolclasses.admin.item";}
    @Override public boolean execute(CommandSender sender,String[] args){
        var sets=plugin.getItems().registry().sets();
        if(args.length==0||"list".equalsIgnoreCase(args[0])){if(sets.isEmpty())sender.sendMessage(Component.text("Определённых сетов нет.",NamedTextColor.YELLOW));else sets.values().forEach(set->sender.sendMessage(Component.text(set.id()+" — "+set.displayName(),NamedTextColor.YELLOW)));return true;}
        if("active".equalsIgnoreCase(args[0])){Player p=args.length>1?Bukkit.getPlayer(args[1]):sender instanceof Player pl?pl:null;if(p==null){sender.sendMessage(Component.text("Игрок не найден.",NamedTextColor.RED));return true;}var active=plugin.getItems().setBonuses().active(p.getUniqueId());if(active.isEmpty())sender.sendMessage(Component.text("Активных сетов нет.",NamedTextColor.GRAY));else for(var set:active)sender.sendMessage(Component.text(set.displayName()+" · "+set.pieces()+" частей · пороги "+set.activeThresholds(),NamedTextColor.GREEN));return true;}
        if("info".equalsIgnoreCase(args[0])&&args.length>1){var set=sets.get(args[1]);if(set==null){sender.sendMessage(Component.text("Сет не найден.",NamedTextColor.RED));return true;}sender.sendMessage(Component.text(set.displayName(),NamedTextColor.GOLD));set.bonuses().forEach((count,bonus)->sender.sendMessage(Component.text(count+" предмета: "+bonus.description()+" "+bonus.stats(),NamedTextColor.GRAY)));return true;}
        sender.sendMessage(Component.text("/rc set list|info <id>|active <игрок>",NamedTextColor.YELLOW));return true;
    }
    @Override public List<String> complete(CommandSender sender,String[] args){if(args.length==2&&"active".equalsIgnoreCase(args[1]))return SubUtil.filter(SubUtil.onlinePlayerNames(),"");return List.of();}
}
