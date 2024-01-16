package harmonised.pmmo.client.events;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.mojang.blaze3d.platform.InputConstants;

import com.mojang.datafixers.util.Pair;
import harmonised.pmmo.api.enums.EventType;
import harmonised.pmmo.api.enums.ModifierDataType;
import harmonised.pmmo.api.enums.ObjectType;
import harmonised.pmmo.api.enums.ReqType;
import harmonised.pmmo.client.gui.StatsScreen;
import harmonised.pmmo.client.utils.DP;
import harmonised.pmmo.config.Config;
import harmonised.pmmo.config.SkillsConfig;
import harmonised.pmmo.config.codecs.SkillData;
import harmonised.pmmo.config.codecs.VeinData;
import harmonised.pmmo.core.Core;
import harmonised.pmmo.core.CoreUtils;
import harmonised.pmmo.setup.ClientSetup;
import harmonised.pmmo.setup.datagen.LangProvider;
import harmonised.pmmo.setup.datagen.LangProvider.Translation;
import harmonised.pmmo.util.Reference;
import harmonised.pmmo.util.RegistryUtil;
import harmonised.pmmo.util.TagUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.LogicalSide;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

@Mod.EventBusSubscriber(modid=Reference.MOD_ID, bus=Mod.EventBusSubscriber.Bus.FORGE, value= Dist.CLIENT)
public class TooltipHandler {
	public static boolean tooltipOn = true;

