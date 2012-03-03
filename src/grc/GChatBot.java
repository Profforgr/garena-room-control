/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package grc;

import grc.bot.SQLThread;
import grc.bot.ChatThread;
import grc.plugin.PluginManager;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Scanner;
import java.util.Vector;
import java.util.ArrayList;
import javax.swing.Timer;
import org.apache.commons.configuration.ConversionException;

/**
 *
 * @author wizardus & GG.Dragon
 */
 
public class GChatBot implements GarenaListener {
	//Rank levels
	public static final int LEVEL_ROOT_ADMIN = 10;
	public static final int LEVEL_ADMIN = 6;
	public static final int LEVEL_TRIAL_ADMIN = 5;
	public static final int LEVEL_TRUSTED = 4;
	public static final int LEVEL_VIP = 3;
	public static final int LEVEL_SAFELIST = 2;
	public static final int LEVEL_PUBLIC = 1;
	public static final int LEVEL_SHITLIST = 0;
	public static final int MAIN_CHAT = -1;
	public static final int ANNOUNCEMENT = -2;
	public static final String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";
	
	private int rotateAnn = -1; //Track which announcement the bot is up to
	
	public GarenaInterface garena;
	public PluginManager plugins;
	public SQLThread sqlthread;
	public ChatThread chatthread;
	
	private ArrayList<String> muteList;
	//ArrayList<String> voteLeaver;
	
	//Settings
	private String trigger;
	private int publicDelay;
	private String root_admin; //root admin for this bot; null if root is disabled
	private boolean commandline; //enable commandline input?
	
	private boolean entryLevels; //whether to kick low/high level users when they join the room
	private int minLevel; //minimum entry level to not be kicked from the room upon joining, only used if grc_bot_entry_levels = true
	private int maxLevel; //maximum entry level to not be kicked from the room upon joining, only used if grc_bot_entry_levels = true
	
	private String access_message; //response to when a user does not have enough access to use a command
	private String welcome_message; //sends this message to all public users when they join the room
	private String banned_word_detect_message; //sends this message as an announcement when a user types a banned word
	private String owner; //any string representing who runs the bot
	private boolean enablePublicCommands;
	private boolean showIp; //show ip in whois/whoami
	private boolean userJoinAnnouncement; //display an announcement when a ranked user joins the room
	private boolean publicUserMessage; //whisper a message to any user that has no rank ie new users
	
	//thread safe objects
	public Vector<UserInfo> userDB; //contains all users to ever enter the room, may be quite large
	
	private HashMap<String, String> aliasToCommand; //maps aliases to the command they alias
	private HashMap<String, String[]> commandToAlias; //maps commands to all of the command's aliases
	private Vector<String> rootCommands; //Will contain all commands that can be used
	private Vector<String> adminCommands;
	private Vector<String> trialAdminCommands;
	private Vector<String> trustedCommands;
	private Vector<String> vipCommands;
	private Vector<String> safelistCommands;
	private Vector<String> publicCommands;
	private Vector<String> shitlistCommands;
	
	//for re-initializing parts of GarenaInterface
	Main main;

	public GChatBot(Main main) {
		this.main = main;
		
		//Initialize command lists
		rootCommands = new Vector<String>();
		adminCommands = new Vector<String>();
		trustedCommands = new Vector<String>();
		vipCommands = new Vector<String>();
		safelistCommands = new Vector<String>();
		publicCommands = new Vector<String>();
		shitlistCommands = new Vector<String>();
		
		aliasToCommand = new HashMap<String, String>();
		commandToAlias = new HashMap<String, String[]>();
	}

	public void init() {
		
		//start input thread
		if(commandline) {
			CommandInputThread commandThread = new CommandInputThread(this);
			commandThread.start();
		}
	}

