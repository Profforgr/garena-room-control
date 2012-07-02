/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package grc;

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
 * @author GG.Dragon
 */
 
public class GRCLog {

	public static final String DATE_FORMAT = "dd.MM.yyyy";
	public static final String TIME_FORMAT = "h:mm a";
	private long lastLog; //when the log file(s) were created
	private int newLogInterval; //ms interval between creating new log file(s)
	
	//types of log messages
	public static final int SERVER = 1;
	public static final int ROOM = 2;
	public static final int COMMAND = 3;
	public static final int DATABASE = 4;
	public static final int ERROR = 5;
	//settings
	public static boolean log;
	public static boolean log_single;
	public static boolean log_server;
	public static boolean log_room;
	public static boolean log_command;
	public static boolean log_database;
	public static boolean log_error;
	//writers
	private static PrintWriter log_single_out;
	private static PrintWriter log_server_out;
	private static PrintWriter log_room_out;
	private static PrintWriter log_command_out;
	private static PrintWriter log_database_out;
	private static PrintWriter log_error_out;
	
	public GRCLog() {
		//load log settings
		log_single = GRCConfig.configuration.getBoolean("grc_log_single", false);
		log_server = GRCConfig.configuration.getBoolean("grc_log_server", false);
		log_room = GRCConfig.configuration.getBoolean("grc_log_room", false);
		log_command = GRCConfig.configuration.getBoolean("grc_log_command", false);
		log_database = GRCConfig.configuration.getBoolean("grc_log_database", false);
		log_error = GRCConfig.configuration.getBoolean("grc_log_error", false);
		newLogInterval = GRCConfig.configuration.getInt("gcb_log_new_file", 86400) * 1000; //convert milliseconds to seconds
	}
	
	public void init() throws IOException {
		//create directories and initialize printwriters
		String currentDate = "";
		if(newLogInterval != 0) {
			currentDate = date() + " ";
		}
		if(log_single) {
			log_single_out = new PrintWriter(new FileWriter(currentDate + "grc.log", true), true);
		}
		if(log_server) {
			//keep seperate logs in seperate folders
			File log_server_dir = new File("log/log_server/");
			//if folder doesn't exist, create
			if(!log_server_dir.exists()) {
				log_server_dir.mkdir();
			}
			//set target to folder and set file name
			File log_server_target = new File(log_server_dir, currentDate + "grc_server.log");
			//initialize printwriter
			log_server_out = new PrintWriter(new FileWriter(log_server_target, true), true);
		}
		if(log_room) {
			//see comments for log_server, same code but with different file names
			File log_room_dir = new File("log/log_room/");
			if(!log_room_dir.exists()) {
				log_room_dir.mkdir();
			}
			File log_room_target = new File(log_room_dir, currentDate + "grc_room.log");
			log_room_out = new PrintWriter(new FileWriter(log_room_target, true), true);
		}
		if(log_command) {
			//see comments for log_server, same code but with different file names
			File log_cmd_dir = new File("log/log_cmd/");
			if(!log_cmd_dir.exists()) {
				log_cmd_dir.mkdir();
			}
			File log_cmd_target = new File(log_cmd_dir, currentDate + "grc_cmd.log");
			log_command_out = new PrintWriter(new FileWriter(log_cmd_target, true), true);
		}
		if(log_database) {
			//see comments for log_server, same code but with different file names
			File log_db_dir = new File("log/log_database/");
			if(!log_db_dir.exists()) {
				log_db_dir.mkdir();
			}
			File log_db_target = new File(log_db_dir, currentDate + "grc_db.log");
			log_database_out = new PrintWriter(new FileWriter(log_db_target, true), true);
		}
		if(log_error) {
			//see comments for log_server, same code but with different file names
			File log_error_dir = new File("log/log_error/");
			if(!log_error_dir.exists()) {
				log_error_dir.mkdir();
			}
			File log_error_target = new File(log_error_dir, currentDate + "grc_error.log");
			log_error_out = new PrintWriter(new FileWriter(log_error_target, true), true);
		}
		lastLog = System.currentTimeMillis();
	}
	