	@SubscribeEvent
	public static void onTooltip(ItemTooltipEvent event) {
		if(!tooltipOn)
            return;

        Player player = event.getEntity();

        if(player != null) {
        	Core core = Core.get(LogicalSide.CLIENT);
            ItemStack stack = event.getItemStack();
			ResourceLocation itemID = RegistryUtil.getId(stack);

            if(itemID == null)
                return;

            if(!ClientSetup.OPEN_MENU.isUnbound() && InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), ClientSetup.OPEN_MENU.getKey().getValue())) {
                Minecraft.getInstance().setScreen(new StatsScreen(stack));
                return;
            }
			Arrays.stream(ReqType.ITEM_APPLICABLE_EVENTS)
					.filter(type -> Config.tooltipReqEnabled(type).get())
					.map(type -> Pair.of(type, getReqData(core, type, stack)))
					.filter(pair -> !pair.getSecond().isEmpty())
					.forEach(pair -> addRequirementTooltip(pair.getFirst().tooltipTranslation, event, pair.getSecond(), core));
			Arrays.stream(EventType.ITEM_APPLICABLE_EVENTS)
					.filter(type -> Config.tooltipXpEnabled(type).get())
					.map(type -> Pair.of(type, getXpData(core, type, player, stack)))
					.filter(pair -> !pair.getSecond().isEmpty())
					.forEach(pair -> addXpValueTooltip(pair.getFirst().tooltipTranslation, event, pair.getSecond(), core));
			Stream.of(ModifierDataType.HELD, ModifierDataType.WORN)
					.filter(type -> Config.tooltipBonusEnabled(type).get())
					.map(type -> Pair.of(type, core.getObjectModifierMap(ObjectType.ITEM, itemID, type, TagUtils.stackTag(stack))))
					.filter(pair -> !pair.getSecond().isEmpty())
					.forEach(pair -> addModifierTooltip(pair.getFirst().tooltip, event, pair.getSecond(), core));
            //============VEIN MINER TOOLTIP DATA COLLECTION ========================
            VeinData veinData = core.getLoader().ITEM_LOADER.getData(itemID).veinData();
            if (!veinData.isUnconfigured() && !veinData.equals(VeinData.EMPTY) && Config.VEIN_ENABLED.get()) {
				addVeinTooltip(LangProvider.VEIN_TOOLTIP, event, veinData, stack.getItem() instanceof BlockItem);
			}
         }
	}
	
	private static void addRequirementTooltip(Translation header, ItemTooltipEvent event, Map<String, Integer> reqs, Core core) {
		event.getToolTip().add(header.asComponent());
		for (Map.Entry<String, Integer> req : reqs.entrySet()) {
			event.getToolTip().add(Component.translatable("pmmo."+req.getKey()).append(Component.literal(" "+String.valueOf(req.getValue()))).setStyle(CoreUtils.getSkillStyle(req.getKey())));
		}
	}
	
	private static void addXpValueTooltip(Translation header, ItemTooltipEvent event, Map<String, Long> values, Core core) {
		event.getToolTip().add(header.asComponent());
		values.entrySet().stream().filter(entry -> entry.getValue() > 0).forEach(value -> {
			event.getToolTip().add(Component.translatable("pmmo."+value.getKey()).append(Component.literal(" "+String.valueOf(value.getValue()))).setStyle(CoreUtils.getSkillStyle(value.getKey())));
		});
	}
	
	private static void addModifierTooltip(Translation header, ItemTooltipEvent event, Map<String, Double> values, Core core) {
		event.getToolTip().add(header.asComponent());
		values.entrySet().stream().filter(entry -> entry.getValue() != 0.0 && entry.getValue() != 1.0).forEach(modifier -> {
			event.getToolTip().add(Component.translatable("pmmo."+modifier.getKey()).append(Component.literal(" "+modifierPercent(modifier.getValue()))).setStyle(CoreUtils.getSkillStyle(modifier.getKey())));
		});
	}
	
	private static void addVeinTooltip(Translation header, ItemTooltipEvent event, VeinData data, boolean isBlockItem) {
		event.getToolTip().add(header.asComponent());
		event.getToolTip().add(LangProvider.VEIN_DATA.asComponent(
				data.chargeCap.orElse(0),
				DP.dp(data.chargeRate.orElse(0d) * 2d)));
		if (isBlockItem) {
			event.getToolTip().add(LangProvider.VEIN_BREAK.asComponent(
					data.consumeAmount.orElse(0)));
		}
	}
	
	private static String modifierPercent(Double value) {
		return DP.dp((value - 1d) * 100d) + "%";
	}
	
	private static Map<String, Long> getXpData(Core core, EventType type, Player player, ItemStack stack) {
		Map<String, Long> map = core.getExperienceAwards(type, stack, player, new CompoundTag());
		if (stack.getItem() instanceof BlockItem) 
			map = core.getCommonXpAwardData(new HashMap<>(), type, RegistryUtil.getId(stack), player, ObjectType.BLOCK, TagUtils.stackTag(stack));
		CoreUtils.processSkillGroupXP(map);
		return map;
	}
	
	private static Map<String, Integer> getReqData(Core core, ReqType type, ItemStack stack) {		
		//if Reqs are not enabled, ignore the getters and return an empty map
		//This will cause the map to be empty and result in no header being added.
		if (!Config.reqEnabled(type).get()) return new HashMap<>();
		
		//Gather req data and populate a map for return
		Map<String, Integer> map = type == ReqType.USE_ENCHANTMENT 
				? core.getEnchantReqs(stack)
				: core.getReqMap(type, stack);
		
		if (stack.getItem() instanceof BlockItem)
			map.putAll(core.getCommonReqData(new HashMap<>(), ObjectType.BLOCK, RegistryUtil.getId(stack), type, TagUtils.stackTag(stack)));
		
		//splits skill groups that aren't using total levels
		CoreUtils.processSkillGroupReqs(map);
		
		//return the raw map if met req filtering is not being applied
		if (!Config.HIDE_MET_REQS.get())
			return map;
		
		//remove values that meet the requirement
		new HashMap<>(map).forEach((skill, level) -> {
			if (SkillsConfig.SKILLS.get().getOrDefault(skill, SkillData.Builder.getDefault()).isSkillGroup()) {
				long total = SkillsConfig.SKILLS.get().get(skill)
						.getGroup()
						.keySet()
						.stream()
						.map(groupskill-> core.getData().getLevel(groupskill, null))
						.mapToLong(Long::longValue).sum();
				if (level <= total) {
					map.remove(skill);
				}
			}
			else if (core.getData().getLevel(skill, null) >= level)
				map.remove(skill);
		});
		
		return map;
	}
}
