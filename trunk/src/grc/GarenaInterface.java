/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package grc;

import grc.plugin.PluginManager;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Vector;
import java.util.zip.CRC32;
import java.util.Calendar;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Iterator;

/**
 *
 * @author wizardus
 */
 
public class GarenaInterface {
	public static final int GARENA_MAIN = 0;
	public static final int GARENA_ROOM = 1;
	public static final int GARENA_PEER = 2;
	public static final String TIME_FORMAT = "HH:mm:ss";
	
	public boolean hasRoomList = false;
	
	//cryptography class
	private GarenaEncrypt crypt;

	//login server address
	private InetAddress main_address;

	//plugin manager
	private PluginManager plugins;
	
	//login server objects
	public Socket socket;
	private DataOutputStream out;
	private DataInputStream in;

	//room server objects
	public Socket room_socket;
	private DataOutputStream rout;
	private DataInputStream rin;
	public int room_id;

	//peer to peer objects
	public int peer_port;
	public DatagramSocket peer_socket;

	public Vector<MemberInfo> members;
	private Vector<RoomInfo> rooms;
	private Vector<GarenaListener> listeners;
	
	//our user ID
	public int user_id;
	
	//unknown values in myinfo block
	private int unknown1;
	private int unknown2;
	private int unknown3;
	private int unknown4;

	//external and internal IP address, will be set later
	public byte[] iExternal;
	private byte[] iInternal;
	
	//external and internal port, will be set later
	private int pExternal;
	private int pInternal;

	//myinfo block
	private byte[] myinfo;

	public GarenaInterface(PluginManager plugins) {
		this.plugins = plugins;
		
		crypt = new GarenaEncrypt();
		members = new Vector<MemberInfo>();
		rooms = new Vector<RoomInfo>();
		listeners = new Vector<GarenaListener>();

		//configuration
		room_id = GRCConfig.configuration.getInt("grc_roomid", 590633);
		peer_port = GRCConfig.configuration.getInt("grc_peerport", 1513);
	}

	public boolean init() {
		Main.println("[GInterface] Initializing...", Log.SERVER);
		crypt.initAES();
		crypt.initRSA();

		//hostname lookup
		try {
			String main_hostname = GRCConfig.configuration.getString("grc_mainhost", "con2.garenaconnect.com");
			main_address = InetAddress.getByName(main_hostname);
		} catch(UnknownHostException uhe) {
			//give error information to Main
			Main.println("[GInterface] Unable to locate main host: " + uhe.getLocalizedMessage(), Log.ERROR);
			Main.stackTrace(uhe);
			
			disconnected(GARENA_MAIN);
			return false;
		}

		//connect
		Main.println("[GInterface] Connecting to " + main_address.getHostAddress() + "...", Log.SERVER);
		try {
			socket = new Socket(main_address, 7456);
			Main.println("[GInterface] Using local port: " + socket.getLocalPort(), Log.SERVER);
		} catch(IOException ioe) {
			//give error information to Main
			Main.println("[GInterface] Error: " + ioe.getLocalizedMessage(), Log.ERROR);
			Main.stackTrace(ioe);
			
			disconnected(GARENA_MAIN);
			return false;
		}

		try {
			out = new DataOutputStream(socket.getOutputStream());
			in = new DataInputStream(socket.getInputStream());
		} catch(IOException ioe) {
			//give error information to Main
			Main.println("[GInterface] Error: " + ioe.getLocalizedMessage(), Log.ERROR);
			Main.stackTrace(ioe);
			
			disconnected(GARENA_MAIN);
			return false;
		}

		//init GP2PP socket
		try {
			//determine bind address from configuration
			InetAddress bindAddress = null;
			String bindAddressString = GRCConfig.configuration.getString("grc_bindaddress", null);

			if(bindAddressString != null && !bindAddressString.trim().equals("")) {
				bindAddress = InetAddress.getByName(bindAddressString);
			}

			//if bindAddress unset, then use wildcard address; otherwise bind to specified address
			if(bindAddress == null) {
				peer_socket = new DatagramSocket(peer_port);
			} else {
				peer_socket = new DatagramSocket(peer_port, bindAddress);
			}

			if(peer_socket.getInetAddress() instanceof Inet6Address) {
				Main.println("[GInterface] Warning: binded to IPv6 address: " + peer_socket.getInetAddress(), Log.ERROR);
			}
		} catch(IOException ioe) {
			//give error information to Main
			Main.println("[GInterface] Unable to establish peer socket on port " + peer_port + ": " + ioe.getLocalizedMessage(), Log.ERROR);
			Main.stackTrace(ioe);
			
			disconnected(GARENA_PEER);
			return false;
		}

		return true;
	}

	public boolean initRoom() {
		Main.println("[GInterface] Connecting to room...", Log.SERVER);

		//update room_id in case this is called from !room command
		room_id = GRCConfig.configuration.getInt("grc_roomid", -1);
		String room_hostname = GRCConfig.configuration.getString("grc_roomhost", null); //default server 9

		//see if we should check by name instead
		if(room_id == -1 || room_hostname == null || room_hostname.trim().equals("")) {
			Main.println("[GInterface] Automatically searching for roomid and roomhost...", Log.SERVER);

			String roomName = GRCConfig.configuration.getString("grc_roomname", null);

			if(roomName == null) {
				Main.println("[GInterface] Error: no room name set; shutting down!", Log.ERROR);
				disconnected(GARENA_ROOM);
				return false;
			}

			File roomFile = new File("grcrooms.txt");

			if(!roomFile.exists()) {
				Main.println("[GInterface] Error: " + roomFile.getAbsolutePath() + " does not exist!", Log.ERROR);
				disconnected(GARENA_ROOM);
				return false;
			}

			//read file and hope there's name in it; don't be case sensitive, but some rooms repeat!
			try {
				BufferedReader in = new BufferedReader(new FileReader(roomFile));
				String line;

				while((line = in.readLine()) != null) {
					String[] parts = line.split("\\*\\*");

					if(parts[0].trim().equalsIgnoreCase(roomName)) {
						room_id = Integer.parseInt(parts[1]);
						room_hostname = parts[3];
						Main.println("[GInterface] Autoset found match; name is [" + parts[0] + "],"
								+ " id is [" + room_id + "]" + ", host is [" + room_hostname + "],"
								+ " and game is [" + parts[5] + "]", Log.SERVER);
						
						break;
					}
				}

				if(room_id == -1 || room_hostname == null || room_hostname.trim().equals("")) {
					Main.println("[GInterface] Error: no matches found; exiting...", Log.ERROR);
					disconnected(GARENA_ROOM);
					return false;
				}
			} catch(IOException ioe) {
				//give error information to Main
				Main.println("[GInterface] Error during autosearch: " + ioe.getLocalizedMessage(), Log.ERROR);
				Main.stackTrace(ioe);
				
				disconnected(GARENA_ROOM);
				return false;
			}
		}

		InetAddress address = null;
		//hostname lookup
		Main.println("[GInterface] Conducting hostname lookup...", Log.SERVER);
		try {
			address = InetAddress.getByName(room_hostname);
		} catch(UnknownHostException uhe) {
			//give error information to Main
			Main.println("[GInterface] Unable to locate room host: " + uhe.getLocalizedMessage(), Log.ERROR);
			Main.stackTrace(uhe);

			disconnected(GARENA_ROOM);
			return false;
		}

		//connect
		Main.println("[GInterface] Connecting to " + address.getHostAddress() + "...", Log.SERVER);
		try {
			room_socket = new Socket(address, 8687);
			Main.println("[GInterface] Using local port: " + room_socket.getLocalPort(), Log.SERVER);
		} catch(IOException ioe) {
			//give error information to Main
			Main.println("[GInterface] Error: " + ioe.getLocalizedMessage(), Log.ERROR);
			disconnected(GARENA_ROOM);
			Main.stackTrace(ioe);
			
			return false;
		}

		try {
			rout = new DataOutputStream(room_socket.getOutputStream());
			rin = new DataInputStream(room_socket.getInputStream());
		} catch(IOException ioe) {
			//give error information to Main
			Main.println("[GInterface] Error: " + ioe.getLocalizedMessage(), Log.ERROR);
			Main.stackTrace(ioe);

			disconnected(GARENA_ROOM);
			return false;
		}

		//notify main server that we joined the room
		sendGSPJoinedRoom(user_id, room_id);

		return true;
	}

