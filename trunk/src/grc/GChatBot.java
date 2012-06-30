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
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.Timer;

/**
 *
 * @author wizardus & GG.Dragon
 */
 
public class GChatBot implements GarenaListener, ActionListener {
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
	private final String startTime;
	private Timer autoAnnTimer;
	private int rotateAnn = 0;
	private int announceInterval;
	
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
	
	public TreeNode userDatabaseRoot; //contains the user database, often synched with mysql database. May be quite large
	
	//thread safe objects
	public Vector<String> autoAnn; //contains the auto announcements
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
		
		autoAnn = new Vector<String>();
		
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
		announceInterval = GRCConfig.configuration.getInt("grc_bot_auto_ann_interval", 120);
		
		autoAnnTimer = new Timer(announceInterval * 1000, this);
		
		registerCommand("exit", LEVEL_ROOT_ADMIN);
		registerCommand("deleteuser", LEVEL_ROOT_ADMIN);
		registerCommand("addadmin", LEVEL_ROOT_ADMIN);
		registerCommand("diagnostics", LEVEL_ROOT_ADMIN);
		//registerCommand("room", LEVEL_ROOT_ADMIN);
		
		registerCommand("kick", LEVEL_ADMIN);
		registerCommand("quickkick", LEVEL_ADMIN);
		registerCommand("ban", LEVEL_ADMIN);
		registerCommand("unban", LEVEL_ADMIN);
		registerCommand("addannounce", LEVEL_ADMIN);
		registerCommand("delannounce", LEVEL_ADMIN);
		registerCommand("setannounceinterval", LEVEL_ADMIN);
		registerCommand("reconnect", LEVEL_ADMIN);
		registerCommand("entrymessage", LEVEL_ADMIN);
		registerCommand("kicktroll", LEVEL_ADMIN);
		//registerCommand("banbot", LEVEL_ADMIN);
		
		registerCommand("clear", LEVEL_TRUSTED);
		registerCommand("findip", LEVEL_TRUSTED);
		registerCommand("checkuserip", LEVEL_TRUSTED);
		registerCommand("traceuser", LEVEL_TRUSTED);
		registerCommand("traceip", LEVEL_TRUSTED);
		registerCommand("refresh", LEVEL_TRUSTED);
		
		registerCommand("announce", LEVEL_VIP);
		registerCommand("getpromote", LEVEL_VIP);
		registerCommand("promote", LEVEL_VIP);
		registerCommand("demote", LEVEL_ADMIN);
		//registerCommand("getunban", LEVEL_VIP);
		
		registerCommand("whois", LEVEL_SAFELIST);
		registerCommand("whoisuid", LEVEL_SAFELIST);
		registerCommand("roomstats", LEVEL_SAFELIST);
		registerCommand("random", LEVEL_SAFELIST);
		registerCommand("getentrymessage", LEVEL_SAFELIST);
		//registerCommand("status", LEVEL_SAFELIST);
		
		int public_level = LEVEL_PUBLIC;
		if(!enablePublicCommands) {
			public_level = LEVEL_SAFELIST;
		}
		
		registerCommand("8ball", public_level);
		registerCommand("slap", public_level);
		registerCommand("whoami", public_level);
		registerCommand("commands", public_level);
		registerCommand("baninfo", public_level);
		//registerCommand("kickinfo", public_level);
		registerCommand("uptime", public_level);
		registerCommand("version", public_level);
		//registerCommand("allstaff", public_level);
		//registerCommand("staff", public_level);
		//registerCommand("creater", public_level);
		registerCommand("alias", public_level);
		registerCommand("help", public_level);
		
