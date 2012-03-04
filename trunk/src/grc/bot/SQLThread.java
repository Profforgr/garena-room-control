/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package grc.bot;

import grc.GRCConfig;
import grc.GChatBot;
import grc.Main;
import grc.UserInfo;
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
 * @author wizardus
 */
public class SQLThread extends Thread {

	ArrayList<Connection> connections;
	String host; //MySQL hostname
	String username; //MySQL username
	String password; //MySQL password

	private int botId; //In case you are running multiple bots at the same time
	private int bannedWordDetectType; //Spam check settings
	private int dbRefreshRate; //how often to synchronize database with bot
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
		dbRefreshRate = GRCConfig.configuration.getInt("grc_bot_refresh_rate", 60); //Default 60
	}

	public void init() {
		connections = new ArrayList<Connection>();
		
		try {
			Class.forName("com.mysql.jdbc.Driver");
		} catch(ClassNotFoundException cnfe) {
			//Main controls what to do with error output
			Main.println("[SQLThread] MySQL driver cannot be found: " + cnfe.getLocalizedMessage(), Main.ERROR);
			//show error stack trace on console
			if(Main.DEBUG) {
				cnfe.printStackTrace();
			}
			//save error stack trace in log
			if(Main.log_error) {
				cnfe.printStackTrace(Main.log_error_out);
			}
			if(Main.log_single) {
				cnfe.printStackTrace(Main.log_single_out);
			}
		}
	}
	
	//gets a connection
	public Connection connection() {
		synchronized(connections) {
			if(connections.isEmpty()) {
				try {
					//Main controls what to do with database output
					Main.println("[SQLThread] Creating new connection...", Main.DATABASE);
					connections.add(DriverManager.getConnection(host, username, password));
				}
				catch(SQLException e) {
					//Main controls what to do with error output
					Main.println("[SQLThread] Unable to connect to mysql database: " + e.getLocalizedMessage(), Main.ERROR);
					//show error stack trace on console
					if(Main.DEBUG) {
						e.printStackTrace();
					}
					//save error stack trace in log
					if(Main.log_error) {
						e.printStackTrace(Main.log_error_out);
					}
					if(Main.log_single) {
						e.printStackTrace(Main.log_single_out);
					}
				}
			}
			//Main controls what to do with database output
			Main.println("[SQLThread] Currently have " + connections.size() + " connections", Main.DATABASE);

			return connections.remove(0);
		}
	}
	
	public void connectionReady(Connection connection) {
		synchronized(connections) {
			connections.add(connection);
			//Main controls what to do with database output
			Main.println("[SQLThread] Recovering connection; now at " + connections.size() + " connections", Main.DATABASE);
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
					//Main controls what to do with database output
					Main.println("[SQLThread] Creating users table if not exists...", Main.DATABASE);
					
					Statement statement = connection.createStatement();
					//not sure how to write this statement without it being a block of text
					statement.execute("CREATE TABLE IF NOT EXISTS users (id INT(11) NOT NULL PRIMARY KEY AUTO_INCREMENT, username varchar(15) NOT NULL, properusername varchar(15) NOT NULL DEFAULT 'unknown', uid INT(10) NOT NULL DEFAULT '0', rank INT(2) NOT NULL DEFAULT '0', ipaddress varchar(15) NOT NULL DEFAULT 'unknown', lastseen varchar(31) NOT NULL DEFAULT 'unknown', promotedby varchar(15) NOT NULL DEFAULT 'unknown', unbannedby varchar(15) NOT NULL DEFAULT 'unknown') ENGINE=MyISAM DEFAULT CHARSET=utf8 AUTO_INCREMENT=1");
				} catch(SQLException e) {
					Main.println("[SQLThread] Error while creating users table: " + e.getLocalizedMessage(), Main.ERROR);
					//show error stack trace on console
					if(Main.DEBUG) {
						e.printStackTrace();
					}
					//save error stack trace in log
					if(Main.log_error) {
						e.printStackTrace(Main.log_error_out);
					}
					if(Main.log_single) {
						e.printStackTrace(Main.log_single_out);
					}
				}
				connectionReady(connection);
			}
			Main.println("[SQLThread] Refreshing internal lists with database...", Main.DATABASE);
			
			Connection connection = connection();
			
			/*try {
				//refresh admin list
				PreparedStatement statement = connection.prepareStatement("SELECT username, properUsername, uid, rank, ipaddress, lastseen, promotedby, unbannedby FROM users");
				ResultSet result = statement.executeQuery();
				bot.userDB.clear();
				while(result.next()) {
					UserInfo user = new UserInfo();
					user.username = result.getString("username");
					user.properUsername = result.getString("properUsername");
					user.userID = result.getInt("uid");
					int rank = result.getInt("rank");
					if(rank == bot.LEVEL_ROOT_ADMIN) {
						rank = bot.LEVEL_ADMIN;
					}
					user.rank = rank;
					user.ipAddress = result.getString("ipaddress");
					user.lastSeen = result.getString("lastseen");
					user.promotedBy = result.getString("promotedby");
					user.unbannedBy = result.getString("unbannedby");
					bot.userDB.add(user);
				}
				
				if(bannedWordDetectType > 0) {
					result = statement.executeQuery("SELECT phrase FROM phrases WHERE type='bannedword'");
					//bot.bannedWords.clear();
					while(result.next()) {
						//bot.bannedWords.add(result.getString("phrase"));
					}
				}
				
				if(initial) {
					Main.println("[SQLThread] Initial refresh: found " + bot.userDB.size() + " Users");
					//Main.println("[SQLThread] Initial refresh: found " + bot.autoAnn.size() + " Auto Announcements");
					if(bannedWordDetectType > 0) {
						Main.println("[SQLThread] Initial refresh: found " + bot.bannedWords.size() + " Banned Words");
					}
				}
			} catch(SQLException e) {
				if(Main.DEBUG) {
					e.printStackTrace();
				}
				Main.println("[SQLThread] Unable to refresh lists: " + e.getLocalizedMessage());
			}*/
			
			connectionReady(connection);
			
			if(initial) {
				initial = false;
			}
			bot.addRoomList();
			bot.addRoot();
			try {
				Thread.sleep(dbRefreshRate*1000);
			} catch(InterruptedException e) {
				//Main controls what to do with error output
				Main.println("[SQLThread] Run sleep was interrupted: " + e.getLocalizedMessage(), Main.ERROR);
			}
		}
	}
}