	public void disconnectRoom() {
		Main.println("[GInterface] Disconnecting from room...", Log.SERVER);

		//send GCRP part
		Main.println("[GInterface] Sending GCRP PART...", Log.SERVER);
		
		ByteBuffer lbuf = ByteBuffer.allocate(9);
		lbuf.order(ByteOrder.LITTLE_ENDIAN);
		lbuf.putInt(5); //message size
		lbuf.put((byte) 0x23); //PART identifier
		lbuf.putInt(user_id);

		try {
			rout.write(lbuf.array());
		} catch(IOException ioe) {
			//ignore
		}

		try {
			room_socket.close();
		} catch(IOException ioe) {
			//ignore
		}

		//cleanup room objects
		members.clear();

		//notify the main server
		sendGSPJoinedRoom(user_id, 0);
	}

	public boolean sendGSPSessionInit() {
		Main.println("[GInterface] Sending GSP session init...", Log.SERVER);

		ByteBuffer block = ByteBuffer.allocate(50);
		block.order(ByteOrder.LITTLE_ENDIAN);
		block.put(crypt.skey.getEncoded());
		block.put(crypt.iv);
		block.putShort((short) 0xF00F);
		byte[] array = block.array();

		ByteBuffer lbuf = null;
		try {
			byte[] encrypted = crypt.rsaEncryptPrivate(array);
			
			lbuf = ByteBuffer.allocate(encrypted.length + 6);
			lbuf.order(ByteOrder.LITTLE_ENDIAN);
			
			lbuf.putInt(258);
			lbuf.putShort((short) 0x00AD);
			lbuf.put(encrypted);
		} catch(Exception e) {
			Main.println("[GInterface] Encryption error: " + e.getLocalizedMessage(), Log.ERROR);
		}

		try {
			out.write(lbuf.array());
			return true;
		} catch(IOException ioe) {
			Main.println("[GInterface] I/O Error: " + ioe.getLocalizedMessage(), Log.ERROR);
			disconnected(GARENA_MAIN);
			return false;
		}
	}

	public boolean readGSPSessionInitReply() {
		Main.println("[GInterface] Reading GSP session init reply...", Log.SERVER);

		try {
			byte[] size_bytes = new byte[3];
			in.read(size_bytes);
			int size = crypt.byteArrayToIntLittleLength(size_bytes, 0, 3);

			//next byte should be 1
			if(in.read() != 1) {
				Main.println("[GInterface] Warning: invalid data from Garena server", Log.ERROR);
			}

			byte[] bb = new byte[size];
			in.read(bb);
			byte[] data = crypt.aesDecrypt(bb);

			if(data[0] == -82 || data[0] == 174) { //-82 since byte is always signed and 174 is over max
				Main.println("[GInterface] GSP session init reply received!", Log.SERVER);
			} else {
				Main.println("[GInterface] Warning: invalid type " + data[0] + " from Garena server", Log.ERROR);
			}

			return true;
		} catch(IOException ioe) {
			Main.println("[GInterface] I/O Error: " + ioe.getLocalizedMessage(), Log.ERROR);
			disconnected(GARENA_MAIN);
			return false;
		} catch(Exception e) {
			Main.println("[GInterface] Decryption error: " + e.getLocalizedMessage(), Log.ERROR);
			return false;
		}
	}

	public boolean sendGSPSessionHello() {
		Main.println("[GInterface] Sending GSP session hello...", Log.SERVER);

		ByteBuffer block = ByteBuffer.allocate(7);
		block.order(ByteOrder.LITTLE_ENDIAN);
		block.put((byte) 0xD3); //hello packet identifier
		block.put((byte) 69); //language identifier; E
		block.put((byte) 78); //.....................N

		int version_identifier = GRCConfig.configuration.getInt("grc_version", 0x0000027C);
		block.putInt(version_identifier); //version identifier
		
		byte[] array = block.array();

		ByteBuffer lbuf = null;
		try {
			byte[] encrypted = crypt.aesEncrypt(array);

			lbuf = ByteBuffer.allocate(4 + encrypted.length);
			lbuf.order(ByteOrder.LITTLE_ENDIAN);
			lbuf.putShort((short) encrypted.length);
			lbuf.put((byte) 0);
			lbuf.put((byte) 1);

			lbuf.put(encrypted);
		} catch(Exception e) {
			Main.println("[GInterface] Encryption error: " + e.getLocalizedMessage(), Log.ERROR);
			return false;
		}

		try {
			out.write(lbuf.array());
			return true;
		} catch(IOException ioe) {
			Main.println("[GInterface] I/O Error: " + ioe.getLocalizedMessage(), Log.ERROR);
			disconnected(GARENA_MAIN);
			return false;
		}
	}

	public boolean readGSPSessionHelloReply() {
		Main.println("[GInterface] Reading GSP session hello reply...", Log.SERVER);

		try {
			byte[] size_bytes = new byte[3];
			in.read(size_bytes);
			int size = crypt.byteArrayToIntLittleLength(size_bytes, 0, 3);

			//next byte should be 1
			if(in.read() != 1) {
				Main.println("[GInterface] Warning: invalid data from Garena server", Log.ERROR);
			}

			byte[] bb = new byte[size];
			in.read(bb);
			byte[] data = crypt.aesDecrypt(bb);

			if(data[0] == -45 || data[0] == 211) {
				Main.println("[GInterface] GSP session hello reply received!", Log.SERVER);
			} else {
				Main.println("[GInterface] Warning: invalid type " + data[0] + " from Garena server", Log.ERROR);
			}

			return true;
		} catch(IOException ioe) {
			Main.println("[GInterface] I/O Error: " + ioe.getLocalizedMessage(), Log.ERROR);
			disconnected(GARENA_MAIN);
			return false;
		} catch(Exception e) {
			Main.println("[GInterface] Decryption error: " + e.getLocalizedMessage(), Log.ERROR);
			return false;
		}
	}