	public String command(MemberInfo member, String command, String payload) {
		int memberRank = getUserRank(member.username.toLowerCase());
		String memberRankTitle = getTitleFromRank(memberRank);
		Main.println("[GChatBot] Received command \"" + command + "\" with payload \"" + payload + "\" from " + memberRankTitle + " " + member.username);

		command = processAlias(command.toLowerCase()); //if it's alias, convert it to original command
		
		//check if we're dealing with command line user, and make sure it's actually commandline
		if(commandline && member.username.equals("commandline") && member.userID == -1 && member.externalIP == null && member.commandline) {
			memberRank = LEVEL_ROOT_ADMIN;
		}

		//flood protection if unvouched user
		if(memberRank == LEVEL_PUBLIC) {
			if(System.currentTimeMillis() - member.lastCommandTime < publicDelay) {
				return null;
			} else {
				member.lastCommandTime = System.currentTimeMillis();
			}
		}
		
		//notify plugins
		String pluginResponse = plugins.onCommand(member, command, payload, memberRank);
		if(pluginResponse != null) {
			return pluginResponse;
		}
		
		/*if(access_message != null) {
			if(LEVEL_ROOT_ADMIN > memberRank) {
				if(rootCommands.contains(command.toLowerCase())) {
					return access_message;
				}
			}
		}*/
		return "Invalid command detected. Please check your spelling and try again";
	}
	
	public String whois(String user) {
		return "";
	}

	public void exit() {
		garena.disconnectRoom();
		System.exit(0);
	}

	public String processAlias(String alias) {
		if(aliasToCommand.containsKey(alias)) {
			return aliasToCommand.get(alias);
		} else {
			return alias;
		}
	}

	public void registerCommand(String command) {
		registerCommand(command, LEVEL_PUBLIC);
	}

	public void registerCommand(String command, int level) {
		//get aliases from configuration file
		String[] aliases = new String[] {command};

		if(GRCConfig.configuration.containsKey("grc_bot_alias_" + command)) {
			try {
				aliases = GRCConfig.configuration.getStringArray("grc_bot_alias_" + command);
			} catch(ConversionException e) {
				Main.println("[GChatBot] Warning: unable to parse entry for alias of " + command);
				aliases = new String[] {command};
			}
		}

		for(String alias : aliases) {
			aliasToCommand.put(alias, command);
		}
		if(level <= LEVEL_ROOT_ADMIN) {
			rootCommands.add(command);
		}
		if(level <= LEVEL_ADMIN) {
			adminCommands.add(command);
		}
		if(level <= LEVEL_TRUSTED) {
			trustedCommands.add(command);
		}
		if(level <= LEVEL_VIP) {
			vipCommands.add(command);
		}
		if(level <= LEVEL_SAFELIST) {
			safelistCommands.add(command);
		}
		if(level <= LEVEL_PUBLIC) {
			publicCommands.add(command);
		}
		if(level <= LEVEL_SHITLIST) {
			shitlistCommands.add(command);
		}
		commandToAlias.put(command, aliases);
	}
	
	public static String arrayToString(String[] a) {
		StringBuilder result = new StringBuilder();
		if (a.length > 0) {
			result.append(a[0]);

			for (int i = 1; i < a.length; i++) {
				result.append(", ");
				result.append(a[i]);
			}
		}
		
		return result.toString();
	}
	
	//Removes all " " from the string
	public String removeSpaces(String text) {
		String result = text.replaceAll(" ", "");
		return result;
	}
	
	//Removes "<" and ">" from ends of the username
	public String trimUsername(String username) {
		if(username.length() > 2) {
			if(username.charAt(username.length()-1) == '>') { //trims > at end of username
				username = username.substring(0, username.length()-1);
			}
			if(username.charAt(0) == '<') { //trims < at start of username
				username = username.substring(1);
			}
		} else {
			return username;
		}
		return username;
	}
	
