/*******************************************************************************
 * Copyright (c) 2012 turt2live (Travis Ralston).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser Public License v2.1
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 * 
 * Contributors:
 * turt2live (Travis Ralston) - initial API and implementation
 ******************************************************************************/
package com.turt2live.antishare.money;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import com.feildmaster.lib.configuration.EnhancedConfiguration;
import com.turt2live.antishare.AntiShare;
import com.turt2live.antishare.api.ASGameMode;
import com.turt2live.antishare.metrics.TrackerList.TrackerType;
import com.turt2live.antishare.money.Tender.TenderType;
import com.turt2live.antishare.notification.Alert.AlertTrigger;
import com.turt2live.antishare.notification.Alert.AlertType;

/**
 * Manages rewards and fines
 * 
 * @author turt2live
 */
public class MoneyManager {

	private List<Reward> rewards = new ArrayList<Reward>();
	private List<Fine> fines = new ArrayList<Fine>();
	private AntiShare plugin;
	private boolean doRewards = false, doFines = false, tab = false, showStatusOnLogin = false;
	private VaultEconomy econ;
	private List<String> silentTo = new ArrayList<String>();
	private String finesTo = "nowhere", rewardsFrom = "nowhere";

	/**
	 * Creates a new Money Manager
	 */
	public MoneyManager(){
		plugin = AntiShare.getInstance();
		load();
		reload();
		if(doRewards || doFines){
			plugin.getTrackers().getTracker(TrackerType.FEATURE_FINES).increment(1);
			Plugin vault = plugin.getServer().getPluginManager().getPlugin("Vault");
			if(vault != null){
				econ = new VaultEconomy();
			}else{
				plugin.log("You have enabled fines/rewards but have not installed Vault. Please install Vault for AntiShare's fine/reward system to work", Level.SEVERE);
			}
		}
	}

	/**
	 * Reloads the manager
	 */
	public void reload(){
		// Load config
		EnhancedConfiguration money = new EnhancedConfiguration(new File(plugin.getDataFolder(), "fines.yml"), plugin);
		money.loadDefaults(plugin.getResource("resources/fines.yml"));
		if(money.needsUpdate()){
			money.saveDefaults();
		}
		money.load();

		// Load silents
		save();
		load();

		// Set settings
		doRewards = money.getBoolean("rewards-enabled");
		doFines = money.getBoolean("fines-enabled");
		tab = money.getBoolean("keep-tab");
		showStatusOnLogin = money.getBoolean("show-status-on-login");
		finesTo = money.getString("send-collections-to");
		rewardsFrom = money.getString("get-collections-from");

		// Prepare
		rewards.clear();
		fines.clear();

		// Load tender
		int finesLoaded = 0;
		int rewardsLoaded = 0;
		for(TenderType type : TenderType.values()){
			String path = type.getConfigurationKey();
			boolean doFine = money.getBoolean(path + ".do-fine");
			boolean doReward = money.getBoolean(path + ".do-reward");
			double fine = money.getDouble(path + ".fine");
			double reward = money.getDouble(path + ".reward");
			double noMoney = money.getString(path + ".no-money").equalsIgnoreCase("default") ? fine : money.getDouble(path + ".no-money");
			ASGameMode affect = ASGameMode.match(money.getString(path + ".give-to"));

			// Sanity
			if(affect == null){
				plugin.log("Configuration Problem: '" + money.getString(path + ".give-to") + "' is not valid! (See '" + (path + ".give-to") + "' in your fines.yml)", Level.WARNING);
				plugin.log("Assuming '" + money.getString(path + ".give-to") + "' means 'NONE'", Level.WARNING);
				affect = ASGameMode.NONE;
			}

			// Check enabled state
			if(!doRewards && doReward){
				doReward = false;
			}
			if(!doFines && doFine){
				doFine = false;
			}
			if(affect == ASGameMode.NONE){
				doFine = false;
				doReward = false;
			}

			// Add fine/reward
			Reward a = new Reward(type, reward, doReward, affect);
			Fine f = new Fine(type, fine, doFine, noMoney, affect);
			rewards.add(a);
			fines.add(f);

			// Record stats
			if(doFine){
				finesLoaded++;
			}
			if(doReward){
				rewardsLoaded++;
			}
		}

		// Spam console
		plugin.log("Fines Loaded: " + finesLoaded, Level.INFO);
		plugin.log("Rewards Loaded: " + rewardsLoaded, Level.INFO);
	}

