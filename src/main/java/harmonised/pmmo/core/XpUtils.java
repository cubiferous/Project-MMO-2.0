package harmonised.pmmo.core;

import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

import com.google.common.base.Preconditions;

import harmonised.pmmo.api.enums.EventType;
import harmonised.pmmo.config.readers.ModifierDataType;
import harmonised.pmmo.util.MsLoggy;
import harmonised.pmmo.util.Reference;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class XpUtils {
	public XpUtils() {}
	
	private  Map<EventType, Map<ResourceLocation, Map<String, Long>>> xpGainData = new HashMap<>();
	private  Map<ModifierDataType, Map<ResourceLocation, Map<String, Double>>> xpModifierData = new HashMap<>();
	
	//===================XP INTERACTION METHODS=======================================
	
	public boolean hasXpGainObjectEntry(EventType eventType, ResourceLocation objectID) {
		if (!xpGainData.containsKey(eventType))
			return false;
		return xpGainData.get(eventType).containsKey(objectID);
	}
	
	public Map<String, Long> getObjectExperienceMap(EventType EventType, ResourceLocation objectID) {
		return xpGainData.computeIfAbsent(EventType, s -> new HashMap<>()).getOrDefault(objectID, new HashMap<>());
	}
	
	public void setObjectXpGainMap(EventType eventType, ResourceLocation objectID, Map<String, Long> xpMap) {
		Preconditions.checkNotNull(eventType);
		Preconditions.checkNotNull(objectID);
		Preconditions.checkNotNull(xpMap);
		xpGainData.computeIfAbsent(eventType, s -> new HashMap<>()).put(objectID, xpMap);
	}
	
	public void setObjectXpModifierMap(ModifierDataType XpValueDataType, ResourceLocation objectID, Map<String, Double> xpMap) {
		Preconditions.checkNotNull(XpValueDataType);
		Preconditions.checkNotNull(objectID);
		Preconditions.checkNotNull(xpMap);
		xpModifierData.computeIfAbsent(XpValueDataType, s -> new HashMap<>()).put(objectID, xpMap);
	}
	
	//====================UTILITY METHODS==============================================
	public Map<String, Long> deserializeAwardMap(ListTag nbt) {
		Map<String, Long> map = new HashMap<>();
		if (nbt.getElementType() != Tag.TAG_COMPOUND) {
			MsLoggy.error("An API method passed an invalid award map.  This may not have negative effects on gameplay," + 
							"but may cause the source implementation to behave unexpectedly");
			return map;
		}
		for (int i = 0; i < nbt.size(); i++) {
			map.put(nbt.getCompound(i).getString(Reference.API_MAP_SERIALIZER_KEY)
				   ,nbt.getCompound(i).getLong(Reference.API_MAP_SERIALIZER_VALUE));
		}
		return map;
	}	
	
	public Map<String, Long> applyXpModifiers(Player player, @Nullable Entity targetEntity, Map<String, Long> mapIn) {
		Map<String, Long> mapOut = new HashMap<>();
		Map<String, Double> modifiers = getConsolidatedModifierMap(player, targetEntity);
		for (Map.Entry<String, Long> award : mapIn.entrySet()) {
			if (modifiers.containsKey(award.getKey()))
				mapOut.put(award.getKey(), (long)(award.getValue() * modifiers.get(award.getKey())));
			else
				mapOut.put(award.getKey(), award.getValue());
		}
		return mapOut;
	}
	
	public void sendXpAwardNotifications(ServerPlayer player, String skillName, long amount) {
		//TODO send packets for guis and drop XP
		player.sendMessage(new TranslatableComponent("pmmo."+skillName).append(": "+String.valueOf(amount)), player.getUUID());
	}
	//====================LOGICAL METHODS==============================================
	
	private Map<String, Double> getConsolidatedModifierMap(Player player, @Nullable Entity entity) {
		Map<String, Double> mapOut = new HashMap<>();
		for (ModifierDataType type : ModifierDataType.values()) {
			Map<String, Double> modifiers = new HashMap<>();
			switch (type) {
			case BIOME: {
				ResourceLocation biomeID = player.level.getBiome(player.blockPosition()).getRegistryName();
				modifiers = xpModifierData.computeIfAbsent(type, s -> new HashMap<>()).getOrDefault(biomeID, new HashMap<>());
				for (Map.Entry<String, Double> modMap : modifiers.entrySet()) {
					mapOut.merge(modMap.getKey(), modMap.getValue(), (n, o) -> {return n * o;});
				}
				break;
			}
			case HELD: {
				ItemStack offhandStack = player.getOffhandItem();
				ItemStack mainhandStack = player.getMainHandItem();
				//TODO get NBT based API data for this
				ResourceLocation offhandID = offhandStack.getItem().getRegistryName();
				modifiers = xpModifierData.computeIfAbsent(type, s -> new HashMap<>()).getOrDefault(offhandID, new HashMap<>());
				for (Map.Entry<String, Double> modMap : modifiers.entrySet()) {
					mapOut.merge(modMap.getKey(), modMap.getValue(), (n, o) -> {return n * o;});
				}				
				ResourceLocation mainhandID = mainhandStack.getItem().getRegistryName();				
				modifiers = xpModifierData.computeIfAbsent(type, s -> new HashMap<>()).getOrDefault(mainhandID, new HashMap<>());
				for (Map.Entry<String, Double> modMap : modifiers.entrySet()) {
					mapOut.merge(modMap.getKey(), modMap.getValue(), (n, o) -> {return n * o;});
				}				
				break;
			}
			case WORN: {
				player.getArmorSlots().forEach((stack) -> {
					//TODO get NBT based API data for this
					ResourceLocation itemID = stack.getItem().getRegistryName();
					Map<String, Double> modifers = xpModifierData.computeIfAbsent(type, s -> new HashMap<>()).getOrDefault(itemID, new HashMap<>());
					for (Map.Entry<String, Double> modMap : modifers.entrySet()) {
						mapOut.merge(modMap.getKey(), modMap.getValue(), (n, o) -> {return n * o;});
					}
				});
				break;
			}
			case DIMENSION: {
				ResourceLocation dimensionID = player.level.dimension().getRegistryName();
				modifiers = xpModifierData.computeIfAbsent(type, s -> new HashMap<>()).getOrDefault(dimensionID, new HashMap<>());
				for (Map.Entry<String, Double> modMap : modifiers.entrySet()) {
					mapOut.merge(modMap.getKey(), modMap.getValue(), (n, o) -> {return n * o;});
				}
				break;
			}
			default: {}
			}
			
		}
		MsLoggy.info("Consolidated Modifier Map: "+MsLoggy.mapToString(mapOut));
		return mapOut;
	}
}