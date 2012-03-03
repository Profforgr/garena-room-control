/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package grc;

/**
 *
 * @author wizardus
 */
 
public class GarenaThread extends Thread {
	public static int GSP_LOOP = 0;
	public static int GCRP_LOOP = 1;
	public static int PEER_LOOP = 2;

	GarenaInterface garenaInterface;
	int type;

	boolean terminated;

	public GarenaThread(GarenaInterface garenaInterface, int type) {
		this.garenaInterface = garenaInterface;
		this.type = type;
		terminated = false;
	}

	public void run() {
		if(type == GSP_LOOP) garenaInterface.readGSPLoop();
		else if(type == GCRP_LOOP) garenaInterface.readGCRPLoop();
		else if(type == PEER_LOOP) garenaInterface.readPeerLoop();

		else return;

		terminated = true;
	}
}
