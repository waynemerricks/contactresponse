package com.thevoiceasia.email;

import java.io.IOException;
import java.net.UnknownHostException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.logging.FileHandler;

import javax.mail.MessagingException;

import com.thevoiceasia.messages.Footers;
import com.thevoiceasia.messages.MessageArchiver;
import com.thevoiceasia.sms.SMSSender;

public class ManualSender extends MessageArchiver implements EmailReader{

	private String mailHost = null, mailUser = null, mailPass = null,
				senderHost =  null, senderUser = null, senderPass = null,
				templateBasePath = null;
	private HashMap<String, String> settings = null;
	private HashMap<String, String> users = null;
	private EmailSender email = null;
	private SMSSender sms = null;
	private boolean DEBUG = false;
	private Footers footers = null;
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
	 * @param senderHost Host Name for outgoing emails
	 * @param senderUser User Name for outgoing emails
	 * @param senderPassword Password for outgoing emails
	 * @param templatePath Template path also used for email footers
	 * @param debug if true, will only send sms/emails to the debug recipient
	 *   as an email
	 */
	public ManualSender(String host, String user, String pass, String dbase,
			String mailHost, String mailUser, String mailPass, 
			String archivePath, String senderHost, String senderUser, 
			String senderPassword, String templatePath, boolean debug){
		
		super(host, user, pass, dbase, archivePath);
		
		this.mailHost = mailHost;
		this.mailUser = mailUser;
		this.mailPass = mailPass;
		this.senderHost = senderHost;
		this.senderUser = senderUser;
		this.senderPass = senderPassword;
		this.templateBasePath = templatePath;
		
		this.DEBUG = debug;
		
		setupLogging();
		database.connect();
		readSettings();
		readUsers();
		
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

	/**
	 * Sends an email and handles exceptions
	 * @param recipient
	 * @param subject
	 * @param body
	 * @return
	 */
	private boolean sendEmail(String recipient, String subject, String body){
		
		boolean success = false;
		
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
		
		return success;
		
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
			insertedId = addOutgoingMessageToDB(type, messageId, userId, 
					subject, body);
			
			//Send the message
			if(type.equals("E") && insertedId != -2){//Email //$NON-NLS-1$
				
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
				
				sendEmail(recipient, subject, body);
				
			}else if(type.equals("S") && insertedId != -2){//SMS //$NON-NLS-1$
				
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
				
				//Debug = send email not sms
				if(DEBUG){
					if(email == null)
						email = new EmailSender(senderHost, senderUser, senderPass);
					
					sendEmail(settings.get("debugRecipient"),  //$NON-NLS-1$
							"SMS TO: " + toNumber, body); //$NON-NLS-1$
				}else
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
		
		if(insertedId != -1){
			
			if(preview.length() > 50)
				preview = preview.substring(0, 47) + "..."; //$NON-NLS-1$
			
			try{
				
				String SQL = "INSERT INTO `messages` (`owner`, `assigned_user`, " + //$NON-NLS-1$
						"`type`, `direction`, `created_by`, `status`, `preview`) " + //$NON-NLS-1$
						"VALUES (?, ?, ?, ?, ?, ?, ?)"; //$NON-NLS-1$
				
				insertMessage = database.getConnection().prepareStatement(SQL);
				
				insertMessage.setInt(1, getContactId(Integer.parseInt(messageId)));//owner
				insertMessage.setInt(2, Integer.parseInt(userId));//Assign to user who sent this
				insertMessage.setString(3, type); //Type
				insertMessage.setString(4, "O"); //Direction //$NON-NLS-1$
				insertMessage.setInt(5, Integer.parseInt(userId)); //Created By
				insertMessage.setString(6, "V"); //status V ==> Unsent Manual  //$NON-NLS-1$
				insertMessage.setString(7, preview); //preview
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
				"UPDATE `messages` SET `status` = '" + status +   //$NON-NLS-1$
					"' WHERE `id` = " + outgoingMessageId,  //$NON-NLS-1$
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
		
		String SQL = "SELECT `" + field + "` FROM `contacts` WHERE `id` = " + id;  //$NON-NLS-1$//$NON-NLS-2$
		
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

	/**
	 * Gets the footer for the given contact via its messageId
	 * @param messageId Message from a contact to lookup
	 * @return Footer to be appended to outgoing message
	 */
	private String getFooter(String messageId) {
		
		String footer = ""; //$NON-NLS-1$
		
		if(footers == null)
			footers = new Footers(database, templateBasePath);
		
		int contactId = getContactId(Integer.parseInt(messageId));
		
		//Get the language for the given contact
		String SQL = "SELECT `languages.mappedTo` FROM `contacts` " + //$NON-NLS-1$
				"INNER JOIN `languages` " + //$NON-NLS-1$
				"ON `contacts`.`language_id` = `languages`.`id` " + //$NON-NLS-1$
				"WHERE `contacts`.`id` = " + contactId; //$NON-NLS-1$
		
		Statement language = null;
		ResultSet results = null;
		int mappedId = -1;
		
		try{
			
			language = database.getConnection().createStatement();
			
			if(language.execute(SQL)){
				
				results = language.getResultSet();
				
				while(results.next())
					mappedId = results.getInt("mappedTo"); //$NON-NLS-1$
					
			}
			
		}catch(SQLException e){
			
			LOGGER.severe("Error looking up language mapped to for contact " +  //$NON-NLS-1$
					contactId);
			e.printStackTrace();
			
		}finally{
			
			close(language, results);
			
		}
		
		if(mappedId != -1){
			
			//Get the language that the contacts language is mapped to
			SQL = "SELECT `language` FROM `languages` WHERE `id` = " + mappedId; //$NON-NLS-1$
			
			try{
				
				language = database.getConnection().createStatement();
				
				if(language.execute(SQL)){
					
					results = language.getResultSet();
					
					while(results.next())//get the footer for this language
						footer = footers.getEmailFooter(
								results.getString("language").toLowerCase()); //$NON-NLS-1$
						
				}
				
			}catch(SQLException e){
				
				LOGGER.severe("Error looking up language name for mappedID " +  //$NON-NLS-1$
						mappedId);
				e.printStackTrace();
				
			}
			
		}
		
		return footer;
		
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
	 *   TEMPLATE_PATH [DEBUG]
	 */
	public static void main(String[] args){
		
		boolean debug = false;
		
		if(args.length == 13)
			debug = true;
		
		if(args.length == 12 || args.length == 13){
			
			ManualSender manual = new ManualSender(args[0], args[1], args[2],
					args[3], args[4], args[5], args[6], args[7], args[8], 
					args[9], args[10], args[11], debug);
			
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
					"MAILUSER MAILPASS EMAILSTOREPATH TEMPLATE_PATH [DEBUG]"); //$NON-NLS-1$
			System.exit(1);
			
		}
		
	}

}