	/**
	 * Saves the Money Manager
	 */
	public void save(){
		File silent = new File(plugin.getDataFolder() + File.separator + "data", "money-silent.txt");
		try{
			silent.getParentFile().mkdirs();
			BufferedWriter out = new BufferedWriter(new FileWriter(silent, false));
			for(String player : silentTo){
				out.write(player + "\r\n");
			}
			out.close();
		}catch(IOException e){
			AntiShare.getInstance().log("AntiShare encountered and error. Please report this to turt2live.", Level.SEVERE);
			e.printStackTrace();
		}
	}

	/**
	 * Loads the Money Manager
	 */
	public void load(){
		silentTo.clear();
		File silent = new File(plugin.getDataFolder() + File.separator + "data", "money-silent.txt");
		try{
			if(silent.exists()){
				BufferedReader in = new BufferedReader(new FileReader(silent));
				String line;
				while ((line = in.readLine()) != null){
					silentTo.add(line);
				}
				in.close();
			}
		}catch(IOException e){
			AntiShare.getInstance().log("AntiShare encountered and error. Please report this to turt2live.", Level.SEVERE);
			e.printStackTrace();
		}
	}

	/**
	 * Tells the player (if required) that they have silenced us.
	 * 
	 * @param player the player
	 */
	public void showStatusOnLogin(Player player){
		if(isSilent(player.getName()) && showStatusOnLogin){
			player.performCommand("as money status");
		}
	}

	/**
	 * Adds an amount to a player's account
	 * 
	 * @param player the player
	 * @param amount the amount
	 * @return the result
	 */
	public TransactionResult addToAccount(Player player, double amount){
		if(econ == null){
			return TransactionResult.NO_VAULT;
		}
		if(!rewardsFrom.equalsIgnoreCase("nowhere")){
			econ.subtract(rewardsFrom, amount);
		}
		return econ.add(player.getName(), amount);
	}

	/**
	 * Subtracts an amount from a player's account
	 * 
	 * @param player the player
	 * @param amount the amount
	 * @return the result
	 */
	public TransactionResult subtractFromAccount(Player player, double amount){
		if(econ == null){
			return TransactionResult.NO_VAULT;
		}
		if(econ.requiresTab(player.getName()) && !tab){
			return TransactionResult.NO_TAB;
		}
		if(!finesTo.equalsIgnoreCase("nowhere")){
			econ.add(finesTo, amount);
		}
		return econ.subtract(player.getName(), amount);
	}

	/**
	 * Gets the player's account balance
	 * 
	 * @param player the player
	 * @return the balance
	 */
	public double getBalance(Player player){
		if(econ == null){
			return 0.0;
		}
		return econ.getBalance(player.getName());
	}

	/**
	 * Formats an amount into a String
	 * 
	 * @param amount the amount
	 * @return the string of the amount
	 */
	public String formatAmount(double amount){
		if(econ == null){
			return String.valueOf(amount);
		}
		return econ.format(amount);
	}

	/**
	 * Gets the raw VaultEconomy hook
	 * 
	 * @return the hook
	 */
	public VaultEconomy getRawEconomyHook(){
		return econ;
	}

	/**
	 * Adds a player to the silent list
	 * 
	 * @param playername the player name
	 */
	public void addToSilentList(String playername){
		silentTo.add(playername);
	}

