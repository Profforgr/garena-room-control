/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package grc.bot;

import grc.GRCConfig;
import grc.GChatBot;
import grc.Main;
import grc.UserInfo;
import grc.TreeNode;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.ResultSet;
import java.math.*;
import java.util.ArrayList;

/**
 *
 * @author wizardus & GG.Dragon
 */
 
public class SQLThread extends Thread {

	private ArrayList<Connection> connections;
	private String host; //MySQL hostname
	private String username; //MySQL username
	private String password; //MySQL password

	private int botId; //In case you are running multiple bots at the same time
	private int bannedWordDetectType; //Spam check settings
	private int dbRefreshRate; //how often to synchronize database with bot in seconds
	private GChatBot bot;
	private boolean initial;

	public SQLThread(GChatBot bot) {
		this.bot = bot;
		initial = true;

		//configuration
		host = GRCConfig.configuration.getString("grc_db_host");
		username = GRCConfig.configuration.getString("grc_db_username");
		password = GRCConfig.configuration.getString("grc_db_password");
		botId = GRCConfig.configuration.getInt("grc_bot_id", 0); //Default 0
		bannedWordDetectType = GRCConfig.configuration.getInt("grc_bot_detect", 3); //Default 3
		dbRefreshRate = GRCConfig.configuration.getInt("grc_bot_refresh_rate", 60); //Default 60 seconds, 1 minute
	}

	public void init() {
		connections = new ArrayList<Connection>();
		
		try {
			Class.forName("com.mysql.jdbc.Driver");
		} catch(ClassNotFoundException cnfe) {
			//give error information to Main
			Main.println("[SQLThread] MySQL driver cannot be found: " + cnfe.getLocalizedMessage(), Main.ERROR);
			Main.stackTrace(cnfe);
		}
	}
	
	//gets a connection
	public Connection connection() {
		synchronized(connections) {
			if(connections.isEmpty()) {
				try {
					Main.println("[SQLThread] Creating new connection...", Main.DATABASE);
					connections.add(DriverManager.getConnection(host, username, password));
				}
				catch(SQLException e) {
					//give error information to Main
					Main.println("[SQLThread] Unable to connect to mysql database: " + e.getLocalizedMessage(), Main.ERROR);
					Main.stackTrace(e);
				}
			}
			Main.println("[SQLThread] Currently have " + connections.size() + " connections", Main.DATABASE);

			return connections.remove(0);
		}
	}
	
	public void connectionReady(Connection connection) {
		synchronized(connections) {
			connections.add(connection);
			Main.println("[SQLThread] Recovering connection; now at " + connections.size() + " connections", Main.DATABASE);
		}
	}
	
	//add new user to database given full information
	public boolean addUser(String username, String properUsername, int uid, int rank, String ip, String lastSeen, String promotedBy, String unbannedBy) {
		try {
			Connection connection = connection();
			PreparedStatement statement = connection.prepareStatement("INSERT INTO users (id, username, properusername, uid, rank, ip, lastseen, promotedby, unbannedby) VALUES (NULL, ?, ?, ?, ?, ?, ?, ?, ?)");
			statement.setString(1, username);
			statement.setString(2, properUsername);
			statement.setInt(3, uid);
			statement.setInt(4, rank);
			statement.setString(5, ip);
			statement.setString(6, lastSeen);
			statement.setString(7, promotedBy);
			statement.setString(8, unbannedBy);
			statement.execute();
			connectionReady(connection);
			return true;
		} catch(SQLException e) {
			//give error information to Main
			Main.println("[SQLThread] Unable to add user " + properUsername + ": " + e.getLocalizedMessage(), Main.ERROR);
			Main.stackTrace(e);
		}
		return false;
	}
	
	//update user already in database given properusername, uid, ip, lastseen
	//occurs when you promote a user who has never been seen by the bot, and then they enter the room
	public boolean updateUser(String properUsername, int uid, String ip, String lastSeen) {
		try {
			Connection connection = connection();
			PreparedStatement statement = connection.prepareStatement("UPDATE users SET properusername=?, uid=?, ip=?, lastseen=? WHERE username=?");
			statement.setString(1, properUsername);
			statement.setInt(2, uid);
			statement.setString(3, ip);
			statement.setString(4, lastSeen);
			statement.setString(5, properUsername.toLowerCase());
			statement.execute();
			connectionReady(connection);
			return true;
		} catch(SQLException e) {
			//give error information to Main
			Main.println("[SQLThread] Unable to update user " + properUsername + ": " + e.getLocalizedMessage(), Main.ERROR);
			Main.stackTrace(e);
			return false;
		}
	}
	
	//update just last seen
	public boolean updateUser(String username, String lastSeen) {
		try {
			Connection connection = connection();
			PreparedStatement statement = connection.prepareStatement("UPDATE users SET lastseen=? WHERE username=?");
			statement.setString(1, lastSeen);
			statement.setString(2, username);
			statement.execute();
			connectionReady(connection);
			return true;
		} catch(SQLException e) {
			//give error information to Main
			Main.println("[SQLThread] Unable to update user " + username + ": " + e.getLocalizedMessage(), Main.ERROR);
			Main.stackTrace(e);
			return false;
		}
	}
	