	public String time() {
		Calendar cal = Calendar.getInstance();
		SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT);
		return sdf.format(cal.getTime());
	}
	
	public String time(int hours) {
		Calendar cal = Calendar.getInstance();
		cal.add(Calendar.HOUR, hours);
		SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT);
		return sdf.format(cal.getTime());
	}
	
	//Check if IP address is valid
	public boolean validIP(String ip) {
		try {
			if(ip == null || ip.isEmpty()) {
				return false;
			}
			String[] parts = ip.split("\\.");
			if(parts.length != 4) {
				return false;
			}
			for(int i = 0; i < parts.length; i++) {
				int num = Integer.parseInt(parts[i]);
				if (num < 0 || num > 255) {
					return false;
				}
			}
			return true;
		} catch(NumberFormatException nfe) {
			return false;
		}
	}
	
	//Add new user to room list, updates info if existing user
	public void addRoomList() {
		
	}
	
	public void addRoot() {
		
	}
	
	//Retrieve userinfo given UID
	public UserInfo userFromID(int uid) {
		for(int i = 0; i < userDB.size(); i++) {
			if(userDB.get(i).userID == uid) {
				return userDB.get(i);
			}
		}
		return null;
	}
	
	//Retrieve userinfo given username in lower case
	public UserInfo userFromName(String name) {
		for(int i = 0; i < userDB.size(); i++) {
			if(userDB.get(i).username.equals(name)) {
				return userDB.get(i);
			}
		}
		return null;
	}
	
	//Retrieve rank given username in lower case
	public int getUserRank(String user) {
		for(int i = 0; i < userDB.size(); i++) {
			if(userDB.get(i).username.equals(user)) {
				return userDB.get(i).rank;
			}
		}
		return LEVEL_PUBLIC;
	}
	
	public String getTitleFromRank(int rank) {
		switch(rank) {
			case LEVEL_ROOT_ADMIN:
				return "Root Admin";
			case LEVEL_ADMIN:
				return "Admin";
			case LEVEL_TRIAL_ADMIN:
				return "Trial Admin";
			case LEVEL_TRUSTED:
				return "Trusted";
			case LEVEL_VIP:
				return "V.I.P.";
			case LEVEL_SAFELIST:
				return "Safelist";
			case LEVEL_PUBLIC:
				return "Random";
			case LEVEL_SHITLIST:
				return "Shitlist";
			default:
				return "Unknown rank";
		}
	}

	public void chatReceived(MemberInfo player, String chat, boolean whisper) {
		int memberRank = getUserRank(player.username.toLowerCase());
		
		if(player != null && chat.startsWith("?trigger")) {
			String trigger_msg = "Trigger: " + trigger;

			if(whisper) {
				chatthread.queueChat(trigger_msg, player.userID);
			} else {
				chatthread.queueChat(trigger_msg, MAIN_CHAT);
			}
		}

		//do we have a command?
		if(player != null && chat.startsWith(trigger) && !chat.substring(1).startsWith(trigger) && !chat.equals(trigger)) {
			//remove trigger from string, and split with space separator
			String[] array = chat.substring(trigger.length()).split(" ", 2);
			String command = array[0];
			String payload = "";

			if(array.length >= 2) {
				payload = array[1];
			}

			String response = command(player, command, payload);
			
			if(response != null) {
				if(whisper) {
					chatthread.queueChat(response, player.userID);
				} else {
					chatthread.queueChat(response, MAIN_CHAT);
				}
			}
		}
	}

	public void playerJoined(MemberInfo player) {
		
	}

	public void playerLeft(MemberInfo player) {

	}

	public void playerStopped(MemberInfo player) {

	}

	public void playerStarted(MemberInfo player) {

	}

	public void disconnected(int x) {
		//try to reconnect

	}
}

class CommandInputThread extends Thread {
	MemberInfo commandlineUser;
	GChatBot bot;
	
	public CommandInputThread(GChatBot bot) {
		this.bot = bot;
		
		commandlineUser = new MemberInfo();
		commandlineUser.username = "commandline";
		commandlineUser.commandline = true;
		commandlineUser.userID = -1;
	}
	
	public void run() {
		Scanner scanner = new Scanner(System.in);

		while(scanner.hasNext()) {
			String chat = scanner.nextLine();
			//remove trigger from string, and split with space separator
			String[] array = chat.split(" ", 2);
			String command = array[0];
			String payload = "";

			if(array.length >= 2) {
				payload = array[1];
			}

			String response = bot.command(commandlineUser, command, payload);

			if(response != null) {
				System.out.println("[RESPONSE] " + response);
			}
		}
	}
}