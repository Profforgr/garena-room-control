/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package grc;

/**
 *
 * @author XIII.Dragon
 */
 
public class UserInfo {
	
	public static int numUsers = 0;
	public String username;
	public String properUsername;
	public int userID;
	public int rank;
	public String ipAddress;
	public String lastSeen;
	public String promotedBy;
	
	public UserInfo() {
		username = "unknown";
		properUsername = "unknown";
		userID = 0;
		rank = 0;
		ipAddress = "unknown";
		lastSeen = "unknown";
		promotedBy = "unknown";
	}
}