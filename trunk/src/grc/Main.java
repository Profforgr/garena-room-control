/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package grc;

import grc.bot.ChatThread;
import grc.bot.SQLThread;
import grc.plugin.PluginManager;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.util.Calendar;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 *
 * @author wizardus
 */
 
public class Main {
	public static String VERSION = "GRC v0.01";
	public static boolean DEBUG = false;
	public static boolean SHOW_INFO = false;
	public static final String DATE_FORMAT = "yyyy-MM-dd";
	public static final int SERVER = 1;
	public static final int ROOM = 2;
	public static final int COMMAND = 3;
	public static final int DATABASE = 4;
	public static final int ERROR = 5;
	public static boolean log;
	public static boolean log_single;
	public static boolean log_server;
	public static boolean log_room;
	public static boolean log_command;
	public static boolean log_database;
	public static boolean log_error;

	public static PrintWriter log_single_out;
	public static PrintWriter log_server_out;
	public static PrintWriter log_room_out;
	public static PrintWriter log_command_out;
	public static PrintWriter log_database_out;
	public static PrintWriter log_error_out;
	
	GChatBot bot;

	PluginManager plugins;
	GarenaInterface garena;
	GarenaThread gsp_thread;
	GarenaThread gcrp_thread;
	GarenaThread pl_thread;
	SQLThread sqlthread;
	ChatThread chatthread;
	GarenaReconnect reconnect;

	//determine what will be loaded, what won't be loaded
	boolean loadPlugins;
	boolean loadPL;
	boolean loadSQL;
	boolean loadChat;

	public void init(String[] args) {
		System.out.println(VERSION);
		GRCConfig.load(args);

		//log settings
		log = GRCConfig.configuration.getBoolean("grc_log", false);
		if(log) {
			log_single = GRCConfig.configuration.getBoolean("grc_log_single", false);
			log_server = GRCConfig.configuration.getBoolean("grc_log_server", false);
			log_room = GRCConfig.configuration.getBoolean("grc_log_room", false);
			log_command = GRCConfig.configuration.getBoolean("grc_log_command", false);
			log_database = GRCConfig.configuration.getBoolean("grc_log_database", false);
			log_error = GRCConfig.configuration.getBoolean("grc_log_error", false);
		} else {
			log_single = false;
			log_server = false;
			log_room = false;
			log_command = false;
			log_database = false;
			log_error = false;
		}

		//first load all the defaults
		loadPlugins = true;
		loadPL = true;
		loadSQL = true;
		loadChat = true;
	}

	public void initPlugins() {
		if(loadPlugins) {
			plugins = new PluginManager();
		}
	}

	public void loadPlugins() {
		if(loadPlugins) {
			plugins.setGarena(garena, gsp_thread, gcrp_thread, pl_thread, sqlthread, chatthread);
			plugins.initPlugins();
			plugins.loadPlugins();
		}
	}

	public boolean initGarena(boolean restart) {
		//connect to garena
		if(!restart) {
			garena = new GarenaInterface(plugins);
			reconnect = new GarenaReconnect(this);
			garena.registerListener(reconnect);
		}

		if(!garena.init()) {
			return false;
		}

		initPeer();

		//authenticate with login server
		if(!garena.sendGSPSessionInit()) return false;
		if(!garena.readGSPSessionInitReply()) return false;
		if(!garena.sendGSPSessionHello()) return false;
		if(!garena.readGSPSessionHelloReply()) return false;
		if(!garena.sendGSPSessionLogin()) return false;
		if(!garena.readGSPSessionLoginReply()) return false;

		if(!restart || gsp_thread.terminated) {
			gsp_thread = new GarenaThread(garena, GarenaThread.GSP_LOOP);
			gsp_thread.start();
		}

		if(loadChat && !restart) {
			chatthread = new ChatThread(garena);
			chatthread.start();
		}

		//make sure we get correct external ip/port; do on restart in case they changed
		lookup();

		return true;
	}
	
	public void initPeer() {
		if(loadPL) {
			//startup GP2PP system
			GarenaThread pl = new GarenaThread(garena, GarenaThread.PEER_LOOP);
			pl.start();
		}
	}

	public void lookup() {
		if(loadPL) {
			//lookup
			garena.sendPeerLookup();

			Main.println("[Main] Waiting for lookup response...", SERVER);
			while(garena.iExternal == null) {
				try {
					Thread.sleep(100);
				} catch(InterruptedException e) {}
			}

			Main.println("[Main] Received lookup response!", SERVER);
		}
	}

