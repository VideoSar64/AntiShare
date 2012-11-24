package com.turt2live.antishare.util.generic;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

import com.turt2live.antishare.AntiShare;
import com.turt2live.antishare.feildmaster.lib.configuration.EnhancedConfiguration;
import com.turt2live.antishare.inventory.ASInventory;
import com.turt2live.antishare.inventory.ASInventory.InventoryType;

public class SelfCompatibility {

	private static void saveBlock(File dir, Block block, String gamemode){
		File file = new File(dir, block.getChunk().getX() + "." + block.getChunk().getZ() + "." + block.getWorld().getName() + ".yml");
		if(file.exists()){
			file.delete();
		}
		EnhancedConfiguration blocks = new EnhancedConfiguration(file, AntiShare.getInstance());
		blocks.load();
		blocks.set(block.getX() + ";" + block.getY() + ";" + block.getZ() + ";" + block.getWorld().getName(), gamemode);
		blocks.save();
	}

	/**
	 * Convert 4.4.0 to 4.4.1+ system
	 */
	public static void convertBlocks(){
		int converted = 0;
		AntiShare plugin = AntiShare.getInstance();
		File dir = new File(plugin.getDataFolder(), "data");
		dir.mkdirs();
		File nDir = new File(plugin.getDataFolder(), "data" + File.separator + "blocks");
		nDir.mkdirs();
		File oldBlockFile = new File(dir, "blocks.yml");
		if(!oldBlockFile.exists()){
			return;
		}
		EnhancedConfiguration blocks = new EnhancedConfiguration(oldBlockFile, plugin);
		blocks.load();
		for(String key : blocks.getKeys(false)){
			String[] keyParts = key.split(";");
			Location location = new Location(Bukkit.getWorld(keyParts[3]), Double.parseDouble(keyParts[0]), Double.parseDouble(keyParts[1]), Double.parseDouble(keyParts[2]));
			if(Bukkit.getWorld(keyParts[3]) == null || location == null || location.getWorld() == null){
				continue;
			}
			Block block = location.getBlock();
			if(block == null){
				location.getChunk().load();
				block = location.getBlock();
			}
			GameMode gm = GameMode.valueOf(blocks.getString(key));
			saveBlock(nDir, block, gm.name());
			converted++;
		}
		oldBlockFile.delete();
		if(converted > 0){
			plugin.getLogger().info("Blocks Converted: " + converted);
		}
	}

	/**
	 * Migrates player data from region_players to data/region_players
	 */
	public static void migratePlayerData(){
		AntiShare plugin = AntiShare.getInstance();
		File newSaveFolder = new File(plugin.getDataFolder(), "data" + File.separator + "region_players");
		File oldSaveFolder = new File(plugin.getDataFolder(), "region_players");
		newSaveFolder.mkdirs();
		if(oldSaveFolder.exists()){
			File[] files = oldSaveFolder.listFiles();
			if(files != null && files.length > 0){
				for(File file : files){
					file.renameTo(new File(newSaveFolder, file.getName()));
				}
				plugin.getLogger().info("Region Player Files Migrated: " + files.length);
			}
			oldSaveFolder.delete();
		}
	}

	/**
	 * Converts 3.1.3 inventories to 3.2.0+ style
	 */
	public static void convert313Inventories(){
		AntiShare plugin = AntiShare.getInstance();
		File[] files = new File(plugin.getDataFolder(), "inventories").listFiles();
		if(files != null){
			for(File file : files){
				EnhancedConfiguration inventory = new EnhancedConfiguration(file, plugin);
				inventory.load();
				String fname = file.getName();
				GameMode gamemode;
				if(fname.replace("_CREATIVE_", "").length() != fname.length()){
					gamemode = GameMode.CREATIVE;
				}else if(fname.replace("_SURVIVAL_", "").length() != fname.length()){
					gamemode = GameMode.SURVIVAL;
				}else{
					continue;
				}
				fname = fname.replace("_" + gamemode.name() + "_", "ANTI=SHARE"); // Unique character
				String[] nameparts = fname.split("\\.")[0].split("ANTI=SHARE");
				if(nameparts.length < 2){
					continue;
				}
				String playerName = nameparts[0];
				World world = plugin.getServer().getWorld(nameparts[1]);
				if(world == null){
					continue;
				}
				List<ASInventory> list = ASInventory.generateInventory(playerName, InventoryType.PLAYER);
				if(list.size() > 0){
					continue;
				}
				ASInventory newi = new ASInventory(InventoryType.PLAYER, playerName, world, gamemode);
				for(String key : inventory.getKeys(false)){
					ItemStack item = inventory.getItemStack(key);
					int slot = -1;
					try{
						slot = Integer.parseInt(key);
					}catch(NumberFormatException e){
						continue;
					}
					if(slot < 0){
						continue;
					}
					if(item != null && item.getType() != Material.AIR){
						newi.set(slot, item);
					}
				}
				newi.save();
			}
			for(File file : files){
				file.delete();
			}
			plugin.getLogger().info("Player Inventories Converted: " + files.length);
		}
	}

	/**
	 * Removes/Archives old inventories
	 */
	public static void cleanupOldInventories(){
		AntiShare plugin = AntiShare.getInstance();
		if(plugin.getConfig().getBoolean("settings.cleanup.use")){
			File timeFile = new File(plugin.getDataFolder(), "lastCleanup");
			if(timeFile.exists()){
				try{
					BufferedReader in = new BufferedReader(new FileReader(timeFile));
					String line = in.readLine();
					int lastMS = Integer.parseInt(line);
					int hours = 3600000 * 6;
					if(System.currentTimeMillis() - lastMS < hours){
						return; // Don't clean
					}
					in.close();
				}catch(IOException e){}catch(NumberFormatException e){}
			}
			try{
				BufferedWriter out = new BufferedWriter(new FileWriter(timeFile, false));
				out.write(String.valueOf(System.currentTimeMillis()));
				out.close();
			}catch(IOException e){}
			long time = plugin.getConfig().getLong("settings.cleanup.after");
			boolean delete = plugin.getConfig().getString("settings.cleanup.method").equalsIgnoreCase("delete");
			File archiveLocation = new File(plugin.getDataFolder(), "archive" + File.separator + "inventories" + File.separator + "players");
			if(!delete && !archiveLocation.exists()){
				archiveLocation.mkdirs();
			}
			File[] files = new File(plugin.getDataFolder(), "inventories" + File.separator + InventoryType.PLAYER.getRelativeFolderName()).listFiles();
			int cleaned = 0;
			if(files != null){
				for(File file : files){
					String player = file.getName().split("\\.")[0];
					OfflinePlayer p = plugin.getServer().getOfflinePlayer(player);
					long diff = System.currentTimeMillis() - p.getLastPlayed();
					long days = diff / (24 * 60 * 60 * 1000);
					if(days >= time){
						if(delete){
							file.delete();
						}else{
							file.renameTo(new File(archiveLocation, file.getName()));
						}
						cleaned++;
					}
				}
			}
			plugin.getLogger().info("Player Inventories Archived/Deleted: " + cleaned);
		}
	}

}
