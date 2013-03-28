package com.turt2live.antishare.manager;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;

// TODO: Document
public class BlockSaver{

	public static final byte CREATIVE_BYTE = 0x1;
	public static final byte SURVIVAL_BYTE = 0x2;
	public static final byte ADVENTURE_BYTE = 0x3;
	public static final byte NO_GAMEMODE_BYTE = 0x0;

	public static class BlockInfo{
		public Location location;
		public GameMode gamemode;
		public byte raw;
	}

	public static byte fromGameMode(GameMode gamemode){
		if(gamemode == null){
			return 0;
		}
		switch (gamemode){
		case CREATIVE:
			return CREATIVE_BYTE;
		case SURVIVAL:
			return SURVIVAL_BYTE;
		case ADVENTURE:
			return ADVENTURE_BYTE;
		default:
			return NO_GAMEMODE_BYTE;
		}
	}

	public static GameMode fromByte(byte bite){
		switch (bite){
		case CREATIVE_BYTE:
			return GameMode.CREATIVE;
		case SURVIVAL_BYTE:
			return GameMode.SURVIVAL;
		case ADVENTURE_BYTE:
			return GameMode.ADVENTURE;
		default:
			return null;
		}
	}

	public static void save(DataOutputStream out, Location location, GameMode type) throws IOException{
		int x = location.getBlockX();
		int y = location.getBlockY();
		int z = location.getBlockZ();
		byte value = fromGameMode(type);
		byte[] world = location.getWorld().getName().getBytes("UTF-8");
		out.writeInt(x);
		out.writeInt(y);
		out.writeInt(z);
		out.writeByte(value);
		out.writeInt(world.length);
		out.write(world);
	}

	public static BlockInfo getNext(DataInputStream in) throws IOException{
		BlockInfo i = new BlockInfo();
		int x = in.readInt();
		int y = in.readInt();
		int z = in.readInt();
		byte data = in.readByte();
		int readLength = in.readInt();
		byte[] worldArray = new byte[readLength];
		in.read(worldArray);
		String world = new String(worldArray, "UTF-8");
		Location location = new Location(Bukkit.getWorld(world), x, y, z);
		i.location = location;
		i.gamemode = fromByte(data);
		i.raw = data;
		return i;
	}

}
