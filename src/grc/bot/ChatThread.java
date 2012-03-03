/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package grc.bot;

import grc.GRCConfig;
import grc.GarenaInterface;
import grc.Main;
import java.util.LinkedList;

/**
 *
 * @author wizardus & GG.Dragon
 */
public class ChatThread extends Thread {
	LinkedList<ChatMessage> chat_queue;
	GarenaInterface garena;
	public static final int MAIN_CHAT = -1;
	public static final int ANNOUNCEMENT = -2;
	public static final int SLEEP = -3;

	private int delay;

	public ChatThread(GarenaInterface garena) {
		this.garena = garena;
		chat_queue = new LinkedList<ChatMessage>();

		//configuration
		delay = GRCConfig.configuration.getInt("grc_bot_delay", 2000);
	}

	public void queueChat(String message, int target_user) {
		synchronized(chat_queue) { //LinkedList is not thread safe
			if(target_user == ANNOUNCEMENT) {
				if(message.length() < 499) {
				ChatMessage add = new ChatMessage(message, target_user);
				chat_queue.add(add);
				} else {
					for(int i = 0; i < message.length(); i+=499) {
						String split = message.substring(i, Math.min(i + 499, message.length() - 1));
						ChatMessage add = new ChatMessage(split, target_user);
						chat_queue.add(add);
					}
				}
			} else {
				if(message.length() < 150) {
					ChatMessage add = new ChatMessage(message, target_user);
					chat_queue.add(add);
				} else {
					while(message.length() > 150) {
						String split = message.substring(0, 150);
						int indexOfLastSpace = split.lastIndexOf(' ');
						split = split.substring(0, indexOfLastSpace);
						message = message.substring(split.length()+1);
						ChatMessage add = new ChatMessage(split, target_user);
						chat_queue.add(add);
					}
					ChatMessage lastPart = new ChatMessage(message, target_user);
					chat_queue.add(lastPart);
				}
			}

			chat_queue.notifyAll(); //in case run() is waiting for us
			Main.println("[QUEUED: " + target_user + "] " + message);
		}
	}

	//in case we're flooding or something
	public void clearQueue() {
		synchronized(chat_queue) {
			chat_queue.clear();
			chat_queue.notifyAll();
		}
	}

	public void run() {
		while(true) {
			//wait until we get a message
			synchronized(chat_queue) {
				while(chat_queue.isEmpty()) {
					try {
						chat_queue.wait(); //wait until queueChat is called
					} catch(InterruptedException e) {}
				}
			}

			ChatMessage message = chat_queue.poll();

			if(message == null) {
				continue;
			}

			if(message.target_user == MAIN_CHAT) {
				garena.sendGCRPChat(message.str);
			} else if(message.target_user == ANNOUNCEMENT) {
				garena.sendGCRPAnnounce(message.str);
				try {
					Thread.sleep(1500);
				} catch(InterruptedException e) {
					Main.println("[ChatThread] Sleep was interrupted!" + e.getLocalizedMessage());
				}
			} else if(message.target_user == SLEEP) { //stops the bot sending messages too quickly
				try {
				Thread.sleep(1500); //prevent flooding
				} catch(InterruptedException e) {
					Main.println("[ChatThread] Sleep was interrupted!" + e.getLocalizedMessage());
				}
			} else {
				garena.sendGCRPWhisper(message.target_user, message.str);
			}

			try {
				Thread.sleep(delay); //prevent flooding
			} catch(InterruptedException e) {}
		}
	}
	
	public void sleep(int time) {
		try {
			Thread.sleep(time);
		} catch(InterruptedException e) {}
	}
}

class ChatMessage {
	String str;
	int target_user; //-1 for not whisper

	public ChatMessage(String str, int target) {
		this.str = str;
		target_user = target;
	}
}