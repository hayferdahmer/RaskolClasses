package dev.raskol.classes.item;

import dev.raskol.classes.item.model.Affix;
import dev.raskol.classes.item.model.ItemDefinition;
import dev.raskol.classes.item.model.ItemRarity;
import dev.raskol.classes.item.model.ItemSlot;
import dev.raskol.classes.item.model.ItemStat;
import dev.raskol.classes.item.model.ItemStatSnapshot;
import dev.raskol.classes.item.model.SetDefinition;
import dev.raskol.classes.item.model.WeaponType;
import dev.raskol.classes.item.service.ItemStatAggregator;
import dev.raskol.classes.classsystem.PlayerClass;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ItemStatAggregatorTest {
    private ItemDefinition item(PlayerClass pc,int requirement,double str){return new ItemDefinition("test_item","Test","IRON_CHESTPLATE",ItemSlot.CHEST,null,ItemRarity.COMMON,20,pc,null,requirement,Map.of(ItemStat.STR,str),List.of(),0,null,List.of(),false,null);}
    @Test void classAndLevelRequirementsFilterItemStats(){
        var eligible=item(PlayerClass.WARRIOR,10,4);var wrongClass=item(PlayerClass.MAGE,1,20);var highLevel=item(PlayerClass.WARRIOR,50,30);
        var result=ItemStatAggregator.aggregate(List.of(eligible,wrongClass,highLevel),List.of(),PlayerClass.WARRIOR,null,20);
        assertEquals(4,result.get(ItemStat.STR));
    }
}
