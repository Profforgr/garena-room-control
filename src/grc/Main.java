/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package grc;

import grc.bot.ChatThread;
import grc.bot.SQLThread;
import grc.plugin.PluginManager;
import java.io.IOException;

/**
 *
 * @author wizardus
 */
 
public class Main {
	public static final String VERSION = "GRC v0.01";
	public static boolean DEBUG = false;
	public static boolean SHOW_INFO = false;
	
	private static boolean keepLog;

	public GarenaInterface garena;
	private GChatBot bot;
	private PluginManager plugins;
	private GarenaThread gsp_thread;
	private GarenaThread gcrp_thread;
	private GarenaThread pl_thread;
	private SQLThread sqlthread;
	private ChatThread chatthread;
	private GarenaReconnect reconnect;
	private GRCLog log;

	public void init(String[] args) {
		System.out.println(VERSION);
		GRCConfig.load(args);
	}

	public void initPlugins() {
		plugins = new PluginManager();
	}

	public void loadPlugins() {
		plugins.setGarena(garena, gsp_thread, gcrp_thread, pl_thread, sqlthread, chatthread);
		plugins.initPlugins();
		plugins.loadPlugins();
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

		if(!restart) {
			chatthread = new ChatThread(garena);
			chatthread.start();
		}

		//make sure we get correct external ip/port; do on restart in case they changed
		lookup();

		return true;
	}
	
	public void initPeer() {
		//startup GP2PP system
		GarenaThread pl = new GarenaThread(garena, GarenaThread.PEER_LOOP);
		pl.start();
	}

	public void lookup() {
		//lookup
		garena.sendPeerLookup();

		Main.println("[Main] Waiting for lookup response...", GRCLog.SERVER);
		while(garena.iExternal == null) {
			try {
				Thread.sleep(100);
			} catch(InterruptedException e) {}
		}

		Main.println("[Main] Received lookup response!", GRCLog.SERVER);
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
	
	public void reconnectRoom() {
		Main.println("[Main] Reconnecting to Garena room", GRCLog.SERVER);
		garena.disconnectRoom();
		//garena.hasRoomList = false;
		
		try {
			Thread.sleep(1500);
		} catch(InterruptedException e) {}
		
		initRoom(true);
		syncRoomLoop();
	}

	public void initBot() {
		bot = new GChatBot(this);
		bot.init();
			
		bot.garena = garena;
		bot.plugins = plugins;
		bot.sqlthread = sqlthread;
		bot.chatthread = chatthread;

		garena.registerListener(bot);
		
		//initiate mysql thread
		sqlthread = new SQLThread(bot);
		sqlthread.init();
		sqlthread.start();
		
		bot.sqlthread = sqlthread;
		
		syncRoomLoop();
	}
	
	public void initLog() {
		if(keepLog) {
			log = new GRCLog();
			try {
				log.init();
			} catch(IOException ioe) {
				ioe.printStackTrace();
			}
		}
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
				garena.stopPlaying();
				playCounter = 0;
				garena.startPlaying(); //make sure we're actually playing
			}

			//handle reconnection interval
			if(reconnectInterval != -1 && reconnectCounter >= reconnectInterval) {
				reconnectCounter = 0;
				//reconnect to Garena Room
				Main.println("[Main] Reconnecting to Garena Room", GRCLog.ROOM);
				garena.disconnectRoom();

				try {
					Thread.sleep(10000);
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
						println("[Main] Sent exp packet to Garena", GRCLog.SERVER);
					}
				}
			}

			try {
				Thread.sleep(10000);
			} catch(InterruptedException e) {}
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
		
		//whether to output information on console
		DEBUG = GRCConfig.configuration.getBoolean("grc_debug", false);
		SHOW_INFO = GRCConfig.configuration.getBoolean("grc_show_info", false);
		
		//whether to keep a log
		keepLog = GRCConfig.configuration.getBoolean("grc_log", false);

		main.initPlugins();
		if(!main.initGarena(false)) return;
		if(!main.initRoom(false)) return;
		main.initBot();
		main.loadPlugins();
		main.initLog();
		main.helloLoop();
	}

	public static void println(String str, int type) {
		//check whether to display each type
		if(type == GRCLog.ROOM || type == GRCLog.COMMAND || ((type == GRCLog.DATABASE || type == GRCLog.SERVER) && SHOW_INFO) || (type == GRCLog.ERROR && DEBUG)) {
			System.out.println(str);
		}
		
		if(keepLog) {
			GRCLog.println(str, type);
		}
	}
	
	//poll garena interface if it has finished parsing room list for users every second
	public void syncRoomLoop() {
		while(true) {
			if(garena.hasRoomList) {
				syncRoom();
				bot.startAnnTimer();
				break;
			} else {
				try {
					Thread.sleep(1000);
				} catch(InterruptedException e) {
					println("[Main] sync room loop sleep interrupted", GRCLog.ERROR);
				}
			}
		}
	}
	
	//if garena interface has finished parsing room list, sync with user database
	public void syncRoom() {
		for(int i = 0; i < garena.members.size(); i++) {
			bot.addUserToDatabase(garena.members.get(i));
		}
	}
	
	public static void stackTrace(Exception e) {
		//show error stack trace on console
		if(DEBUG) {
			e.printStackTrace();
		}
		//save error stack trace in log
		GRCLog.stackTrace(e);
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