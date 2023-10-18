package harmonised.pmmo.client.gui.component;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import harmonised.pmmo.config.SkillsConfig;
import harmonised.pmmo.config.codecs.SkillData;
import harmonised.pmmo.core.Core;
import harmonised.pmmo.core.IDataStorage;
import harmonised.pmmo.util.Reference;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.Widget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.gui.ScrollPanel;
import net.minecraftforge.fml.LogicalSide;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class PlayerStatsComponent extends GuiComponent implements Widget, GuiEventListener, NarratableEntry {
    protected static final ResourceLocation TEXTURE_LOCATION = new ResourceLocation(Reference.MOD_ID, "textures/gui/player_stats.png");
    protected static final Core core = Core.get(LogicalSide.CLIENT);
    protected Minecraft minecraft;
    private boolean visible;
    private int xOffset;
    private int width;
    private int height;
    private boolean widthTooNarrow;
    private int timesInventoryChanged;
    
    protected PlayerStatsScroller statsScroller;
    
    public static final int IMAGE_WIDTH = 147;
    public static final int IMAGE_HEIGHT = 166;
    private static final int OFFSET_X_POSITION = 86;
    
    public PlayerStatsComponent() {
		super();
	}

    public void init(int width, int height, Minecraft minecraft, boolean widthTooNarrow) {
        this.minecraft = minecraft;
        this.width = width;
        this.height = height;
        this.widthTooNarrow = widthTooNarrow;
        this.timesInventoryChanged = minecraft.player.getInventory().getTimesChanged();
        
        if (this.visible) {
            this.initVisuals();
        }
    }
    
    public void tick() {
        if (this.isVisible()) {
            if (this.timesInventoryChanged != this.minecraft.player.getInventory().getTimesChanged()) {
                this.timesInventoryChanged = this.minecraft.player.getInventory().getTimesChanged();
            }
        }
    }
    
    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTicks) {
        if (this.isVisible()) {
            poseStack.pushPose();
            poseStack.translate(0.0D, 0.0D, 120.0D);
            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            RenderSystem.setShaderTexture(0, TEXTURE_LOCATION);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            int i = (this.width - IMAGE_WIDTH) / 2 - this.xOffset;
            int j = (this.height - IMAGE_HEIGHT) / 2;
            this.blit(poseStack, i, j, 0, 0, 147, 166);
            this.statsScroller.render(poseStack, mouseX, mouseY, partialTicks);
            poseStack.popPose();
        }
    }
    
    public void initVisuals() {
        this.xOffset = this.widthTooNarrow ? 0 : OFFSET_X_POSITION;
        int i = (this.width - IMAGE_WIDTH) / 2 - this.xOffset;
        int j = (this.height - IMAGE_HEIGHT) / 2;
        
        this.statsScroller = new PlayerStatsScroller(Minecraft.getInstance(), 131, 150, j + 8, i + 8);
        this.statsScroller.populateAbilities(core, this.minecraft);
    }
    
    public int updateScreenPosition(int x, int y) {
        int i;
        if (this.isVisible() && !this.widthTooNarrow) {
            i = 177 + (x - y - 200) / 2;
        } else {
            i = (x - y) / 2;
        }
        
        return i;
    }
    
    public void toggleVisibility() {
        this.setVisible(!this.isVisible());
    }
    
    public boolean isVisible() {
        return this.visible;
    }
    
    protected void setVisible(boolean visible) {
        if (visible) {
            this.initVisuals();
        }
        this.visible = visible;
    }
    
    @Override
    public boolean mouseClicked(double pMouseX, double pMouseY, int pButton) {
        if (!this.isVisible()) return false;
        return this.statsScroller.mouseClicked(pMouseX, pMouseY, pButton) || GuiEventListener.super.mouseClicked(pMouseX, pMouseY, pButton);
    }
    
    @Override
    public boolean mouseScrolled(double pMouseX, double pMouseY, double pDelta) {
        if (!this.isVisible()) return false;
        return this.statsScroller.mouseScrolled(pMouseX, pMouseY, pDelta) || GuiEventListener.super.mouseScrolled(pMouseX, pMouseY, pDelta);
    }
    
    @Override
    public boolean mouseDragged(double pMouseX, double pMouseY, int pButton, double pDragX, double pDragY) {
        if (!this.isVisible()) return false;
        return this.statsScroller.mouseDragged(pMouseX, pMouseY, pButton, pDragX, pDragY) || GuiEventListener.super.mouseDragged(pMouseX, pMouseY, pButton, pDragX, pDragY);
    }
    
    @Override @NotNull public NarrationPriority narrationPriority() { return NarrationPriority.NONE; }
    @Override public void updateNarration(@NotNull NarrationElementOutput pNarrationElementOutput) { }
    
    static class PlayerStatsScroller extends ScrollPanel {
        private final List<String> skillsKeys = new ArrayList<>();
        private final List<StatComponent> abilities = new ArrayList<>();
        
        public PlayerStatsScroller(Minecraft client, int width, int height, int top, int left) {
            super(client, width, height, top, left, 1, 6,
                0x00FFFFFF, 0x00FFFFFF, 0x88212121, 0xFF000000, 0xFF555555);
        }
        
        public void populateAbilities(Core core, Minecraft minecraft) {
            IDataStorage dataStorage = core.getData();
            
            this.skillsKeys.addAll(dataStorage.getXpMap(null).keySet());
            this.skillsKeys.sort(Comparator.<String>comparingLong(skill -> dataStorage.getXpRaw(null, skill)).reversed());
            
            for (String skillKey : this.skillsKeys) {
                SkillData skillData = SkillsConfig.SKILLS.get().getOrDefault(skillKey, SkillData.Builder.getDefault());
                this.abilities.add(new StatComponent(minecraft, this.left + 1, this.top, skillKey, skillData));
            }
        }
    
        @Override
        protected void drawPanel(PoseStack guiGraphics, int entryRight, int relativeY, Tesselator tess, int mouseX, int mouseY) {
            for (StatComponent component : this.abilities) {
                component.setPosition(component.x, relativeY);
                component.render(guiGraphics, mouseX, mouseY, Minecraft.getInstance().getDeltaFrameTime());

                relativeY += StatComponent.BASE_HEIGHT + this.border;
            }
        }
        
        @Override
        protected int getScrollAmount() {
            return StatComponent.BASE_HEIGHT + this.border;
        }
        
        @Override
        protected int getContentHeight() {
            int height = this.abilities.size() * (StatComponent.BASE_HEIGHT + this.border);
            if (height < this.bottom - this.top - 1) {
                height = this.bottom - this.top - 1;
            }
            return height;
        }
        
        @Override @NotNull public NarrationPriority narrationPriority() { return NarrationPriority.NONE; }
        @Override public void updateNarration(@NotNull NarrationElementOutput pNarrationElementOutput) {}
    }
    
    static class StatComponent extends ImageButton {
        private final Minecraft minecraft;
    
        private final String skillName;
        private final SkillData skillData;
        
        private final Color skillColor;
        private final int skillLevel;
        private final long skillCurrentXP;
        private final long skillXpToNext;
        
        private static final int BASE_HEIGHT = 24;
        
        public StatComponent(Minecraft minecraft, int pX, int pY, String skillKey, SkillData skillData) {
            super(pX, pY, 123, 24, 0, 167, 25, TEXTURE_LOCATION, pButton -> {});
            this.minecraft = minecraft;
            this.skillName = new TranslatableComponent("pmmo." + skillKey).getString();
            this.skillData = skillData;
            
            this.skillColor = new Color(skillData.getColor());
            this.skillCurrentXP = core.getData().getXpRaw(null, skillKey);
            this.skillLevel = core.getData().getLevelFromXP(skillCurrentXP);
            this.skillXpToNext = core.getData().getBaseXpForLevel(this.skillLevel+1)-this.skillCurrentXP;
        }
    
        @Override
        public void renderButton(PoseStack graphics, int pMouseX, int pMouseY, float pPartialTick) {
            super.renderButton(graphics, pMouseX, pMouseY, pPartialTick);

            RenderSystem.setShaderTexture(0, skillData.getIcon());
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            blit(graphics, this.x + 3, this.y + 3, 18, 18, 0, 0, skillData.getIconSize(), skillData.getIconSize(), skillData.getIconSize(), skillData.getIconSize());

            renderProgressBar(graphics);
            GuiComponent.drawString(graphics, minecraft.font, skillName, this.x + 24, this.y + 5, skillColor.getRGB());
            GuiComponent.drawString(graphics, minecraft.font, String.valueOf(skillLevel), (this.x + this.width - 5) - minecraft.font.width(String.valueOf(skillLevel)), this.y + 5, skillColor.getRGB());
        }
        
        public void renderProgressBar(PoseStack graphics) {
            int renderX = this.x + 24;
            int renderY = this.y + (minecraft.font.lineHeight + 6);
            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            RenderSystem.setShaderTexture(0, TEXTURE_LOCATION);
            if (this.isHovered) {
                MutableComponent text = new TextComponent("%s => %s".formatted(this.skillXpToNext, this.skillLevel +1));
                GuiComponent.drawString(graphics, minecraft.font, text, renderX, renderY-1, this.skillColor.getRGB());
            }
            else {
                RenderSystem.setShaderColor(skillColor.getRed() / 255.0f, skillColor.getGreen() / 255.0f, skillColor.getBlue() / 255.0f, skillColor.getAlpha() / 255.0f);
                GuiComponent.blit(graphics, renderX, renderY, 94, 5, 0.0F, 217.0F, 102, 5, 256, 256);

                long baseXP = core.getData().getBaseXpForLevel(skillLevel);
                long requiredXP = core.getData().getBaseXpForLevel(skillLevel + 1);
                float percent = 100.0f / (requiredXP - baseXP);
                int xp = (int) Math.min(Math.floor(percent * (skillCurrentXP - baseXP)), 94);
                GuiComponent.blit(graphics, renderX, renderY, xp, 5, 0.0F, 223.0F, 102, 5, 256, 256);
            }
        }
    }
}
