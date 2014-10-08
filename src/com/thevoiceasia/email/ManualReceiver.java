package com.thevoiceasia.email;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.logging.FileHandler;

import com.thevoiceasia.messages.MessageArchiver;

public class ManualReceiver extends MessageArchiver implements EmailReader{

	private String mailHost = null, mailUser = null, mailPass = null;
	private HashMap<String, String> settings = null;
	private long expiredTimeOut = 0L;
	
	/**
	 * Works on the incoming inbox for manual replies, also responsible for 
	 * timing out replies and sending out the responses for manual replies
	 * @param host Database Host for CRS
	 * @param user User Name for CRS Database
	 * @param pass Password for CRS Database
	 * @param dbase Database Name for CRS Database
	 * @param mailHost Host Name for incoming inbox
	 * @param mailUser User Name for incoming inbox
	 * @param mailPass Password for incoming inbox
	 * @param archivePath Path to archive the messages on incoming inbox
	 */
	public ManualReceiver(String host, String user, String pass, String dbase,
			String mailHost, String mailUser, String mailPass, 
			String archivePath){
		
		super(host, user, pass, dbase, archivePath);
		
		this.mailHost = mailHost;
		this.mailUser = mailUser;
		this.mailPass = mailPass;
		
		setupLogging();
		database.connect();
		readSettings();
		
		//Calculate timeout in millis
		int minutes = Integer.parseInt(settings.get("manualTimeOut")); //$NON-NLS-1$
		expiredTimeOut = minutes * 60000;
		
	}
	
	/**
	 * Read all settings from DB
	 */
	private void readSettings() {
		
		String SQL = "SELECT `name`, `value` FROM `settings`"; //$NON-NLS-1$
		
		Statement getSettings = null;
		ResultSet results = null;
		
		try{
			
			getSettings = database.getConnection().createStatement();
			
			if(getSettings.execute(SQL)){
				
				results = getSettings.getResultSet();
				settings = new HashMap<String, String>();
				
				while(results.next()){
					
					settings.put(results.getString("name"), //$NON-NLS-1$
							results.getString("value")); //$NON-NLS-1$
					
				}
				
			}
			
		}catch(SQLException e){
			
			LOGGER.severe("Unable to read settings from DB"); //$NON-NLS-1$
			e.printStackTrace();
			System.exit(1);
			
		}finally{
			
			close(getSettings, results);
			
		}
		
	}
	
	@Override
	public boolean receiveEmail(Date receivedDate, String from, String to,
			String name, String body, String subject) {
		
		String messageId = ""; //$NON-NLS-1$
		String type = ""; //$NON-NLS-1$
		int insertedId = -1;
	    boolean success = false;
		
		if(to.contains("@" + settings.get("internalEmailDomain"))){//$NON-NLS-1$ //$NON-NLS-2$
			
			if(body == null)
				body = ""; //$NON-NLS-1$
			else
				body = body.trim();
			
			if(!isDatabaseConnected())
				connectToDB();
				
			//94086e3
			//last message id, type [e|s], user id
			//Get message id, message type and user id
			to = to.split("@")[0]; //$NON-NLS-1$
			
		    String userId = ""; //$NON-NLS-1$
		    
			for(int i = 0; i < to.length(); i++){
				
				char c = to.charAt(i);
				
				if(Character.isAlphabetic(c)){
					
					//we're at the split point
					type = ("" + c).toUpperCase(); //$NON-NLS-1$
					
				}else{
					
					if(type.length() == 0)//We're still reading message id
						messageId += c;
					else//We're getting the user id
						userId += c;
					
				}
				
			}
			
			//Strip any Thunderbird signatures from emails/SMS
			body = stripThunderbirdSig(body).trim();
			
			//Add to DB for outgoing and archive
			if(type.equals("S")) //$NON-NLS-1$
				subject = ""; //Don't need subject in SMS //$NON-NLS-1$
			
			insertedId = addOutgoingMessageToDB(type, messageId, userId, 
					subject, body);
			
			//Archive Old Messages this reply relates to
			updateMessageStatus(Integer.parseInt(messageId), insertedId, "A"); //$NON-NLS-1$
			
			//Archive the message to file
			String fileBody = body;
			
			if(subject.length() > 0)//Add subject as first line
				fileBody = "S:" + subject + "\n" + fileBody;  //$NON-NLS-1$//$NON-NLS-2$
			
			success = writeMessageToArchive(insertedId, fileBody);
			
		}else
			success = true;//flag as dealt with so email receiver can mark for deletion
	
		return success;
		
	}
	
