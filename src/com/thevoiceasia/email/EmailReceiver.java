package com.thevoiceasia.email;

/* GENERAL */
import java.io.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.*;
import java.util.*;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

/* EMAIL */
import com.sun.mail.pop3.*;

import javax.mail.*;
import javax.mail.internet.MimeMultipart;

import org.apache.commons.validator.routines.EmailValidator;

/* TVA STUFF */
import com.thevoiceasia.database.*;
import com.thevoiceasia.html.HTML2Text;
import com.thevoiceasia.sms.XpressMS;


public class EmailReceiver extends Thread{

	/* STATIC SETTINGS */
	private static final int EMAIL_CHECK_PERIOD = 60; //Time to check for emails in seconds
	private static final Logger LOGGER = Logger.getLogger("com.thevoiceasia"); //$NON-NLS-1$
	private static final Level LEVEL = Level.INFO;//Logging level of this class
	private static final boolean DEBUG_MODE = false;
	
	/* CLASS VARS */
	private DatabaseHelper database = null;
	private String MAIL_SERVER, MAIL_USER, MAIL_PASSWORD, ARCHIVE_PATH;
	private boolean archiveValid = false;
	
	/**
	 * Receives Email from the given email inbox (no subfolders)
	 * @param dbHost MySQL Host
	 * @param dbUser MySQL Username
	 * @param dbPass MySQL Password
	 * @param dbBase MySQL Database
	 * @param mailHost Email Host
	 * @param mailUser Email User
	 * @param mailPass Email Password
	 * @param emailStorePath Path to where email content should be archived
	 */
	public EmailReceiver(String dbHost, String dbUser, String dbPass, String dbBase, String mailHost,
			String mailUser, String mailPass, String emailStorePath){
		
		database = new DatabaseHelper(dbHost, dbBase, dbUser, dbPass);
		
		MAIL_SERVER = mailHost;
		MAIL_USER = mailUser;
		MAIL_PASSWORD = mailPass;
		ARCHIVE_PATH = emailStorePath;
	
		File directory = new File(ARCHIVE_PATH);
		
		if(directory.exists() && directory.isDirectory() && directory.canWrite())
			archiveValid = true;
		
		setupLogging();
		
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
	 * Runnable start point
	 */
	public void run(){
		
		boolean run = true;
		
		LOGGER.info("ContactResponse-Importer: Started on inbox " + MAIL_USER); //$NON-NLS-1$
		
		while(run){
		
			database.connect();
			
			receiveEmail(MAIL_SERVER, MAIL_USER, MAIL_PASSWORD, 50);
			
			database.disconnect();
			
			try{
				
				LOGGER.finest("ContactResponse-Importer: Sleeping..."); //$NON-NLS-1$
				sleep(EMAIL_CHECK_PERIOD * 1000);
				
			}catch(InterruptedException e){
				
				run = false;
				LOGGER.warning("ContactResponse-Importer: Interrupted!");
				
			}
			
		}
		
		LOGGER.info("ContactResponse-Importer:  Stopped"); //$NON-NLS-1$
		
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
	 * Entry point for Email Receiver
	 * @param args 8 arguments expected:
	 *   DB_HOST DB_USER DB_PASS DBASE MAIL_HOST MAIL_USER MAIL_PASS EMAIL_STORE_PATH
	 */
	public static void main(String[] args){
		
		if(args.length == 8){
			EmailReceiver emr = new EmailReceiver(args[0], args[1], args[2], args[3], args[4], args[5],
					args[6], args[7]);
			
			if(!emr.isValidArchive()){
				System.out.println("Email Store Path invalid, doesn't exist or has no write permissions: " + args[7]);
				System.exit(1);
			}
				
			emr.start();
		}else{
			System.out.println("USAGE: DBHOST DBUSER DBPASS DBASE MAILHOST MAILUSER MAILPASS EMAILSTOREPATH");
			System.exit(1);
		}
		
	}
	
	/**
	 * Gets the email address from the email line
	 * @param addressFromEmail - address from email
	 * @return 
	 */
	private String getAddress(String addressFromEmail){
		
		/*
		 * Two types of addresses:
		 * 1: someone@blah.com
		 * 2: someone <someone@blah.com>
		 * 3: 
		 */
		String address = null;
		
		addressFromEmail = addressFromEmail.trim();
		
		if(addressFromEmail.contains("<") && addressFromEmail.endsWith(">")){ //$NON-NLS-1$ //$NON-NLS-2$
			
			addressFromEmail = addressFromEmail.split("<")[1]; //$NON-NLS-1$
			address = addressFromEmail.split(">")[0]; //$NON-NLS-1$
			
		}else//Type 1 address
			address = addressFromEmail;
			
		return address.toLowerCase();
		
	}
	
	/**
	 * Returns address type
	 * @param addressFromEmail email address to check
	 * @return 1 = Plain email, 2 = Email Name + Email
	 */
	private int getAddressType(String addressFromEmail){
		
		int type = 1;
		
		addressFromEmail = addressFromEmail.trim();
		
		if(addressFromEmail.contains("<") && addressFromEmail.endsWith(">")) //$NON-NLS-1$ //$NON-NLS-2$
			type = 2;
		
		return type;	
		
	}
	
	/**
	 * Get name from Type 2 email
	 * @param addressFromEmail Address to parse
	 * @return Name or null if this is not a type 2
	 */
	private String getName(String addressFromEmail){
		
		/*
		 * Two types of addresses:
		 * 1: someone@blah.com
		 * 2: someone <someone@blah.com>
		 * 2a: "someone@blah.com" <someone@blah.com> 
		 */
		String name = null;
		
		addressFromEmail = addressFromEmail.trim();
		
		if(addressFromEmail.contains("<") && addressFromEmail.endsWith(">")){ //$NON-NLS-1$ //$NON-NLS-2$
			
			name = addressFromEmail.split("<")[0].trim();
		
			/* Sometimes email won't send with a "Name component"
			 * Most of the time it is Name <address>
			 * Other times its "Email Address" <address> so we need to check for this case here
			 */
			if(name.startsWith("\"") && name.endsWith("\"")){//Remove the pre/post "
				
				name = name.substring(1);
				name = name.substring(0, name.length() - 1);
				
			}
		
			EmailValidator ev = EmailValidator.getInstance(false);
			
			if(ev.isValid(name))//If what we're left with is a valid email, then this isn't a name
				name = null;
			
		}
		
		return name;
		
	}
	
	/**
	 * Receive Email loop
	 * @param pop3Host email server host name
	 * @param user email user
	 * @param password email password
	 * @param number max number of emails to read in one loop
	 */
	private void receiveEmail(String pop3Host, String user, String password, int number) {

		POP3Store emailStore = null;
		Folder emailFolder = null;
		
		try {
			
			Properties properties = new Properties();
			properties.setProperty("mail.pop3.ssl.socketFactory.class", "com.thevoiceasia.email.AlwaysTrustSSLContextFactory");  //$NON-NLS-1$//$NON-NLS-2$
			properties.setProperty("mail.pop3.ssl.socketFactory.port", "110"); //$NON-NLS-1$ //$NON-NLS-2$
			properties.setProperty("mail.pop3.starttls.enable", "true");  //$NON-NLS-1$//$NON-NLS-2$
			Session emailSession = Session.getDefaultInstance(properties);

			URLName url = new URLName("pop3", pop3Host, 110, "", user, password);  //$NON-NLS-1$//$NON-NLS-2$
			emailStore = (POP3Store) emailSession.getStore(url);
			LOGGER.finest("Connecting to server " + pop3Host + " as " + user); //$NON-NLS-1$ //$NON-NLS-2$
			emailStore.connect(user, password);
			LOGGER.finest("Connected..."); //$NON-NLS-1$
			
			LOGGER.finest("Opening INBOX"); //$NON-NLS-1$
			emailFolder = emailStore.getFolder("INBOX"); //$NON-NLS-1$
			emailFolder.open(Folder.READ_WRITE);
			LOGGER.finest("Opened..."); //$NON-NLS-1$
			
			int emailCount = emailFolder.getMessageCount();//number of messages in INBOX
			LOGGER.finest(emailCount + " messages found"); //$NON-NLS-1$
			
			if(emailCount > 0){
				
				//Loop through emails adding to DB
				LOGGER.info("Reading " + emailCount + " emails..."); //$NON-NLS-1$
				
				for(int i = emailCount; i > 0; i--){
					
					Message message = emailFolder.getMessage(i);
					
					if(!message.isSet(Flags.Flag.DELETED) && !message.isSet(Flags.Flag.SEEN)){
						
						LOGGER.finest("Email: " + i); //$NON-NLS-1$
						
						/* Deals with null pointer in recipients[0] */
						boolean skip = false;
						
						Address[] recipients = null;
						
						try{
							recipients = message.getAllRecipients();
						}catch(MessagingException e){
							
							LOGGER.warning("MESSAGING EXCEPTION: There was a problem getting this messages recipients, skipping message"); //$NON-NLS-1$
							skip = true;
							
						}
						
						if(!skip){//problem with this message so skip it
							
							String toAddress = null;
							
							if(recipients != null){
							
								int j = 0;
								
								while(toAddress == null && j < recipients.length){
								
									if(recipients[j] != null)
										toAddress = recipients[j].toString();
									
								}
								
								toAddress = getAddress(toAddress);
								LOGGER.finest("Sent To: " + toAddress); //$NON-NLS-1$
								
							}
							
							//Do the same to get the senders address
							Address[] from = message.getFrom();
							
							String fromAddress = null;
							String contactName = null;
							
							if(from != null){
							
								int j = 0;
								
								while(fromAddress == null && j < from.length){
								
									if(from[j] != null)
										fromAddress = from[j].toString();
									
								}
								
								if(getAddressType(fromAddress) == 2)
									contactName = getName(fromAddress);
								
								fromAddress = getAddress(fromAddress);
								
								
								LOGGER.finest("From: " + fromAddress); //$NON-NLS-1$
								
							}
							
							Date headerDate = formatHeaderDate(message.getHeader("Received")[0]); //$NON-NLS-1$
							
							//Read message content and pick out email, name, content
							//and referrer so we know what form was used
							String messageContent = null;
							String subject = message.getSubject();
							
							if(subject == null)
								subject = "";
							
							if(message.getContent() instanceof MimeMultipart){
								
								MimeMultipart msgContent = (MimeMultipart)message.getContent();
								
								if(!fromAddress.contains("@sms.xpressms.com"))
									messageContent = prefixSubject(getMessageContent(msgContent), subject);
								else
									messageContent = parseSMS(getMessageContent(msgContent));
								
							}else{
								
								if(!fromAddress.contains("@sms.xpressms.com"))
									messageContent = prefixSubject(message.getContent().toString(), subject);
								else
									messageContent = parseSMS(message.getContent().toString());
									
							}
								
							
							//All messages in inbox should be processed and then deleted
							if(!DEBUG_MODE){
								
								/* Actually do something with the email here
								 * receivedDate: Date when email was sent
								 * messageContent: Email hopefully in plain text (no attachments)
								 * toAddress: Email Address of original recipient
								 * fromAddress: Email Address of person who sent this message 
								 */
								if(messageContent != null){
									
									if(processEmail(headerDate, fromAddress, toAddress, contactName, messageContent))//Finished so mark message for deletion
										message.setFlag(Flags.Flag.DELETED, true);
									else
										message.setFlag(Flags.Flag.SEEN, true);
										
									if(message.isSet(Flags.Flag.DELETED))
										LOGGER.finest("Marked for Deletion"); //$NON-NLS-1$
									
								}else
									LOGGER.warning("Unable to get message content for " + fromAddress);
								
							}else
								LOGGER.info(fromAddress + "/" + toAddress + "\n" + messageContent);
							
						}
					}
					
				}
				
				LOGGER.finest("Finished reading emails"); //$NON-NLS-1$
				
			}else
				LOGGER.finest("Inbox Empty, nothing to do");
			
		} catch (NoSuchProviderException e) {
			
			e.printStackTrace();
			LOGGER.warning("NoSuchProviderException");
			
		} catch (MessagingException e) {
			
			e.printStackTrace();
			LOGGER.warning("MessagingException");
			
		} catch (IOException e) {
		
			LOGGER.warning("Error getting message content"); //$NON-NLS-1$
			e.printStackTrace();
			
		} finally {
			
			if(emailFolder != null && emailStore != null){
				
				LOGGER.finest("Closing connections..."); //$NON-NLS-1$
				
				try{
					
					emailFolder.close(true);
					emailStore.close();
					
				}catch(MessagingException e){
					
					LOGGER.severe("Failed to close email store and commit changes");
					
				}
				
				LOGGER.finest("Connections closed"); //$NON-NLS-1$
				
			}
			
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
	 * Gets plain text from a multipart message
	 * @param msgContent MultiPart Message to check
	 * @return plain text of message
	 */
	private String getMessageContent(MimeMultipart msgContent) {
		
		String text = null;
		String html = null;
		
		try{
			
			int messagePartCount = msgContent.getCount();
			boolean found = false;
			int i = 0;
			
			while(i < messagePartCount && !found){//Loop through message sections
			
				Part messageBody = msgContent.getBodyPart(i);
					
				if(messageBody.isMimeType("text/plain")){
						
					text = messageBody.getContent().toString();
					found = true;
						
				}else if(text == null && html == null){
					
					if(messageBody.isMimeType("text/html"))
						html = HTML2Text.convert(messageBody.getContent().toString());//Use Jericho to render to plain text
					else if(messageBody.isMimeType("multipart/alternative")){
						
						text = getMessageContent((MimeMultipart)messageBody.getContent());
						found = true;
						
					}
						
				}
				
				i++;
					
			}
				
			if(!found && html != null){//if we didn't find plain text but we have html use it
					
				text = html;
				found = true;
					
			}
			
		}catch(MessagingException e){
			
			e.printStackTrace();
			LOGGER.warning("MessagingException");
			
		}catch(IOException e){
			
			LOGGER.warning("Error getting message content"); //$NON-NLS-1$
			e.printStackTrace();
			
		}
		
		return text;
		
	}

	/**
	 * Processes the email details and puts it into the messages table, archiving the content
	 * to the message archive
	 * @param sentDate Date email was sent
	 * @param fromAddress Email address this was sent from
	 * @param toAddress Address this email was sent to
	 * @param contactName Name of contact or null if none
	 * @return true if processed successfully
	 */
	private boolean processEmail(Date sentDate, String fromAddress, String toAddress,
			String contactName, String messageContent) {
		
		boolean success = false;
		
		String type = "E";
		boolean sms = false;
		
		if(fromAddress.contains("@sms.xpressms.com")){
		
			sms = true;
			type = "S";
			
		}
		
		int existingContactId = getContactId(fromAddress, contactName, sms);
		String preview = messageContent.replaceAll("\n", "  ").replaceAll("\r", "");
		
		if(preview.startsWith("S:"))//Remove Subject prefix don't need it in preview
			preview = preview.substring(2);
		
		if(preview.length() > 50)
			preview = preview.substring(0, 47) + "...";
		
		String SQL = "INSERT INTO `messages` (`owner`, `type`, `direction`, `preview`) VALUES (?, ?, ?, ?)";
		
		Connection mysql = database.getConnection();
		PreparedStatement insertMessage = null;
		ResultSet insertIDs = null;
		
		try{
			
			//Bind all variables to statement
			insertMessage = mysql.prepareStatement(SQL, Statement.RETURN_GENERATED_KEYS);
			insertMessage.setInt(1, existingContactId);
			insertMessage.setString(2, type);
			insertMessage.setString(3, "I");
			insertMessage.setString(4, preview);
			
			//Execute it
			int rows = insertMessage.executeUpdate();
			
			if(rows > 0){
			
				LOGGER.finest("Successfully added message from " + fromAddress);
				insertIDs = insertMessage.getGeneratedKeys();
				
				while(insertIDs.next()){
				
					int id = insertIDs.getInt(1);
					success = writeMessageToArchive(id, messageContent);
					
					if(!success){
						
						LOGGER.warning("Error writing to archive, removing message from database: " + fromAddress);
						removeMessageFromDatabase(id);
						
					}
				
				}
				
				if(success)
					LOGGER.finest("Successfully archived message from " + fromAddress);
				else
					LOGGER.severe("Failed to archive message from " + fromAddress);
				
			}
			
		}catch(SQLException e){
			
			e.printStackTrace();
			LOGGER.severe("SQL Error while inserting message");
			
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
	 * Deletes a message from the message table.
	 * Called when writeToArchive() fails so we don't have a
	 * message in the table that doesn't exist in archive
	 * @param id id to delete
	 */
	private void removeMessageFromDatabase(int id) {
		
		LOGGER.info("Deleting message ID: " + id);
		String SQL = "DELETE FROM `messages` WHERE `id` = " + id;
		
		Connection mysql = database.getConnection();
		Statement deleteMessage = null;
		
		try{
			
			deleteMessage = mysql.createStatement();
			
			//Execute it
			deleteMessage.executeUpdate(SQL);
			
		}catch(SQLException e){
			
			e.printStackTrace();
			LOGGER.severe("SQL Error while inserting message");
			
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
	 * Writes the message content to a text file in the ARCHIVE location
	 * @param fileID name of file and message record id it relates to
	 * @param messageContent Content to go in the file
	 * @return true if we succeeded in writing the file
	 */
	private boolean writeMessageToArchive(int fileID, String messageContent) {
		
		boolean created = false;
		
		String path = ARCHIVE_PATH + System.getProperty("file.separator") + fileID;
		
		File temp = new File(path);
		
		try {
			if(!temp.exists() && temp.createNewFile()){
				
				try{
					
					PrintWriter writer = new PrintWriter(path, "UTF-8");
					writer.print(messageContent);
					writer.close();
					
					created = true;
					
				}catch(UnsupportedEncodingException e){
					
					e.printStackTrace();
					LOGGER.severe("Can't encode UTF8 file for " + path);
					
				}catch(FileNotFoundException e){
					
					e.printStackTrace();
					LOGGER.severe("Can't write file " + path);
					
				}
				
			}
		} catch (IOException e) {
			
			e.printStackTrace();
			LOGGER.severe("Can't create new file " + path);
			
		}
		
		return created;
		
	}

	/**
	 * Gets a contact id from the contacts table, if no contact exists, it will be created
	 * @param fromAddress Address to lookup
	 * @param name Name of contact or null if none
	 * @param smsMessage if this is an sms message it searches by phone and not email
	 * @return id of existing or new contact
	 */
	private int getContactId(String fromAddress, String name, boolean smsMessage) {
		
		int id = -1;
		
		String SQL = "SELECT `id`, `name` FROM `contacts` WHERE `email` = ?";
		
		if(smsMessage){
			
			SQL = "SELECT `id` FROM `contacts` WHERE `phone` LIKE ?";
			fromAddress = "%" + fromAddress.split("@")[0];
			
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
					
					if(name != null && contactIDs.getString(2).equals("Unknown"))
						updateName(id, name);
					
				}
				
				if(id != -1)
					LOGGER.finest("Found contact ID For: " + fromAddress);
				else{
					if(smsMessage)
						fromAddress = fromAddress.substring(1);//Remove prefix of % for like lookup
					
					id = createNewContact(fromAddress, name, smsMessage);
				}
			}
			
		}catch(SQLException e){
			
			e.printStackTrace();
			LOGGER.severe("SQL Error while looking up contact ID for " + fromAddress);
			
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
	 * Sets the name of the given contact
	 * @param id id of record to change
	 * @param name name to change it to
	 * @return true if successfully updated
	 */
	private boolean updateName(int id, String name) {
		
		boolean success = false;
		String SQL = "UPDATE `contacts` SET `name` = ?, `updated` = ? WHERE `id` = ?";
		
		LOGGER.info("Updating Name for " + id + " to " + name);
		
		
		Connection mysql = database.getConnection();
		PreparedStatement updateContact = null;
		
		try{
			
			//Bind all variables to statement
			updateContact = mysql.prepareStatement(SQL);
			updateContact.setString(1, name);
			updateContact.setString(2, new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()));
			updateContact.setInt(3, id);
			
			//Execute it
			int rows = updateContact.executeUpdate();
			
			if(rows > 0)
				success = true;
				
		}catch(SQLException e){
			
			e.printStackTrace();
			LOGGER.severe("SQL Error while updating name on contact " + id + " to " + name);
			
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
	 * Creates a new contact
	 * @param fromAddress either phone or sms number to create with
	 * @param name Name of contact to save
	 * @param smsMessage if sms then we'll insert the fromAddress into the phone field
	 * else we'll just enter it as an email address
	 * @return id of inserted contact record
	 */
	private int createNewContact(String fromAddress, String name, boolean smsMessage) {
		
		int id = -1;
		
		String SQL = "INSERT INTO `contacts` (`email`) VALUES (?)";
		
		if(name != null && !smsMessage)
			SQL = "INSERT INTO `contacts` (`email`, `name`) VALUES (?, ?)";
		else if(name == null && smsMessage)
			SQL = "INSERT INTO `contacts` (`phone`) VALUES (?)";
		else if(name != null && smsMessage) //I think name will always be null for an SMS but just in case
			SQL = "INSERT INTO `contacts` (`phone`, `name`) VALUES (?, ?)";
			
		LOGGER.info("Creating new contact for " + fromAddress);
		
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
				
			}
			
		}catch(SQLException e){
			
			e.printStackTrace();
			LOGGER.severe("SQL Error while creating new contact for " + fromAddress);
			
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
	 * Formats the date received in the header into a java.util.Date
	 * @param headerReceived
	 * @return Date of the header
	 */
	private Date formatHeaderDate(String headerReceived) {
		
		Date receivedDate = null;//Just in case there is a problem we'll default to now
		
		try{
			
			String[] lines = headerReceived.split("\n"); //$NON-NLS-1$
			
			String dateTime = null;
			
			if(lines.length > 1)
				dateTime = lines[1].split("\\+")[0].trim(); //$NON-NLS-1$
			else
				dateTime = headerReceived.split("\\+")[0].trim(); //$NON-NLS-1$
				
			if(dateTime.contains(";")) //$NON-NLS-1$
				dateTime = dateTime.split("; ")[1]; //$NON-NLS-1$
			
			SimpleDateFormat sdf = new SimpleDateFormat("E, d MMM yyyy HH:mm:ss"); //$NON-NLS-1$
			
			try{
				
				receivedDate = sdf.parse(dateTime);
				
			}catch(ParseException e){
				
			}
			
		}catch(Exception e){//For weird header stuff I want to catch and debug
			
			LOGGER.warning("Error with header: " + headerReceived);
			
		}
		
		return receivedDate;
		
	}
	
}
