package com.thevoiceasia.email;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Date;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.thevoiceasia.contact.Contact;
import com.thevoiceasia.database.DatabaseHelper;
import com.thevoiceasia.html.HTML2Text;
import com.thevoiceasia.sms.XpressMS;

public class EmailArchiver extends Thread implements EmailReader {

	private DatabaseHelper database = null;
	private String ARCHIVE_PATH = null, mailHost = null, mailUser = null, mailPass = null;
	private boolean archiveValid = false;
	private static final Logger LOGGER = Logger.getLogger("com.thevoiceasia"); //$NON-NLS-1$
	private static final Level LEVEL = Level.INFO;//Logging level of this class
	private static final int EMAIL_CHECK_PERIOD = 60; //Time to check for emails in seconds
		
	/**
	 * Receives Email from the given email inbox (no subfolders) and archives
	 * it in the database and emailStorePath
	 * @param dbHost MySQL Host
	 * @param dbUser MySQL User Name
	 * @param dbPass MySQL Password
	 * @param dbBase MySQL Database
	 * @param mailHost Email Host
	 * @param mailUser Email User
	 * @param mailPass Email Password
	 * @param emailStorePath Path to where email content should be archived
	 */
	public EmailArchiver(String dbHost, String dbUser, String dbPass, 
			String dbBase, String mailHost, String mailUser, String mailPass, 
			String emailStorePath){
		
		this.mailHost = mailHost;
		this.mailUser = mailUser;
		this.mailPass = mailPass;
		
		database = new DatabaseHelper(dbHost, dbBase, dbUser, dbPass);
		ARCHIVE_PATH = emailStorePath;
		
		File directory = new File(ARCHIVE_PATH);
		
		if(directory.exists() && directory.isDirectory() && directory.canWrite())
			archiveValid = true;
		
		setupLogging();
		
	}
	
	/**
	 * Connects to MySQL
	 */
	public void connectToDB(){
		
		database.connect();
		
	}
	
	/**
	 * Disconnect from MySQL
	 */
	public void disconnectFromDB(){
		
		database.disconnect();
		
	}
	