	public boolean sendGSPSessionLogin() {
		Main.println("[GInterface] Sending GSP session login...", Log.SERVER);
		String username = GRCConfig.configuration.getString("grc_username");
		String password = GRCConfig.configuration.getString("grc_password");

		ByteBuffer block = ByteBuffer.allocate(69);
		block.order(ByteOrder.LITTLE_ENDIAN);
		block.put((byte) 0x1F); //packet identifier
		block.put((byte) 0);
		block.put((byte) 0);
		block.put((byte) 0);
		block.put((byte) 0);

		//now we need to put username
		try {

			byte[] username_bytes = username.getBytes("UTF-8");
			if(username_bytes.length > 16) {
				Main.println("[GInterface] Fatal error: your username is much too long.", Log.ERROR);
				System.exit(-1);
			}

			byte[] username_buf = new byte[16];
			System.arraycopy(username_bytes, 0, username_buf, 0, username_bytes.length);
			block.put(username_buf);
		} catch(UnsupportedEncodingException e) {
			Main.println("[GInterface] Fatal error: " + e.getLocalizedMessage(), Log.ERROR);
			System.exit(-1);
		}

		block.put((byte) 0);
		block.put((byte) 0);
		block.put((byte) 0);
		block.put((byte) 0);

		block.putInt(0x20); //32; size of password hash

		//now we need to hash password and send
		String password_hash = crypt.md5(password);
		try {
			byte[] password_bytes = password_hash.getBytes("UTF-8");
			if(password_bytes.length > 33) {
				Main.println("[GInterface] Fatal error: password hash is much too long!", Log.ERROR);
				System.exit(-1);
			}

			byte[] password_buf = new byte[33];
			System.arraycopy(password_bytes, 0, password_buf, 0, password_bytes.length);
			block.put(password_buf);
		} catch(UnsupportedEncodingException e) {
			Main.println("[GInterface] Fatal error: " + e.getLocalizedMessage(), Log.ERROR);
			System.exit(-1);
		}

		block.put((byte) 1);

		//now we need to put internal IP
		byte[] addr = crypt.internalAddress();
		block.put(addr);

		//external peer port; don't change from 1513
		block.put((byte) 5);
		block.put((byte) -23);

		byte[] array = block.array();

		ByteBuffer lbuf = null;
		try {
			byte[] encrypted = crypt.aesEncrypt(array);

			lbuf = ByteBuffer.allocate(4 + encrypted.length);
			lbuf.order(ByteOrder.LITTLE_ENDIAN);
			lbuf.putShort((short) encrypted.length);
			lbuf.put((byte) 0);
			lbuf.put((byte) 1);

			lbuf.put(encrypted);
		} catch(Exception e) {
			Main.println("[GInterface] Encryption error: " + e.getLocalizedMessage(), Log.ERROR);
			return false;
		}

		try {
			out.write(lbuf.array());
			return true;
		} catch(IOException ioe) {
			Main.println("[GInterface] I/O Error: " + ioe.getLocalizedMessage(), Log.ERROR);
			disconnected(GARENA_MAIN);
			return false;
		}
	}

	public boolean readGSPSessionLoginReply() {
		Main.println("[GInterface] Reading GSP session login reply...", Log.SERVER);

		try {
			byte[] size_bytes = new byte[3];
			in.read(size_bytes);
			int size = crypt.byteArrayToIntLittleLength(size_bytes, 0, 3);

			//next byte should be 1
			if(in.read() != 1) {
				Main.println("[GInterface] Warning: invalid data from Garena server", Log.ERROR);
			}

			byte[] bb = new byte[size];
			in.read(bb);
			byte[] data = crypt.aesDecrypt(bb);

			if(data[0] == -187 || data[0] == 69) {
				Main.println("[GInterface] Successfully logged in!", Log.SERVER);
			} else if(data[0] == -210 || data[0] == 46) {
				Main.println("[GInterface] Invalid username or password.", Log.ERROR);
			} else {
				Main.println("[GInterface] Warning: invalid type " + data[0] + " from Garena server", Log.ERROR);
			}

			myinfo = new byte[data.length - 9];
			System.arraycopy(data, 9, myinfo, 0, data.length - 9);
			processMyInfo(myinfo);

			return true;
		} catch(IOException ioe) {
			Main.println("[GInterface] I/O Error: " + ioe.getLocalizedMessage(), Log.ERROR);
			disconnected(GARENA_MAIN);
			return false;
		} catch(Exception e) {
			Main.println("[GInterface] Decryption error: " + e.getLocalizedMessage(), Log.ERROR);
			return false;
		}
	}

	public void processMyInfo(byte[] array) {
		ByteBuffer buf = ByteBuffer.allocate(4096);
		buf.order(ByteOrder.LITTLE_ENDIAN);
		buf.put(array);

		user_id = buf.getInt(0);
		Main.println("[GInterface] Server says your ID is: " + user_id, Log.SERVER);

		byte[] str_bytes = new byte[16];
		buf.position(4);
		buf.get(str_bytes);
		Main.println("[GInterface] Server says your username is: " + (crypt.strFromBytes(str_bytes)), Log.SERVER);

		str_bytes = new byte[2];
		buf.position(20);
		buf.get(str_bytes);
		Main.println("[GInterface] Server says your country is: " + (crypt.strFromBytes(str_bytes)), Log.SERVER);

		unknown1 = buf.get(24);
		Main.println("[GInterface] Server says your experience is: " + crypt.unsignedByte(buf.get(25)), Log.SERVER);
		unknown2 = buf.get(26);

		/* get ports through lookup method
		int b1 = (0x000000FF & ((int)buf.get(40))); //make sure it's unsigned
		int b2 = (0x000000FF & ((int)buf.get(41)));
		pExternal = b1 << 8 | b2;
		Main.println("[GInterface] Setting external peer port to " + pExternal, Log.SERVER);
		//22 0's
		b1 = (0x000000FF & ((int)buf.get(64)));
		b2 = (0x000000FF & ((int)buf.get(65)));
		pInternal = b1 << 8 | b2;
		Main.println("[GInterface] Setting internal peer port to " + pInternal, Log.SERVER); */
		//19 0's
		unknown3 = buf.get(85);
		unknown4 = buf.get(88);

		str_bytes = new byte[array.length - 92];
		buf.position(92);
		buf.get(str_bytes);
		Main.println("[GInterface] Server says your email address is: " + (crypt.strFromBytes(str_bytes)), Log.SERVER);
	}

	public void readGSPLoop() {
		ByteBuffer lbuf = ByteBuffer.allocate(2048);
		while(true) {
			lbuf.clear();
			lbuf.order(ByteOrder.LITTLE_ENDIAN);

			try {
				byte[] size_bytes = new byte[3];
				in.readFully(size_bytes);
				int size = crypt.byteArrayToIntLittleLength(size_bytes, 0, 3);

				if(in.read() != 1) {
					Main.println("[GInterface] GSPLoop: warning: invalid data from Garena server", Log.ERROR);
				}

				Main.println("[GInterface] GSPLoop: received " + size + " bytes of encrypted data", Log.SERVER);

				byte[] bb = new byte[size];
				in.readFully(bb);
				byte[] data = crypt.aesDecrypt(bb);

				//notify plugins
				plugins.onPacket(GARENA_MAIN, -1, data, 0, data.length);

				if(data[0] == 68) {
					processQueryResponse(data);
				} else {
					Main.println("[GInterface] GSPLoop: unknown type received: " + data[0], Log.ERROR);
				}
			} catch(IOException ioe) {
				Main.println("[GInterface] GSPLoop: error: " + ioe.getLocalizedMessage(), Log.ERROR);
				disconnected(GARENA_MAIN);
				return;
			} catch(Exception e) {
				//give error information to Main
				Main.println("[GInterface] GSLoop: error: " + e.getLocalizedMessage(), Log.ERROR);
				Main.stackTrace(e);
			}
		}
	}

	public void processQueryResponse(byte[] data) throws IOException {
		int id = crypt.byteArrayToIntLittle(data, 1);
		Main.println("[GInterface] Query response: user ID is " + id, Log.SERVER);
	}

