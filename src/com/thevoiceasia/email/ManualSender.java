package com.thevoiceasia.email;

import java.io.IOException;
import java.net.UnknownHostException;
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
	
	//Read user id ==> Name map
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
		
		//TODO Archive the message going out
		String messageId = ""; //$NON-NLS-1$
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
			
		    String type = ""; //$NON-NLS-1$
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
			
			//TODO mark as sent
			updateMessageStatus(messageId, ""); //$NON-NLS-1$
			//M = Sent Manual Email
			//N = Sent Manual SMS
			//A = Archive older than where status = T
			
		}else{
			
			//TODO mark as error
			//G = Failed Manual Email
			//H = Failed Manual SMS
			
		}
		
		return success;
		
	}
	
	private void updateMessageStatus(String messageId, String string) {
		// TODO Auto-generated method stub
		
	}

	private String getContactPhone(String messageId) {
		// TODO Auto-generated method stub
		return null;
	}

	private String getContactEmail(String messageId) {
		// TODO Auto-generated method stub
		return null;
	}

	private String getFooter(String messageId) {
		// TODO Auto-generated method stub
		return null;
	}

	private String stripThunderbirdSig(String body) {
		// TODO Auto-generated method stub
		return null;
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