		//start input thread
		if(commandline) {
			CommandInputThread commandThread = new CommandInputThread(this);
			commandThread.start();
		}
	}
	
	public void actionPerformed(ActionEvent e) {
		if(e.getSource() == autoAnnTimer) {
			boolean announce = true;
			while(announce) {
				if(rotateAnn < autoAnn.size()-1) {
					rotateAnn++;
				} else {
					rotateAnn = 0;
				}
				chatthread.queueChat(autoAnn.get(rotateAnn), chatthread.ANNOUNCEMENT);
				announce = false;
			}
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
					sqlthread.syncDatabase();
					return "Success! " + targetUser.username + " has been deleted";
				} else {
					chatthread.queueChat("Failed. There was an error with your database. Please inform GG.Dragon", chatthread.ANNOUNCEMENT);
					return null;
				}
			} else if(command.equals("diagnostics")) {
				int uidTreeHeight = getUidTreeHeight(userDatabaseRoot);
				int nameTreeHeight = getNameTreeHeight(userDatabaseRoot);
				return "Height of uid tree: " + uidTreeHeight + ". Height of name tree: " + nameTreeHeight;
			}
		}
		
		//ADMIN COMMANDS
		if(memberRank >= LEVEL_ADMIN) {
			if(command.equals("demote")) {
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
				String reason = parts[1];
				return kick(member, user, target, reason, false);
			} else if(command.equals("quickkick")) {
				String[] parts = payload.split(" ", 2);
				if(parts.length < 2) {
					chatthread.queueChat("Invalid format detected. Correct format is " + trigger + "quickkick <username> <reason>. For further help use " + trigger + "help quickkick", member.userID);
					return null;
				}
				String target = trimUsername(removeSpaces(parts[0])); //format payload into something easier to process
				String reason = parts[1];
				return kick(member, user, target, reason, true);
			} else if(command.equals("ban")) {
				String[] parts = payload.split(" ", 3);
				if(parts.length < 3 || !GarenaEncrypt.isInteger(parts[1])) {
					chatthread.queueChat("Invalid format detected. Correct format is " + trigger + "ban <username> <time_in_hours> <reason>. For further help use " + trigger + "help ban", member.userID);
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
			} else if(command.equals("addannounce")) {
				if(payload.equals("")) {
					chatthread.queueChat("Invalid foramt detected. Correct format is " + trigger + "addannounce <message>. For further help use " + trigger + "help addannounce", member.userID);
					return null;
				}
				if(sqlthread.addAnnounce(payload)) {
					autoAnn.add(payload);
					startAnnTimer();
					chatthread.queueChat("Success! Your message has been added to the auto announcement list", member.userID);
					return null;
				} else {
					chatthread.queueChat("Failed. There was an error with your database. Please inform GG.Dragon", chatthread.ANNOUNCEMENT);
					return null;
				}
			} else if(command.equals("delannounce")) {
				if(payload.equals("")) {
					chatthread.queueChat("Invalid format detected. Correct format is " + trigger + "delannounce <message>. For further help use " + trigger + "help delannounce", member.userID);
					return null;
				}
				if(autoAnn.contains(payload)) { //check if payload is in the autoann vector
					if(sqlthread.delAnnounce(payload)) {
						autoAnn.remove(payload);
						if(autoAnn.size() == 0) { //if autoann is empty, stop
							autoAnnTimer.stop();
						}
						chatthread.queueChat("Success! Your message has been deleted from the auto announcement list", member.userID);
						return null;
					} else {
						chatthread.queueChat("Failed. There was an error with your database. Please inform GG.Dragon", chatthread.ANNOUNCEMENT);
						return null;
					}
				} else {
					chatthread.queueChat("Failed. No such message found! Tip: message is case sensitive", member.userID);
					return null;
				}
			} else if(command.equals("setannounceinterval")) {
				payload = removeSpaces(payload);
				if(payload.equals("") || !GarenaEncrypt.isInteger(payload)) {
					chatthread.queueChat("Invalid format detected. Correct format is " + trigger + "setannounceinterval <time_in_seconds>. For further help use " + trigger + "help setannounceinterval", member.userID);
					return null;
				}
				autoAnnTimer.setDelay(Integer.parseInt(payload) * 1000); //convert seconds to ms
				chatthread.queueChat("Success! Auto messages will now be sent every " + autoAnnTimer.getDelay() / 1000 + " seconds", member.userID);
				return null;
			} else if(command.equals("reconnect")) {
				main.reconnectRoom();
				return null;
			} else if(command.equals("entrymessage")) {
				if(payload.length() == 0) {
					chatthread.queueChat("Invalid format detected. Correct format is " + trigger + "entrymessage <user> <message>. For further help use " + trigger + "help entrymessage", member.userID);
					return null;
				}
				String[] parts = payload.split(" ", 2);
				if(parts.length < 2) {
					chatthread.queueChat("Invalid format detected. Correct format is " + trigger + "entrymessage <user> <message>. For further help use " + trigger + "help entrymessage", member.userID);
					return null;
				}
				String target = trimUsername(parts[0]); //format payload into something easier to process
				UserInfo targetUser = getUserFromName(target, userDatabaseRoot);
				if(target == null) {
					return "Invalid user - can't be found";
				}
				if(sqlthread.updateEntryMsg(target, parts[1])) {
					targetUser.entryMsg = parts[1];
					if(targetUser.properUsername.equals("unknown")) {
						return "Success - " + target + "'s new custom entry message is: " + targetUser.entryMsg;
					} else {
						return "Success - " + targetUser.properUsername + "'s new custom entry message is: " + targetUser.entryMsg;
					}
				} else {
					chatthread.queueChat("Failed. There was an error with your database. Please inform GG.Dragon", chatthread.ANNOUNCEMENT);
					return null;
				}
			} else if(command.equals("kicktroll")) {
				String[] parts = payload.split(" ", 2);
				String target = trimUsername(removeSpaces(parts[0])); //format payload into something easier to process
				if(target.length() == 0) {
					chatthread.queueChat("Invalid format detected. Correct format is " + trigger + "kicktroll <username>. For further help use " + trigger + "help kicktroll", member.userID);
					return null;
				}
				String reason = "troll";
				if(parts.length > 1) {
					reason += " - " + parts[1];
				}
				return kick(member, user, target, reason, true);
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
			} else if(command.equals("refresh")) {
				if(sqlthread.syncDatabase()) {
					return "Refresh: found " + UserInfo.numUsers + " Users, " + autoAnn.size() + " announcements.";
				} else {
					chatthread.queueChat("Failed. There was an error with your database. Please inform GG.Dragon", chatthread.ANNOUNCEMENT);
					return null;
				}
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
			} else if(command.equals("promote")) {
				if(payload.length() == 0) {
					chatthread.queueChat("Invalid format detected. Correct format is " + trigger + "promote [user] [rank]. Rank is optional. For further help use " + trigger + "help promote", member.userID);
				}
				//split payload into user and rank
				String[] parts = payload.split(" ", 2);
				String target = trimUsername(parts[0]);
				//stop users from promoting themselves
				if(target.toLowerCase().equals(user.username)) {
					return "Failed - you can't promote yourself!";
				}
				//find target userinfo
				UserInfo targetUser = getUserFromName(target.toLowerCase(), userDatabaseRoot);
				if(targetUser == null) { //target wasn't found
					return "Failed - " + target + " can't be found!";
				}
				if(parts.length == 1) { //if only username given
					return promote(member, user, targetUser);
				} else { //else username and rank given
					return promote(member, user, targetUser, parts[1]);
				}
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
				String[] parts = payload.split(" ", 2);
				long scale = 100;
				if(!parts[0].equals("")) {
					try {
						scale = Long.parseLong(removeSpaces(parts[0]));
					} catch(NumberFormatException e) {
						return "Invalid number specified";
					}
				}
				long random = (long)(Math.random()*scale)+1;
				return "You randomed: " + random;
			} else if(command.equals("getentrymessage")) {
				String target = trimUsername(removeSpaces(payload));
				if(target.length() == 0) {
					chatthread.queueChat("Invalid format detected. Correct format is " + trigger + "getentrymessage <username>. For further help use " + trigger + "help getentrymessage", member.userID);
					return null;
				}
				UserInfo targetUser = getUserFromName(target, userDatabaseRoot);
				if(target == null) {
					return "Invalid user - can't be found";
				}
				if(targetUser.properUsername.equals("unknown")) {
					return "<" + target + ">'s entry message is: " + targetUser.entryMsg;
				} else {
					return "<" + targetUser.properUsername + ">'s entry message is: " + targetUser.entryMsg;
				}
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
			} else if(command.equals("alias")) {
				payload = removeSpaces(payload);
				if(payload.equals("")) {
					chatthread.queueChat("Invalid format detected. Correct format is " + trigger + "alias <command>. For further help use " + trigger + "help alias", member.userID);
					return null;
				}
				String cmd_check = processAlias(payload.toLowerCase());
				if(commandToAlias.containsKey(cmd_check)) {
					return "Aliases: " + arrayToString(commandToAlias.get(cmd_check));
				} else {
					chatthread.queueChat("Command has no aliases or command not found!", member.userID);
					return null;
				}
			} else if(command.equals("8ball")) {
				return magicEightBall();
			} else if(command.equals("slap")) {
				String tempString = removeSpaces(payload);
				if(tempString.equals("")) {
					int randomUser = (int)(Math.random()*garena.members.size()); //no +1 because array starts at 0
					payload = garena.members.get(randomUser).username;
				}
				return slap(member.username, payload);
			} else if(command.equals("help")) {
				return help(payload);
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
	
	public String promote(MemberInfo member, UserInfo user, UserInfo target) {
		String rank = getTitle(target.rank + 1); //get name for rank above current rank
		return promote(member, user, target, rank);
	}
	
	public String promote(MemberInfo member, UserInfo user, UserInfo target, String rank) {
		//get rank number from name
		int newRank = getRankNumber(rank.toLowerCase());
		if(newRank < 0) { //if returned rank is negative, invalid rank name was given
			return "Failed - invalid rank!";
		}
		//do checks for loopholes so users can't mess with ranks
		//if it's ok, promote
		if(newRank < target.rank) { //don't let users promote down ranks
			return "Failed - you can't promote users down ranks. Use " + trigger + "demote instead";
		}
		switch(newRank) {
			case LEVEL_ROOT_ADMIN:
				return "Failed - you can't promote users to Root Admin!";
			case LEVEL_ADMIN:
				if(user.rank < LEVEL_ROOT_ADMIN) {
					return "Failed - only the Root Admin may promote users to Admin";
				} else { //promote is ok
					return setRank(member, target, newRank);
				}
			case LEVEL_TRIAL_ADMIN:
				if(user.rank < LEVEL_ADMIN) {
					return "Failed - you must be at least an Admin to promote users to Trial Admin";
				} else { //promote is ok
					return setRank(member, target, newRank);
				}
			case LEVEL_TRUSTED:
				if(user.rank < LEVEL_ADMIN) {
					return "Failed - you must be at least an Admin to promote users to Trusted";
				} else { //promote is ok
					return setRank(member, target, newRank);
				}
			case LEVEL_VIP:
				if(user.rank < LEVEL_ADMIN) {
					return "Failed - you must be at least an Admin to promote users to V.I.P.";
				} else { //promote is ok
					return setRank(member, target, newRank);
				}
			case LEVEL_SAFELIST:
				if(user.rank < LEVEL_VIP) { //not actually needed because promote requires vip rank anyway
					return "Failed - you must be at least a V.I.P. to promote users to Safelist";
				} else { //promote is ok
					return setRank(member, target, newRank);
				}
			case LEVEL_PUBLIC:
				if(user.rank < LEVEL_TRUSTED) {
					return "Failed - you must be at least Trusted to remove users from shitlist";
				} else { //promote is ok
					return setRank(member, target, newRank);
				}
			default: //this should never occur
				return "Failed - you can't promote users to the Shitlist, use " + trigger + "demote instead";
		}
	}
	
	public String demote(MemberInfo member, UserInfo user, UserInfo target) {
		String rank = getTitle(target.rank - 1); //get name for rank above current rank
		return promote(member, user, target, rank);
	}
	
	public String demote(MemberInfo member, UserInfo user, UserInfo target, String rank) {
		//get rank number from name
		int newRank = getRankNumber(rank.toLowerCase());
		if(newRank < 0) { //if returned rank is negative, invalid rank name was given
			return "Failed - invalid rank!";
		}
		//do checks for loopholes so users can't mess with ranks
		//if it's ok, demote
		if(newRank > target.rank) { //don't let users demote up ranks
			return "Failed - you can't demote users up ranks. Use " + trigger + "promote instead";
		} else if(user.rank < target.rank) {
			return "Failed - you can't demote users above your rank";
		}
		switch(newRank) {
			case LEVEL_ROOT_ADMIN:
				return "Failed - you can't demote users to Root Admin!";
			case LEVEL_ADMIN:
				return "Failed - you can't demote users to Admin!";
			case LEVEL_TRIAL_ADMIN:
				if(user.rank < LEVEL_ROOT_ADMIN) {
					return "Failed - only the Root Admin may demote Admins";
				} else { //demote is ok
					return setRank(member, target, newRank);
				}
			case LEVEL_TRUSTED:
				if(user.rank < LEVEL_ADMIN) {
					return "Failed - you must be at least an Admin to demote users to Trusted";
				} else { //demote is ok
					return setRank(member, target, newRank);
				}
			case LEVEL_VIP:
				if(user.rank < LEVEL_ADMIN) {
					return "Failed - you must be at least an Admin to demote users to V.I.P.";
				} else { //demote is ok
					return setRank(member, target, newRank);
				}
			case LEVEL_SAFELIST:
				if(user.rank < LEVEL_ADMIN) {
					return "Failed - you must be at least an Admin to demote users to Safelist";
				} else { //demote is ok
					return setRank(member, target, newRank);
				}
			case LEVEL_PUBLIC:
				if(user.rank < LEVEL_VIP) { //not actually needed because demote requires vip rank anyway
					return "Failed - you must be at least a V.I.P. to demote users to public";
				} else if(!user.username.equalsIgnoreCase(target.promotedBy) && user.rank == LEVEL_VIP) { //if a vip, can only demote users you promtoed
					return "Failed - you can only demote users you have promoted";
				} else { //demote is ok
					return setRank(member, target, newRank);
				}
			default: //shitlist
				if(user.rank < LEVEL_TRUSTED) {
					return "Failed - you must be at least Trusted to add users to the Shitlist";
				} else { //demote is ok
					return setRank(member, target, newRank);
				}
		}
	}
	
	public String setRank(MemberInfo admin, UserInfo targetUser, int rank) {
		//update rank in database and in memory
		if(sqlthread.updateRank(targetUser.username, admin.username, rank)) {
			targetUser.rank = rank;
			targetUser.promotedBy = admin.username;
			//Success! <GG.Dragon> is now an Admin!
			return "Success - <" + targetUser.properUsername + "> is now " + getGrammaticalTitle(rank);
		} else {
			chatthread.queueChat("Failed. There was an error with your database. Please inform GG.Dragon", chatthread.ANNOUNCEMENT);
			return null;
		}
	}
	
	public int getRankNumber(String name) {
		if(name.equals("root admin") || name.equals("root") || name.equals("r a")) {
			return LEVEL_ROOT_ADMIN;
		} else if(name.equals("admin") || name.equals("adm") || name.equals("a")) {
			return LEVEL_ADMIN;
		} else if(name.equals("trial admin") || name.equals("trial") || name.equals("trial adm") || name.equals("t adm") || name.equals("t a")) {
			return LEVEL_TRIAL_ADMIN;
		} else if(name.equals("trusted") || name.equals("trust") || name.equals("t")) {
			return LEVEL_TRUSTED;
		} else if(name.equals("v.i.p.") || name.equals("v.i.p") || name.equals("vip")) {
			return LEVEL_VIP;
		} else if(name.equals("safelist") || name.equals("safe") || name.equals("sflist") || name.equals("sf")) {
			return LEVEL_SAFELIST;
		} else if(name.equals("random") || name.equals("public") || name.equals("r") || name.equals("p")) {
			return LEVEL_PUBLIC;
		} else  if(name.equals("shitlist") || name.equals("shit") || name.equals("shlist") || name.equals("sh") || name.equals("sht")) {
			return LEVEL_SHITLIST;
		} else { //rank not found, return negative
			return -1;
		}
	}
	
	public String kick(MemberInfo member, UserInfo user, String target, String reason, boolean quick) {
		//check if kick is ok
		if(target.equalsIgnoreCase(member.username)) {
			return "Failed. You can't kick yourself!";
		}
		//don't kick if user is not in room
		MemberInfo victim = garena.memberFromName(target);
		if(victim == null) {
			return "Failed. Unable to find " + target + " in room";
		}
		//don't kick if target is an admin
		UserInfo targetUser = getUserFromName(target, userDatabaseRoot);
		if(targetUser.rank == LEVEL_ROOT_ADMIN) {
			return "Failed. " + target + " is a Root Admin!";
		} else if(targetUser.rank == LEVEL_ADMIN && user.rank != LEVEL_ROOT_ADMIN) {
			return "Failed. " + target + " is an Admin!";
		}
		//kick is ok, continue
		String date = time();
		String expiry;
		if(quick) { //if quickkick
			expiry = date; //no ban time for a quickkick
		} else { //if normal kick
			expiry = time(15); //15 minutes for a kick
		}
		if(sqlthread.ban(victim.username, victim.userID, victim.externalIP.toString().substring(1), member.username, reason, date, expiry, garena.room_id)) {
			garena.kick(victim, reason);
			try {
				Thread.sleep(1100);
			} catch(InterruptedException e) {
				//give error information to Main
				Main.println("[GChatBot] Sleep interrupted: " + e.getLocalizedMessage(), Main.ERROR);
				Main.stackTrace(e);
			}
			if(quick) { //unban straight away if a quick kick
				garena.unban(victim.username);
				try {
					Thread.sleep(1000);
				} catch(InterruptedException e) {
					//give error information to Main
					Main.println("[GChatBot] Sleep interrupted: " + e.getLocalizedMessage(), Main.ERROR);
					Main.stackTrace(e);
				}
			}
			chatthread.queueChat("For information about this kick use " + trigger + "baninfo " + victim.username, chatthread.ANNOUNCEMENT);
			return null;
		} else {
			chatthread.queueChat("Failed. There was an error with your database. Please inform GG.Dragon", chatthread.ANNOUNCEMENT);
			return null;
		}
	}
	
	public void getPromote(String payload, MemberInfo member) {
		payload = trimUsername(removeSpaces(payload)); //format payload into something easier to process
		UserInfo targetUser = getUserFromName(payload.toLowerCase(), userDatabaseRoot); //get userinfo
		//check if targetuser is ok
		if(targetUser == null) {
			chatthread.queueChat("Failed. " + payload + " is an unknown user! For further help use " + trigger + "help promote", member.userID);
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
	
	public String slap(String user, String target) {
		UserInfo targetUser = getUserFromName(target.toLowerCase(), userDatabaseRoot);
		if(targetUser != null) {
			if(!targetUser.properUsername.equals("unknown")) {
				/* if user exists and has a proper username, use it instead of target */
				target = "<" + targetUser.properUsername + ">";
			}
		}
		user = "<" + user + ">"; //so you can find yourself in room
		int scale = 12;
		int random = (int)(Math.random()*scale) + 1;
		switch(random) {
			case 1:
				return user + " slaps " + target + " with a large trout.";
			case 2:
				return user + " slaps " + target + " with a pink Macintosh.";
			case 3:
				return user + " throws a Playstation 3 at " + target + ".";
			case 4:
				return user + " drives a car over " + target + ".";
			case 5:
				return user + " steals " + target + "'s cookies. mwahahah!";
			case 6:
				return user + " washes " + target + "'s car. Oh, the irony!";
			case 7:
				return user + " burns " + target + "'s house.";
			case 8:
				return user + " finds " + target + "'s picture on uglypeople.com.";
			case 9:
				return user + " breaks out the slapping rod and looks sternly at " + target + ".";
			case 10:
				return user + " tosses " + target + " into a pile of needles.";
			case 11:
				return user + " slaps some sense into " + target + ".";
			case 12:
				return user + " throws " + target + " into a rose bush.";
			default:
				return "";
		}
	}
	
	public String magicEightBall() {
		//based on info from http://en.wikipedia.org/wiki/Magic_8-Ball
		int scale = 20;
		int random = (int)(Math.random()*scale) + 1;
		switch(random) {
			case 1:
				return "It is certain";
			case 2:
				return "It is decidedly so";
			case 3:
				return "Without a doubt";
			case 4:
				return "Yes – definitely";
			case 5:
				return "You may rely on it";
			case 6:
				return "As I see it, yes";
			case 7:
				return "Most likely";
			case 8:
				return "Outlook good";
			case 9:
				return "Signs point to yes";
			case 10:
				return "Yes";
			case 11:
				return "Reply hazy, try again";
			case 12:
				return "Ask again later";
			case 13:
				return "Better not tell you now";
			case 14:
				return "Cannot predict now";
			case 15:
				return "Concentrate and ask again";
			case 16:
				return "Don't count on it";
			case 17:
				return "My reply is no";
			case 18:
				return "My sources say no";
			case 19:
				return "Outlook not so good";
			case 20:
				return "Very doubtful";
			default:
				return "";
		}
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
		return "There are " + numPlayers + " players in this room. " + numGold + goldPlural + ", " + numBasic + basicPlural + ". " + numPlaying + playingPlural + " Warcraft 3 open. ";
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
		if(user == null) { //if user doesn't exist
			return;
		}
		if(user.entryMsg.length() > 0) { //if user has a custom entry msg
			chatthread.queueChat(user.entryMsg, chatthread.ANNOUNCEMENT);
			return;
		} else { //else display default custom entry msg
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
	
	public void startAnnTimer() {
		if(!autoAnnTimer.isRunning()) { //if timer isn't running, start
			autoAnnTimer.start();
		}
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
		return text.replaceAll(" ", "");
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
	
	public int getNameTreeHeight(TreeNode node) {
		if(node == null) {
			return 0;
		} else {
			return Math.max(getNameTreeHeight(node.getLeftUser()), getNameTreeHeight(node.getRightUser())) + 1;
		}
	}
	
	public int getUidTreeHeight(TreeNode node) {
		if(node == null) {
			return 0;
		} else {
			return Math.max(getUidTreeHeight(node.getLeftUid()), getUidTreeHeight(node.getRightUid())) + 1;
		}
	}
	
	public String help(String cmd) {
		if(cmd.length() == 0) {
			return "Command trigger: '" + trigger + "' Use " + trigger + "help [command] for info on a specific command. For a list of commands use " + trigger + "commands. For a list of aliases of a command use " + trigger + "alias [command]. If you whisper a command to the bot, it will respond in a whisper if possible. Garena Client Broadcaster is developed by uakf.b. Chat bot is developed by GG.Dragon aka XIII.Dragon";
		} else {
			cmd = processAlias(removeSpaces(cmd.toLowerCase()));
			if(cmd.equals("exit")) {
				return "Rank required: " + getTitle(LEVEL_ROOT_ADMIN) + ". Format: " + trigger + "exit. Shuts down the bot";
			} else if(cmd.equals("deleteuser")) {
				return "Rank required: " + getTitle(LEVEL_ROOT_ADMIN) + ". Format: " + trigger + "deleteuser [username]. Deletes target user from the database";
			} else if(cmd.equals("promote")) {
				return "Rank required: " + getTitle(LEVEL_VIP) + ". Format: " + trigger + "promote [username] [rank]. Rank is optional. Promotes up one rank if no rank is given. V.I.P. users can only promote users up to Safelist";
			} else if(cmd.equals("demote")) {
				return "Rank required: " + getTitle(LEVEL_VIP) + ". Format: " + trigger + "demote [username] [rank]. Rank is optional. Demotes down one rank if no rank is given. V.I.P. users can only demote users they promoted";
			} else if(cmd.equals("kick")) {
				return "Rank required: " + getTitle(LEVEL_ADMIN) + ". Format: " + trigger + "kick [username] [reason]. Bans the user from the room for 15 minutes. Must have a reason specified";
			} else if(cmd.equals("quickkick")) {
				return "Rank required: " + getTitle(LEVEL_ADMIN) + ". Format: " + trigger + "quickkick [username] [reason]. Bans the user from the room for 1 second. Must specify a reason";
			} else if(cmd.equals("ban")) {
				return "Rank required: " + getTitle(LEVEL_ADMIN) + ". Format: " + trigger + "ban [username] [time_in_hours] [reason}. Bans the user from the room for specified length of time. Must specify a reason";
			} else if(cmd.equals("unban")) {
				return "Rank required: " + getTitle(LEVEL_ADMIN) + ". Format: " + trigger + "unban [username] [reason]. Unbans the user from the room. Must specify a reason.";
			} else if(cmd.equals("addannounce")) {
				return "Rank required: " + getTitle(LEVEL_ADMIN) + ". Format: " + trigger + "addannounce [message]. Adds the message to a list of messages which are announced every " + autoAnnTimer.getDelay() / 1000 + "seconds";
			} else if(cmd.equals("delannounce")) {
				return "Rank required: " + getTitle(LEVEL_ADMIN) + ". Format: " + trigger + "delannounce [message]. Deletes the message from the list of automatic announce messages. Is case sensitive";
			} else if(cmd.equals("setannounceinterval")) {
				return "Rank required: " + getTitle(LEVEL_ADMIN) + ". Format: " + trigger + "setannounceinterval [time_in_seconds]. Sets the interval between automatic announce messages from being sent. Must be a valid number";
			} else if(cmd.equals("reconnect")) {
				return "Rank required: " + getTitle(LEVEL_ADMIN) + ". Format: " + trigger + "reconnect. Makes the bot rejoin the room with a small delay. Used to fix certain errors";
			} else if(cmd.equals("clear")) {
				return "Rank required: " + getTitle(LEVEL_TRUSTED) + ". Format: " + trigger + "clear. Clears the chat queue. Used if the bot has too many messages queued up";
			} else if(cmd.equals("findip")) {
				return "Rank required: " + getTitle(LEVEL_TRUSTED) + ". Format: " + trigger + "findip [ip_address]. Finds all the users in the room who are using the IP address";
			} else if(cmd.equals("checkuserip")) {
				return "Rank required: " + getTitle(LEVEL_TRUSTED) + ". Format: " + trigger + "checkuserip [username]. Finds all users in the room who share an IP address with target user";
			} else if(cmd.equals("traceuser")) {
				return "Rank required: " + getTitle(LEVEL_TRUSTED) + ". Format: " + trigger + "traceuser [username]. Returns 2 website links that give approximate geo location of the IP address";
			} else if(cmd.equals("traceip")) {
				return "Rank required: " + getTitle(LEVEL_TRUSTED) + ". Format: " + trigger + "traceip [ip_address]. Returns 2 website links that give approximate geo location of the IP address";
			} else if(cmd.equals("refresh")) {
				return "Rank required: " + getTitle(LEVEL_TRUSTED) + ". Format: " + trigger + "refresh. Syncs bot memory with MySQL database";
			} else if(cmd.equals("announce")) {
				return "Rank required: " + getTitle(LEVEL_VIP) + ". Format: " + trigger + "announce [message]. Sends the message as a system message";
			} else if(cmd.equals("getpromote")) {
				return "Rank required: " + getTitle(LEVEL_VIP) + ". Format: " + trigger + "getpromote [username]. Gets all the players target user has promoted";
			} else if(cmd.equals("whois")) {
				return "Rank required: " + getTitle(LEVEL_SAFELIST) + ". Format: " + trigger + "whois [username]. Returns basic information about target user";
			} else if(cmd.equals("whoisuid")) {
				return "Rank required: " + getTitle(LEVEL_SAFELIST) + ". Format: " + trigger + "whoisuid [uid]. Returns basic information about target UID. Must be a number";
			} else if(cmd.equals("roomstats")) {
				return "Rank required: " + getTitle(LEVEL_SAFELIST) + ". Format: " + trigger + "roomstats. Returns basic information about the users in the room";
			} else if(cmd.equals("random")) {
				return "Rank required: " + getTitle(LEVEL_SAFELIST) + ". Format: " + trigger + "random [number]. Randoms a number between 1 and specified number. If no number is given, defaults to 100";
			} else if(cmd.equals("8ball")) {
				return "Rank required: " + getTitle(LEVEL_PUBLIC) + ". Format: " + trigger + "8ball. Returns a phrase from the popular novelty toy 8ball";
			} else if(cmd.equals("slap")) {
				return "Rank required: " + getTitle(LEVEL_PUBLIC) + ". Format: " + trigger + "slap [username]. Slaps target user with a random slap phrase. If no username is given, randomly picks a user in the room to slap";
			} else if(cmd.equals("whoami")) {
				return "Rank required: " + getTitle(LEVEL_PUBLIC) + ". Format: " + trigger + "whoami. Returns basic information about yourself";
			} else if(cmd.equals("commands")) {
				return "Rank required: " + getTitle(LEVEL_PUBLIC) + ". Format: " + trigger + "commands. Returns a list of commands that you are able to use";
			} else if(cmd.equals("baninfo")) {
				return "Rank required: " + getTitle(LEVEL_PUBLIC) + ". Format: " + trigger + "baninfo [username]. Returns ban information about target user. Only works on users banned by this bot";
			} else if(cmd.equals("uptime")) {
				return "Rank required: " + getTitle(LEVEL_PUBLIC) + ". Format: " + trigger + "uptime. Returns the time the bot was started";
			} else if(cmd.equals("version")) {
				return "Rank required: " + getTitle(LEVEL_PUBLIC) + ". Format: " + trigger + "version. Returns current DotA and Warcraft 3 TFT version";
			} else if(cmd.equals("alias")) {
				return "Rank required: " + getTitle(LEVEL_PUBLIC) + ". Format: " + trigger + "alias [command]. Returns a list of accepted aliases for specified command";
			} else if(cmd.equals("help")) {
				return "Rank required: " + getTitle(LEVEL_PUBLIC) + ". Format: " + trigger + "help [command]. Returns help information about the specified command. If no command is given it returns general help information";
			} else {
				return "Can not find any help information for specified command. If you think you have received this response in error, contact GG.Dragon";
			}
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