	public boolean sendGSPQueryUser(String username) {
		Main.println("[GInterface] Querying by name: " + username, Log.SERVER);

		byte[] username_bytes = username.getBytes();

		ByteBuffer block = ByteBuffer.allocate(username_bytes.length + 6);
		block.order(ByteOrder.LITTLE_ENDIAN);
		block.put((byte) 0x57); //query packet identifier
		block.putInt(username_bytes.length); //string size, excluding null byte
		block.put(username_bytes);
		block.put((byte) 0); //null byte; since getBytes does not add it automatically
		byte[] array = block.array();

		ByteBuffer lbuf = null;
		try {
			byte[] encrypted = crypt.aesEncrypt(array);

			lbuf = ByteBuffer.allocate(4 + encrypted.length);
			lbuf.putShort((short) encrypted.length);
			lbuf.put((byte) 0);
			lbuf.put((byte) 1);

			lbuf.put(encrypted);
		} catch(Exception e) {
			Main.println("[GInterface] Encryption error: " + e.getLocalizedMessage(), Log.ERROR);
			return false;
		}

		try {
			out.write(lbuf.array());
			return true;
		} catch(IOException ioe) {
			Main.println("[GInterface] I/O error: " + ioe.getLocalizedMessage(), Log.ERROR);
			disconnected(GARENA_MAIN);
			return false;
		}
	}

	//username is requester username, sent with friend request
	//message is the one sent with friend request that requested user will read
	public boolean sendGSPRequestFriend(int id, String username, String message) {
		Main.println("[GInterface] Friend requesting: " + id, Log.SERVER);

		byte[] username_bytes = username.getBytes();
		byte[] message_bytes = message.getBytes();

		ByteBuffer block = ByteBuffer.allocate(username_bytes.length + message_bytes.length + 19);
		block.order(ByteOrder.LITTLE_ENDIAN);
		block.put((byte) 0x48); //request packet identifier
		block.putInt(user_id); //requester user_id
		block.putInt(id); //requested user_id

		block.putInt(username_bytes.length);
		block.put(username_bytes);
		block.put((byte) 0);

		block.putInt(message_bytes.length);
		block.put(message_bytes);
		block.put((byte) 0); //null byte; since getBytes does not add it automatically
		byte[] array = block.array();

		ByteBuffer lbuf = null;
		try {
			byte[] encrypted = crypt.aesEncrypt(array);

			lbuf = ByteBuffer.allocate(4 + encrypted.length);
			lbuf.order(ByteOrder.LITTLE_ENDIAN);

			lbuf.putShort((short) encrypted.length);
			lbuf.put((byte) 0);
			lbuf.put((byte) 1);

			lbuf.put(encrypted);
		} catch(Exception e) {
			Main.println("[GInterface] Encryption error: " + e.getLocalizedMessage(), Log.ERROR);
			return false;
		}

		try {
			out.write(lbuf.array());
			return true;
		} catch(IOException ioe) {
			Main.println("[GInterface] I/O error: " + ioe.getLocalizedMessage(), Log.ERROR);
			disconnected(GARENA_MAIN);
			return false;
		}
	}

	//sends message so main server knows we joined a room
	public boolean sendGSPJoinedRoom(int userId, int roomId) {
		ByteBuffer block = ByteBuffer.allocate(9);
		block.order(ByteOrder.LITTLE_ENDIAN);

		block.put((byte) 0x52); //joined room identifier
		block.putInt(userId); //user ID
		block.putInt(roomId); //joined room ID
		byte[] array = block.array();

		ByteBuffer lbuf = null;
		try {
			byte[] encrypted = crypt.aesEncrypt(array);

			lbuf = ByteBuffer.allocate(4 + encrypted.length);
			lbuf.order(ByteOrder.LITTLE_ENDIAN);
			lbuf.putShort((short) encrypted.length);
			lbuf.put((byte) 0);
			lbuf.put((byte) 1);

			lbuf.put(encrypted);
		} catch(Exception e) {
			Main.println("[GInterface] Encryption error: " + e.getLocalizedMessage(), Log.ERROR);
			return false;
		}

		try {
			out.write(lbuf.array());
			return true;
		} catch(IOException ioe) {
			Main.println("[GInterface] I/O error: " + ioe.getLocalizedMessage(), Log.ERROR);
			disconnected(GARENA_MAIN);
			return false;
		}
	}

	//only to be sent when connected to a room server
	//should be sent every 15 minutes if connected to a room
	public boolean sendGSPXP(int userId, int xpGain, int gameType) {
		ByteBuffer block = ByteBuffer.allocate(13);
		block.order(ByteOrder.LITTLE_ENDIAN);

		block.put((byte) 0x67); //GSP XP
		block.putInt(userId); //user ID
		block.putInt(xpGain); //xpGain = 50 basic, 100 gold, 200 premium, 300 platinum
		block.putInt(gameType); //game type
		byte[] array = block.array();

		ByteBuffer lbuf = null;
		try {
			byte[] encrypted = crypt.aesEncrypt(array);

			lbuf = ByteBuffer.allocate(4 + encrypted.length);
			lbuf.order(ByteOrder.LITTLE_ENDIAN);
			lbuf.putShort((short) encrypted.length);
			lbuf.put((byte) 0);
			lbuf.put((byte) 1);

			lbuf.put(encrypted);
		} catch(Exception e) {
			Main.println("[GInterface] Encryption error: " + e.getLocalizedMessage(), Log.ERROR);
			return false;
		}

		try {
			out.write(lbuf.array());
			return true;
		} catch(IOException ioe) {
			Main.println("[GInterface] I/O error: " + ioe.getLocalizedMessage(), Log.ERROR);
			disconnected(GARENA_MAIN);
			return false;
		}
	}

	public boolean sendGCRPMeJoin() {
		Main.println("[GInterface] Sending GCRP me join...", Log.SERVER);
		String username = GRCConfig.configuration.getString("grc_username");
		String password = GRCConfig.configuration.getString("grc_password");
		String roomPassword = GRCConfig.configuration.getString("grc_roompassword", "");
		
		ByteBuffer buf = ByteBuffer.allocate(4096);
		buf.order(ByteOrder.LITTLE_ENDIAN);

		//fix myinfo

		//now we need to put external IP
		if(iExternal != null) {
			myinfo[28] = iExternal[0];
			myinfo[29] = iExternal[1];
			myinfo[30] = iExternal[2];
			myinfo[31] = iExternal[3];
		}

		//internal address
		iInternal = crypt.internalAddress();
		myinfo[32] = iInternal[0];
		myinfo[33] = iInternal[1];
		myinfo[34] = iInternal[2];
		myinfo[35] = iInternal[3];

		//put external port, in big endian
		if(pExternal != 0) {
			byte[] port = crypt.shortToByteArray((short) pExternal);
			myinfo[40] = port[0];
			myinfo[41] = port[1];
		}

		//put internal port, in big endian
		byte[] port = crypt.shortToByteArray((short) room_socket.getPort());
		myinfo[42] = (byte) port[0];
		myinfo[43] = (byte) port[1];

		//add myinfo
		byte[] deflated = crypt.deflate(myinfo);
		Main.println("[GInterface] deflated myinfo block from " + myinfo.length + " bytes to " + deflated.length + " bytes", Log.SERVER);

		buf.putInt(deflated.length + 66); //message size
		buf.put((byte) 0x22); //JOIN message identifier
		buf.putInt(room_id);
		buf.putInt(1);
		buf.putInt(deflated.length + 4); //CRC and myinfo size

		//generate CRC32
		CRC32 crc = new CRC32();
		crc.update(myinfo);
		buf.putInt((int) crc.getValue());

		buf.put(deflated);

		//begin suffix
		
		//first 15 bytes are for room password
		byte[] roomPasswordBytes = null;
		try {
			roomPasswordBytes = roomPassword.getBytes("UTF-8");

			if(roomPasswordBytes.length > 15) {
				System.out.println("[GInterface] Warning: cutting room password to 15 bytes");
			}

			int len = Math.min(roomPasswordBytes.length, 15);

			buf.put(roomPasswordBytes, 0, len);

			if(len < 15) {
				//fill in zero bytes; room password section must be exactly 15 bytes
				byte[] remainder = new byte[15 - len];
				buf.put(remainder); //values in byte arrays default to zero
			}
		} catch(UnsupportedEncodingException e) {
			Main.println("[GInterface] Error: " + e.getLocalizedMessage() + "; ignoring room password", Log.ERROR);

			buf.putInt(0);
			buf.putInt(0);
			buf.putInt(0);
			buf.putShort((short) 0);
			buf.put((byte) 0);
		}

		//now we need to hash password and send
		String password_hash = crypt.md5(password);
		try {
			byte[] password_bytes = password_hash.getBytes("UTF-8");
			if(password_bytes.length > 33) {
				Main.println("[GInterface] Fatal error: password hash is much too long!", Log.ERROR);
				System.exit(-1);
			}

			byte[] password_buf = new byte[32];
			System.arraycopy(password_bytes, 0, password_buf, 0, Math.min(password_buf.length, password_bytes.length));

			buf.put(password_buf);
		} catch(UnsupportedEncodingException e) {
			Main.println("[GInterface] Fatal error: " + e.getLocalizedMessage(), Log.ERROR);
			System.exit(-1);
		}

		buf.putShort((short) 0);

		try {
			rout.write(buf.array(), buf.arrayOffset(), buf.position());
			return true;
		} catch(IOException ioe) {
			Main.println("[GInterface] I/O error: " + ioe.getLocalizedMessage(), Log.ERROR);
			disconnected(GARENA_ROOM);
			return false;
		}
	}

