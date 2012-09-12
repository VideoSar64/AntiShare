/*******************************************************************************
 * Copyright (c) 2012 turt2live (Travis Ralston).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser Public License v2.1
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 * 
 * Contributors:
 *     turt2live (Travis Ralston) - initial API and implementation
 ******************************************************************************/
package com.turt2live.antishare.notification;

import java.util.logging.Level;

import com.turt2live.antishare.AntiShare;
import com.turt2live.antishare.AntiShare.LogType;

/**
 * Messenger - Sends messages to the console based on configuration options
 * 
 * @author turt2live
 */
public class ConsoleMessenger {

	private boolean silent = false;
	private boolean silentStartup = false;
	private boolean silentShutdown = false;
	private boolean silentBlocks = false;
	private boolean silentErrors = false;
	private boolean debug = false;

	/**
	 * Creates a new Messenger
	 */
	public ConsoleMessenger(){
		reload();
	}

	/**
	 * Reloads the messenger's settings
	 */
	public void reload(){
		AntiShare plugin = AntiShare.getInstance();
		debug = plugin.getConfig().getBoolean("other.debug");
		if(!debug){
			silent = plugin.getConfig().getBoolean("other.silent-overall");
			silentStartup = plugin.getConfig().getBoolean("other.silent-startup");
			silentShutdown = plugin.getConfig().getBoolean("other.silent-shutdown");
			silentBlocks = plugin.getConfig().getBoolean("other.silent-blocks");
			silentErrors = plugin.getConfig().getBoolean("other.silent-errors");
		}else{
			silent = false;
			silentStartup = false;
			silentShutdown = false;
			silentBlocks = false;
			silentErrors = false;
		}
	}

	/**
	 * Sends an INFO message
	 * 
	 * @param message
	 */
	public void info(String message){
		log(message, Level.INFO, LogType.INFO);
	}

	/**
	 * Sends a message to the console if needed
	 * 
	 * @param message the message
	 * @param level the Log Level
	 * @param type the Log Entry Type
	 */
	public void log(String message, Level level, LogType type){
		switch (type){
		case STARTUP:
			if(!silentStartup){
				AntiShare.getInstance().getLogger().log(level, message);
			}
			break;
		case SHUTDOWN:
			if(!silentShutdown){
				AntiShare.getInstance().getLogger().log(level, message);
			}
			break;
		case INFO:
			if(!silent){
				AntiShare.getInstance().getLogger().log(level, message);
			}
			break;
		case BLOCK:
			if(!silentBlocks){
				AntiShare.getInstance().getLogger().log(level, message);
			}
			break;
		case ERROR:
			if(!silentErrors){
				AntiShare.getInstance().getLogger().log(level, message);
			}
		case BYPASS:
			AntiShare.getInstance().getLogger().log(level, message);
			break;
		}
	}

}