	//sync user database with mysql database
	//unfortunately the best way to do this is to remake the userDB tree each time
	public boolean syncDatabase() {
		try {
			Connection connection = connection();
			PreparedStatement statement = connection.prepareStatement("SELECT username, properusername, uid, rank, ip, lastseen, promotedby, unbannedby FROM users");
			ResultSet result = statement.executeQuery();
			bot.userDatabaseRoot.clear();
			UserInfo.numUsers = 0;
			while(result.next()) {
				UserInfo user = new UserInfo();
				user.username = result.getString("username");
				user.properUsername = result.getString("properusername");
				user.userID = result.getInt("uid");
				int rank = result.getInt("rank");
				//prevent people from editing database to give themselves root admin
				if(user.rank == bot.LEVEL_ROOT_ADMIN) {
					rank = bot.LEVEL_ADMIN;
				}
				user.rank = rank;
				user.ipAddress = result.getString("ip");
				user.lastSeen = result.getString("lastseen");
				user.promotedBy = result.getString("promotedby");
				user.unbannedBy = result.getString("unbannedby");
				TreeNode newUser = new TreeNode(user);
				if(user.userID != 0) {
					//if user does not have a valid uid, don't add to uid sorted database
					//occurs when you promote someone that the bot has never seen
					bot.addUserByUid(newUser, bot.userDatabaseRoot);
				}
				bot.addUserByName(newUser, bot.userDatabaseRoot);
				UserInfo.numUsers++;
			}
			return true;
		} catch(SQLException e) {
			//give error information to Main
			Main.println("[SQLThread] Unable to refresh lists: " + e.getLocalizedMessage(), Main.ERROR);
			Main.stackTrace(e);
			return false;
		}
	}

	public void run() {
		while(true) {
			if(initial) {
				Connection connection = connection();
				/*try {
					Main.println("[SQLThread] Creating bans table if not exists...");
					Statement statement = connection.createStatement();
					statement.execute("CREATE TABLE IF NOT EXISTS bans (id INT(11) NOT NULL PRIMARY KEY AUTO_INCREMENT, botid int(11) NOT NULL, server varchar(100) NOT NULL, name varchar(15) NOT NULL, ip varchar(15) NOT NULL, date datetime NOT NULL, gamename varchar(31) NOT NULL, admin varchar(15) NOT NULL, reason varchar(255) NOT NULL, gamecount int(11) NOT NULL DEFAULT '0', expiredate varchar(31) NOT NULL DEFAULT '', warn int(11) NOT NULL DEFAULT '0') ENGINE=MyISAM DEFAULT CHARSET=utf8 AUTO_INCREMENT=1");
				} catch(SQLException e) {
					if(Main.DEBUG) {
						e.printStackTrace();
					}
					Main.println("[SQLThread] Error while creating bans table: " + e.getLocalizedMessage());
				}
				try {
					Main.println("[SQLThread] Creating kicks table if not exists...");
					Statement statement = connection.createStatement();
					statement.execute("CREATE TABLE IF NOT EXISTS kicks (id INT(11) NOT NULL PRIMARY KEY AUTO_INCREMENT, botid int(11) NOT NULL, server varchar(100) NOT NULL, name varchar(15) NOT NULL, ip varchar(15) NOT NULL, date datetime NOT NULL, admin varchar(15) NOT NULL, reason varchar (150) NOT NULL) ENGINE=MyISAM DEFAULT CHARSET=utf8 AUTO_INCREMENT=1");
				} catch(SQLException e) {
					if(Main.DEBUG) {
						e.printStackTrace();
					}
					Main.println("[SQLThread] Error while creating kicks table: " + e.getLocalizedMessage());
				}
				try {
					Main.println("[SQLThread] Creating phrases table if not exists...");
					Statement statement = connection.createStatement();
					statement.execute("CREATE TABLE IF NOT EXISTS phrases (id INT(11) NOT NULL PRIMARY KEY AUTO_INCREMENT, type varchar(100) NOT NULL, phrase varchar(150) NOT NULL) ENGINE=MyISAM DEFAULT CHARSET=utf8 AUTO_INCREMENT=1");
				} catch(SQLException e) {
					if(Main.DEBUG) {
						e.printStackTrace();
					}
					Main.println("[SQLThread] Error while creating phrases table: " + e.getLocalizedMessage());
				}*/
				try {
					Main.println("[SQLThread] Creating users table if not exists...", Main.DATABASE);
					
					Statement statement = connection.createStatement();
					//not sure how to write this statement without it being a block of text
					statement.execute("CREATE TABLE IF NOT EXISTS users (id INT(11) NOT NULL PRIMARY KEY AUTO_INCREMENT, username varchar(15) NOT NULL, properusername varchar(15) NOT NULL DEFAULT 'unknown', uid INT(10) NOT NULL DEFAULT '0', rank INT(2) NOT NULL DEFAULT '0', ip varchar(15) NOT NULL DEFAULT 'unknown', lastseen varchar(31) NOT NULL DEFAULT 'unknown', promotedby varchar(15) NOT NULL DEFAULT 'unknown', unbannedby varchar(15) NOT NULL DEFAULT 'unknown') ENGINE=MyISAM DEFAULT CHARSET=utf8 AUTO_INCREMENT=1");
				} catch(SQLException e) {
					//give error information to Main
					Main.println("[SQLThread] Error while creating users table: " + e.getLocalizedMessage(), Main.ERROR);
					Main.stackTrace(e);
				}
				connectionReady(connection);
			}
			Main.println("[SQLThread] Refreshing internal lists with database...", Main.DATABASE);
			
			//sync database
			syncDatabase();
			
			Main.println("[SQLThread] Refresh: found " + UserInfo.numUsers + " Users", Main.DATABASE);
			
			
			if(initial) {
				initial = false;
			}
			
			bot.addRoot();
			
			try {
				Thread.sleep(dbRefreshRate*1000);
			} catch(InterruptedException e) {
				//give error information to Main
				Main.println("[SQLThread] Run sleep was interrupted: " + e.getLocalizedMessage(), Main.ERROR);
				Main.stackTrace(e);
			}
		}
	}
}