	//returns whether init succeeded; restart=true indicates this isn't the first time we're calling
	public boolean initRoom(boolean restart) {
		//connect to room
		if(!garena.initRoom()) return false;
		if(!garena.sendGCRPMeJoin()) return false;

		if(!restart || gcrp_thread.terminated) {
			gcrp_thread = new GarenaThread(garena, GarenaThread.GCRP_LOOP);
			gcrp_thread.start();
		}
		
		// we ought to say we're starting the game; we'll do later too
		garena.startPlaying();

		return true;
	}

	public void initBot() {
		bot = new GChatBot(this);
		bot.init();
			
		bot.garena = garena;
		bot.plugins = plugins;
		bot.sqlthread = sqlthread;
		bot.chatthread = chatthread;

		garena.registerListener(bot);
		
		if(loadSQL) {
			//initiate mysql thread
			sqlthread = new SQLThread(bot);
			sqlthread.init();
			sqlthread.start();
		}
		
		bot.sqlthread = sqlthread;
	}
	
	public void helloLoop() {
		
		int playCounter = 0;
		int reconnectCounter = 0;
		int xpCounter = 0;
		
		//see how often to reconnect
		int reconnectMinuteInterval = GRCConfig.configuration.getInt("grc_reconnect_interval", -1);
		//divide by six to get interval measured for 10 second delays
		int reconnectInterval = -1;

		if(reconnectMinuteInterval > 0) {
			reconnectInterval = reconnectMinuteInterval * 6;
		}

		//see how often to send XP packet; every 15 minutes
		int xpInterval = 90; //15 * 60 / 10
		
		if(loadPL) {
			while(true) {
				try {
					garena.displayMemberInfo();
				} catch(IOException ioe) {
					ioe.printStackTrace();
				}

				garena.sendPeerHello();

				playCounter++;
				reconnectCounter++;
				xpCounter++;

				//handle player interval
				if(playCounter > 360000) { //1 hour
					playCounter = 0;
					garena.startPlaying(); //make sure we're actually playing
				}

				//handle reconnection interval
				if(reconnectInterval != -1 && reconnectCounter >= reconnectInterval) {
					reconnectCounter = 0;
					//reconnect to Garena room
					Main.println("[Main] Reconnecting to Garena room", ROOM);
					garena.disconnectRoom();

					try {
						Thread.sleep(1000);
					} catch(InterruptedException e) {}

					initRoom(true);
				}

				//handle xp interval
				if(xpCounter >= xpInterval) {
					xpCounter = 0;

					//send GSP XP packet only if connected to room
					if(garena.room_socket.isConnected()) {
						//xp rate = 100 (doesn't matter what they actually are, server determines amount of exp gained)
						//gametype = 1001 for warcraft/dota
						garena.sendGSPXP(garena.user_id, 100, 1001);
						if(DEBUG) {
							println("[Main] Sent exp packet to Garena", SERVER);
						}
					}
				}

				try {
					Thread.sleep(10000);
				} catch(InterruptedException e) {}
			}
		} else {
			//send start playing so that we don't disconnect from the room
			
			while(true) {
				
				garena.sendPeerHello();
				
				playCounter++;
				xpCounter++;
				
				//handle player interval
				if(playCounter > 360000) { //1 hour
					playCounter = 0;
					garena.startPlaying();
				}
				
				//handle xp interval
				if(xpCounter >= xpInterval) {
					xpCounter = 0;
					
					//send GSP XP packet only if connected to room
					if(garena.room_socket.isConnected()) {
						//xp rate = 100 (doesn't matter what they actually are, server determines amount of exp gained)
						//gametype = 1001 for warcraft/dota
						garena.sendGSPXP(garena.user_id, 100, 1001);
						if(DEBUG) {
							println("[Main] Sent exp packet to Garena", SERVER);
						}
					}
				}

				try {
					Thread.sleep(10000);
				} catch(InterruptedException e) {}
			}
		}
	}

