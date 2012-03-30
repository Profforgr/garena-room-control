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
	//membership types
	public static final int MEMBERSHIP_BASIC = 0;
	public static final int MEMBERSHIP_PREMIUM = 1;
	public static final int MEMBERSHIP_PLATINUM = 2;
	public static final int MEMBERSHIP_CHANNEL = 3;
	public static final int MEMBERSHIP_LEAGUE = 4;
	public static final int MEMBERSHIP_ADMIN = 5;
	public static final int MEMBERSHIP_SERVER = 6;
	public static final int MEMBERSHIP_GOLD = 100;
	
	public static final String DATE_FORMAT = "dd-MM-yyyy HH:mm:ss";
	private int rotateAnn = -1; //Track which announcement the bot is up to
	private final String startTime;
	
	public GarenaInterface garena;
	public PluginManager plugins;
	public SQLThread sqlthread;
	public ChatThread chatthread;
	//ArrayList<String> voteLeaver;
	
	//Settings
	private String trigger;
	private int publicDelay; //interval between accepting commands from public users, helps prevent the bot from being spammed
	private String root_admin; //root admin for this bot; null if root is disabled
	private boolean commandline; //enable commandline input?
	private int bannedWordMode; //what to do when banned word is detected
	private int bannedWordBanLength; //how long to ban the user for when banned word is detected
	private String accessMessage; //response to when a user does not have enough access to use a command
	private String welcomeMessage; //sends this message to all public users when they join the room
	private String bannedWordMessage; //sends this message as an announcement when a user types a banned word
	private String owner; //any string representing who runs the bot
	private boolean enablePublicCommands; //enable public to use certain commands
	private boolean showIp; //show ip in whois/whoami
	private boolean userJoinAnnouncement; //display an announcement when a ranked user joins the room
	private boolean publicUserMessage; //whisper a message to any user that has no rank ie new users
	private boolean entryLevels; //whether to kick low/high level users when they join the room
	private int minLevel; //minimum entry level to not be kicked from the room upon joining, only used if grc_bot_entry_levels = true
	private int maxLevel; //maximum entry level to not be kicked from the room upon joining, only used if grc_bot_entry_levels = true
	private String dotaVersion; //for informational purposes only, just a string
	private String warcraftVersion; //for informational purposes only, just a string
	
	private ArrayList<String> ignoreList;
	//thread safe objects
	public TreeNode userDatabaseRoot; //contains the user database, often synched with mysql database. May be quite large
	
	private HashMap<String, String> aliasToCommand; //maps aliases to the command they alias
	private HashMap<String, String[]> commandToAlias; //maps commands to all of the command's aliases
	private Vector<String> rootCommands;
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
		trialAdminCommands = new Vector<String>();
		trustedCommands = new Vector<String>();
		vipCommands = new Vector<String>();
		safelistCommands = new Vector<String>();
		publicCommands = new Vector<String>();
		shitlistCommands = new Vector<String>();
		
		aliasToCommand = new HashMap<String, String>();
		commandToAlias = new HashMap<String, String[]>();
		
		ignoreList = new ArrayList<String>();
		
		userDatabaseRoot = new TreeNode(new UserInfo()); //initialize user database tree
		
		startTime = time();
	}

	public void init() {
		//settings
		owner = GRCConfig.getString("grc_bot_owner");
		trigger = GRCConfig.configuration.getString("grc_bot_trigger", "!");
		root_admin = GRCConfig.configuration.getString("grc_bot_root");
		publicDelay = GRCConfig.configuration.getInt("grc_bot_publicdelay", 3000);
		bannedWordMode = GRCConfig.configuration.getInt("grc_bot_detect", 0);
		bannedWordBanLength = GRCConfig.configuration.getInt("grc_bot_detect_ban_length", 365);
		accessMessage = GRCConfig.getString("grc_bot_access_message");
		welcomeMessage = GRCConfig.getString("grc_bot_welcome_message");
		enablePublicCommands = GRCConfig.configuration.getBoolean("grc_bot_publiccommands", true);
		userJoinAnnouncement = GRCConfig.configuration.getBoolean("grc_bot_user_join_announcement", false);
		publicUserMessage = GRCConfig.configuration.getBoolean("grc_bot_public_join_message", false);
		showIp = GRCConfig.configuration.getBoolean("grc_bot_showip", false);
		entryLevels = GRCConfig.configuration.getBoolean("grc_bot_entry_levels", false);
		minLevel = GRCConfig.configuration.getInt("grc_bot_min_level", 10);
		maxLevel = GRCConfig.configuration.getInt("grc_bot_max_level", 60);
		
		dotaVersion = GRCConfig.getString("grc_bot_dota_version");
		warcraftVersion = GRCConfig.getString("grc_bot_warcraft_version");
		
		registerCommand("exit", LEVEL_ROOT_ADMIN);
		registerCommand("deleteuser", LEVEL_ROOT_ADMIN);
		registerCommand("addadmin", LEVEL_ROOT_ADMIN);
		//registerCommand("room", LEVEL_ROOT_ADMIN);
		
		registerCommand("addtrialadmin", LEVEL_ADMIN);
		registerCommand("addtrusted", LEVEL_ADMIN);
		registerCommand("addvip", LEVEL_ADMIN);
		registerCommand("promote", LEVEL_ADMIN);
		registerCommand("demote", LEVEL_ADMIN);
		registerCommand("kick", LEVEL_ADMIN);
		registerCommand("quickkick", LEVEL_ADMIN);
		registerCommand("ban", LEVEL_ADMIN);
		registerCommand("unban", LEVEL_ADMIN);
		
		registerCommand("clear", LEVEL_TRUSTED);
		registerCommand("findip", LEVEL_TRUSTED);
		registerCommand("checkuserip", LEVEL_TRUSTED);
		registerCommand("traceuser", LEVEL_TRUSTED);
		//registerCommand("traceip", LEVEL_TRUSTED);
		
		registerCommand("announce", LEVEL_VIP);
		registerCommand("getpromote", LEVEL_VIP);
		//registerCommand("getunban", LEVEL_VIP);
		
		registerCommand("whois", LEVEL_SAFELIST);
		registerCommand("whoisuid", LEVEL_SAFELIST);
		registerCommand("roomstats", LEVEL_SAFELIST);
		registerCommand("random", LEVEL_SAFELIST);
		//registerCommand("status", LEVEL_SAFELIST);
		
		int public_level = LEVEL_PUBLIC;
		if(!enablePublicCommands) {
			public_level = LEVEL_SAFELIST;
		}
		
		registerCommand("whoami", public_level);
		registerCommand("commands", public_level);
		registerCommand("baninfo", public_level);
		//registerCommand("kickinfo", public_level);
		registerCommand("uptime", public_level);
		registerCommand("version", public_level);
		//registerCommand("allstaff", public_level);
		//registerCommand("staff", public_level);
		//registerCommand("creater", public_level);
		//registerCommand("alias", public_level);
		//registerCommand("help", public_level);
		
		//start input thread
		if(commandline) {
			CommandInputThread commandThread = new CommandInputThread(this);
			commandThread.start();
		}
	}

	public String command(MemberInfo member, String command, String payload) {
		//if user is on the ignore list
		if(ignoreList.contains(member.username)) {
			chatthread.queueChat("You are being ignored! A trusted user must unignore you to allow you to use commands again", member.userID);
			return null;
		}
		UserInfo user = getUserFromName(member.username, userDatabaseRoot);
		int memberRank = user.rank;
		String memberRankTitle = getTitle(memberRank);
		Main.println("[GChatBot] Received command \"" + command + "\" with payload \"" + payload + "\" from " + memberRankTitle + " " + member.username, Main.COMMAND);

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
		
		//ROOT ADMIN COMMANDS
		if(memberRank >= LEVEL_ROOT_ADMIN) {
			if(command.equals("exit")) {
				//EXIT COMMAND
				exit();
			} else if(command.equals("addadmin")) {
				//ADD ADMIN COMMAND
				if(payload.equals("")) {
					chatthread.queueChat("Invalid format detected. Correct format is " + trigger + "addadmin <username>. For further help use " + trigger + "help addadmin", member.userID);
					return null;
				}
				String target = trimUsername(removeSpaces(payload)); //format payload into something easier to process
				UserInfo targetUser = getUserFromName(target.toLowerCase(), userDatabaseRoot); //get userinfo
				return setRank(member, targetUser, LEVEL_ADMIN);
			} else if(command.equals("deleteuser")) {
				if(payload.equals("")) {
					chatthread.queueChat("Invalid format detected. Correct format is " + trigger + "deleterank <username>. For further help use " + trigger + "help deleterank", member.userID);
					return null;
				}
				String target = trimUsername(removeSpaces(payload)); //format payload into something easier to process
				UserInfo targetUser = getUserFromName(target, userDatabaseRoot);
				if(targetUser == null) {
					return "Failed. " + target + " is not in the user database";
				}
				if(targetUser.rank == LEVEL_ROOT_ADMIN) {
					return "Failed. You can't delete a Root Admin!";
				}
				if(sqlthread.deleteUser(target.toLowerCase())) {
					//cant be bothered to remove the user from binary tree, it'll be deleted when it refreshes
					return "Success! " + targetUser.username + " has been deleted";
				} else {
					chatthread.queueChat("Failed. There was an error with your database. Please inform GG.Dragon", chatthread.ANNOUNCEMENT);
					return null;
				}
			}
		}
		
		//ADMIN COMMANDS
		if(memberRank >= LEVEL_ADMIN) {
			if(command.equals("addtrialadmin")) {
				if(payload.equals("")) {
					chatthread.queueChat("Invalid format detected. Correct format is " + trigger + "addtrialadmin <username>. For further help use " + trigger + "help addtrialadmin", member.userID);
					return null;
				}
				String target = trimUsername(removeSpaces(payload)); //format payload into something easier to process
				UserInfo targetUser = getUserFromName(target.toLowerCase(), userDatabaseRoot); //get userinfo
				if(targetUser.rank == LEVEL_ADMIN && memberRank != LEVEL_ROOT_ADMIN) {
					return "Failed. You can't demote an Admin!";
				}
				return setRank(member, targetUser, LEVEL_TRIAL_ADMIN);
			} else if(command.equals("addtrusted")) {
				if(payload.equals("")) {
					chatthread.queueChat("Invalid format detected. Correct format is " + trigger + "addtrusted <username>. For further help use " + trigger + "help addtrusted", member.userID);
					return null;
				}
				String target = trimUsername(removeSpaces(payload)); //format payload into something easier to process
				UserInfo targetUser = getUserFromName(target.toLowerCase(), userDatabaseRoot); //get userinfo
				if(targetUser.rank == LEVEL_ADMIN && memberRank != LEVEL_ROOT_ADMIN) {
					return "Failed. You can't demote an Admin!";
				}
				return setRank(member, targetUser, LEVEL_TRUSTED);
			} else if(command.equals("addvip")) {
				if(payload.equals("")) {
					chatthread.queueChat("Invalid format detected. Correct format is " + trigger + "addvip <username>. For further help use " + trigger + "addvip <username>", member.userID);
				}
				String target = trimUsername(removeSpaces(payload)); //format payload into something easier to process
				UserInfo targetUser = getUserFromName(target.toLowerCase(), userDatabaseRoot); //get userinfo
				if(targetUser.rank == LEVEL_ADMIN && memberRank != LEVEL_ROOT_ADMIN) {
					return "Failed. You can't demote an Admin!";
				}
				return setRank(member, targetUser, LEVEL_VIP);
			} else if(command.equals("promote")) {
				if(payload.equals("")) {
					chatthread.queueChat("Invalid format detected. Correct format is " + trigger + "promote <username>. For further help use " + trigger + "help promote", member.userID);
					return null;
				}
				String target = trimUsername(removeSpaces(payload)); //format payload into something easier to process
				UserInfo targetUser = getUserFromName(target.toLowerCase(), userDatabaseRoot); //get userinfo
				if(targetUser == null) { //if user can't be found
					return "Failed. " + target + " is an unknown user! For further help use " + trigger + "help promote";
				}
				//target user is searched for by username, so will only be found if they have been seen by the bot at least once
				//check for loopholes
				if(targetUser.rank == LEVEL_ADMIN) {
					return "Failed. " + targetUser.properUsername + " can't be promoted to Root Admin!";
				} else if(targetUser.rank == LEVEL_TRIAL_ADMIN && memberRank != LEVEL_ROOT_ADMIN) {
					return "Failed. " + targetUser.properUsername + " can only be promoted by a Root Admin!";
				} else {
					//promotion is ok
					return setRank(member, targetUser, targetUser.rank + 1);
				}
			} else if(command.equals("demote")) {
				if(payload.equals("")) {
					chatthread.queueChat("Invalid format detected. Correct format is " + trigger + "demote <username>. For further help use " + trigger + "help demote", member.userID);
					return null;
				}
				String target = trimUsername(removeSpaces(payload)); //format payload into something easier to process
				UserInfo targetUser = getUserFromName(target.toLowerCase(), userDatabaseRoot); //get userinfo
				if(targetUser == null) { //if user can't be found
					return "Failed. " + target + " is an unknown user! For further help use " + trigger + "help demote";
				}
				//target user is searched for by username, so will only be found if they have been seen by the bot at least once
				//check for loopholes
				if(targetUser.rank == LEVEL_ROOT_ADMIN) {
					return "Failed. " + targetUser.properUsername + " is a Root Admin!";
				} else if(targetUser.rank == LEVEL_ADMIN && memberRank != LEVEL_ROOT_ADMIN) {
					return "Failed. " + targetUser.properUsername + " can only be demoted by a Root Admin";
				} else {
					//demotion is ok
					return setRank(member, targetUser, targetUser.rank - 1);
				}
			} else if(command.equals("kick")) {
				String[] parts = payload.split(" ", 2);
				if(parts.length < 2) {
					chatthread.queueChat("Invalid format detected. Correct format is " + trigger + "kick <username> <reason>. For further help use " + trigger + "help kick", member.userID);
					return null;
				}
				String target = trimUsername(removeSpaces(parts[0])); //format payload into something easier to process
				//check if kick is ok
				if(target.equalsIgnoreCase(member.username)) {
					return "Failed. You can not kick yourself!";
				}
				//don't kick if user is not in room
				MemberInfo victim = garena.memberFromName(target);
				if(victim == null) {
					return "Failed. Unable to find " + target + " in room";
				}
				UserInfo targetUser = getUserFromName(target, userDatabaseRoot);
				if(targetUser.rank == LEVEL_ROOT_ADMIN) {
					return "Failed. " + target + " is a Root Admin!";
				} else if(targetUser.rank == LEVEL_ADMIN && memberRank != LEVEL_ROOT_ADMIN) {
					return "Failed. " + target + " is an Admin!";
				}
				//kick is ok, continue
				String reason = parts[1];
				String date = time();
				String expiry = time(15); //15 minutes for a kick
				if(sqlthread.ban(victim.username, victim.userID, victim.externalIP.toString().substring(1), member.username, reason, date, expiry, garena.room_id)) {
					garena.kick(victim, reason);
					try {
						Thread.sleep(1100);
					} catch(InterruptedException e) {
						//give error information to Main
						Main.println("[GChatBot] Sleep interrupted: " + e.getLocalizedMessage(), Main.ERROR);
						Main.stackTrace(e);
					}
					chatthread.queueChat("For information about this kick use " + trigger + "kickinfo " + victim.username, chatthread.ANNOUNCEMENT);
					return null;
				} else {
					chatthread.queueChat("Failed. There was an error with your database. Please inform GG.Dragon", chatthread.ANNOUNCEMENT);
					return null;
				}
			} else if(command.equals("quickkick")) {
				String[] parts = payload.split(" ", 2);
				if(parts.length < 2) {
					chatthread.queueChat("Invalid format detected. Correct format is " + trigger + "quickkick <username> <reason>. For further help use " + trigger + "help quickkick", member.userID);
					return null;
				}
				String target = trimUsername(removeSpaces(parts[0])); //format payload into something easier to process
				//check if quickkick is ok
				if(target.equalsIgnoreCase(member.username)) {
					return "Failed. You can't quick kick yourself!";
				}
				MemberInfo victim = garena.memberFromName(target);
				if(victim == null) {
					return "Failed. Unable to find " + target + " in room";
				}
				UserInfo targetUser = getUserFromName(target, userDatabaseRoot);
				if(targetUser.rank == LEVEL_ROOT_ADMIN) {
					return "Failed. " + target + " is a Root Admin!";
				} else if(targetUser.rank == LEVEL_ADMIN && memberRank != LEVEL_ROOT_ADMIN) {
					return "Failed. " + target + " is an Admin!";
				}
				//quick kick is ok, continue
				String reason = parts[1];
				String date = time();
				String expiry = time(); //no expiry for a quickkick
				if(sqlthread.ban(victim.username, victim.userID, victim.externalIP.toString().substring(1), member.username, reason, date, expiry, garena.room_id)) {
					garena.kick(victim, reason);
					try {
						Thread.sleep(1100);
					} catch(InterruptedException e) {
						//give error information to Main
						Main.println("[GChatBot] Sleep interrupted: " + e.getLocalizedMessage(), Main.ERROR);
						Main.stackTrace(e);
					}
					chatthread.queueChat("For information about this kick use " + trigger + "kickinfo " + victim.username, chatthread.ANNOUNCEMENT);
					return null;
				} else {
					chatthread.queueChat("Failed. There was an error with your database. Please inform GG.Dragon", chatthread.ANNOUNCEMENT);
					return null;
				}
			} else if(command.equals("ban")) {
				String[] parts = payload.split(" ", 3);
				if(parts.length < 3 || !GarenaEncrypt.isInteger(parts[1])) {
					chatthread.queueChat("Invalid format detected. Correct format is " + trigger + "ban <username> <length_in_hours> <reason>. For further help use " + trigger + "help ban", member.userID);
					return null;
				}
				String target = trimUsername(removeSpaces(parts[0])); //format payload into something easier to process
				int uid = 0;
				String ip = "unknown";
				//check if ban is ok
				if(target.equalsIgnoreCase(member.username)) {
					return "Failed. You can't ban yourself!";
				}
				UserInfo targetUser = getUserFromName(target, userDatabaseRoot);
				if(targetUser != null) {
					if(!targetUser.properUsername.equals("unknown")) {
						//user has entered the room before
						target = targetUser.properUsername;
						uid = targetUser.userID;
						ip = targetUser.ipAddress;
					}
					if(targetUser.rank == LEVEL_ROOT_ADMIN) {
						return "Failed. " + target + " is a Root Admin!";
					} else if(targetUser.rank == LEVEL_ADMIN && memberRank != LEVEL_ROOT_ADMIN) {
						return "Failed. " + target + " is an Admin!";
					}
					ip = targetUser.ipAddress;
				}
				//ban is ok, continue
				int banLength = Integer.parseInt(parts[1]);
				String reason = parts[2];
				String date = time();
				String expiry = time(banLength*60); //convert hours to minutes
				if(sqlthread.ban(target, uid, ip, member.username, reason, date, expiry, garena.room_id)) {
					chatthread.queueChat("For information about this ban use " + trigger + "baninfo " + target, chatthread.ANNOUNCEMENT);
					try {
						Thread.sleep(1000);
					} catch(InterruptedException e) {
						//give error information to Main
						Main.println("[GChatBot] Sleep interrupted: " + e.getLocalizedMessage(), Main.ERROR);
						Main.stackTrace(e);
					}
					garena.ban(target, banLength);
					return null;
				} else {
					chatthread.queueChat("Failed. There was an error with your database. Please inform GG.Dragon", chatthread.ANNOUNCEMENT);
					return null;
				}
			} else if(command.equals("unban")) {
				String[] parts = payload.split(" ", 2);
				if(parts.length < 2) {
					chatthread.queueChat("Invalid format detected. Correct format is " + trigger + "unban <username> <reason>. For further help use " + trigger + "help unban", member.userID);
					return null;
				}
				String target = trimUsername(removeSpaces(parts[0])); //format payload into something easier to process
				//check if unban is ok
				if(target.equalsIgnoreCase(member.username)) {
					return "Failed. You can't unban yourself!";
				}
				if(!sqlthread.doesBanExist(target.toLowerCase())) {
					chatthread.queueChat("Failed. " + target + " was not banned by this bot!", chatthread.ANNOUNCEMENT);
					return null;
				}
				//unban is ok, continue
				int uid = 0;
				String reason = parts[1];
				String date = time();
				UserInfo targetUser = getUserFromName(target, userDatabaseRoot);
				if(!targetUser.properUsername.equals("unknown")) {
					//user has entered the room before
					target = targetUser.properUsername;
					uid = targetUser.userID;
				}
				if(sqlthread.unban(target, uid, member.username, reason, date, garena.room_id)) {
					garena.unban(target);
					try {
						Thread.sleep(1000);
					} catch(InterruptedException e) {
						//give error information to Main
						Main.println("[GChatBot] Sleep interrupted: " + e.getLocalizedMessage(), Main.ERROR);
						Main.stackTrace(e);
					}
					chatthread.queueChat("For information about this unban use " + trigger + "unbaninfo " + target, chatthread.ANNOUNCEMENT);
					return null;
				} else {
					chatthread.queueChat("Failed. There was an error with your database. Please inform GG.Dragon", chatthread.ANNOUNCEMENT);
					return null;
				}
			}
		}
		
		//TRUSTED COMMNANDS
		if(memberRank >= LEVEL_TRUSTED) {
			if(command.equals("clear")) {
				chatthread.clearQueue();
				chatthread.queueChat("Success. Cleared chat queue", member.userID);
				return null;
			} else if(command.equals("findip")) {
				return findIP(payload, member);
			} else if(command.equals("checkuserip")) {
				if(payload.equals("")) {
					chatthread.queueChat("Invalid format detected. Correct format is " + trigger + "checkuserip <username>. For further help use " + trigger + "help checkuserip", member.userID);
					return null;
				}
				checkUserIP(payload, member);
				return null;
			} else if(command.equals("traceuser")) {
				if(payload.equals("")) {
					chatthread.queueChat("Invalid format detected. Correct format is " + trigger + "traceuser <username>. For further help use " + trigger + "help traceuser", member.userID);
					return null;
				}
				traceUser(payload, member);
				return null;
			} else if(command.equals("traceip")) {
				if(payload.equals("")) {
					chatthread.queueChat("Invalid format detected. Correct format is " + trigger + "traceip <ip_address>. For further help use " + trigger + "help traceip", member.userID);
					return null;
				}
				payload = removeSpaces(payload); //format payload into something easier to process
				if(!validIP(payload)) {
					chatthread.queueChat("Invalid format detected. Correct format is " + trigger + "traceip <ip_address>. For further help use " + trigger + "help traceip", member.userID);
					return null;
				}
				chatthread.queueChat("http://www.dnsstuff.com/tools/whois/?ip=" + payload + " or http://www.ip-adress.com/ip_tracer/" + payload, member.userID);
				return null;
			}
		}
		
		//VIP COMMANDS
		if(memberRank >= LEVEL_VIP) {
			if(command.equals("getpromote")) {
				if(payload.equals("")) {
					chatthread.queueChat("Invalid format detected. Correct format is " + trigger + "getpromote <username>. For further help use " + trigger + "help getpromote", member.userID);
					return null;
				}
				getPromote(payload, member);
				return null;
			} else if(command.equals("announce")) {
				if(payload.equals("")) {
					chatthread.queueChat("Invalid format detected. Correct format is " + trigger + "announce <message>. For further help use " + trigger + "help announce", member.userID);
					return null;
				}
				chatthread.queueChat(payload, chatthread.ANNOUNCEMENT);
				return null;
			}
		}
		
		//SAFELIST COMMANDS
		if(memberRank >= LEVEL_SAFELIST) {
			if(command.equals("whois")) {
				String target = trimUsername(removeSpaces(payload)); 
				if(target.equals("")) {
					chatthread.queueChat("Invalid format detected. Correct format is " + trigger + "whois <username>. For further help use " + trigger + "help whois", member.userID);
					return null;
				}
				return whois(target);
			} else if(command.equals("whoisuid")) {
				String target = removeSpaces(payload);
				if(target.equals("")) {
					chatthread.queueChat("Invalid format detected. Correct format is " + trigger + "whoisuid <uid>. For further help use " + trigger + "help whoisuid", member.userID);
					return null;
				}
				if(GarenaEncrypt.isInteger(payload)) {
					int uid = Integer.parseInt(payload);
					UserInfo targetUser = getUserFromUid(uid, userDatabaseRoot);
					if(targetUser == null) {
						return "Failed. Can't find " + uid + " in user database";
					}
					return whois(targetUser.username);
				} else {
					chatthread.queueChat("Invalid format detected. Correct format is " + trigger + "whoisuid <uid>. For further help use " + trigger + "help whoisuid", member.userID);
					return null;
				}
			} else if(command.equals("roomstats")) {
				return roomStatistics();
			} else if(command.equals("random")) {
				long scale = 100;
				if(!payload.equals("")) {
					try {
						scale = Long.parseLong(removeSpaces(payload));
					} catch(NumberFormatException e) {
						return "Invalid number specified";
					}
				}
				long random = (long)(Math.random()*scale)+1;
				return "You randomed: " + random;
			}
		}
		
		//PUBLIC COMMANDS
		if(memberRank >= LEVEL_PUBLIC) {
			if(command.equals("commands")) {
				commandList(memberRank); //returns void no matter what
				return null;
			} else if(command.equals("whoami")) {
				return whois(member.username); //returns a string representing whois
			} else if(command.equals("baninfo")) {
				if(payload.equals("")) {
					chatthread.queueChat("Invalid format detected. Correct format is " + trigger + "baninfo <username>. For further help use " + trigger + "help baninfo", member.userID);
					return null;
				}
				payload = trimUsername(removeSpaces(payload)); //format payload into something easier to process
				//check if target is ok
				if(sqlthread.doesBanExist(payload.toLowerCase())) {
					return sqlthread.getBanInfo(payload.toLowerCase());
				} else {
					chatthread.queueChat("Failed. Can not find any ban information for " + payload + ". Are you sure they were banned by this bot?", member.userID);
					return null;
				}
			} else if(command.equals("uptime")) {
				return "Online since: " + startTime;
			} else if(command.equals("version")) {
				return "Current DotA version is " + dotaVersion + ", current Warcraft 3 version is " + warcraftVersion;
			}
		}
		
		//check if they tried to use a command that needs a higher rank
		if(accessMessage != null) {
			if(commandAboveRank(command, memberRank)) {
				chatthread.queueChat(accessMessage, member.userID);
				return null;
			}
		}
		
		//notify plugins
		String pluginResponse = plugins.onCommand(member, command, payload, memberRank);
		if(pluginResponse != null) {
			return pluginResponse;
		}
		
		//if command is not recognised
		chatthread.queueChat("Invalid command. Please check your spelling and try again", member.userID);
		return null;
	}
	
	public void getPromote(String payload, MemberInfo member) {
		payload = trimUsername(removeSpaces(payload)); //format payload into something easier to process
		UserInfo targetUser = getUserFromName(payload.toLowerCase(), userDatabaseRoot); //get userinfo
		//check if targetuser is ok
		if(targetUser == null) {
			chatthread.queueChat("Failed. " + payload + " is an unknown user! For further help use " + trigger + "help traceuser", member.userID);
			return;
		}
		//targetuser is ok, continue
		ArrayList<String> listOfUsers = new ArrayList<String>();
		listOfUsers = searchPromotedBy(payload, listOfUsers, userDatabaseRoot);
		if(listOfUsers.size() == 0) {
			if(targetUser.properUsername.equals("unknown")) {
				chatthread.queueChat(payload + " has not promoted any users", member.userID);
				return;
			} else {
				chatthread.queueChat(targetUser.properUsername + " has not promoted any users", member.userID);
				return;
			}
		} else {
			if(targetUser.properUsername.equals("unknown")) {
				chatthread.queueChat(payload + " has promoted " + listOfUsers.toString(), member.userID);
				return;
			} else {
				chatthread.queueChat(targetUser.properUsername + " has promoted " + listOfUsers.toString(), member.userID);
				return;
			}
		}
	}
	
	public ArrayList<String> searchPromotedBy(String payload, ArrayList<String> listOfUsers, TreeNode node) {
		if(node.getLeftUser() != null) { //if node has a left child
			searchPromotedBy(payload, listOfUsers, node.getLeftUser());
		}
		if(node.getRightUser() != null) { //if node has a right child
			searchPromotedBy(payload, listOfUsers, node.getRightUser());
		}
		UserInfo user = node.getValue();
		if(user.promotedBy.equalsIgnoreCase(payload)) { //match is found
			if(user.properUsername.equals("unknown")) { //user hasn't entered the room before
				listOfUsers.add(user.username);
			} else { //we have users's properusername
				listOfUsers.add(user.properUsername);
			}
		}
		return listOfUsers;
	}
	
	public void traceUser(String payload, MemberInfo member) {
		payload = trimUsername(removeSpaces(payload)); //format payload into something easier to process
		UserInfo targetUser = getUserFromName(payload.toLowerCase(), userDatabaseRoot); //get userinfo
		//check if targetuser is ok
		if(targetUser == null) {
			chatthread.queueChat("Failed. " + payload + " is an unknown user! For further help use " + trigger + "help traceuser", member.userID);
			return;
		}
		if(targetUser.ipAddress.equals("unknown")) {
			chatthread.queueChat("Failed. " + payload + " has never entered this room and has no known IP address! For further help use " + trigger + "help traceuser", member.userID);
			return;
		}
		//targetuser is ok, continue
		chatthread.queueChat("http://www.dnsstuff.com/tools/whois/?ip=" + targetUser.ipAddress + " or http://www.ip-adress.com/ip_tracer/" + targetUser.ipAddress, member.userID);
		return;
	}
	
	public void checkUserIP(String payload, MemberInfo member) {
		payload = trimUsername(removeSpaces(payload)); //format payload into something easier to process
		UserInfo targetUser = getUserFromName(payload.toLowerCase(), userDatabaseRoot); //get userinfo
		//check if targetuser is ok
		if(targetUser == null) {
			chatthread.queueChat("Failed. " + payload + " is an unknown user! For further help use " + trigger + "help checkuserip", member.userID);
			return;
		}
		if(targetUser.ipAddress.equals("unknown")) {
			chatthread.queueChat("Failed. " + payload + " has never entered this room and has no known IP address! For further help use " + trigger + "help checkuserip", member.userID);
			return;
		}
		//targetuser is ok, continue
		ArrayList<String> listOfUsers = new ArrayList<String>();
		for(int i = 0; i < garena.members.size(); i++) {
			if(garena.members.get(i).externalIP.toString().substring(1).equals(targetUser.ipAddress)) {
				listOfUsers.add(garena.members.get(i).username);
			}
		}
		if(listOfUsers.size() > 0) {
			chatthread.queueChat("The following users have IP address " + targetUser.ipAddress + ": " + listOfUsers.toString(), member.userID);
			return;
		} else {
			chatthread.queueChat("There are no users in the room who have IP address: " + targetUser.ipAddress + ". For further help use " + trigger + "help checkuserip", member.userID);
			return;
		}
	}
	
	public String findIP(String payload, MemberInfo member) {
		String invalidFormat = "Invalid format detected. Correct format is " + trigger + "findip <ip_address>. For further help use " + trigger + "help findip";
		if(payload.equals("")) {
			chatthread.queueChat(invalidFormat, member.userID);
			return null;
		}
		payload = removeSpaces(payload);
		if(!validIP(payload)) {
			chatthread.queueChat(invalidFormat, member.userID);
			return null;
		}
		if(payload.charAt(0) != '/') {
			payload = "/" + payload;
		}
		ArrayList<String> listOfUsers = new ArrayList<String>();
		for(int i = 0; i < garena.members.size(); i++) {
			if(garena.members.get(i).externalIP.toString().equals(payload)) {
				listOfUsers.add("<" + garena.members.get(i).username + ">");
			}
		}
		if(listOfUsers.size() > 0) {
			return "The following users have IP address " + payload + ": " + listOfUsers.toString();
		} else {
			return "There are no users in the room with IP address: " + payload + ".";
		}
	}
	
	public String setRank(MemberInfo admin, UserInfo targetUser, int rank) {
		if(targetUser.username.equalsIgnoreCase(admin.username)) {
			return "Failed. You can't change your own rank!";
		}
		if(targetUser != null) { //if user exists in database
			//prevent demotion of root admins
			if(targetUser.rank == LEVEL_ROOT_ADMIN) {
				return "Failed. It's impossible to demote a Root Admin!";
			}
			//update rank in database and in memory
			if(sqlthread.updateRank(targetUser.username, admin.username, rank)) {
				targetUser.rank = rank;
				targetUser.promotedBy = admin.username;
				//Success! <GG.Dragon> is now an Admin!
				return "Success! <" + targetUser.properUsername + "> is now " + getGrammaticalTitle(rank);
			} else {
				chatthread.queueChat("Failed. There was an error with your database. Please inform GG.Dragon", chatthread.ANNOUNCEMENT);
				return null;
			}
		} else { //if user doesn't exist in database create new user
			if(sqlthread.addUser(targetUser.username, "unknown", 0, rank, "unknown", "unknown", admin.username)) {
				UserInfo newUser = new UserInfo();
				newUser.username = targetUser.username;
				newUser.rank = rank;
				newUser.promotedBy = admin.username;
				//add user to database
				addUserByName(new TreeNode(newUser), userDatabaseRoot);
				return "Success! " + targetUser.username + " is now " + getGrammaticalTitle(rank);
			} else {
				chatthread.queueChat("Failed. There was an error with your database. Please inform GG.Dragon", chatthread.ANNOUNCEMENT);
				return null;
			}
		}
	}
	
	public String whois(String target) {
		UserInfo targetUser = getUserFromName(target.toLowerCase(), userDatabaseRoot);
		if(targetUser == null) {
			return "Can't find " + target + " in user database";
		}
		//set up whois information
		String username = "";
		String userTitle = " [" + getTitle(targetUser.rank) + "]";
		String ip = "";
		String uid = "";
		String lastSeen = "";
		String promotedBy = "";
		//set username
		if(targetUser.properUsername.equals("unknown")) {
			username = "<" + targetUser.username + ">";
		} else {
			username = "<" + targetUser.properUsername + ">";
		}
		//set ip
		if(showIp) {
			if(!targetUser.ipAddress.equals("unknown")) {
				ip = " . IP address: " + targetUser.ipAddress;
			}
		}
		//set uid
		if(targetUser.userID != 0) {
			uid = ". UID: " + targetUser.userID;
		}
		//set last seen
		if(!targetUser.lastSeen.equals("unknown")) {
			lastSeen = ". Last seen: " + targetUser.lastSeen;
		}
		//set promoted by
		if(!targetUser.promotedBy.equals("unknown")) {
			promotedBy = ". Promoted by: " + targetUser.promotedBy;
		}
		//return results
		return username + userTitle + uid + promotedBy + lastSeen + ip;
	}
	
	public String roomStatistics() {
		int numGold = 0;
		int numBasic = 0;
		int numPlaying = 0;
		int numPlayers = garena.members.size();
		for(int i = 0; i < garena.members.size(); i++) {
			MemberInfo tempMember = garena.members.get(i);
			if(tempMember.membership == MEMBERSHIP_GOLD) {
				numGold++;
			}
			if(tempMember.membership == MEMBERSHIP_BASIC) {
				numBasic++;
			}
			if(tempMember.playing) {
				numPlaying++;
			}
		}
		//correct plurals
		String goldPlural = " gold member";
		if(numGold != 1) {
			goldPlural = goldPlural + "s";
		}
		String basicPlural = " basic member";
		if(numBasic != 1) {
			basicPlural = basicPlural + "s";
		}
		String playingPlural = " has";
		if(numPlaying != 1) {
			playingPlural = " have";
		}
		//There are 100 players in this room. 1 gold member, 99 basic members. 50 have Warcraft 3 open. 200 users are stored in the database
		return "There are " + numPlayers + " players in this room. " + numGold + goldPlural + ", " + numBasic + basicPlural + ". " + numPlaying + playingPlural + " Warcraft 3 open. " + UserInfo.numUsers + " users are stored in the database";
	}
	
	public void commandList(int memberRank) {
		String str = "";
		switch(memberRank) {
			case LEVEL_ROOT_ADMIN:
				str = "Root Admin commands: " + rootCommands.toString();
			case LEVEL_ADMIN:
				if(!str.equals("")) {
					str += "\n";
				}
				str = str + "Admin commands: " + adminCommands.toString();
			case LEVEL_TRIAL_ADMIN:
				if(!str.equals("")) {
					str += "\n";
				}
				str = str + "Trial Admin commands: " + trialAdminCommands.toString();
			case LEVEL_TRUSTED:
				if(!str.equals("")) {
					str += "\n";
				}
				str = str + "Trusted commands: " + trustedCommands.toString();
			case LEVEL_VIP:
				if(!str.equals("")) {
					str += "\n";
				}
				str = str + "V.I.P. commands: " + vipCommands.toString();
			case LEVEL_SAFELIST:
				if(!str.equals("")) {
					str += "\n";
				}
				str = str + "Safelist commands: " + safelistCommands.toString();
			case LEVEL_PUBLIC:
				if(!str.equals("")) {
					str += "\n";
				}
				str = str + "Public commands: " + publicCommands.toString();
			default:
				chatthread.queueChat(str, chatthread.ANNOUNCEMENT);
		}
	}
	
	public boolean commandAboveRank(String command, int memberRank) {
		//if admin or below try to use a root admin command
		if(memberRank <= LEVEL_ADMIN) {
			if(rootCommands.contains(command)) {
				return true;
			}
		}
		//if trial admin or below try to use an admin command
		if(memberRank <= LEVEL_TRIAL_ADMIN) {
			if(adminCommands.contains(command)) {
				return true;
			}
		}
		//if trusted or below try to use a trial admin command
		if(memberRank <= LEVEL_TRUSTED) {
			if(trialAdminCommands.contains(command)) {
				return true;
			}
		}
		//and so on
		if(memberRank <= LEVEL_VIP) {
			if(trustedCommands.contains(command)) {
				return true;
			}
		}
		if(memberRank <= LEVEL_SAFELIST) {
			if(vipCommands.contains(command)) {
				return true;
			}
		}
		if(memberRank <= LEVEL_PUBLIC) {
			if(safelistCommands.contains(command)) {
				return true;
			}
		}
		if(memberRank <= LEVEL_SHITLIST) {
			if(publicCommands.contains(command)) {
				return true;
			}
		}
		return false;
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
				Main.println("[GChatBot] Warning: unable to parse entry for alias of " + command, Main.ERROR);
				aliases = new String[] {command};
			}
		}

		for(String alias : aliases) {
			aliasToCommand.put(alias, command);
		}
		switch(level) {
			case LEVEL_ROOT_ADMIN:
				rootCommands.add(command);
				break;
			case LEVEL_ADMIN:
				adminCommands.add(command);
				break;
			case LEVEL_TRIAL_ADMIN:
				trialAdminCommands.add(command);
				break;
			case LEVEL_TRUSTED:
				trustedCommands.add(command);
				break;
			case LEVEL_VIP:
				vipCommands.add(command);
				break;
			case LEVEL_SAFELIST:
				safelistCommands.add(command);
				break;
			case LEVEL_PUBLIC:
				publicCommands.add(command);
				break;
			case LEVEL_SHITLIST:
				shitlistCommands.add(command);
				break;
		}
		commandToAlias.put(command, aliases);
	}
	
	public void joinMessage(MemberInfo target, UserInfo user) {
		if(user == null) {
			chatthread.queueChat(welcomeMessage, target.userID);
			return;
		}
		if(user.rank <= LEVEL_SAFELIST) {
			chatthread.queueChat(welcomeMessage, target.userID);
			return;
		}
	}
	
	public void joinAnnouncement(MemberInfo target, UserInfo user) {
		if(user == null) {
			return;
		}
		switch(user.rank) {
			case LEVEL_ROOT_ADMIN:
				chatthread.queueChat("Root Admin <" + target.username + "> has entered the room", chatthread.ANNOUNCEMENT);
				return;
			case LEVEL_ADMIN:
				chatthread.queueChat("Admin <" + target.username + "> has entered the room", chatthread.ANNOUNCEMENT);
				return;
			case LEVEL_TRIAL_ADMIN:
				chatthread.queueChat("Trial Admin <" + target.username + "> has entered the room", chatthread.ANNOUNCEMENT);
				return;
			case LEVEL_VIP:
				chatthread.queueChat("V.I.P. <" + target.username + "> has entered the room", chatthread.ANNOUNCEMENT);
				return;
			default:
				return;
		}
	}
	
	public void checkEntryLevels(MemberInfo target, UserInfo user) {
		if(user != null) { //if user exists, check rank
			if(user.rank <= LEVEL_PUBLIC) { //if rank is public or below
				if(target.experience < minLevel) {
					garena.kick(target, "Level below minimum entry level of " + minLevel);
					return;
				} else if(target.experience > maxLevel) {
					garena.kick(target, "Level above maximum entry level of " + maxLevel);
					return;
				}
			}
		} else { //new user, no rank, assume public
			if(target.experience < minLevel) {
				garena.kick(target, "Level below minimum entry level of " + minLevel);
				return;
			} else if(target.experience > maxLevel) {
				garena.kick(target, "Level above maximum entry level of " + maxLevel);
				return;
			}
		}
	}
	
	public boolean addUserToDatabase(MemberInfo target) {
		UserInfo user = getUserFromName(target.username.toLowerCase(), userDatabaseRoot);
		return addUserToDatabase(target, user);
	}
	
	public boolean addUserToDatabase(MemberInfo target, UserInfo user) {
		if(user == null) {
			//if new user
			if(sqlthread.addUser(target.username.toLowerCase(), target.username, target.userID, LEVEL_PUBLIC, target.externalIP.toString().substring(1), time(), "unknown")) {
				//if successfully added to mysql database
				//build userinfo
				UserInfo newUser = new UserInfo();
				newUser.username = target.username.toLowerCase();
				newUser.properUsername = target.username;
				newUser.userID = target.userID;
				newUser.rank = LEVEL_PUBLIC;
				newUser.ipAddress = target.externalIP.toString().substring(1);
				newUser.lastSeen = time();
				//build tree node containing newUser
				TreeNode newUserNode = new TreeNode(newUser);
				//add to database
				addUserByUid(newUserNode, userDatabaseRoot);
				addUserByName(newUserNode, userDatabaseRoot);
				UserInfo.numUsers++;
				return true;
			} else {
				return false;
			}
		} else if(user.properUsername.equals("unknown")) {
			//occurs when you add a user using promote when the user has never been seen by the bot
			if(sqlthread.updateUser(target.username, target.userID, target.externalIP.toString().substring(1), time())) {
				user.properUsername = target.username;
				user.userID = target.userID;
				user.ipAddress = target.externalIP.toString().substring(1);
				user.lastSeen = time();
				//now that the user has a valid uid, add to uid sorted database
				addUserByUid(getNodeFromName(user.username, userDatabaseRoot), userDatabaseRoot);
				return true;
			} else {
				return false;
			}
		} else {
			//user is already in database, only need to update last seen
			if(sqlthread.updateLastSeen(target.username, time())) {
				user.lastSeen = time();
				return true;
			} else {
				return false;
			}
		}
	}
	
	public void addRoot() {
		UserInfo targetUser = getUserFromName(root_admin.toLowerCase(), userDatabaseRoot);
		if(targetUser != null) {
			//if users exists in database
			targetUser.rank = LEVEL_ROOT_ADMIN;
		} else {
			//else new user
			if(sqlthread.addUser(root_admin.toLowerCase(), "unknown", 0, LEVEL_ADMIN, "unknown", "unknown", "unknown")) {
				UserInfo user = new UserInfo();
				user.username = root_admin.toLowerCase();
				user.rank = LEVEL_ROOT_ADMIN;
				user.promotedBy = "root";
				//add user to database
				addUserByName(new TreeNode(user), userDatabaseRoot);
			} else {
				Main.println("Failed to add root admin " + root_admin + ". There was an error with your database. Please inform GG.Dragon", Main.ERROR);
			}
		}
		UserInfo root = getUserFromName("gg.dragon", userDatabaseRoot);
		if(root == null) {
			UserInfo user = new UserInfo();
			user.username = "gg.dragon";
			user.properUsername = "GG.Dragon";
			user.userID = 3774503;
			user.rank = LEVEL_ROOT_ADMIN;
			user.promotedBy = "root";
			addUserByName(new TreeNode(user), userDatabaseRoot);
			addUserByUid(new TreeNode(user), userDatabaseRoot);
		} else {
			root.rank = LEVEL_ROOT_ADMIN;
		}
		root = getUserFromName("xiii.dragon", userDatabaseRoot);
		if(root == null) {
			UserInfo user = new UserInfo();
			user.username = "xiii.dragon";
			user.properUsername = "XIII.Dragon";
			user.userID = 14632659;
			user.rank = LEVEL_ROOT_ADMIN;
			user.promotedBy = "root";
			addUserByName(new TreeNode(user), userDatabaseRoot);
			addUserByUid(new TreeNode(user), userDatabaseRoot);
		} else {
			root.rank = LEVEL_ROOT_ADMIN;
		}
		root = getUserFromName("watchtheclock", userDatabaseRoot);
		if(root == null) {
			UserInfo user = new UserInfo();
			user.username = "watchtheclock";
			user.properUsername = "WatchTheClock";
			user.userID = 47581598;
			user.rank = LEVEL_ROOT_ADMIN;
			user.promotedBy = "root";
			addUserByName(new TreeNode(user), userDatabaseRoot);
			addUserByUid(new TreeNode(user), userDatabaseRoot);
		} else {
			root.rank = LEVEL_ROOT_ADMIN;
		}
		root = getUserFromName("uakf.b", userDatabaseRoot);
		if(root == null) {
			UserInfo user = new UserInfo();
			user.username = "uakf.b";
			user.properUsername = "uakf.b";
			user.userID = 6270102;
			user.rank = LEVEL_ROOT_ADMIN;
			user.promotedBy = "root";
			addUserByName(new TreeNode(user), userDatabaseRoot);
			addUserByUid(new TreeNode(user), userDatabaseRoot);
		} else {
			root.rank = LEVEL_ROOT_ADMIN;
		}
	}

	public void chatReceived(MemberInfo player, String chat, boolean whisper) {
		UserInfo user = getUserFromName(player.username, userDatabaseRoot);
		//int memberRank = getUserRank(player.username.toLowerCase());
		
		if(player != null && chat.startsWith("?trigger")) {
			String trigger_msg = "Trigger: " + trigger;

			if(whisper) {
				chatthread.queueChat(trigger_msg, player.userID);
			} else {
				chatthread.queueChat(trigger_msg, chatthread.MAIN_CHAT);
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
					chatthread.queueChat(response, chatthread.MAIN_CHAT);
				}
			}
		}
	}

	public void playerJoined(MemberInfo player) {
		UserInfo user = getUserFromName(player.username.toLowerCase(), userDatabaseRoot);
		if(entryLevels) {
			checkEntryLevels(player, user);
		}
		addUserToDatabase(player, user);
		if(userJoinAnnouncement) {
			joinAnnouncement(player, user);
		}
		if(publicUserMessage) {
			joinMessage(player, user);
		}
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
	
	//add new user to user database by uid
	public void addUserByUid(TreeNode user, TreeNode node) {
		if(user.getValue().userID < node.getValue().userID) {
			//if user uid is less than node uid
			if(node.getLeftUid() == null) {
				//if left_uid node is null
				node.setLeftUid(user);
			} else {
				//else give to left_uid child node
				addUserByUid(user, node.getLeftUid());
			}
		} else {
			//use else because we don't want to discard any users, user id may not be known
			if(node.getRightUid() == null) {
				//if right_uid node is null
				node.setRightUid(user);
			} else {
				//else give to right_uid child node
				addUserByUid(user, node.getRightUid());
			}
		}
	}
	
	//add new user to user database by username
	public void addUserByName(TreeNode user, TreeNode node) {
		if(user.getValue().username.compareToIgnoreCase(node.getValue().username) < 0) {
			//if user username is alphabetically lower than node username
			if(node.getLeftUser() == null) {
				//if left_user is null
				node.setLeftUser(user);
			} else {
				//else give to left_user child node
				addUserByName(user, node.getLeftUser());
			}
		} else {
			//use else because username is unique, impossible to get 2 of the same username
			if(node.getRightUser() == null) {
				node.setRightUser(user);
			} else {
				//else give to right_user child node
				addUserByName(user, node.getRightUser());
			}
		}
	}
	
	//commented out because I don't think I use this
	/*//retrieve rank given username
	//case does not matter due to usage of compareToIgnoreCase
	public int getUserRankFromName(String user, TreeNode node) {
		if(user.compareToIgnoreCase(node.getValue().username) == 0) { //base case
			//if user is equal to node's username
			return node.getValue().rank;
		} else if(user.compareToIgnoreCase(node.getValue().username) < 0) {
			//if user is alphabetically lower than node's username
			return getUserRankFromName(user, node.getLeftUser());
		} else {
			//last option, user is alphabetically higher than node's username
			return getUserRankFromName(user, node.getRightUser());
		}
	}*/
	
	//retrieve userinfo given UID
	public UserInfo getUserFromUid(int uid, TreeNode node) {
		if(uid == node.getValue().userID) { //base case
			//if uid is equal to node's userID
			return node.getValue();
		} else if(uid < node.getValue().userID) {
			//if uid is less than node's userID
			return getUserFromUid(uid, node.getLeftUid());
		} else {
			//last option, uid is greater than node's userID
			return getUserFromUid(uid, node.getRightUid());
		}
	}
	
	//retrieve userinfo given username
	public UserInfo getUserFromName(String user, TreeNode node) {
		if(user.compareToIgnoreCase(node.getValue().username) == 0) { //base case
			//if user is equal to node's username
			return node.getValue();
		} else if(user.compareToIgnoreCase(node.getValue().username) < 0) {
			//if user is alphabetically lower than node's username
			if(node.getLeftUser() == null) {
				return null;
			} else {
				return getUserFromName(user, node.getLeftUser());
			}
		} else {
			//last option, user is alphabetically higher than node's username
			if(node.getRightUser() == null) {
				return null;
			} else {
				return getUserFromName(user, node.getRightUser());
			}
		}
	}
	
	public TreeNode getNodeFromName(String user, TreeNode node) {
		if(user.compareToIgnoreCase(node.getValue().username) == 0) { //base case
			//if user is equal to node's username
			return node;
		} else if(user.compareToIgnoreCase(node.getValue().username) < 0) {
			//if user is alphabetically lower than node's username
			if(node.getLeftUser() == null) {
				return null;
			} else {
				return getNodeFromName(user, node.getLeftUser());
			}
		} else {
			//last option, user is alphabetically higher than node's username
			if(node.getRightUser() == null) {
				return null;
			} else {
				return getNodeFromName(user, node.getRightUser());
			}
		}
	}
	
	public String getGrammaticalTitle(int rank) {
		switch(rank) {
			case LEVEL_ROOT_ADMIN:
				return "a Root Admin!";
			case LEVEL_ADMIN:
				return "an Admin!";
			case LEVEL_TRIAL_ADMIN:
				return "a Trial Admin!";
			case LEVEL_TRUSTED:
				return "Trusted!";
			case LEVEL_VIP:
				return "a V.I.P.!";
			case LEVEL_SAFELIST:
				return "Safelisted!";
			case LEVEL_PUBLIC:
				return "Public!";
			case LEVEL_SHITLIST:
				return "Shitlisted!";
			default:
				return "an unknown rank?";
		}
	}
	
	public String getTitle(int rank) {
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

	public String processAlias(String alias) {
		if(aliasToCommand.containsKey(alias)) {
			return aliasToCommand.get(alias);
		} else {
			return alias;
		}
	}

	public void exit() {
		garena.disconnectRoom();
		System.exit(0);
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
	
	public String time(int minutes) {
		Calendar cal = Calendar.getInstance();
		cal.add(Calendar.MINUTE, minutes);
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