	public void readGCRPLoop() {
		ByteBuffer lbuf = ByteBuffer.allocate(65536);

		while(true) {
			lbuf.clear();
			lbuf.order(ByteOrder.LITTLE_ENDIAN);

			try {
				byte[] header = new byte[4];
				rin.readFully(header);

				int size = crypt.byteArrayToIntLittleLength(header, 0, 3);
				int type = rin.read();

				if(type == 48) {
					processAnnounce(size - 1, lbuf);
				} else if(type == 44) {
					processMemberList(size - 1, lbuf);
				} else if(type == 34) {
					//JOIN message
					MemberInfo added = readMemberInfo(size - 1, lbuf);
					Main.println("[GarenaInterface] New member joined: " + added.username + " with id " + added.userID, Log.ROOM);

					for(GarenaListener listener : listeners) {
						listener.playerJoined(added);
					}
				} else if(type == 35) {
					processMemberLeave(size - 1, lbuf);
				} else if(type == 37) {
					processMemberTalk(size - 1, lbuf);
				} else if(type == 58) {
					processMemberStart(size - 1, lbuf);
				} else if(type == 57) {
					processMemberStop(size - 1, lbuf);
				} else if(type == 127) {
					processWhisper(size - 1, lbuf);
				} else if(type == 54) {
					int error_id = -1;
					if(size >= 2) error_id = rin.read();

					//read remaining
					if(size > 2) {
						byte[] tmp = new byte[size - 2];
						rin.read(tmp);
					}

					String error_string;

					switch(error_id) {
						case 0x07:
							error_string = "room full";
							break;
						case 0x0A:
							error_string = "insufficient level";
							break;
						default:
							error_string = "unknown";
					}

					Main.println("[GInterface] Error received: id: " + error_id + "; means: " + error_string, Log.ERROR);
					disconnected(GARENA_ROOM);
					return;
				} else {
					if(type == -1) {
						disconnected(GARENA_ROOM);
						return;
					}

					Main.println("[GInterface] GCRPLoop: unknown type received: " + type + "; size is: " + size, Log.SERVER);

					//make sure we read it all anyway
					if(size < 1000000000 && size >= 2) {
						byte[] tmp = new byte[size - 1]; //we already read type
						rin.read(tmp);

						//notify plugins
						plugins.onPacket(GARENA_ROOM, type, tmp, 0, size - 1);
					}
				}
			} catch(IOException ioe) {
				//give error information to Main
				Main.println("[GInterface] GCRP loop IO error: " + ioe.getLocalizedMessage(), Log.ERROR);
				Main.stackTrace(ioe);
				
				disconnected(GARENA_ROOM);
				return;
			} catch(Exception e) {
				//give error information to Main
				Main.println("[GInterface] GCRP loop error: " + e.getLocalizedMessage(), Log.ERROR);
				Main.stackTrace(e);
			}
		}
	}

	public void processAnnounce(int packet_size, ByteBuffer lbuf) throws IOException {
		lbuf.clear();
		lbuf.put((byte) rin.read());
		lbuf.put((byte) rin.read());
		lbuf.put((byte) rin.read());
		lbuf.put((byte) rin.read());
		int serverRoomId = lbuf.getInt(0);

		byte[] str_bytes = new byte[packet_size - 4];
		rin.readFully(str_bytes);
		String welcome_str = crypt.strFromBytes16(str_bytes);
		Main.println("[GInterface] Server says: " + welcome_str, Log.ROOM);
	}

	public void processMemberList(int packet_size, ByteBuffer lbuf) throws IOException {
		hasRoomList = false;
		lbuf.clear();
		//read 8 bytes
		byte[] tmp = new byte[8];
		rin.readFully(tmp);
		lbuf.put(tmp);

		int cRoom_id = lbuf.getInt(0);

		if(cRoom_id != room_id) {
			Main.println("[GInterface] Server says room ID is " + cRoom_id + "; tried to join room " + room_id, Log.ROOM);
		}

		int num_members = lbuf.getInt(4);
		Main.println("[GInterface] There are " + num_members + " members in this room", Log.ROOM);
		
		members.clear(); //in case of reconnecting when dc

		for(int i = 0; i < num_members; i++) {
			readMemberInfo(64, lbuf);
		}

		int read_size = 8 + 64 * num_members;
		if(packet_size > read_size) {
			tmp = new byte[packet_size - read_size];
			rin.read(tmp);
		}

		displayMemberInfo();
		hasRoomList = true;
	}

	public MemberInfo readMemberInfo(int size, ByteBuffer lbuf) throws IOException {
		lbuf.clear();
		lbuf.order(ByteOrder.LITTLE_ENDIAN);
		MemberInfo member = new MemberInfo();

		//read 64 bytes
		byte[] tmp = new byte[64];
		rin.readFully(tmp);
		lbuf.put(tmp);

		member.userID = lbuf.getInt(0);

		//username string
		lbuf.position(4);
		byte[] username_bytes = new byte[16];
		lbuf.get(username_bytes);
		member.username = crypt.strFromBytes(username_bytes);

		//country string
		lbuf.position(20);
		byte[] country_bytes = new byte[2];
		lbuf.get(country_bytes);
		member.country = crypt.strFromBytes(country_bytes);
		
		//membership int value
		lbuf.position(23);
		member.membership = lbuf.get();

		member.experience = crypt.unsignedByte(lbuf.get(25));
		member.playing = (lbuf.get(27)) == 1;

		//external IP
		lbuf.position(28);
		byte[] external_bytes = new byte[4];
		lbuf.get(external_bytes); //IP is stored in big endian anyway

		try {
			member.externalIP = InetAddress.getByAddress(external_bytes);
		} catch(UnknownHostException e) {
			Main.println("[GInterface] Error: " + e.getLocalizedMessage(), Log.ERROR);
			return member;
		}

		//internal IP
		lbuf.position(28);
		byte[] internal_bytes = new byte[4];
		lbuf.get(internal_bytes); //IP is stored in big endian anyway

		try {
			member.internalIP = InetAddress.getByAddress(internal_bytes);
		} catch(UnknownHostException e) {
			Main.println("[GInterface] Error: " + e.getLocalizedMessage(), Log.ERROR);
			return member;
		}

		//ports in big endian
		lbuf.order(ByteOrder.BIG_ENDIAN);
		member.externalPort = crypt.unsignedShort(lbuf.getShort(40));
		member.internalPort = crypt.unsignedShort(lbuf.getShort(42));
		member.virtualSuffix = crypt.unsignedByte(lbuf.get(44));
		lbuf.order(ByteOrder.LITTLE_ENDIAN);
		members.add(member);

		//read remainder
		if(size > 64) {
			tmp = new byte[size - 64];
			rin.read(tmp);
		}

		return member;
	}