	public void newLogLoop() {
		if(newLogInterval != 0) {
			while(true) {
				if(System.currentTimeMillis() - lastLog > newLogInterval) {
					Main.println("[Main] Closing old log file and creating new log file", ROOM);
					String currentDate = date() + " ";
					if(log_single) {
						log_single_out.close();
						try {
							log_single_out = new PrintWriter(new FileWriter(currentDate + "grc.log", true), true);
						} catch(IOException ioe) {
							//give error information to Main
							Main.println("[GRCLog] Init single log failed: " + ioe.getLocalizedMessage(), GRCLog.ERROR);
							Main.stackTrace(ioe);
						}
					}
					if(log_server) {
						log_server_out.close();
						try {
							//keep seperate logs in seperate folders
							File log_server_dir = new File("log/log_server/");
							//if folder doesn't exist, create
							if(!log_server_dir.exists()) {
								log_server_dir.mkdir();
							}
							//set target to folder and set file name
							File log_server_target = new File(log_server_dir, currentDate + "grc_server.log");
							//initialize printwriter
							log_server_out = new PrintWriter(new FileWriter(log_server_target, true), true);
						} catch(IOException ioe) {
							//give error information to Main
							Main.println("[GRCLog] Init server log failed: " + ioe.getLocalizedMessage(), GRCLog.ERROR);
							Main.stackTrace(ioe);
						}
					}
					if(log_room) {
						log_room_out.close();
						try {
							File log_room_dir = new File("log/log_room/");
							if(!log_room_dir.exists()) {
								log_room_dir.mkdir();
							}
							File log_room_target = new File(log_room_dir, currentDate + "grc_room.log");
							log_room_out = new PrintWriter(new FileWriter(log_room_target, true), true);
						} catch(IOException ioe) {
							//give error information to Main
							Main.println("[GRCLog] Init room log failed: " + ioe.getLocalizedMessage(), GRCLog.ERROR);
							Main.stackTrace(ioe);
						}
					}
					if(log_command) {
						log_command_out.close();
						try {
							File log_cmd_dir = new File("log/log_cmd/");
							if(!log_cmd_dir.exists()) {
								log_cmd_dir.mkdir();
							}
							File log_cmd_target = new File(log_cmd_dir, currentDate + "grc_cmd.log");
							log_command_out = new PrintWriter(new FileWriter(log_cmd_target, true), true);
						} catch(IOException ioe) {
							//give error information to Main
							Main.println("[GRCLog] Init command log failed: " + ioe.getLocalizedMessage(), GRCLog.ERROR);
							Main.stackTrace(ioe);
						}
					}
					if(log_database) {
						log_database_out.close();
						try {
							File log_db_dir = new File("log/log_database/");
							if(!log_db_dir.exists()) {
								log_db_dir.mkdir();
							}
							File log_db_target = new File(log_db_dir, currentDate + "grc_db.log");
							log_database_out = new PrintWriter(new FileWriter(log_db_target, true), true);
						} catch(IOException ioe) {
							//give error information to Main
							Main.println("[GRCLog] Init database log failed: " + ioe.getLocalizedMessage(), GRCLog.ERROR);
							Main.stackTrace(ioe);
						}
					}
					if(log_error) {
						log_error_out.close();
						try {
							File log_error_dir = new File("log/log_error/");
							if(!log_error_dir.exists()) {
								log_error_dir.mkdir();
							}
							File log_error_target = new File(log_error_dir, currentDate + "grc_error.log");
							log_error_out = new PrintWriter(new FileWriter(log_error_target, true), true);
						} catch(IOException ioe) {
							//give error information to Main
							Main.println("[GRCLog] Init database log failed: " + ioe.getLocalizedMessage(), GRCLog.ERROR);
							Main.stackTrace(ioe);
						}
					}
					lastLog = System.currentTimeMillis();
				}
				try {
					Thread.sleep(10000);
				} catch(InterruptedException e) {
					Main.println("[Main] New day loop sleep interrupted", ERROR);
				}
			}
		}
	}
	
	public static void println(String message, int type) {
		//for single log file
		if(log_single && log_single_out != null) {
			log_single_out.println("[" + dateAndTime() + "]" + message);
		}
		//for each log type (server, room, command, database, error)
		switch(type) {
			case SERVER:
				//write to file
				if(log_server && log_server_out != null) {
					log_server_out.println("[" + dateAndTime() + "]" + message);
				}
				break;
			case ROOM:
				if(log_room && log_room_out != null) {
					log_room_out.println("[" + dateAndTime() + "]" + message);
				}
				break;
			case COMMAND:
				if(log_command && log_command_out != null) {
					log_command_out.println("[" + dateAndTime() + "]" + message);
				}
				break;
			case DATABASE:
				if(log_database && log_database_out != null) {
					log_database_out.println("[" + dateAndTime() + "]" + message);
				}
				break;
			case ERROR:
				if(log_error && log_error_out != null) {
					log_error_out.println("[" + dateAndTime() + "]" + message);
				}
				break;
			default:
				System.out.println(message);
				System.out.println("Ouput type unknown, discarding");
		}
	}
	
	public static void stackTrace(Exception e) {
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
	
	public static String dateAndTime() {
		Calendar cal = Calendar.getInstance();
		SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT + " " + TIME_FORMAT);
		return sdf.format(cal.getTime());
	}
}