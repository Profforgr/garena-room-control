/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package grc;

/**
 *
 * @author wizardus
 */
 
public class GarenaReconnect implements GarenaListener {
	Main main;

	public GarenaReconnect(Main main) {
		this.main = main;
	}

	public void chatReceived(MemberInfo member, String chat, boolean whisper) {}
	public void playerLeft(MemberInfo member) {}
	public void playerJoined(MemberInfo member) {}
	public void playerStarted(MemberInfo member) {} //player started playing (started VPN)
	public void playerStopped(MemberInfo member) {} //player stopped playing
	public void disconnected(int x) {
		if(x == GarenaInterface.GARENA_MAIN) {
			Main.println("[GarenaReconnect] GARENA_MAIN disconnected; reconnecting shortly...", GRCLog.SERVER);
		} else if(x == GarenaInterface.GARENA_PEER) {
			Main.println("[GarenaReconnect] GARENA_PEER disconnected; reconnecting shortly...", GRCLog.SERVER);
		} else if(x == GarenaInterface.GARENA_ROOM) {
			Main.println("[GarenaReconnect] GARENA_ROOM disconnected; reconnecting shortly...", GRCLog.SERVER);
		} else {
			Main.println("[GarenaReconnect] Error: unknown type: " + x, GRCLog.ERROR);
			return;
		}

		GarenaReconnectThread rt = new GarenaReconnectThread(main, x);
		rt.start();
	}
}

class GarenaReconnectThread extends Thread {
	Main main;
	int x;

	public GarenaReconnectThread(Main main, int x) {
		this.main = main;
		this.x = x;
	}

	public void run() {
		try {
			Thread.sleep(10000);
		} catch(InterruptedException e) {

		}
		GarenaInterface garena = main.garena;

		//TODO:make this work...
		if(garena.socket.isClosed()) {
			main.initGarena(true);
		}

		if(garena.room_socket.isClosed()) {
			main.initRoom(true);
		}

		if(garena.peer_socket.isClosed()) {
			main.initPeer();
		}
	}
}