	/**
	 * Get emails to process
	 * @throws InterruptedException 
	 */
	public void getEmails() throws InterruptedException{
		
		EmailReceiver receiver = new EmailReceiver(mailHost, mailUser, mailPass, this);
		receiver.start();
		receiver.join();
		
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
	 * Set the Logger object
	 */
	private void setupLogging(){
		
		LOGGER.setLevel(LEVEL);
		
		try{
			
			LOGGER.addHandler(new FileHandler("contactresponseincoming.log")); //$NON-NLS-1$
			
		}catch(IOException e){
			
			e.printStackTrace();
			
		}
		
	}
	
	/**
	 * Entry point for Email Receiver
	 * @param args 8 arguments expected:
	 *   DB_HOST DB_USER DB_PASS DBASE MAIL_HOST MAIL_USER MAIL_PASS EMAIL_STORE_PATH
	 */
	public static void main(String[] args){
		
		if(args.length == 8){
			
			EmailArchiver archiver = new EmailArchiver(args[0], args[1], args[2], args[3], args[4], args[5],
					args[6], args[7]);
			
			if(!archiver.isValidArchive()){
				System.out.println("Email Store Path invalid, doesn't exist or has no write permissions: " + args[7]); //$NON-NLS-1$
				System.exit(1);
			}
			
			LOGGER.info("Archiver: Started on inbox " + args[5]); //$NON-NLS-1$
				
			boolean go = true;
			
			while(go){
				
				try {
					
					archiver.connectToDB();
					archiver.getEmails();
					archiver.disconnectFromDB();
					
					LOGGER.finest("Archiver: Sleeping for " + EMAIL_CHECK_PERIOD); //$NON-NLS-1$
					sleep(1000 * EMAIL_CHECK_PERIOD);
					
				} catch (InterruptedException e) {
					
					LOGGER.info("Archiver: Interrupted, exiting"); //$NON-NLS-1$
					e.printStackTrace();
					go = false;
					
				} finally {
					
					archiver.disconnectFromDB();
					
				}
				
			}
			
		}else{
			
			System.out.println("USAGE: DBHOST DBUSER DBPASS DBASE MAILHOST MAILUSER MAILPASS EMAILSTOREPATH"); //$NON-NLS-1$
			System.exit(1);
			
		}
		
	}
	
	/**
	 * Returns only the Message portion of the SMS message as provided by Express SMS
	 * @param messageContent full SMS in plain text
	 * @return just the message bit
	 */
	private String parseSMS(String messageContent) {
		
		return XpressMS.toMessage(messageContent);
		
	}

	/**
	 * Prefix subject as first line of plain text
	 * @param text message content
	 * @param subject subject of message
	 * @return prepended text S:<subject goes here>\n<Message Content goes here>
	 */
	private String prefixSubject(String text, String subject) {
		
		if(text != null)
			text = text.trim();
		else
			text = ""; //$NON-NLS-1$
		
		subject = subject.trim();
		
		//Prefix with subject if applicable
		if(subject.length() > 0)
			text = "S:" + subject + "\n" + text; //$NON-NLS-1$ //$NON-NLS-2$
			
		return text;
		
	}
	
	/**
	 * Writes the message content to a text file in the ARCHIVE location
	 * @param fileID name of file and message record id it relates to
	 * @param messageContent Content to go in the file
	 * @return true if we succeeded in writing the file
	 */
	private boolean writeMessageToArchive(int fileID, String messageContent) {
		
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
	private void removeMessageFromDatabase(int id) {
		
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
	 * Helper method that will strip out more than 1 line with empty text and
	 * new line chars
	 * @param parseMe
	 * @return String without excessive blank new lines
	 */
	private String stripExcessiveNewLines(String parseMe){
		
		String[] lines = parseMe.split("\n"); //$NON-NLS-1$
		parseMe = ""; //$NON-NLS-1$
		
		int blankCount = 0;
		
		for(String temp : lines){
			
			if(temp.trim().length() == 0)
				blankCount++;
			else
				blankCount = 0;
			
			if(blankCount < 3)
				parseMe += temp + "\n"; //$NON-NLS-1$
			
		}
		
		return parseMe;
		
	}
	
	@Override
	public boolean receiveEmail(Date receivedDate, String from, String to, 
			String name, String body, String subject) {
		
		boolean success = false;
		
		if(body == null)
			body = ""; //$NON-NLS-1$
		else
			body = body.trim();
		
		String type = "E"; //$NON-NLS-1$
		boolean sms = false;
		
		if(from.contains("@sms.xpressms.com")){ //$NON-NLS-1$
		
			sms = true;
			type = "S"; //$NON-NLS-1$
			body = parseSMS(body);//Split XpressMS info out of the body
			
		}else
			body = prefixSubject(body, subject);
		
		//We can still have html here if the email registered plain text but sent html
		//So strip it
		if(HTML2Text.hasHTML(body))
			body = HTML2Text.convert(body);
		
		if(HTML2Text.hasHTML(subject))
			subject = HTML2Text.convert(subject);
		
		body = stripExcessiveNewLines(body);
		subject = stripExcessiveNewLines(subject);
		
		Contact contact = new Contact(database, from, name, sms);
		
		if(subject.endsWith("form submitted")) //$NON-NLS-1$
			contact.updateWithWebForm(body);
			
		int existingContactId = contact.getID();
		String preview = body.replaceAll("\n", "  ").replaceAll("\r", ""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		
		if(preview.startsWith("S:"))//Remove Subject prefix don't need it in preview //$NON-NLS-1$
			preview = preview.substring(2);
		
		if(preview.length() > 50)
			preview = preview.substring(0, 47) + "..."; //$NON-NLS-1$
		
		String SQL = "INSERT INTO `messages` (`owner`, `type`, `direction`, " + //$NON-NLS-1$
				"`preview`) VALUES (?, ?, ?, ?)"; //$NON-NLS-1$
		
		Connection mysql = database.getConnection();
		PreparedStatement insertMessage = null;
		ResultSet insertIDs = null;
		
		try{
			
			//Bind all variables to statement
			insertMessage = mysql.prepareStatement(SQL, 
					Statement.RETURN_GENERATED_KEYS);
			insertMessage.setInt(1, existingContactId);
			insertMessage.setString(2, type);
			insertMessage.setString(3, "I"); //$NON-NLS-1$
			insertMessage.setString(4, preview);
			
			//Execute it
			int rows = insertMessage.executeUpdate();
			
			if(rows > 0){
			
				LOGGER.finest("Successfully added message from " + from); //$NON-NLS-1$
				insertIDs = insertMessage.getGeneratedKeys();
				
				while(insertIDs.next()){
				
					int id = insertIDs.getInt(1);
					success = writeMessageToArchive(id, body);
					
					if(!success){
						
						LOGGER.warning("Error writing to archive, removing " + //$NON-NLS-1$
								"message from database: " + from); //$NON-NLS-1$
						removeMessageFromDatabase(id);
						
					}
				
				}
				
				if(success)
					LOGGER.finest("Successfully archived message from "  //$NON-NLS-1$
							+ from);
				else
					LOGGER.severe("Failed to archive message from "  //$NON-NLS-1$
							+ from);
				
			}
			
		}catch(SQLException e){
			
			e.printStackTrace();
			LOGGER.severe("SQL Error while inserting message"); //$NON-NLS-1$
			
		}finally{
			
			if(insertIDs != null){
            	try{
            		insertIDs.close();
            		insertIDs = null;
            	}catch(Exception e){}
            }
            	
            if(insertMessage != null){//Close Statement
            	try{
            		insertMessage.close();
            		insertMessage = null;
            	}catch(Exception e){}
            }
        	
		}
		
		return success;
		
	}

}