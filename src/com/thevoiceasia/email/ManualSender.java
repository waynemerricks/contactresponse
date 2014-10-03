package com.thevoiceasia.email;

import java.io.IOException;
import java.net.UnknownHostException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Date;
import java.util.HashMap;
import java.util.logging.FileHandler;

import javax.mail.MessagingException;

import com.thevoiceasia.messages.MessageArchiver;
import com.thevoiceasia.sms.SMSSender;

public class ManualSender extends MessageArchiver implements EmailReader{

	private String mailHost = null, mailUser = null, mailPass = null,
				senderHost =  null, senderUser = null, senderPass = null;
	private HashMap<String, String> settings = null;
	private HashMap<String, String> users = null;
	private EmailSender email = null;
	private SMSSender sms = null;
	private boolean DEBUG = false;
	
	public ManualSender(String host, String user, String pass, String dbase,
			String mailHost, String mailUser, String mailPass, 
			String archivePath, String senderHost, String senderUser, 
			String senderPassword, boolean debug){
		
		super(host, user, pass, dbase, archivePath);
		
		this.mailHost = mailHost;
		this.mailUser = mailUser;
		this.mailPass = mailPass;
		this.senderHost = senderHost;
		this.senderUser = senderUser;
		this.senderPass = senderPassword;
		this.DEBUG = debug;
		
		setupLogging();
		database.connect();
		readSettings();
		readUsers();
		
		//TODO time out 30minutes T status to D
		
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
	
	/**
	 * Read user id ==> Name map
	 */
	private void readUsers(){
		
		String SQL = "SELECT `id`, `name` FROM `users`"; //$NON-NLS-1$
		
		Statement getUsers = null;
		ResultSet results = null;
		
		try{
			
			getUsers = database.getConnection().createStatement();
			
			if(getUsers.execute(SQL)){
				
				results = getUsers.getResultSet();
				users = new HashMap<String, String>();
				
				while(results.next()){
					
					users.put(results.getString("id"), //$NON-NLS-1$
							results.getString("name")); //$NON-NLS-1$
					
				}
				
			}
			
		}catch(SQLException e){
			
			LOGGER.severe("Unable to read users from DB"); //$NON-NLS-1$
			e.printStackTrace();
			System.exit(1);
			
		}finally{
			
			close(getUsers, results);
			
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
				
				char c = from.charAt(i);
				
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
			body = stripThunderbirdSig(body);
			
			//Add to DB for outgoing and archive
			insertedId = addOutgoingMessageToDB(type, messageId, userId, 
					subject, body);
			
			//Send the message
			if(type.equals("E")){//Email //$NON-NLS-1$
				
				if(email == null)
					email = new EmailSender(senderHost, senderUser, senderPass);
				
				String recipient = getContactEmail(messageId);
				
				if(DEBUG){
					
					body = "Original Recipient: " + recipient + "\n" + body; //$NON-NLS-1$ //$NON-NLS-2$
					recipient = settings.get("debugRecipient"); //$NON-NLS-1$
					
				}
				
				//Add -- From User to the end of the message
				body += "\n--\n\t" + users.get(userId); //$NON-NLS-1$
				body += getFooter(messageId);//Add the footers as necessary
				
				try{
					
					success = email.sendEmail(recipient, 
							settings.get("emailFromAddress"), subject,  //$NON-NLS-1$
							body);
					
				}catch(MessagingException e){
					
					LOGGER.severe("MessagingException: " + recipient); //$NON-NLS-1$
					e.printStackTrace();
					
				}catch(UnknownHostException e){
					
					LOGGER.severe("UnknownHostException: " + recipient + " " +   //$NON-NLS-1$//$NON-NLS-2$
							senderHost);
					e.printStackTrace();
					
				}
				
			}else if(type.equals("S")){//SMS //$NON-NLS-1$
				
				if(sms == null)
					sms = new SMSSender(database);
				
				//Put the first name of user name on the end of message
				//e.g. John Smith becomes: blah blah blah -- John
				//SMS are character tight (although it will split it just costs
				//you more)
				//body += "-- " + users.get(userId).substring(0, 1); //$NON-NLS-1$
				if(users.get(userId).contains(" ")) //$NON-NLS-1$
					body += "-- " + users.get(userId).split(" ")[0];//Just first name //$NON-NLS-1$ //$NON-NLS-2$
					
				String toNumber = getContactPhone(messageId);
				success = sms.sendSMS(toNumber, body);
				
			}
			
		}else
			success = true;//flag as dealt with so email receiver can mark for deletion

		if(success){
			
			//M = Sent Manual Email
			//N = Sent Manual SMS
			//A = Archive older than where status = T
			String status = "M"; //$NON-NLS-1$
			
			if(type.equals("S")) //$NON-NLS-1$
				status = "N"; //$NON-NLS-1$
			
			updateMessageStatus(Integer.parseInt(messageId), insertedId, 
					status);
			
		}else{
			
			//Mark as error
			//G = Failed Manual Email
			//H = Failed Manual SMS
			String status = "G"; //$NON-NLS-1$
			
			if(type.equals("S")) //$NON-NLS-1$
				status = "H"; //$NON-NLS-1$
			
			updateMessageStatus(Integer.parseInt(messageId), insertedId, 
					status);
			
		}
		
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
	 */
	private int addOutgoingMessageToDB(String type, String messageId,
			String userId, String subject, String body) {
		
		int insertedId = -1;
		PreparedStatement insertMessage = null;
		ResultSet results = null;
		
		try{
			
			String SQL = "INSERT INTO `messages` (`owner`, `assigned_user`, " + //$NON-NLS-1$
					"`type`, `direction`, `created_by`, `status`, `preview`) " + //$NON-NLS-1$
					"VALUES (?, ?, ?, ?, ?, ?, ?)"; //$NON-NLS-1$
			
			//TODO finish this, need to prepare the statement and archive to file
			//V unsent manual message
			insertMessage = database.getConnection().prepareStatement(SQL);
			
			insertMessage.execute(SQL, Statement.RETURN_GENERATED_KEYS);
				
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
		
		String[] SQL = {
				"UPDATE `messages` SET `status` = '" + status +   //$NON-NLS-1$
					"' WHERE `id` = " + outgoingMessageId,  //$NON-NLS-1$
				"UPDATE `messages` SET `status` = 'R' WHERE `id` = " + //$NON-NLS-1$
					contactMessageId, 
				"UPDATE `messages` SET `status` = 'A' WHERE `status` = 'T' " + //$NON-NLS-1$
					"AND `owner` = " + contactId + " AND `id` < " +  //$NON-NLS-1$ //$NON-NLS-2$
					contactMessageId
		};
		
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
	 * Gets the contacts phone from the given message Id
	 * @param messageId
	 * @return
	 */
	private String getContactPhone(String messageId) {
		
		int contactId = getContactId(Integer.parseInt(messageId));
		
		return getContactValue("phone", contactId); //$NON-NLS-1$
		
	}
	
	/**
	 * Gets the given value from the contacts table where the id matches
	 * @param field field to get
	 * @param id id of record to get field for
	 * @return value of field
	 */
	private String getContactValue(String field, int id){
		
		String value = null;
		
		String SQL = "SELECT `" + field + "` FROM `contacts WHERE `id` = " + id;  //$NON-NLS-1$//$NON-NLS-2$
		
		Statement select = null;
		ResultSet results = null;
		
		try{
			
			select = database.getConnection().createStatement();
			
			if(select.execute(SQL)){
				
				results = select.getResultSet();
				
				while(results.next())
					value = results.getString(field);
				
			}
			
		}catch(SQLException e){
			
			LOGGER.severe("Error while reading contact field " + field); //$NON-NLS-1$
			e.printStackTrace();
			
		}finally{
			
			close(select, results);
			
		}
		
		return value;
		
	}

	/**
	 * Gets the contacts email associated with the given message
	 * @param messageId
	 * @return
	 */
	private String getContactEmail(String messageId) {
		
		int contactId = getContactId(Integer.parseInt(messageId));
		
		return getContactValue("email", contactId); //$NON-NLS-1$
		
	}

	private String getFooter(String messageId) {
		// TODO Auto-generated method stub
		return null;
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
			body = body.substring(0, body.indexOf("-- \n")); //$NON-NLS-1$
			
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
		
	}
	
	/**
	 * Set the Logger object
	 */
	private void setupLogging(){
		
		LOGGER.setLevel(LEVEL);
		
		try{
			
			LOGGER.addHandler(new FileHandler("contactresponsemanual.log")); //$NON-NLS-1$
			
		}catch(IOException e){
			
			e.printStackTrace();
			
		}
		
	}
	
	/**
	 * Entry point for Manual Sender
	 * @param args 8 arguments expected:
	 *   DB_HOST DB_USER DB_PASS DBASE MAIL_HOST MAIL_USER MAIL_PASS \ 
	 *   EMAIL_STORE_PATH SEND_EMAIL_HOST SEND_EMAIL_USER SEND_EMAIL_PASS \
	 *   [DEBUG]
	 */
	public static void main(String[] args){
		
		boolean debug = false;
		
		if(args.length == 12)
			debug = true;
		
		if(args.length == 11 || args.length == 12){
			
			ManualSender manual = new ManualSender(args[0], args[1], args[2],
					args[3], args[4], args[5], args[6], args[7], args[8], 
					args[9], args[10], debug);
			
			if(!manual.isValidArchive()){
				
				System.out.println("Email Store Path invalid, doesn't exist " + //$NON-NLS-1$
						"or has no write permissions: " + args[7]); //$NON-NLS-1$
				System.exit(1);
				
			}
			
			LOGGER.info("ManualSender: Started on inbox " + args[5]); //$NON-NLS-1$
				
			boolean go = true;
			
			while(go){
				
				try {
					
					manual.getEmails();
					if(manual.isDatabaseConnected())
						manual.disconnectFromDB();
					
					LOGGER.finest("ManualSender: Sleeping for " + CHECK_PERIOD); //$NON-NLS-1$
					sleep(1000 * CHECK_PERIOD);
					
				} catch (InterruptedException e) {
					
					LOGGER.info("ManualSender: Interrupted, exiting"); //$NON-NLS-1$
					e.printStackTrace();
					go = false;
					
				} finally {
					
					manual.disconnectFromDB();
					
				}
				
			}
			
		}else{
			
			System.out.println("USAGE: DBHOST DBUSER DBPASS DBASE MAILHOST " + //$NON-NLS-1$
					"MAILUSER MAILPASS EMAILSTOREPATH [DEBUG]"); //$NON-NLS-1$
			System.exit(1);
			
		}
		
	}

}
