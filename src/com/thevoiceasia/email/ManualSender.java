package com.thevoiceasia.email;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.UnknownHostException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.logging.FileHandler;

import javax.mail.MessagingException;

import com.thevoiceasia.messages.Footers;
import com.thevoiceasia.messages.MessageArchiver;
import com.thevoiceasia.sms.SMSSender;

public class ManualSender extends MessageArchiver{

	private String senderHost =  null, senderUser = null, senderPass = null,
				templateBasePath = null;
	private HashMap<String, String> settings = null;
	private HashMap<String, String> users = null;
	private EmailSender email = null;
	private SMSSender sms = null;
	private boolean DEBUG = false;
	private Footers footers = null;
	
	/**
	 * Works on the incoming inbox for manual replies, also responsible for 
	 * timing out replies and sending out the responses for manual replies
	 * @param host Database Host for CRS
	 * @param user User Name for CRS Database
	 * @param pass Password for CRS Database
	 * @param dbase Database Name for CRS Database
	 * @param archivePath Path to archive the messages on incoming inbox
	 * @param senderHost Host Name for outgoing emails
	 * @param senderUser User Name for outgoing emails
	 * @param senderPassword Password for outgoing emails
	 * @param templatePath Template path also used for email footers
	 * @param debug if true, will only send sms/emails to the debug recipient
	 *   as an email
	 */
	public ManualSender(String host, String user, String pass, String dbase,
			String archivePath, String senderHost, String senderUser, 
			String senderPassword, String templatePath, boolean debug){
		
		super(host, user, pass, dbase, archivePath);
		
		this.senderHost = senderHost;
		this.senderUser = senderUser;
		this.senderPass = senderPassword;
		this.templateBasePath = templatePath;
		
		this.DEBUG = debug;
		
		setupLogging();
		database.connect();
		readSettings();
		readUsers();
		
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
	 * Sends an SMS to the given phone number
	 * @param phoneNumber
	 * @param text
	 * @return
	 */
	private boolean sendSMS(String phoneNumber, String text){
		
		if(sms == null)
			sms = new SMSSender(database);
		
		return sms.sendSMS(phoneNumber, text);
		
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
			
			if(email == null)
				email = new EmailSender(senderHost, senderUser, senderPass);
				
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
	
	public void getEmails(){
		
		//Read any V status messages in messages table
		//Turn back into an email/sms and send as appropriate
		String SQL = "SELECT `id`, `owner`, `type`, `created_by` FROM " + //$NON-NLS-1$
				"`messages` WHERE `direction` = 'O' AND `status` = 'V'"; //$NON-NLS-1$
		
		Statement manualEmails = null;
		ResultSet results = null;
		
		try{
			
			manualEmails = database.getConnection().createStatement();
			
			if(manualEmails.execute(SQL)){
				
				results = manualEmails.getResultSet();
				
				while(results.next()){
					
					String type = results.getString("type"); //$NON-NLS-1$
					
					if(type.equals("E")){ //$NON-NLS-1$
						
						//Email
						String toEmail = getContactEmail(
								results.getInt("owner")); //$NON-NLS-1$
						
						String[] temp = readFromArchive(results.getInt("id")); //$NON-NLS-1$
						String subject = ""; //$NON-NLS-1$
						String body = ""; //$NON-NLS-1$
						
						if(temp.length == 1)//only body no subject
							body = temp[0];
						else if(temp.length == 2){//subject + body
							
							subject = temp[0];
							body = temp[1];
							
						}
						
						//Append Footer
						body = body + "\n" + getFooter(results.getInt("owner"));  //$NON-NLS-1$//$NON-NLS-2$
						
						if(!DEBUG){
							
							if(sendEmail(toEmail, subject, body))
								updateMessageStatus(results.getInt("id"), "S"); //$NON-NLS-1$ //$NON-NLS-2$
							else
								updateMessageStatus(results.getInt("id"), "F"); //$NON-NLS-1$ //$NON-NLS-2$
							
						}else{
								
							body = "Original Recipient: " + toEmail + "\n" + body; //$NON-NLS-1$ //$NON-NLS-2$
							toEmail = settings.get("debugRecipient"); //$NON-NLS-1$
							sendEmail(toEmail, subject, body);
							
						}
						
					}else if(type.equals("S")){ //$NON-NLS-1$
						
						// SMS
						String phone = getContactPhone(
								results.getInt("owner")); //$NON-NLS-1$
						
						String[] temp = readFromArchive(results.getInt("id")); //$NON-NLS-1$
						
						String body = ""; //$NON-NLS-1$
						
						if(temp.length == 1)//should always be 1 its an SMS so no subject
							body = temp[0].trim();
						
						//Append User Name
						String user = users.get(results.getString("created_by")); //$NON-NLS-1$
						
						if(user.contains(" ")) //$NON-NLS-1$
							user = user.split(" ")[0]; //$NON-NLS-1$
						
						body = body + " --" + user;  //$NON-NLS-1$
						
						if(!DEBUG){
							
							if(sendSMS(phone, body))
								updateMessageStatus(results.getInt("id"), "S"); //$NON-NLS-1$ //$NON-NLS-2$
							else
								updateMessageStatus(results.getInt("id"), "F"); //$NON-NLS-1$ //$NON-NLS-2$
							
						}else{
							
							//Send DEBUG email instead
							String subject = "SMS TO: " + phone; //$NON-NLS-1$
							sendEmail(settings.get("debugRecipient"), subject,  //$NON-NLS-1$
									body);
							
						}
					}
					
				}
				
			}
			
		}catch(SQLException e){
			
			LOGGER.severe("Error getting manual replies from messages table"); //$NON-NLS-1$
			e.printStackTrace();
			
		}finally{
			
			close(manualEmails, results);
			
		}
		
	}
	
	
	/**
	 * Reads the given file and returns the content
	 * @param path path to read
	 * @return Array[0] = content or in emails case 0 = subject, 1 = content
	 */
	private String[] readArchiveFile(String path){
		
		int strings = 1;
		
		if(path.contains("email")) //$NON-NLS-1$
			strings = 2;
		
		String[] returnValues = new String[strings];
		
		for(int i = 0; i < strings; i++)
			returnValues[i] = ""; //$NON-NLS-1$
		
		File template = new File(path);
		BufferedReader reader = null;
		
		if(template.exists() && template.canRead()){
			
			try{
				
				reader = new BufferedReader(
						new FileReader(template));
				
				boolean done = false;
				boolean first = true;
				
				while(!done){
					
					String line = reader.readLine();
					
					if(line == null)
						done = true;
					else{
						
						if(first && strings == 2 && line.startsWith("S:")){ //$NON-NLS-1$
							
							first = false;
							returnValues[0] = line.substring(2);//Subject
							
						}else
							returnValues[returnValues.length - 1] += line + "\n"; //$NON-NLS-1$
							
					}
					
				}
				
			}catch(FileNotFoundException e){
				
				LOGGER.severe("Template file not found " + template.getName()); //$NON-NLS-1$
				e.printStackTrace();
				
			}catch(IOException e){
				
				LOGGER.severe("Template IO Error " + template.getName()); //$NON-NLS-1$
				e.printStackTrace();
				
			}finally{
				
				if(reader != null){
					
					try{
						reader.close();
					}catch(Exception e){}
					
					reader = null;
					
				}
				
			}
			
		}
		
		return returnValues;
		
	}
	
	/**
	 * Reads the given message id from the archive
	 * @param id
	 * @return array[0] subject array[1] body (or just array[0] body)
	 */
	private String[] readFromArchive(int id) {
		
		return readArchiveFile(ARCHIVE_PATH +
				System.getProperty("file.separator") +  //$NON-NLS-1$
				id);
		
	}

	/**
	 * Updates contact messages and the outgoing reply with the required status
	 * @param messageId ID of the message to set the status
	 * @param status Status to set outgoing message to
	 */
	private boolean updateMessageStatus(int messageId, String status) {
		
		boolean updated = true;
		
		String SQL = "UPDATE `messages` SET `status` = '" + status +   //$NON-NLS-1$
			  		 "' WHERE `id` = " + messageId;  //$NON-NLS-1$
			
		Statement updateMessages = null;
		
		try{
			
			updateMessages = database.getConnection().createStatement();
			
			updateMessages.execute(SQL);
					
			if(updateMessages.getUpdateCount() < 1){
						
				LOGGER.warning("Could not update messages table: " +  //$NON-NLS-1$
								SQL);
						updated = false;
						
			}
					
		}catch(SQLException e){
			
			LOGGER.severe("Error updating message status: " + SQL); //$NON-NLS-1$
			e.printStackTrace();
			updated = false;
			
		}finally{
			
			close(updateMessages, null);
			
		}
		
		return updated;
		
	}

	/**
	 * Gets the contacts phone from the given contact Id
	 * @param contactId
	 * @return
	 */
	private String getContactPhone(int contactId) {
		
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
	 * @param contactId
	 * @return
	 */
	private String getContactEmail(int contactId) {
		
		return getContactValue("email", contactId); //$NON-NLS-1$
		
	}

	/**
	 * Gets the footer for the given contact via its messageId
	 * @param messageId Message from a contact to lookup
	 * @return Footer to be appended to outgoing message
	 */
	private String getFooter(int contactId) {
		
		String footer = ""; //$NON-NLS-1$
		
		if(footers == null)
			footers = new Footers(database, templateBasePath);
		
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
	 * Set the Logger object
	 */
	private void setupLogging(){
		
		LOGGER.setLevel(LEVEL);
		
		try{
			
			LOGGER.addHandler(new FileHandler(
					"contactresponsemanualsender.log")); //$NON-NLS-1$
			
		}catch(IOException e){
			
			e.printStackTrace();
			
		}
		
	}
	
	/**
	 * Entry point for Manual Sender
	 * @param args 10 arguments expected:
	 *   DB_HOST DB_USER DB_PASS DBASE \ 
	 *   EMAIL_STORE_PATH SEND_EMAIL_HOST SEND_EMAIL_USER SEND_EMAIL_PASS \
	 *   TEMPLATE_PATH [DEBUG]
	 */
	public static void main(String[] args){
		
		boolean debug = false;
		
		if(args.length == 10)
			debug = true;
		
		if(args.length == 9 || args.length == 10){
			
			ManualSender manual = new ManualSender(args[0], args[1], args[2],
					args[3], args[4], args[5], args[6], args[7], args[8], debug);
			
			if(!manual.isValidArchive()){
				
				System.out.println("Email Store Path invalid, doesn't exist " + //$NON-NLS-1$
						"or has no write permissions: " + args[7]); //$NON-NLS-1$
				System.exit(1);
				
			}
			
			LOGGER.info("ManualSender: Started"); //$NON-NLS-1$
				
			manual.connectToDB();
			manual.getEmails();
			manual.disconnectFromDB();
			
		}else{
			
			System.out.println("USAGE: DBHOST DBUSER DBPASS DBASE MAILHOST " + //$NON-NLS-1$
					"MAILUSER MAILPASS EMAILSTOREPATH TEMPLATE_PATH [DEBUG]"); //$NON-NLS-1$
			System.exit(1);
			
		}
		
	}

}