	/**
	 * Removes a player from the silent list
	 * 
	 * @param playername the player name
	 */
	public void removeFromSilentList(String playername){
		silentTo.remove(playername);
	}

	/**
	 * Determines if balance messages should be sent to the player
	 * 
	 * @param playername the player name
	 * @return true if the Money Manager should be silent to this player
	 */
	public boolean isSilent(String playername){
		return silentTo.contains(playername);
	}

	/**
	 * Gets a fine from a type
	 * 
	 * @param type the type
	 * @return the fine, or null if not found
	 */
	public Fine getFine(TenderType type){
		for(Fine fine : fines){
			if(fine.getType() == type){
				return fine;
			}
		}
		return null;
	}

	/**
	 * Gets an reward from a type
	 * 
	 * @param type the type
	 * @return the reward, or null if not found
	 */
	public Reward getReward(TenderType type){
		for(Reward reward : rewards){
			if(reward.getType() == type){
				return reward;
			}
		}
		return null;
	}

	/**
	 * Fires for an reward or fine. <br>
	 * Permission checks are done internally from here on in.
	 * 
	 * @param trigger the event trigger
	 * @param type the type
	 * @param player the person to apply this to
	 */
	public void fire(AlertTrigger trigger, AlertType type, Player player){
		if(type == AlertType.GENERAL || type == AlertType.REGION){
			return;
		}
		boolean fine = type == AlertType.ILLEGAL;

		// Find tender
		TenderType tender = null;
		switch (trigger){
		case BLOCK_BREAK:
			tender = TenderType.BLOCK_BREAK;
			break;
		case BLOCK_PLACE:
			tender = TenderType.BLOCK_PLACE;
			break;
		case PLAYER_DEATH:
			tender = TenderType.DEATH;
			break;
		case ITEM_DROP:
			tender = TenderType.ITEM_DROP;
			break;
		case ITEM_PICKUP:
			tender = TenderType.ITEM_PICKUP;
			break;
		case RIGHT_CLICK:
			tender = TenderType.RIGHT_CLICK;
			break;
		case USE_ITEM:
			tender = TenderType.USE;
			break;
		case CREATIVE_BLOCK:
			tender = TenderType.CREATIVE_BLOCK;
			break;
		case SURVIVAL_BLOCK:
			tender = TenderType.SURVIVAL_BLOCK;
			break;
		case HIT_PLAYER:
			tender = TenderType.HIT_PLAYER;
			break;
		case HIT_MOB:
			tender = TenderType.HIT_MOB;
			break;
		case COMMAND:
			tender = TenderType.COMMAND;
			break;
		default:
			return; // No need to handle it
		}

		// Apply reward/fine
		if(fine){
			getFine(tender).apply(player);
		}else{
			getReward(tender).apply(player);
		}
	}

	/**
	 * Prints the fines/rewards to the writer
	 * 
	 * @param out the writer
	 * @throws IOException thrown if something goes wrong
	 */
	public void print(BufferedWriter out) throws IOException{
		out.write("Properties: \r\n\tUse Tab: " + tab + "\r\n");
		out.write("\tDo Fines: " + doFines + "\r\n");
		out.write("\tDo Rewards: " + doRewards + "\r\n");
		out.write("\tEcon Found: " + (econ != null) + "\r\n");
		out.write("\tFines Loaded: " + fines.size() + "\r\n");
		out.write("\tRewards Loaded: " + rewards.size() + "\r\n");
		out.write("\tSilent Players: " + silentTo.size() + "\r\n");
		for(Fine fine : fines){
			out.write("FINE: " + fine.getType().name() + " [Enabled: " + fine.isEnabled() + ", Amount: " + fine.getAmount() + ", Over Charge: " + fine.getOverCharge() + "]\r\n");
		}
		for(Reward reward : rewards){
			out.write("REWARD: " + reward.getType().name() + " [Enabled: " + reward.isEnabled() + ", Amount: " + reward.getAmount() + "]\r\n");
		}
	}
}
