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
import java.util.logging.Level;
import java.util.logging.Logger;

import com.thevoiceasia.database.DatabaseHelper;

public class MessageArchiver extends Thread{

	//Variables accessible to any class that extends this
	private String smsEmail = null;
	protected boolean archiveValid = false;
	private boolean connected = false;
	protected DatabaseHelper database = null;
	protected String ARCHIVE_PATH = null;
	
	protected static final Logger LOGGER = Logger.getLogger("com.thevoiceasia"); //$NON-NLS-1$
	protected static final Level LEVEL = Level.INFO;//Logging level of this class
	protected static final int CHECK_PERIOD = 60; //Time to check for emails in seconds
	
	public MessageArchiver(String host, String user, 
			String pass, String dbase, String archivePath){
		
		database = new DatabaseHelper(host, dbase, user, pass);
		
		ARCHIVE_PATH = archivePath;
		
		File directory = new File(ARCHIVE_PATH);
		
		if(directory.exists() && directory.isDirectory() && directory.canWrite())
			archiveValid = true;
	
		connectToDB();
		
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
		
		disconnectFromDB();
		
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
	 * Uses the to address to figure out who we should assign this message
	 * @param to email address that this message was sent to
	 * @param from who it came from in case we 
	 * @param body body of the email to check for keywords
	 * @param phone true if this is a phone number and not an email address
	 * @return -1 = use normal NULL value, else use the id to assign
	 */
	protected int getAssignedUserID(String to, String from, String body,  
			boolean phone) {
		
		/* TODO Use the to address to decide if this should be assigned
		 * to another user or a show
		 * 
		 * Create table for to address ==> user
		 */
		int assignTo = -1;
		
		if(to != null){
			
			if(phone || to.equalsIgnoreCase(smsEmail)){//Phone Number
				
				//Need to assign by Special Groups
				
			}else{//Email
				
				
				
				
			}
			
		}
		
		return assignTo;
		
	}
	
	protected void getAssignedUserList() {
		// TODO Auto-generated method stub
		
		
	}
	
}