	/**
	 * @param args the command line arguments
	 */
	public static void main(String[] args) throws IOException {
		/* Use this to decrypt Garena packets
		try {
			GarenaEncrypt encrypt = new GarenaEncrypt();
			encrypt.initRSA();

			byte[] data = readWS(args[0]);
			byte[] plain = encrypt.rsaDecryptPrivate(data);

			byte[] key = new byte[32];
			byte[] init_vector = new byte[16];
			System.arraycopy(plain, 0, key, 0, 32);
			System.arraycopy(plain, 32, init_vector, 0, 16);
			encrypt.initAES(key, init_vector);

			data = readWS(args[1]);
			byte[] out = encrypt.aesDecrypt(data);

			Main.println(encrypt.hexEncode(out));
		} catch (Exception e) {
			e.printStackTrace();
		}
		System.exit(0);*/

		
		Main main = new Main();
		main.init(args);

		//init log
		if(log) {
			if(log_single) {
				log_single_out = new PrintWriter(new FileWriter("grc.log", true), true);
			}
			if(log_server) {
				//keep seperate logs in seperate folders
				File log_server_dir = new File("log/log_server/");
				//if folder doesn't exist, create
				if(!log_server_dir.exists()) {
					log_server_dir.mkdir();
				}
				//set target to folder and set file name
				File log_server_target = new File(log_server_dir, "grc_server.log");
				//initialize printwriter
				log_server_out = new PrintWriter(new FileWriter(log_server_target, true), true);
			}
			if(log_room) {
				//see comments for log_server, same code but with different file names
				File log_room_dir = new File("log/log_room/");
				if(!log_room_dir.exists()) {
					log_room_dir.mkdir();
				}
				File log_room_target = new File(log_room_dir, "grc_room.log");
				log_room_out = new PrintWriter(new FileWriter(log_room_target, true), true);
			}
			if(log_command) {
				//see comments for log_server, same code but with different file names
				File log_cmd_dir = new File("log/log_cmd/");
				if(!log_cmd_dir.exists()) {
					log_cmd_dir.mkdir();
				}
				File log_cmd_target = new File(log_cmd_dir, "grc_cmd.log");
				log_command_out = new PrintWriter(new FileWriter(log_cmd_target, true), true);
			}
			if(log_database) {
				//see comments for log_server, same code but with different file names
				File log_db_dir = new File("log/log_database/");
				if(!log_db_dir.exists()) {
					log_db_dir.mkdir();
				}
				File log_db_target = new File(log_db_dir, "grc_db.log");
				log_database_out = new PrintWriter(new FileWriter(log_db_target, true), true);
			}
			if(log_error) {
				//see comments for log_server, same code but with different file names
				File log_error_dir = new File("log/log_error/");
				if(!log_error_dir.exists()) {
					log_error_dir.mkdir();
				}
				File log_error_target = new File(log_error_dir, "grc_error.log");
				log_error_out = new PrintWriter(new FileWriter(log_error_target, true), true);
			}
		}
		
		//whether to output information on console
		DEBUG = GRCConfig.configuration.getBoolean("grc_debug", false);
		SHOW_INFO = GRCConfig.configuration.getBoolean("grc_show_info", false);

		main.initPlugins();
		if(!main.initGarena(false)) return;
		if(!main.initRoom(false)) return;
		main.initBot();
		main.loadPlugins();
		main.helloLoop();
	}

	public static void println(String str, int type) {
		//check whether to display each type
		if(type == ROOM || type == COMMAND || ((type == DATABASE || type == SERVER) && SHOW_INFO) || (type == ERROR && DEBUG)) {
			System.out.println(str);
		}
		
		if(log) {
			//format date nicely
			Date date = new Date();
			String dateString = DateFormat.getDateTimeInstance().format(date);
			
			//for single log file
			if(log_single && log_single_out != null) {
				log_single_out.println("[" + dateString + "]" + str);
			}
			//for each log type (server, room, command, database, error)
			switch(type) {
				case SERVER:
					//write to file
					if(log_server && log_server_out != null) {
						log_server_out.println("[" + dateString + "]" + str);
					}
					break;
				case ROOM:
					if(log_room && log_room_out != null) {
						log_room_out.println("[" + dateString + "]" + str);
					}
					break;
				case COMMAND:
					if(log_command && log_command_out != null) {
						log_command_out.println("[" + dateString + "]" + str);
					}
					break;
				case DATABASE:
					if(log_database && log_database_out != null) {
						log_database_out.println("[" + dateString + "]" + str);
					}
					break;
				case ERROR:
					if(log_error && log_error_out != null) {
						log_error_out.println("[" + dateString + "]" + str);
					}
					break;
				default:
					System.out.println(str);
					System.out.println("Ouput type unknown, discarding");
			}
		}
	}
	
	public static void stackTrace(Exception e) {
		//show error stack trace on console
		if(DEBUG) {
			e.printStackTrace();
		}
		//save error stack trace in log
		if(log_error) {
			e.printStackTrace(log_error_out);
		}
		if(log_single) {
			e.printStackTrace(log_single_out);
		}
	}
	
	public static String date() {
		Calendar cal = Calendar.getInstance();
		SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT);
		return sdf.format(cal.getTime());
	}

	//hexadecimal string to byte array
	public static byte[] readWS(String s) {
		int len = s.length();
		
		byte[] data = new byte[len / 2];
		for (int i = 0; i < len; i += 2) {
			data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
								 + Character.digit(s.charAt(i+1), 16));
		}
		
		return data;
	}

}