	public void displayMemberInfo() throws IOException {
		FileWriter out = new FileWriter("room_users.txt");

		for(int i = 0; i < members.size(); i++) {
			MemberInfo member = members.get(i);
			
			out.write("user id: " + member.userID);
			out.write("\tusername: " + member.username);
			out.write("\tcountry: " + member.country);
			out.write("\texperience: " + member.experience);
			out.write("\tplaying?: " + member.playing);
			out.write("\texternal IP: " + member.externalIP);
			out.write("\tinternal IP: " + member.internalIP);
			out.write("\texternal port: " + member.externalPort);
			out.write("\tinternal port: " + member.internalPort);
			out.write("\tcorrect IP: " + member.correctIP);
			out.write("\tcorrect port: " + member.correctPort);
			out.write("\tvirtual suffix: " + member.virtualSuffix);
			out.write("\r\n");
		}

		out.close();
	}

	public void displayRoomInfo() throws IOException {
		FileWriter out = new FileWriter("rooms.txt");

		for(RoomInfo room : rooms) {
			out.write("room id: " + room.roomId);
			out.write("\t# users: " + room.numUsers);
			out.write("\n");
		}

		out.close();
	}

	public void processMemberLeave(int packet_size, ByteBuffer lbuf) throws IOException {
		lbuf.clear();
		lbuf.order(ByteOrder.LITTLE_ENDIAN);
		byte[] tmp = new byte[packet_size]; //should be 4
		rin.read(tmp);
		lbuf.put(tmp);

		int user_id = lbuf.getInt(0);
		MemberInfo member = null;

		for(int i = 0; i < members.size(); i++) {
			if(members.get(i).userID == user_id) {
				member = members.remove(i);
			}
		}

		if(member != null) {
			Main.println("[GarenaInterface] " + member.username + " with ID " + member.userID + " has left the room", Log.ROOM);
		} else {
			Main.println("[GarenaInterface] Unlisted member " + user_id + " has left the room", Log.ROOM);
		}

		for(GarenaListener listener : listeners) {
			listener.playerLeft(member);
		}
	}

	public void processMemberStart(int packet_size, ByteBuffer lbuf) throws IOException {
		lbuf.clear();
		lbuf.order(ByteOrder.LITTLE_ENDIAN);
		byte[] tmp = new byte[packet_size]; //should be 4
		rin.read(tmp);
		lbuf.put(tmp);

		int user_id = lbuf.getInt(0);
		MemberInfo member = memberFromID(user_id);

		if(member != null) {
			member.playing = true;
			Main.println("[GarenaInterface] " + member.username + " with ID " + member.userID + " has started playing", Log.ROOM);
		} else {
			Main.println("[GarenaInterface] Unlisted member " + user_id + " has started playing", Log.ROOM);
		}

		for(GarenaListener listener : listeners) {
			listener.playerStarted(member);
		}
	}

	public void processMemberStop(int packet_size, ByteBuffer lbuf) throws IOException {
		lbuf.clear();
		lbuf.order(ByteOrder.LITTLE_ENDIAN);
		byte[] tmp = new byte[packet_size];
		rin.read(tmp);
		lbuf.put(tmp);

		int user_id = lbuf.getInt(0);
		MemberInfo member = memberFromID(user_id);

		if(member != null) {
			member.playing = false;
			Main.println("[GarenaInterface] " + member.username + " with ID " + member.userID + " has stopped playing", Log.ROOM);
		} else {
			Main.println("[GarenaInterface] Unlisted member " + user_id + " has stopped playing", Log.ROOM);
		}

		for(GarenaListener listener : listeners) {
			listener.playerStopped(member);
		}
	}

	public void processWhisper(int packet_size, ByteBuffer lbuf) throws IOException {
		lbuf.clear();
		lbuf.order(ByteOrder.LITTLE_ENDIAN);
		byte[] tmp = new byte[packet_size];
		rin.read(tmp);
		lbuf.put(tmp);

		int user_id = lbuf.getInt(0);
		MemberInfo member = memberFromID(user_id);

		lbuf.position(8);
		byte[] chat_bytes = new byte[packet_size - 8];
		lbuf.get(chat_bytes);
		String chat_string = crypt.strFromBytes16(chat_bytes);

		if(member != null) {
			Main.println("[GarenaInterface] " + member.username + " with ID " + member.userID + " whispers: " + chat_string, Log.ROOM);
		} else {
			Main.println("[GarenaInterface] Unlisted member " + user_id + " whispers: " + chat_string, Log.ROOM);
		}

		for(GarenaListener listener : listeners) {
			listener.chatReceived(member, chat_string, true);
		}
	}

	public void processMemberTalk(int packet_size, ByteBuffer lbuf) throws IOException {
		lbuf.clear();
		lbuf.order(ByteOrder.LITTLE_ENDIAN);
		byte[] tmp = new byte[packet_size]; //should be 4
		rin.read(tmp);
		lbuf.put(tmp);

		int cRoom_id = lbuf.getInt(0);

		if(cRoom_id != room_id) {
			Main.println("[GInterface] Server says room ID is " + cRoom_id + "; tried to join room " + room_id, Log.ROOM);
		}

		int user_id = lbuf.getInt(4);
		MemberInfo member = memberFromID(user_id);

		lbuf.position(12);
		byte[] chat_bytes = new byte[packet_size - 12];
		lbuf.get(chat_bytes);
		String chat_string = crypt.strFromBytes16(chat_bytes);

		if(member != null) {
			Main.println("[GarenaInterface] " + member.username + " with ID " + member.userID + ": " + chat_string, Log.ROOM);
		} else {
			Main.println("[GarenaInterface] Unlisted member " + user_id + ": " + chat_string, Log.ROOM);
		}

		for(GarenaListener listener : listeners) {
			listener.chatReceived(member, chat_string, false);
		}
	}

	public boolean sendGCRPChat(String text) {
		Main.println("[GarenaInterface] Sending message: " + text, Log.ROOM);

		byte[] chat_bytes = null;

		try {
			chat_bytes = text.getBytes("UnicodeLittleUnmarked");
		} catch(UnsupportedEncodingException e) {
			Main.println("[GInterface] Error: " + e.getLocalizedMessage(), Log.ERROR);
			return false;
		}

		ByteBuffer lbuf = ByteBuffer.allocate(19 + chat_bytes.length);
		lbuf.order(ByteOrder.LITTLE_ENDIAN);
		lbuf.putInt(15 + chat_bytes.length); //message size
		lbuf.put((byte) 0x25); //chat type
		lbuf.putInt(room_id);
		lbuf.putInt(user_id);
		lbuf.putInt(chat_bytes.length);
		lbuf.put(chat_bytes);
		lbuf.putShort((short) 0); //null byte

		try {
			rout.write(lbuf.array());
			return true;
		} catch(IOException ioe) {
			Main.println("[GInterface] Error in chat: " + ioe.getLocalizedMessage(), Log.ERROR);
			disconnected(GARENA_ROOM);
			return false;
		}
	}

