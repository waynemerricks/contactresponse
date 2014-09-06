package com.thevoiceasia.messages;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.thevoiceasia.contact.Contact;
import com.thevoiceasia.database.DatabaseHelper;
import com.thevoiceasia.user.FreeUsers;

public class MessageArchiver extends Thread{

	//Variables accessible to any class that extends this
	private String smsEmail = null;
	protected boolean archiveValid = false;
	private boolean connected = false;
	protected DatabaseHelper database = null;
	protected String ARCHIVE_PATH = null;
	private ArrayList<RoutingPriority> routing = new ArrayList<RoutingPriority>();
	private FreeUsers users = null;
	
	protected static final Logger LOGGER = Logger.getLogger("com.thevoiceasia"); //$NON-NLS-1$
	protected static final Level LEVEL = Level.INFO;//Logging level of this class
	protected static final int CHECK_PERIOD = 60; //Time to check for emails in seconds
	
	/**
	 * Creates an Archiver that pushes messages to the database
	 * @param host Database Host ip/name
	 * @param user User name to use
	 * @param pass Password to use
	 * @param dbase Database to use
	 * @param archivePath path where body of message will be written in text 
	 * files
	 */
	public MessageArchiver(String host, String user, 
			String pass, String dbase, String archivePath){
		
		database = new DatabaseHelper(host, dbase, user, pass);
		
		ARCHIVE_PATH = archivePath;
		
		File directory = new File(ARCHIVE_PATH);
		
		if(directory.exists() && directory.isDirectory() && directory.canWrite())
			archiveValid = true;
		
	}
	
	/**
	 * Reads Users that are active without max contacts from DB
	 */
	protected void initialiseFreeUsers(){
		
		users = new FreeUsers(database);
		
	}
	
	/**
	 * Returns the FreeUsers object of this class
	 * @return
	 */
	protected FreeUsers getFreeUsers(){
		
		return users;
		
	}
	
	/**
	 * Returns the next available user ID
	 * @return null if no usable ID
	 */
	protected int getNextFreeUser(){
		
		return users.getNextAvailableUser();
		
	}
	
	/**
	 * Flag that indicates whether the archive for this object is valid and
	 * has write permissions
	 * @return true if all is good, false if things are bad
	 */
	public boolean isValidArchive(){
		
		return archiveValid;
		
	}
	
	/**
	 * Connects to MySQL
	 */
	public void connectToDB(){
		
		database.connect();
		connected = true;
		
	}
	
	/**
	 * Disconnect from MySQL
	 */
	public void disconnectFromDB(){
		
		database.disconnect();
		connected = false;
		
	}
	
	/**
	 * Return if we're in connected or disconnected state
	 */
	public boolean isDatabaseConnected(){
		
		return connected;
		
	}
	
	/**
	 * Writes the message content to a text file in the ARCHIVE location
	 * @param fileID name of file and message record id it relates to
	 * @param messageContent Content to go in the file
	 * @return true if we succeeded in writing the file
	 */
	protected boolean writeMessageToArchive(int fileID, String messageContent) {
		
		boolean created = false;
		
		String path = ARCHIVE_PATH + System.getProperty("file.separator") +  //$NON-NLS-1$
				fileID;
		
		File temp = new File(path);
		
		try {
			if(!temp.exists() && temp.createNewFile()){
				
				try{
					
					PrintWriter writer = new PrintWriter(path, "UTF-8"); //$NON-NLS-1$
					writer.print(messageContent);
					writer.close();
					
					created = true;
					
				}catch(UnsupportedEncodingException e){
					
					e.printStackTrace();
					LOGGER.severe("Can't encode UTF8 file for " + path); //$NON-NLS-1$
					
				}catch(FileNotFoundException e){
					
					e.printStackTrace();
					LOGGER.severe("Can't write file " + path); //$NON-NLS-1$
					
				}
				
			}
		} catch (IOException e) {
			
			e.printStackTrace();
			LOGGER.severe("Can't create new file " + path); //$NON-NLS-1$
			
		}
		
		return created;
		
	}
	
	/**
	 * Deletes a message from the message table.
	 * Called when writeToArchive() fails so we don't have a
	 * message in the table that doesn't exist in archive
	 * @param id id to delete
	 */
	protected void removeMessageFromDatabase(int id) {
		
		LOGGER.info("Deleting message ID: " + id); //$NON-NLS-1$
		String SQL = "DELETE FROM `messages` WHERE `id` = " + id; //$NON-NLS-1$
		
		Connection mysql = database.getConnection();
		Statement deleteMessage = null;
		
		try{
			
			deleteMessage = mysql.createStatement();
			
			//Execute it
			deleteMessage.executeUpdate(SQL);
			
		}catch(SQLException e){
			
			e.printStackTrace();
			LOGGER.severe("SQL Error while inserting message"); //$NON-NLS-1$
			
		}finally{
			
			if(deleteMessage != null){//Close Statement
            	try{
            		deleteMessage.close();
            		deleteMessage = null;
            	}catch(Exception e){}
            }
        	
		}
		
	}
	