	/**
	 * Adds the outgoing message to the DB and archive files
	 * @param type Message Type E/S (Email/SMS)
	 * @param messageId ID of the newest message from a contact covered by this 
	 *   outgoing reply
	 * @param userId ID of the user that created this outgoing reply
	 * @param subject Subject of the message (ignored for SMS)
	 * @param body Body of the message
	 * @return record id returned when inserted into the messages table
	 *   or -1 if not inserted or -2 if blank message
	 */
	private int addOutgoingMessageToDB(String type, String messageId,
			String userId, String subject, String body) {
		
		int insertedId = -1;
		PreparedStatement insertMessage = null;
		ResultSet results = null;
		
		String preview = body.replaceAll("\n", "  ").replaceAll("\r", ""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		preview = preview.trim();
		
		if(preview.length() < 1){
			
			if(subject.trim().length() > 1)
				preview = subject;
			else
				insertedId = -2;//flag as error
		}
		
		if(insertedId == -1){
			
			if(preview.length() > 50)
				preview = preview.substring(0, 47) + "..."; //$NON-NLS-1$
			
			try{
				
				String SQL = "INSERT INTO `messages` (`owner`, `assigned_user`, " + //$NON-NLS-1$
						"`type`, `direction`, `created_by`, `status`, `preview`) " + //$NON-NLS-1$
						"VALUES (?, ?, ?, ?, ?, ?, ?)"; //$NON-NLS-1$
				
				insertMessage = database.getConnection().prepareStatement(SQL, Statement.RETURN_GENERATED_KEYS);
				
				insertMessage.setInt(1, getContactId(Integer.parseInt(messageId)));//owner
				insertMessage.setInt(2, Integer.parseInt(userId));//Assign to user who sent this
				insertMessage.setString(3, type); //Type
				insertMessage.setString(4, "O"); //Direction //$NON-NLS-1$
				insertMessage.setInt(5, Integer.parseInt(userId)); //Created By
				insertMessage.setString(6, "V"); //status V ==> Unsent Manual  //$NON-NLS-1$
				insertMessage.setString(7, preview); //preview
					
				//Execute it
				int rows = insertMessage.executeUpdate();
				
				if(rows > 0){
				
					results = insertMessage.getGeneratedKeys();
					
					while(results.next())
						insertedId = results.getInt(1);
					
				}else
					LOGGER.warning("Could not insert message"); //$NON-NLS-1$
				
			}catch(SQLException e){
				
				LOGGER.severe("Error inserting outgoing message"); //$NON-NLS-1$
				e.printStackTrace();
				
			}finally{
				
				close(insertMessage, results);
				
			}
			
		}
		
		return insertedId;
		
	}
	
	/**
	 * Updates contact messages and the outgoing reply with the required status
	 * @param contactMessageId newest message ID from the contact that this 
	 *   relates to
	 * @param outgoingMessageId ID of the outgoing message to set the status
	 * @param status Status to set outgoing message to
	 */
	private boolean updateMessageStatus(int contactMessageId, 
			int outgoingMessageId, String status) {
		
		boolean updated = true;
		
		int contactId = getContactId(contactMessageId);
		String currentSQL = null;
		
		String[] SQL = null;
		
		if(outgoingMessageId == -1 || outgoingMessageId == -2){
			
			//Error so reset status back to D
			SQL = new String[]{
				"UPDATE `messages` SET `status` = 'D' WHERE `status` = 'T' " + //$NON-NLS-1$
				"AND `owner` = " + contactId + " AND `id` <= " +  //$NON-NLS-1$ //$NON-NLS-2$
				contactMessageId
			};
			
		}else{
			
			SQL = new String[]{
				"UPDATE `messages` SET `status` = 'R' WHERE `id` = " + //$NON-NLS-1$
					contactMessageId, 
				"UPDATE `messages` SET `status` = 'A' WHERE `status` = 'T' " + //$NON-NLS-1$
					"AND `owner` = " + contactId + " AND `id` < " +  //$NON-NLS-1$ //$NON-NLS-2$
					contactMessageId
			};
			
		}
		
		Statement updateMessages = null;
		
		try{
			
			updateMessages = database.getConnection().createStatement();
			
			for(int i = 0; i < SQL.length; i++){
				
				if(updated == true){
					
					currentSQL = SQL[i];
					updateMessages.execute(SQL[i]);
					
					if(updateMessages.getUpdateCount() < 1){
						
						LOGGER.warning("Could not update messages table: " +  //$NON-NLS-1$
								currentSQL);
						updated = false;
						
					}
					
				}
				
			}
			
		}catch(SQLException e){
			
			LOGGER.severe("Error updating message status: " + currentSQL); //$NON-NLS-1$
			e.printStackTrace();
			updated = false;
			
		}finally{
			
			close(updateMessages, null);
			
		}
		
		return updated;
		
	}
	
	/**
	 * Gets a contact id via a message id lookup
	 * @param contactMessageId id to lookup
	 * @return owner of that message (maps to contact id)
	 */
	private int getContactId(int contactMessageId) {
		
		int contactId = -1;
		String SQL = "SELECT `owner` FROM `messages` WHERE `id` = " +  //$NON-NLS-1$
				contactMessageId;
		
		Statement selectMessage = null;
		ResultSet results = null;
		
		try{
			
			selectMessage = database.getConnection().createStatement();
			
			if(selectMessage.execute(SQL)){
				
				results = selectMessage.getResultSet();
				
				while(results.next())
					contactId = results.getInt("owner"); //$NON-NLS-1$
				
			}
			
		}catch(SQLException e){
			
			LOGGER.severe("Error getting contact id from message " +  //$NON-NLS-1$
					contactMessageId);
			
		}finally{
			
			close(selectMessage, results);
			
		}
		
		return contactId;
		
	}
	
	/**
	 * Removes any thunderbird signatures from the given string.
	 * 
	 * Very simple: TB prefixes signatures with "-- \n" so if it contains
	 * this, substring previous to this string
	 * @param body String to check
	 * @return String minus TB signature
	 */
	private String stripThunderbirdSig(String body) {
		
		//Strip everything after this string including the string itself
		if(body.contains("-- \n")) //$NON-NLS-1$
			body = body.substring(0, body.indexOf("-- \n")).trim(); //$NON-NLS-1$
		else if(body.contains("-- \r\n"))//$NON-NLS-1$
			body = body.substring(0, body.indexOf("-- \r\n")).trim();//$NON-NLS-1$
		
		return body;
		
	}
	
	/**
	 * Get emails to process
	 * @throws InterruptedException 
	 */
	public void getEmails() throws InterruptedException{
		
		EmailReceiver receiver = new EmailReceiver(mailHost, mailUser, mailPass, this);
		receiver.start();
		receiver.join();
		checkForTimedOutMessages();
		
	}
	
	/**
	 * Resets any messages waiting for a manual response that have timed out
	 * back to the default D Status so people will once again see them in 
	 * their inboxes
	 */
	private void checkForTimedOutMessages() {
		
		Date expired = new Date(new Date().getTime() - expiredTimeOut);
		
		SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss"); //$NON-NLS-1$
		
		String expiredTimeStamp = sdf.format(expired);
		String SQL = "UPDATE `messages` SET `status` = 'D' WHERE " + //$NON-NLS-1$
				"`status` = 'T' AND `updated` < " + //$NON-NLS-1$
				expiredTimeStamp; 
		
		Statement resetManual = null;
		
		try{
			
			resetManual = database.getConnection().createStatement();
			resetManual.executeUpdate(SQL);
				
		}catch(SQLException e){
			
			LOGGER.severe("Error while resetting manual response messages"); //$NON-NLS-1$
			e.printStackTrace();
			
		}finally{
			
			close(resetManual, null);
			
		}
		
	}
	
	/**
	 * Set the Logger object
	 */
	private void setupLogging(){
		
		LOGGER.setLevel(LEVEL);
		
		try{
			
			LOGGER.addHandler(new FileHandler(
					"contactresponsemanualreceiver.log")); //$NON-NLS-1$
			
		}catch(IOException e){
			
			e.printStackTrace();
			
		}
		
	}
	
	/**
	 * Entry point for Manual Sender
	 * @param args 8 arguments expected:
	 *   DB_HOST DB_USER DB_PASS DBASE MAIL_HOST MAIL_USER MAIL_PASS \ 
	 *   EMAIL_STORE_PATH
	 */
	public static void main(String[] args){
		
		if(args.length == 8){
			
			ManualReceiver manual = new ManualReceiver(args[0], args[1], args[2],
					args[3], args[4], args[5], args[6], args[7]);
			
			if(!manual.isValidArchive()){
				
				System.out.println("Email Store Path invalid, doesn't exist " + //$NON-NLS-1$
						"or has no write permissions: " + args[7]); //$NON-NLS-1$
				System.exit(1);
				
			}
			
			LOGGER.info("ManualReceiver: Started on inbox " + args[5]); //$NON-NLS-1$
				
			boolean go = true;
			
			while(go){
				
				try {
					
					manual.getEmails();
					
					if(manual.isDatabaseConnected())
						manual.disconnectFromDB();
					
					LOGGER.finest("ManualReceiver: Sleeping for " + CHECK_PERIOD); //$NON-NLS-1$
					sleep(1000 * CHECK_PERIOD);
					
				} catch (InterruptedException e) {
					
					LOGGER.info("ManualReceiver: Interrupted, exiting"); //$NON-NLS-1$
					e.printStackTrace();
					go = false;
					
				} finally {
					
					manual.disconnectFromDB();
					
				}
				
			}
			
		}else{
			
			System.out.println("USAGE: DBHOST DBUSER DBPASS DBASE MAILHOST " + //$NON-NLS-1$
					"MAILUSER MAILPASS EMAILSTOREPATH"); //$NON-NLS-1$
			System.exit(1);
			
		}
		
	}
	
}