	public boolean sendGCRPAnnounce(String text) {
		Main.println("[GarenaInterface] Sending announce: " + text, Log.ROOM);

		byte[] chat_bytes = null;

		try {
			chat_bytes = text.getBytes("UnicodeLittleUnmarked");
		} catch(UnsupportedEncodingException e) {
			Main.println("[GInterface] Error in announce: " + e.getLocalizedMessage(), Log.ERROR);
			return false;
		}

		ByteBuffer lbuf = ByteBuffer.allocate(11 + chat_bytes.length);
		lbuf.order(ByteOrder.LITTLE_ENDIAN);
		lbuf.putInt(7  + chat_bytes.length); //message size
		lbuf.put((byte) 0x30); //annouce (welcome message) type
		lbuf.putInt(room_id);
		lbuf.put(chat_bytes);
		lbuf.putShort((short) 0); //null byte

		try {
			rout.write(lbuf.array());
			return true;
		} catch(IOException ioe) {
			Main.println("[GInterface] Error: " + ioe.getLocalizedMessage(), Log.ERROR);
			disconnected(GARENA_ROOM);
			return false;
		}
	}

	public boolean ban(String username, int hours) {
		int seconds = hours * 3600;
		Main.println("[GarenaInterface] Banning " + username + " for " + seconds + " seconds", Log.ROOM);

		byte[] username_bytes = null;

		try {
			username_bytes = username.getBytes("UTF-8");
		} catch(UnsupportedEncodingException e) {
			Main.println("[GInterface] Error in ban: " + e.getLocalizedMessage(), Log.ERROR);
			return false;
		}

		ByteBuffer lbuf = ByteBuffer.allocate(14 + username_bytes.length);
		lbuf.order(ByteOrder.LITTLE_ENDIAN);
		lbuf.putInt(10 + username_bytes.length); //message size
		lbuf.put((byte) 0x78); //ban message identifier
		lbuf.putInt(room_id);
		lbuf.put(username_bytes);
		lbuf.put((byte) 0); //null byte
		lbuf.putInt(seconds);

		try {
			rout.write(lbuf.array());
			return true;
		} catch(IOException ioe) {
			Main.println("[GInterface] Error in ban: " + ioe.getLocalizedMessage(), Log.ERROR);
			disconnected(GARENA_ROOM);
			return false;
		}
	}

	public boolean unban(String username) {
		return ban(username, 0);
	}

	public boolean kick(MemberInfo member) {
		return kick(member, "");
	}

	public boolean kick(MemberInfo member, String reason) {
		Main.println("[GarenaInterface] Kicking " + member.username + " with user ID " + member.userID + "; reason: " + reason, Log.ROOM);

		byte[] reason_bytes = null;

		try {
			reason_bytes = reason.getBytes("UTF-8");
		} catch(UnsupportedEncodingException e) {
			Main.println("[GInterface] Error in kick: " + e.getLocalizedMessage(), Log.ERROR);
			return false;
		}

		ByteBuffer lbuf = ByteBuffer.allocate(18 + reason_bytes.length);
		lbuf.order(ByteOrder.LITTLE_ENDIAN);
		lbuf.putInt(14 + reason_bytes.length); //message size
		lbuf.put((byte) 0x28); //kick message identifier
		lbuf.putInt(user_id);
		lbuf.putInt(member.userID);
		//reason
		lbuf.putInt(reason_bytes.length); //reason size, excluding null terminator
		lbuf.put(reason_bytes);
		lbuf.put((byte) 0); //null terminator for reason

		try {
			rout.write(lbuf.array());
			return true;
		} catch(IOException ioe) {
			Main.println("[GInterface] Error: " + ioe.getLocalizedMessage(), Log.ERROR);
			disconnected(GARENA_ROOM);
			return false;
		}
	}
	
	public void startPlaying() {
		Main.println("[GInterface] Sending GCRP START...", Log.SERVER);

		ByteBuffer lbuf = ByteBuffer.allocate(9);
		lbuf.order(ByteOrder.LITTLE_ENDIAN);
		lbuf.putInt(5); //message size
		lbuf.put((byte) 0x3a); //GCRP START
		lbuf.putInt(user_id);

		try {
			rout.write(lbuf.array());
		} catch(IOException ioe) {
			Main.println("[GInterface] Error: " + ioe.getLocalizedMessage(), Log.ERROR);
			disconnected(GARENA_ROOM);
		}
	}

	public void stopPlaying() {
		Main.println("[GInterface] Sending GCRP STOP...", Log.ROOM);

		ByteBuffer lbuf = ByteBuffer.allocate(9);
		lbuf.order(ByteOrder.LITTLE_ENDIAN);
		lbuf.putInt(5); //message size
		lbuf.put((byte) 0x39); //GCRP START
		lbuf.putInt(user_id);

		try {
			rout.write(lbuf.array());
		} catch(IOException ioe) {
			Main.println("[GInterface] Error: " + ioe.getLocalizedMessage(), Log.ERROR);
			disconnected(GARENA_ROOM);
		}
	}

	public boolean sendGCRPWhisper(int target_user, String text) {
		Main.println("[GarenaInterface] Sending whisper to " + target_user + ": " + text, Log.ROOM);

		byte[] chat_bytes = null;

		try {
			chat_bytes = text.getBytes("UnicodeLittleUnmarked");
		} catch(UnsupportedEncodingException e) {
			Main.println("[GInterface] Error: " + e.getLocalizedMessage(), Log.ERROR);
			return false;
		}

		ByteBuffer lbuf = ByteBuffer.allocate(15 + chat_bytes.length);
		lbuf.order(ByteOrder.LITTLE_ENDIAN);
		lbuf.putInt(11  + chat_bytes.length); //message size
		lbuf.put((byte) 0x7F); //whisper
		lbuf.putInt(user_id);
		lbuf.putInt(target_user);
		lbuf.put(chat_bytes);
		lbuf.putShort((short) 0); //null byte

		try {
			rout.write(lbuf.array());
			return true;
		} catch(IOException ioe) {
			Main.println("[GInterface] Error: " + ioe.getLocalizedMessage(), Log.ERROR);
			disconnected(GARENA_ROOM);
			return false;
		}
	}

	public void registerListener(GarenaListener listener) {
		listeners.add(listener);
	}

	public void deregisterListener(GarenaListener listener) {
		listeners.remove(listener);
	}

