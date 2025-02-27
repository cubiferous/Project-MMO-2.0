package harmonised.pmmo.compat.curios;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.ArrayList;
import java.util.List;

public class CuriosCompat {
    public static boolean hasCurio = false;
    //provide curio data to various methods
    public static List<ItemStack> getItems(Player player) {
        List<ItemStack> curioItems = new ArrayList<>();
        CuriosApi.getCuriosHelper().getEquippedCurios(player).ifPresent((handler) -> {
            for (int i = 0; i < handler.getSlots(); i++) {
                curioItems.add(handler.getStackInSlot(i));
            }
        });
        return curioItems;
    }
}
