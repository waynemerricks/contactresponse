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
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
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
	 * Gets a contact id from the contacts table, if no contact exists, it will be created
	 * @param fromAddress Address to lookup
	 * @param name Name of contact or null if none
	 * @param smsMessage if this is an sms message it searches by phone and not email
	 * @return id of existing or new contact
	 */
	private int getContactId(String fromAddress, String name, 
			boolean smsMessage, Contact contact) {
		
		int id = -1;
		
		String SQL = "SELECT `id`, `name` FROM `contacts` WHERE `email` = ?"; //$NON-NLS-1$
		
		if(smsMessage){
			
			SQL = "SELECT `id` FROM `contacts` WHERE `phone` LIKE ?"; //$NON-NLS-1$
			fromAddress = "%" + fromAddress.split("@")[0];  //$NON-NLS-1$//$NON-NLS-2$
			
		}
		
		Connection mysql = database.getConnection();
		PreparedStatement selectContact = null;
		ResultSet contactIDs = null;
		
		try{
			
			//Bind all variables to statement
			selectContact = mysql.prepareStatement(SQL);
			selectContact.setString(1, fromAddress);
			
			//Execute it
			if(selectContact.execute()){
			
				contactIDs = selectContact.getResultSet();
				
				while(contactIDs.next()){
					
					id = contactIDs.getInt(1);
					
					if((name != null || contact.name != null) && contactIDs.getString(2).equals("Unknown")) //$NON-NLS-1$
						updateName(id, name, contact);
					
				}
				
				if(id != -1)
					LOGGER.finest("Found contact ID For: " + fromAddress); //$NON-NLS-1$
				else{
					if(smsMessage)
						fromAddress = fromAddress.substring(1);//Remove prefix of % for like lookup
					
					id = createNewContact(fromAddress, name, smsMessage, 
							contact);
					
				}
			}
			
		}catch(SQLException e){
			
			e.printStackTrace();
			LOGGER.severe("SQL Error while looking up contact ID for " + fromAddress); //$NON-NLS-1$
			
		}finally{
			
			if(contactIDs != null){
            	try{
            		contactIDs.close();
            		contactIDs = null;
            	}catch(Exception e){}
            }
            	
            if(selectContact != null){//Close Statement
            	try{
            		selectContact.close();
            		selectContact = null;
            	}catch(Exception e){}
            }
        	
		}
		
		return id;
		
	}
	
	/**
	 * Creates a new contact
	 * @param fromAddress either phone or sms number to create with
	 * @param name Name of contact to save
	 * @param smsMessage if sms then we'll insert the fromAddress into the phone field
	 * @param contact Contact object that may be null or may have useful info
	 * else we'll just enter it as an email address
	 * @return id of inserted contact record
	 */
	private int createNewContact(String fromAddress, String name, 
			boolean smsMessage, Contact contact) {
		
		int id = -1;
		
		String SQL = "INSERT INTO `contacts` (`email`) VALUES (?)"; //$NON-NLS-1$
		
		if(name != null && !smsMessage)
			SQL = "INSERT INTO `contacts` (`email`, `name`) VALUES (?, ?)"; //$NON-NLS-1$
		else if(name == null && smsMessage)
			SQL = "INSERT INTO `contacts` (`phone`) VALUES (?)"; //$NON-NLS-1$
		else if(name != null && smsMessage) //I think name will always be null for an SMS but just in case
			SQL = "INSERT INTO `contacts` (`phone`, `name`) VALUES (?, ?)"; //$NON-NLS-1$
			
		LOGGER.info("Creating new contact for " + fromAddress); //$NON-NLS-1$
		
		Connection mysql = database.getConnection();
		PreparedStatement insertContact = null;
		ResultSet contactIDs = null;
		
		try{
			
			//Bind all variables to statement
			insertContact = mysql.prepareStatement(SQL, Statement.RETURN_GENERATED_KEYS);
			insertContact.setString(1, fromAddress);
			
			if(name != null)
				insertContact.setString(2, name);
			
			//Execute it
			int rows = insertContact.executeUpdate();
			
			if(rows > 0){
			
				contactIDs = insertContact.getGeneratedKeys();
				
				while(contactIDs.next())
					id = contactIDs.getInt(1);
				
				if(contact != null)
					updateContact(id, contact, mysql);
				
			}
			
		}catch(SQLException e){
			
			e.printStackTrace();
			LOGGER.severe("SQL Error while creating new contact for " + fromAddress); //$NON-NLS-1$
			
		}finally{
			
			if(contactIDs != null){
            	try{
            		contactIDs.close();
            		contactIDs = null;
            	}catch(Exception e){}
            }
            	
            if(insertContact != null){//Close Statement
            	try{
            		insertContact.close();
            		insertContact = null;
            	}catch(Exception e){}
            }
        	
		}
		
		return id;
		
	}
	
	/**
	 * Sets the name of the given contact
	 * @param id id of record to change
	 * @param name name to change it to
	 * @return true if successfully updated
	 */
	private boolean updateName(int id, String name, Contact contact) {
		
		boolean success = false;
		
		if(name == null)//Use contact name as we must have had that as not null
			name = contact.name;
		
		String SQL = "UPDATE `contacts` SET `name` = ?, `updated` = ? WHERE `id` = ?"; //$NON-NLS-1$
		
		LOGGER.info("Updating Name for " + id + " to " + name); //$NON-NLS-1$ //$NON-NLS-2$
		
		Connection mysql = database.getConnection();
		
		PreparedStatement updateContact = null;
		
		try{
			
			//Bind all variables to statement
			updateContact = mysql.prepareStatement(SQL);
			updateContact.setString(1, name);
			updateContact.setString(2, new SimpleDateFormat(
					"yyyyMMddHHmmss").format(new Date())); //$NON-NLS-1$
			updateContact.setInt(3, id);
			
			//Execute it
			int rows = updateContact.executeUpdate();
			
			if(rows > 0)
				success = true;
			
			if(contact != null)
				updateContact(id, contact, mysql);
				
		}catch(SQLException e){
			
			e.printStackTrace();
			LOGGER.severe("SQL Error while updating name on contact " + id + //$NON-NLS-1$
					" to " + name);  //$NON-NLS-1$
			
		}finally{
			
			if(updateContact != null){//Close Statement
            	try{
            		updateContact.close();
            		updateContact = null;
            	}catch(Exception e){}
            }
        	
		}
		
		return success;
		
	}
	
	/**
	 * Update a contact with the information given
	 * @param id id of contact to update
	 * @param contact information to update it to
	 * @param mysql Read/Write Connection to DB
	 */
	private void updateContact(int id, Contact contact, Connection mysql) {
		
		String SQL = "UPDATE `contacts` SET `updated` = ? "; //$NON-NLS-1$
		
		Contact existing = getContact(id, mysql);
		
		if(contact.phoneNumber != null && existing.phoneNumber == null)
			SQL += " `phone` = ? "; //$NON-NLS-1$
		
		if(contact.gender != null && existing.gender == null)
			SQL += " `gender` = ? "; //$NON-NLS-1$
		
		SQL += "WHERE `id` = ?"; //$NON-NLS-1$
		
		if(SQL.length() > 50){//50 is the length without any fields so don't update
			
			LOGGER.info("Updating Contact with Form info"); //$NON-NLS-1$
			
			PreparedStatement updateContact = null;
			
			try{
				
				//Bind all variables to statement
				updateContact = mysql.prepareStatement(SQL);
				updateContact.setString(1, new SimpleDateFormat(
						"yyyyMMddHHmmss").format(new Date())); //$NON-NLS-1$
				
				int nextIndex = 2;
				
				if(contact.phoneNumber != null && existing.phoneNumber == null){
			
					updateContact.setString(nextIndex, contact.phoneNumber);
					nextIndex++;
					
				}
				
				if(contact.gender != null && existing.gender == null){
			
					updateContact.setString(nextIndex, contact.gender);
					nextIndex++;
					
				}
				
				updateContact.setInt(nextIndex, id);
				
				//Execute it
				updateContact.executeUpdate();
				
				updateCustomFields(contact, existing, mysql);
				
			}catch(SQLException e){
				
				e.printStackTrace();
				LOGGER.severe("SQL Error while updating contact " + id); //$NON-NLS-1$
				
			}finally{
				
				if(updateContact != null){//Close Statement
		        	try{
		        		updateContact.close();
		        		updateContact = null;
		        	}catch(Exception e){}
		        }
		    	
			}
		
		}
		
	}

	/**
	 * Helper method to change db lookup of "null" to java null
	 * @param check
	 * @return
	 */
	private String checkNull(String check){
	
		if(check.equalsIgnoreCase("null") || check.trim().length() == 0) //$NON-NLS-1$
			check = null;
		
		return check;
		
	}
	
	/**
	 * Gets a contact given its id
	 * @param id id to search for
	 * @param mysql read connection to DB
	 * @return Contact or null if not found
	 */
	private Contact getContact(int id, Connection mysql) {
		
		String SQL = "SELECT `name`, `gender`, `phone`, `email` FROM `contacts` " + //$NON-NLS-1$
				"WHERE id = ?"; //$NON-NLS-1$
		
		LOGGER.info("Getting Contact: " + id); //$NON-NLS-1$
		
		PreparedStatement selectContact = null;
		ResultSet contactResults = null;
		Contact c = null;
		
		try{
			
			//Bind all variables to statement
			selectContact = mysql.prepareStatement(SQL);
			selectContact.setInt(1, id);
			
			//Execute it
			selectContact.execute();
			contactResults = selectContact.getResultSet();
			
			while(contactResults.next()){
				
				c = new Contact();
				c.name = checkNull(contactResults.getString("name")); //$NON-NLS-1$
				c.gender = checkNull(contactResults.getString("gender")); //$NON-NLS-1$
				c.phoneNumber = checkNull(contactResults.getString("phone")); //$NON-NLS-1$
				c.email = checkNull(contactResults.getString("email")); //$NON-NLS-1$
				
			}
			
			if(c != null)
				getCustomFields(id, mysql);
			
		}catch(SQLException e){
			
			e.printStackTrace();
			LOGGER.severe("SQL Error while getting contact " + id); //$NON-NLS-1$
			
		}finally{
			
			if(selectContact != null){//Close Statement
	        	try{
	        		selectContact.close();
	        		selectContact = null;
	        	}catch(Exception e){}
	        }
	    	
		}
		
		return c;
		
	}

	
	private void updateCustomFields(Contact contact, Contact existing,
			Connection mysql) {
		// TODO Auto-generated method stub
		
	}

	private HashMap<String, String> getCustomFields(int id, Connection mysql) {
		//TODO close resultset on this and one of the other contact things
		HashMap<String, String> custom = new HashMap<String, String>();
		
		String[] tables = {"contact_values_large", "contact_values_medium",  //$NON-NLS-1$ //$NON-NLS-2$
				"contact_values_small"}; //$NON-NLS-1$
		
		for (int i = 0; i < tables.length; i++){
			
			String SQL = "SELECT `contact_fields`.`label`, `" + tables[i] +  //$NON-NLS-1$
				"`.`value` FROM `contact_fields` INNER JOIN `" + tables[i] + //$NON-NLS-1$
				"` ON `" + tables[i] + "`.`field_id` = `contact_fields`.`id` " + //$NON-NLS-1$ //$NON-NLS-2$
				"WHERE `" + tables[i] + "`.`owner_id` = ?"; //$NON-NLS-1$ //$NON-NLS-2$
			
			LOGGER.info("Getting custom for Contact: " + id); //$NON-NLS-1$
			
			PreparedStatement selectContact = null;
			ResultSet customResults = null;
			Contact c = null;
			
			try{
				
				//Bind all variables to statement
				selectContact = mysql.prepareStatement(SQL);
				selectContact.setInt(1, id);
				
				//Execute it
				selectContact.execute();
				customResults = selectContact.getResultSet();
				
				while(customResults.next()){
					
					custom.put(results.getString(""), value)
					c.name = checkNull(customResults.getString("name")); //$NON-NLS-1$
					c.gender = checkNull(customResults.getString("gender")); //$NON-NLS-1$
					c.phoneNumber = checkNull(customResults.getString("phone")); //$NON-NLS-1$
					c.email = checkNull(customResults.getString("email")); //$NON-NLS-1$
					
				}
				
				if(c != null)
					getCustomFields(id, mysql);
				
			}catch(SQLException e){
				
				e.printStackTrace();
				LOGGER.severe("SQL Error while getting contact " + id); //$NON-NLS-1$
				
			}finally{
				
				if(selectContact != null){//Close Statement
		        	try{
		        		selectContact.close();
		        		selectContact = null;
		        	}catch(Exception e){}
		        }
		    	
			}
			
		}
		
		return custom;
		
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
		
		Contact c = null;
		
		if(subject.endsWith("form submitted") && name == null) //$NON-NLS-1$
			c = parseFormInformation(body);
			
		if(c != null){
			
			if(c.name != null)
				name = c.name;
			
		}
			
		
		int existingContactId = getContactId(from, name, sms, c);
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

	/**
	 * Creates a Contact based on website contact forms
	 * @param body body of the email to parse
	 * @return null if we found nothing useful else a contact with set vars
	 * for whatever section we found (null if they are not set)
	 */
	private Contact parseFormInformation(String body) {
		
		String[] lines = body.split("\n"); //$NON-NLS-1$
		Contact c = new Contact();
		boolean addedInfo = false;
		
		for(String temp : lines){
		
			String[] field = temp.split(":"); //$NON-NLS-1$
			
			if(field[0].toLowerCase().startsWith("gender")){ //$NON-NLS-1$
				
				c.gender = field[1].trim().substring(0, 1).toUpperCase();
				addedInfo = true;
				
			}else if(field[0].toLowerCase().startsWith("name")){ //$NON-NLS-1$
				
				c.name = field[1].trim();
				addedInfo = true;
			
			}else if(field[0].toLowerCase().startsWith("email")){ //$NON-NLS-1$
				
				c.email = field[1].trim();//Don't really need this but whatever
				addedInfo = true;
				
			}else if(field[0].toLowerCase().startsWith("phone")){ //$NON-NLS-1$
				
				c.phoneNumber = field[1].trim();
				
				while(c.phoneNumber.startsWith("+")) //$NON-NLS-1$
					c.phoneNumber = c.phoneNumber.substring(1);
				
				addedInfo = true;
				
			}else if(field[0].toLowerCase().startsWith("house")){ //$NON-NLS-1$
				
				c.address = field[1].trim();
				
				for(int i = 2; i < field.length; i++)
					c.address += " " + field[i].trim(); //$NON-NLS-1$
				
				addedInfo = true;
				
			}else if(field[0].toLowerCase().startsWith("city")){ //$NON-NLS-1$
				
				if(c.address == null)
					c.address = field[1].trim();
				else
					c.address += "\n" + field[1].trim(); //$NON-NLS-1$
				
				c.city = field[1].trim();
				
				for(int i = 2; i < field.length; i++){
					
					c.address += " " + field[i].trim(); //$NON-NLS-1$
					c.city += " " + field[i].trim(); //$NON-NLS-1$
					
				}
				
				addedInfo = true;
				
			}else if(field[0].toLowerCase().startsWith("country")){ //$NON-NLS-1$
				
				if(c.address == null)
					c.address = field[1].trim();
				else
					c.address += "\n" + field[1].trim(); //$NON-NLS-1$
				
				c.country = field[1].trim();
				
				for(int i = 2; i < field.length; i++){
					
					c.address += " " + field[i].trim(); //$NON-NLS-1$
					c.country += " " + field[i].trim(); //$NON-NLS-1$
					
				}
				
				addedInfo = true;
				
			}
			
		}
		
		if(!addedInfo)
			c = null;
		
		return c;
		
	}

}