	public void readPeerLoop() {
		byte[] buf_array = new byte[65536];
		ByteBuffer lbuf = ByteBuffer.allocate(65536);

		while(true) {
			lbuf.clear();
			lbuf.order(ByteOrder.LITTLE_ENDIAN);

			try {
				DatagramPacket packet = new DatagramPacket(buf_array, buf_array.length);
				peer_socket.receive(packet);

				//notify plugins
				plugins.onPacket(GARENA_PEER, -1, buf_array, packet.getOffset(), packet.getLength());

				int length = packet.getLength();
				lbuf.put(buf_array, 0, length);

				if(buf_array[0] == 0x06) {
					iExternal = new byte[4];
					lbuf.position(8);
					lbuf.get(iExternal);

					lbuf.order(ByteOrder.BIG_ENDIAN);
					pExternal = lbuf.getShort(12);
					lbuf.order(ByteOrder.LITTLE_ENDIAN);

					String str_external = crypt.unsignedByte(iExternal[0]) +
							"." + crypt.unsignedByte(iExternal[1]) +
							"." + crypt.unsignedByte(iExternal[2]) +
							"." + crypt.unsignedByte(iExternal[3]);

					Main.println("[GInterface] PeerLoop: set address to " + str_external + " and port to " + pExternal, Log.SERVER);
				} else if(buf_array[0] == 0x3F) {
					int room_prefix = crypt.unsignedShort(lbuf.getShort(1));
					int num_rooms = crypt.unsignedByte(lbuf.get(3));

					Main.println("[GInterface] Receiving " + num_rooms + " rooms with prefix " + room_prefix, Log.SERVER);

					for(int i = 0; i < num_rooms; i++) {
						RoomInfo room = new RoomInfo();
						int suffix = crypt.unsignedByte(lbuf.get(4 + i * 2));
						room.roomId = room_prefix * 256 + suffix;

						room.numUsers = crypt.unsignedByte(lbuf.get(5 + i * 2));
						rooms.add(room);
					}
				} else if(buf_array[0] == 0x0F) {
					int id = crypt.byteArrayToIntLittle(buf_array, 4);
					MemberInfo member = memberFromID(id);

					if(member != null) {
						member.correctIP = packet.getAddress();
						member.correctPort = packet.getPort();
					} else {
						//Main.println("[GInterface] Received HELLO reply from invalid member: " + id, Log.ROOM);
					}
				} else if(buf_array[0] == 0x02) {
					int id = crypt.byteArrayToIntLittle(buf_array, 4);
					MemberInfo member = memberFromID(id);

					if(member != null) {
						member.correctIP = packet.getAddress();
						member.correctPort = packet.getPort();

						sendPeerHelloReply(member.userID, member.correctIP, member.correctPort, lbuf);
					} else {
						//Main.println("[GInterface] Received HELLO from invalid member: " + id, Log.ROOM);
					}
				} else if(buf_array[0] == 0x0D) {
					int conn_id = crypt.byteArrayToIntLittle(buf_array, 4);

					if(conn_id == 0) {
						continue; //happens sometimes
					}
				} else if(buf_array[0] == 0x01) {
					int senderId = lbuf.getInt(4);
					MemberInfo sender = memberFromID(senderId);

					lbuf.order(ByteOrder.BIG_ENDIAN);
					int sourcePort = GarenaEncrypt.unsignedShort(lbuf.getShort(8));
					int destPort = GarenaEncrypt.unsignedShort(lbuf.getShort(12));
					lbuf.order(ByteOrder.LITTLE_ENDIAN);

					lbuf.position(16);

					// Main.println("[GInterface] Received UDP broadcast from " + sender.username + " from port " + sourcePort + " to port " + destPort, Log.ROOM);
					
				} else {
					Main.println("[GInterface] PeerLoop: unknown type received: " + buf_array[0] + "; size is: " + length, Log.ERROR);
				}
			} catch(IOException ioe) {
				Main.println("[GInterface] Error: " + ioe.getLocalizedMessage(), Log.ERROR);
				return;
			}
		}
	}

	public void sendPeerLookup() {
		//lookup external IP, port
		byte[] tmp = new byte[8];
		tmp[0] = 0x05;

		//we don't use peer_port because even if we're hosting Garena on 1515, server is still 1513
		DatagramPacket packet = new DatagramPacket(tmp, tmp.length, main_address, 1513);

		try {
			peer_socket.send(packet);
		} catch(IOException ioe) {
			ioe.printStackTrace();
		}
	}

	public void sendPeerRoomUsage() {
		//lookup external IP, port
		ByteBuffer lbuf = ByteBuffer.allocate(5);
		lbuf.order(ByteOrder.LITTLE_ENDIAN);
		lbuf.put(0, (byte) 0x02); //room usage lookup identifier
		lbuf.putInt(1, user_id); //our user ID

		//we don't use peer_port because even if we're hosting Garena on 1515, server is still 1513
		DatagramPacket packet = new DatagramPacket(lbuf.array(), lbuf.array().length, main_address, 1513);

		try {
			peer_socket.send(packet);
		} catch(IOException ioe) {
			ioe.printStackTrace();
		}
	}

	public void sendPeerHello() {
		for(MemberInfo target : members) {
			if(target.userID == user_id) {
				continue;
			}

			//LAN FIX: correct IP address of target user
			if(GRCConfig.configuration.getBoolean("grc_lanfix", false)) {
				if(target.username.equalsIgnoreCase(GRCConfig.configuration.getString("grc_lanfix_username", "garena"))) {
					try {
						target.correctIP = InetAddress.getByName(GRCConfig.configuration.getString("grc_lanfix_ip", "192.168.1.2"));
						target.correctPort = GRCConfig.configuration.getInt("grc_lanfix_port", 1513);
					} catch(IOException ioe) {
						ioe.printStackTrace();
					}
				}
			}
			

			if(target.correctIP == null) {
				//send on both external and internal
				sendPeerHello(target.userID, target.externalIP, target.externalPort);
				sendPeerHello(target.userID, target.internalIP, target.internalPort);
			} else {
				sendPeerHello(target.userID, target.correctIP, target.correctPort);
			}
		}
	}

	public void sendPeerHello(int target_id, InetAddress address, int port) {
		ByteBuffer lbuf = ByteBuffer.allocate(16);
		lbuf.order(ByteOrder.LITTLE_ENDIAN);
		lbuf.put(0, (byte) 0x02);
		lbuf.putInt(4, user_id);

		DatagramPacket packet = new DatagramPacket(lbuf.array(), lbuf.array().length, address, port);

		try {
			peer_socket.send(packet);
		} catch(IOException ioe) {
			//ioe.printStackTrace(); //this happens a lot; ignore!
		}
	}

	public void sendPeerHelloReply(int target_id, InetAddress address, int port, ByteBuffer lbuf) {
		lbuf.clear();
		lbuf.order(ByteOrder.LITTLE_ENDIAN);
		lbuf.put(0, (byte) 0x0F);
		lbuf.putInt(4, user_id);
		lbuf.putInt(12, target_id);
		lbuf.position(0);
		byte[] tmp = new byte[16];
		lbuf.get(tmp);

		DatagramPacket packet = new DatagramPacket(tmp, tmp.length, address, port);

		try {
			peer_socket.send(packet);
		} catch(IOException ioe) {
			//ioe.printStackTrace(); //this happens a lot; ignore!
		}
	}

	public MemberInfo memberFromID(int id) {
		for(int i = 0; i < members.size(); i++) {
			if(members.get(i).userID == id) {
				return members.get(i);
			}
		}

		return null;
	}

	public MemberInfo memberFromName(String name) {
		for(int i = 0; i < members.size(); i++) {
			if(members.get(i).username.equalsIgnoreCase(name)) {
				return members.get(i);
			}
		}

		return null;
	}
	
	public static String time() {
		Calendar cal = Calendar.getInstance();
		SimpleDateFormat sdf = new SimpleDateFormat(TIME_FORMAT);
		return sdf.format(cal.getTime());
	}

	public void disconnected(int x) {
		if(x == GARENA_MAIN && socket != null && socket.isConnected()) {
			try {
				socket.close();
			} catch(IOException ioe) {
				//ignore
			}
		} else if(x == GARENA_ROOM && room_socket != null && room_socket.isConnected()) {
			try {
				room_socket.close();
			} catch(IOException ioe) {
				//ignore
			}
		} else if(x == GARENA_PEER && peer_socket != null && peer_socket.isConnected()) {
			peer_socket.close();
		}

		for(GarenaListener listener : listeners) {
			listener.disconnected(x);
		}

		plugins.onDisconnect(x);
	}
}
