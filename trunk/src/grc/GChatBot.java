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
	
	public GarenaInterface garena;
	public PluginManager plugins;
	public SQLThread sqlthread;
	public ChatThread chatthread;
	//ArrayList<String> voteLeaver;
	
	//Settings
	private String trigger;
	private int publicDelay;
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
		//registerCommand("deleteuser", LEVEL_ROOT_ADMIN);
		registerCommand("addadmin", LEVEL_ROOT_ADMIN);
		//registerCommand("room", LEVEL_ROOT_ADMIN);
		
		registerCommand("addtrialadmin", LEVEL_ADMIN);
		//registerCommand("kick", LEVEL_ADMIN);
		//registerCommand("quickkick", LEVEL_ADMIN);
		//registerCommand("ban", LEVEL_ADMIN);
		//registerCommand("unban", LEVEL_ADMIN);
		
		//registerCommand("clear", LEVEL_TRUSTED);
		//registerCommand("findip", LEVEL_TRUSTED);
		//registerCommand("checkuserip", LEVEL_TRUSTED);
		//registerCommand("traceuser", LEVEL_TRUSTED);
		//registerCommand("traceip", LEVEL_TRUSTED);
		
		//registerCommand("getpromote", LEVEL_VIP);
		//registerCommand("getunban", LEVEL_VIP);
		
		registerCommand("whois", LEVEL_SAFELIST);
		registerCommand("whoisuid", LEVEL_SAFELIST);
		registerCommand("roomstats", LEVEL_SAFELIST);
		//registerCommand("random", LEVEL_SAFELIST);
		//registerCommand("status", LEVEL_SAFELIST);
		
		int public_level = LEVEL_PUBLIC;
		if(!enablePublicCommands) {
			public_level = LEVEL_SAFELIST;
		}
		
		registerCommand("whoami", public_level);
		registerCommand("commands", public_level);
		//registerCommand("baninfo", public_level);
		//registerCommand("kickinfo", public_level);
		//registerCommand("uptime", public_level);
		//registerCommand("version", public_level);
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
		String memberRankTitle = getTitleFromRank(memberRank);
		Main.println("[GChatBot] Received command \"" + command + "\" with payload \"" + payload + "\" from " + memberRankTitle + " " + member.username, Main.ROOM);

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
				if(targetUser != null) { //if user exists in database
					//prevent demotion of root admins even by other root admin
					if(targetUser.rank == LEVEL_ROOT_ADMIN) {
						return "Failed. It's impossible to demote a Root Admin!";
					}
					//update rank in database and in memory
					if(sqlthread.updateRank(target.toLowerCase(), member.username, LEVEL_ADMIN)) {
						targetUser.rank = LEVEL_ADMIN;
						return "Success! <" + targetUser.properUsername + "> is now an Admin!";
					} else {
						chatthread.queueChat("Failed. There was an error with your database. Please inform GG.Dragon", chatthread.ANNOUNCEMENT);
						return null;
					}
				} else { //if user doesn't exist in database create new user
					if(sqlthread.addUser(target.toLowerCase(), "unknown", 0, LEVEL_ADMIN, "unknown", "unknown", member.username, "unknown")) {
						UserInfo newUser = new UserInfo();
						newUser.username = target.toLowerCase();
						newUser.rank = LEVEL_ADMIN;
						newUser.promotedBy = member.username;
						//add user to database
						addUserByName(new TreeNode(newUser), userDatabaseRoot);
						return "Success! " + target + " is now an Admin!";
					} else {
						chatthread.queueChat("Failed. There was an error with your database. Please inform GG.Dragon", chatthread.ANNOUNCEMENT);
						return null;
					}
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
				if(target.equalsIgnoreCase(member.username)) {
					return "Failed. You can't demote yourself!";
				}
				UserInfo targetUser = getUserFromName(target.toLowerCase(), userDatabaseRoot); //get userinfo
				if(targetUser != null) { //if user exists in database
					//stop people from doing bad stuff
					if(targetUser.rank == LEVEL_ROOT_ADMIN) {
						return "Failed. It's impossible to demote a Root Admin!";
					} else if(targetUser.rank == LEVEL_ADMIN && memberRank != LEVEL_ROOT_ADMIN) {
						return "Failed. You can't demote an Admin!";
					}
					//if promotion is ok
					//update rank in database and in memory
					if(sqlthread.updateRank(target.toLowerCase(), member.username, LEVEL_TRIAL_ADMIN)) {
						targetUser.rank = LEVEL_TRIAL_ADMIN;
						return "Success! <" + targetUser.properUsername + "> is now a Trial Admin!";
					} else {
						chatthread.queueChat("Failed. There was an error with your database. Please inform GG.Dragon", chatthread.ANNOUNCEMENT);
						return null;
					}
				} else { //new user
					if(sqlthread.addUser(target.toLowerCase(), "unknown", 0, LEVEL_TRIAL_ADMIN, "unknown", "unknown", member.username, "unknown")) {
						UserInfo newUser = new UserInfo();
						user.username = target.toLowerCase();
						user.rank = LEVEL_TRIAL_ADMIN;
						user.promotedBy = member.username;
						//add user to database
						addUserByName(new TreeNode(newUser), userDatabaseRoot);
						return "Success! " + target + " is now a Trial Admin!";
					} else {
						chatthread.queueChat("Failed. There was an error with your database. Please inform GG.Dragon", chatthread.ANNOUNCEMENT);
						return null;
					}
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
			}
		}
		
		//PUBLIC COMMANDS
		if(memberRank >= LEVEL_PUBLIC) {
			if(command.equals("commands")) {
				commandList(memberRank); //void
				return null;
			} else if(command.equals("whoami")) {
				return whois(member.username); //returns a string representing whois
			}
		}
		
		//check if they tried to use a command that needs a higher rank
		if(accessMessage != null) {
			if(commandAboveRank(command, memberRank)) {
				return accessMessage;
			}
		}
		
		//notify plugins
		String pluginResponse = plugins.onCommand(member, command, payload, memberRank);
		if(pluginResponse != null) {
			return pluginResponse;
		}
		
		//if command is not recognised
		return "Invalid command detected. Please check your spelling and try again";
	}
	
	public String whois(String target) {
		UserInfo targetUser = getUserFromName(target.toLowerCase(), userDatabaseRoot);
		if(targetUser == null) {
			return "Can't find " + target + " in user database";
		}
		//set up whois information
		String username = "";
		String userTitle = " [" + getTitleFromRank(targetUser.rank) + "]";
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
			case LEVEL_SHITLIST:
				if(!str.equals("")) {
					str += "\n";
				}
				str = str + "Shitlist commands: " + shitlistCommands.toString();
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
		if(user.rank <= LEVEL_SAFELIST) {
			chatthread.queueChat(welcomeMessage, target.userID);
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
			if(sqlthread.addUser(target.username.toLowerCase(), target.username, target.userID, LEVEL_PUBLIC, target.externalIP.toString().substring(1), time(), "unknown", "unknown")) {
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
			if(sqlthread.addUser(root_admin.toLowerCase(), "unknown", 0, LEVEL_ADMIN, "unknown", "unknown", "unknown", "unknown")) {
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