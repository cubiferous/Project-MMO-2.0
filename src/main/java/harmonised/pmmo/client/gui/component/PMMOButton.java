package harmonised.pmmo.client.gui.component;

import harmonised.pmmo.client.gui.PlayerStatsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;

public class PMMOButton extends ImageButton {
    private static final WidgetSprites SPRITES = new WidgetSprites(PlayerStatsScreen.PLAYER_STATS_LOCATION, PlayerStatsScreen.PLAYER_STATS_LOCATION);
    public PMMOButton(Screen parent, int pX, int pY, int pWidth, int pHeight, int pXTexStart, int pYTexStart, int yDiff) {
        super(pX, pY, pWidth, pHeight, SPRITES,
            (button) -> {
                Minecraft minecraft = Minecraft.getInstance();
                
                if (parent instanceof PlayerStatsScreen) {
                    minecraft.setScreen(new InventoryScreen(minecraft.player));
                }
                if (parent instanceof InventoryScreen) {
                    minecraft.setScreen(new PlayerStatsScreen(minecraft.player));
                }
        });
        //TODO figure out what the text settings are
    }
}