	/**
	 * Helper method to close statements and resultsets
	 * @param statement
	 * @param results
	 */
	protected void close(Statement statement, ResultSet results){
		
		if(results != null){
			
			try{
				results.close();
			}catch(Exception e){}
			
			results = null;
			
		}

		if(statement != null){
			
			try{
				statement.close();
			}catch(Exception e){}
			
			statement = null;
			
		}
		
	}

	/**
	 * Reads smsEmail settings and priorities from DB
	 */
	protected void readDatabaseValues(){
		
		Statement getSMS = null;
		ResultSet results = null;
		
		try{
			
			getSMS = database.getConnection().createStatement();
			getSMS.execute("SELECT `value` FROM `settings` WHERE `name` = " + //$NON-NLS-1$
					"'smsEmail'"); //$NON-NLS-1$
			results = getSMS.getResultSet();
			
			while(results.next()){
				
				smsEmail = results.getString("value"); //$NON-NLS-1$
				
			}
			
		}catch(SQLException e){
			
			LOGGER.severe("Error reading setting for SMS Address"); //$NON-NLS-1$
			e.printStackTrace();
			
		}finally{
			
			close(getSMS, results);
			
		}
		
		getAssignedUserList();
		
	}
	
	/**
	 * Figure out who we should assign this message
	 * @param to email address that this message was sent to or phone topic
	 * @param body body of the email to check for keywords
	 * @param phone true if this is a phone number and not an email address
	 * @return -1 = use normal NULL value, else use the id to assign
	 */
	protected int getAssignedUserID(Contact contact, String to, String body, 
			boolean phone) {
		
		int assignTo = -1;
		
		if(to != null){
		
			String type = "E"; //$NON-NLS-1$
			
			if(phone)
				type = "P"; //$NON-NLS-1$
			else if(to.equals(smsEmail))
				type = "S"; //$NON-NLS-1$
			
			RoutingPriority rp = getHighestPriorityRoute(type, body, to);
			
			if(rp != null){//null = no matches should we time lookup shows?
				
				if(rp.useContactDefault())
					assignTo = contact.getAssignedUser();
				else
					assignTo = rp.sendToUser;
				
			}
			
		}
		
		return assignTo;
		
	}
	
	/**
	 * Supplement to getAssignedUserID loops through the RoutingPriorities array
	 * and assigns the highest Route we have for this message type/content
	 * @param type
	 * @param searchContent
	 * @param searchAddress
	 * @return
	 */
	private RoutingPriority getHighestPriorityRoute(String type, 
			String searchContent, String searchAddress){
		
		int highest = -1;
		RoutingPriority rp = null;
		
		for(int i = 0; i < routing.size(); i++){
			
			if(routing.get(i).appliesToType(type)){
				
				if((routing.get(i).isKeyWord() && 
						routing.get(i).hasKeyWord(searchContent))
						|| (!routing.get(i).isKeyWord() && 
							routing.get(i).appliesToAddress(searchAddress))){
				
					if(highest > routing.get(i).priority || highest == -1){
						
						highest = routing.get(i).priority;
						rp = routing.get(i);
						
					}
					
				}
				
			}
			
		}
		
		return rp;
		
	}
	
	/**
	 * Gets the RoutingPriority information from the DB
	 */
	protected void getAssignedUserList() {
		
		Statement getPriorities = null;
		ResultSet results = null;
		
		try{
			
			getPriorities = database.getConnection().createStatement();
			getPriorities.execute("SELECT * FROM `message_priorities`" + //$NON-NLS-1$
					" ORDER BY `priority` ASC"); //$NON-NLS-1$
			results = getPriorities.getResultSet();
			
			while(results.next())
				routing.add(new RoutingPriority(results.getString("type"), //$NON-NLS-1$
						results.getString("term"), results.getInt("user_id"),  //$NON-NLS-1$//$NON-NLS-2$
						results.getInt("priority"))); //$NON-NLS-1$
			
		}catch(SQLException e){
			
			LOGGER.severe("Error reading routing priorities"); //$NON-NLS-1$
			e.printStackTrace();
			
		}finally{
			
			close(getPriorities, results);
			
		}
		
	}
	
}
