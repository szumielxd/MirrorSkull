package me.szumielxd.mirrorskull;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.WrappedGameProfile;
import com.comphenix.protocol.wrappers.WrappedWatchableObject;
import com.comphenix.protocol.wrappers.nbt.NbtBase;
import com.comphenix.protocol.wrappers.nbt.NbtCompound;
import com.comphenix.protocol.wrappers.nbt.NbtFactory;
import com.comphenix.protocol.wrappers.nbt.NbtList;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;

public class MirrorSkull extends JavaPlugin {
	
	
	public static final String PREFIX = "§7[§a§lM§5§lS§r§7] §3";
	
	
	private static final String NBT_TAG = "mirror-skull";
	private static final GameProfile BLOCK_PROFILE = new GameProfile(UUID.fromString("00000000-0000-0000-0000-000000000000"), "");
	//
	private static Class<?> NMS_GameProfileSerializer;
	
	
	static {
		BLOCK_PROFILE.getProperties().put(NBT_TAG, new Property(NBT_TAG, "true"));
		try {
			String version = Bukkit.getServer().getClass().getName().split("\\.")[3];
			NMS_GameProfileSerializer = Class.forName(String.format("net.minecraft.server.%s.%s", version, "GameProfileSerializer"));
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
	}
	
	
	private ProtocolManager protocolManager;
	
	
	@Override
	public void onEnable() {
		this.getCommand("mirrorskull").setExecutor(new MainCommand(this));
		this.protocolManager = ProtocolLibrary.getProtocolManager();
		this.protocolManager.addPacketListener(new PacketAdapter(this, ListenerPriority.LOW,
				PacketType.Play.Server.ENTITY_METADATA,
				PacketType.Play.Server.ENTITY_EQUIPMENT,
				PacketType.Play.Server.WINDOW_ITEMS,
				PacketType.Play.Server.SET_SLOT,
				PacketType.Play.Server.TILE_ENTITY_DATA,
				PacketType.Play.Server.MAP_CHUNK) {
			
			@Override
			public void onPacketSending(@NotNull PacketEvent event) {
				try {
					if (event.getPacketType().equals(PacketType.Play.Server.ENTITY_METADATA)) {
						PacketContainer packet = event.getPacket();
						List<WrappedWatchableObject> entitymeta = packet.getWatchableCollectionModifier().read(0);
						entitymeta.forEach(watch -> {
							if (watch.getValue() instanceof ItemStack) {
								ItemStack item = (ItemStack) watch.getValue();
								itemCheck(item, event.getPlayer());
							}
						});
					} else if (event.getPacketType().equals(PacketType.Play.Server.ENTITY_EQUIPMENT)) {
						PacketContainer packet = event.getPacket();
						ItemStack item = packet.getItemModifier().read(0);
						itemCheck(item, event.getPlayer());
					} else if (event.getPacketType().equals(PacketType.Play.Server.WINDOW_ITEMS)) {
						PacketContainer packet = event.getPacket();
						Optional.ofNullable(packet.getItemArrayModifier().readSafely(0)).map(Stream::of)
								.orElseGet(() -> packet.getItemListModifier().read(0).stream())
								.forEach(i -> itemCheck(i, event.getPlayer()));
					} else if (event.getPacketType().equals(PacketType.Play.Server.SET_SLOT)) {
						PacketContainer packet = event.getPacket();
						ItemStack item = packet.getItemModifier().read(0);
						itemCheck(item, event.getPlayer());
					} else if (event.getPacketType().equals(PacketType.Play.Server.TILE_ENTITY_DATA)) {
						PacketContainer packet = event.getPacket();
						NbtCompound nbt = NbtFactory.asCompound(packet.getNbtModifier().read(0).deepClone());
						try {
							processTile(nbt, event.getPlayer());
							packet.getNbtModifier().write(0, nbt);
						} catch (IllegalArgumentException e) {
							// it's completely normal
						}
					} else if (event.getPacketType().equals(PacketType.Play.Server.MAP_CHUNK)) {
						PacketContainer packet = event.getPacket();
						for (NbtBase<?> base : packet.getListNbtModifier().read(0)) {
							NbtCompound nbt = NbtFactory.asCompound(base);
							try {
								processTile(nbt, event.getPlayer());
							} catch (IllegalArgumentException e) {
								// it's completely normal
							}
						}
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			
		});
	}
	
	
	@Override
	public void onDisable() {
		this.protocolManager.removePacketListeners(this);
	}
	
	
	private static void processTile(@NotNull NbtCompound nbt, @NotNull Player player) throws Exception {
		NbtCompound owner = nbt.getCompound("Owner");
		NbtCompound properties = owner.getCompound("Properties");
		NbtList<Object> mirrorProp = properties.getList(NBT_TAG);
		if ("true".equals((NbtFactory.asCompound(mirrorProp.asCollection().iterator().next())).getString("Value"))) {
			WrappedGameProfile profile = WrappedGameProfile.fromPlayer(player);
			NMS_GameProfileSerializer.getMethod("serialize", owner.getHandle().getClass(), profile.getHandle().getClass())
					.invoke(null, owner.getHandle(), profile.getHandle());
			
		}
	}
	
	
	private static void itemCheck(@Nullable ItemStack item, Player target) {
		if (item != null && item.getItemMeta() instanceof SkullMeta && isValid(item)) {
			SkullMeta meta = (SkullMeta) item.getItemMeta();
			try {
				GameProfile profileCopy = (GameProfile) target.getClass().getDeclaredMethod("getProfile").invoke(target);
				GameProfile profile = new GameProfile(profileCopy.getId(), profileCopy.getName());
				BLOCK_PROFILE.getProperties().entries().forEach(e -> profile.getProperties().put(e.getKey(), e.getValue()));
				profileCopy.getProperties().entries().forEach(e -> profile.getProperties().put(e.getKey(), e.getValue()));
				try {
					Method meth = meta.getClass().getDeclaredMethod("setProfile", GameProfile.class);
					meth.setAccessible(true);
					meth.invoke(meta, profile);
				} catch (NoSuchMethodException e) {
					try {
						Field f = meta.getClass().getDeclaredField("profile");
						f.setAccessible(true);
						f.set(meta, profile);
					} catch (SecurityException | IllegalAccessException | NoSuchFieldException ex) {
						ex.printStackTrace();
					}
				}
				item.setItemMeta(meta);
			} catch (NoSuchMethodException | SecurityException | IllegalAccessException | InvocationTargetException e) {
				e.printStackTrace();
			}
		}
	}
	
	
	private static boolean isValid(@Nullable ItemStack item) {
		if (item == null) return false;
		try {
			if (SkullMeta.class.isInstance(item.getItemMeta())) {
				SkullMeta meta = (SkullMeta) item.getItemMeta();
				Field f = meta.getClass().getDeclaredField("profile");
				f.setAccessible(true);
				GameProfile profile = (GameProfile) f.get(meta);
				if (profile != null) return profile.getProperties().get(NBT_TAG).stream().anyMatch(prop -> "true".equals(prop.getValue()));
			}
		} catch (SecurityException | IllegalAccessException | IllegalArgumentException | NoSuchFieldException e) {
			e.printStackTrace();
		}
		return false;
	}
	
	
	public @NotNull ItemStack createSkull() {
		ItemStack is = null;
		new ItemStack(Material.ACACIA_DOOR, 1, (short) 3);
		try {
			is = ItemStack.class.getConstructor(Material.class, int.class, short.class).newInstance(Material.getMaterial("SKULL_ITEM"), 1, (short)3);
		} catch (NoSuchMethodException | SecurityException | InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			is = new ItemStack(Material.getMaterial("PLAYER_HEAD"), 1);
			e.printStackTrace();
		}
		SkullMeta meta = (SkullMeta) is.getItemMeta();
		try {
			Method meth = meta.getClass().getDeclaredMethod("setProfile", GameProfile.class);
			meth.setAccessible(true);
			meth.invoke(meta, BLOCK_PROFILE);
		} catch (NoSuchMethodException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			try {
				Field f = meta.getClass().getDeclaredField("profile");
				f.setAccessible(true);
				f.set(meta, BLOCK_PROFILE);
			} catch (SecurityException | IllegalAccessException | NoSuchFieldException ex) {
				ex.printStackTrace();
			}
		}
		meta.setDisplayName("§aMagic Skull §7(skin mirror)");
		is.setItemMeta(meta);
		
		return is;
	}
	

}
