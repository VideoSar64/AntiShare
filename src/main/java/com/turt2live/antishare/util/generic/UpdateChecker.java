/*******************************************************************************
 * Copyright (c) 2012 turt2live (Travis Ralston).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 * 
 * Contributors:
 * turt2live (Travis Ralston) - initial API and implementation
 ******************************************************************************/
package com.turt2live.antishare.util.generic;

import java.io.IOException;
import java.net.URL;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.turt2live.antishare.AntiShare;
import com.turt2live.antishare.lang.LocaleMessage;
import com.turt2live.antishare.lang.Localization;

/**
 * Update Checker
 * 
 * @author turt2live
 */
public class UpdateChecker {

	/*
	 * Class function replicated from Vault (thanks Sleaker!)
	 * 
	 * URL: https://github.com/MilkBowl/Vault/blob/master/src/net/milkbowl/vault/Vault.java#L118
	 */

	/**
	 * Creates and starts the update checker
	 */
	public static void start(){
		final AntiShare plugin = AntiShare.getInstance();
		if(plugin.getConfig().getBoolean("other.dont-look-for-updates")){
			return;
		}
		plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, new Runnable(){

			@Override
			public void run(){
				try{
					if(isOutdated()){
						String newVersion = getBukkitDevVersion();
						String currentVersion = getCurrentVersion();
						plugin.getLogger().warning(Localization.getMessage(LocaleMessage.UPDATE_READY, newVersion, currentVersion));
						plugin.getLogger().warning(Localization.getMessage(LocaleMessage.UPDATE_LINK, "http://dev.bukkit.org/server-mods/antishare"));
					}
				}catch(NumberFormatException e){} // Don't handle
			}
		}, 0, 36000); // 30 minutes
	}

	/**
	 * Determines if AntiShare is outdated
	 * 
	 * @return true if outdated
	 * @throws NumberFormatException if the version of AntiShare is abnormal
	 */
	public static boolean isOutdated() throws NumberFormatException{
		double current = Double.valueOf(getCurrentVersion().split("-")[0].replaceFirst("\\.", ""));
		double release = Double.valueOf(getReleasedVersion());
		return release > current;
	}

	/**
	 * Gets the active version of AntiShare
	 * 
	 * @return the active version of AntiShare
	 */
	public static String getCurrentVersion(){
		return AntiShare.getInstance().getDescription().getVersion();
	}

	/**
	 * Gets the public release version of AntiShare
	 * 
	 * @return the public release version of AntiShare
	 */
	public static String getReleasedVersion(){
		return getBukkitDevVersion().replaceFirst("\\.", "");
	}

	/**
	 * Gets the public release version of AntiShare
	 * 
	 * @return the public release version or AntiShare
	 */
	// Borrowed from Vault, thanks Sleaker!
	// https://github.com/MilkBowl/Vault/blob/master/src/net/milkbowl/vault/Vault.java#L520
	public static String getBukkitDevVersion(){
		String pluginUrlString = "http://dev.bukkit.org/server-mods/antishare/files.rss";
		try{
			URL url = new URL(pluginUrlString);
			Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(url.openConnection().getInputStream());
			doc.getDocumentElement().normalize();
			NodeList nodes = doc.getElementsByTagName("item");
			Node firstNode = nodes.item(0);
			if(firstNode.getNodeType() == 1){
				Element firstElement = (Element) firstNode;
				NodeList firstElementTagName = firstElement.getElementsByTagName("title");
				Element firstNameElement = (Element) firstElementTagName.item(0);
				NodeList firstNodes = firstNameElement.getChildNodes();
				return firstNodes.item(0).getNodeValue().replace("v", "").trim();
			}
		}catch(IOException e){} // Do not handle
		catch(SAXException e){} // Do not handle
		catch(ParserConfigurationException e){} // Do not handle
		return getCurrentVersion();
	}
}
