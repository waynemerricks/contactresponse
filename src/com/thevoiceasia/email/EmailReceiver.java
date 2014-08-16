package com.thevoiceasia.email;

/* GENERAL */
import java.io.*;
import java.text.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/* EMAIL */
import com.sun.mail.pop3.*;

import javax.mail.*;
import javax.mail.internet.MimeMultipart;

/* TVA STUFF */
import com.thevoiceasia.database.*;


public class EmailReceiver extends Thread{

	/* STATIC SETTINGS */
	private static final String MAIL_TYPE = "pop3"; //$NON-NLS-1$
	private static final int EMAIL_CHECK_PERIOD = 60; //Time to check for emails in seconds
	private static final Logger LOGGER = Logger.getLogger("com.thevoiceasia"); //$NON-NLS-1$
	private static final Level LEVEL = Level.INFO;//Logging level of this class
	private static final boolean DEBUG_MODE = true;
	private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yy-MM-DD HH:mm:ss.SSS");
	
	/* CLASS VARS */
	private DatabaseHelper database = null;
	private String MAIL_SERVER, MAIL_USER, MAIL_PASSWORD;
	
	/**
	 * Receives Email from the given email inbox (no subfolders)
	 * @param dbHost MySQL Host
	 * @param dbUser MySQL Username
	 * @param dbPass MySQL Password
	 * @param dbBase MySQL Database
	 * @param mailHost Email Host
	 * @param mailUser Email User
	 * @param mailPass Email Password
	 */
	public EmailReceiver(String dbHost, String dbUser, String dbPass, String dbBase, String mailHost,
			String mailUser, String mailPass){
		
		LOGGER.setLevel(LEVEL);
		
		database = new DatabaseHelper(dbHost, dbBase, dbUser, dbPass);
		
		MAIL_SERVER = mailHost;
		MAIL_USER = mailUser;
		MAIL_PASSWORD = mailPass;
		
	}
	
	/**
	 * Returns current date/time formatted as yy-MM-DD HH:mm:ss.SSS
	 * @return
	 */
	private String getNow(){
	
		return DATE_FORMAT.format(new Date());
		
	}
	
	public void run(){
		
		boolean run = true;
		
		LOGGER.info(getNow() + " ContactResponse-Importer: Started on inbox " + MAIL_USER); //$NON-NLS-1$
		
		while(run){
		
			receiveEmail(MAIL_SERVER, MAIL_TYPE, MAIL_USER, MAIL_PASSWORD, 50);
			
			try{
				
				LOGGER.info(getNow() + " ContactResponse-Importer: Sleeping..."); //$NON-NLS-1$
				sleep(EMAIL_CHECK_PERIOD * 1000);
				
			}catch(InterruptedException e){
				
				run = false;
				LOGGER.warning(getNow() + " ContactResponse-Importer: Interrupted!");
				
			}
			
		}
		
		LOGGER.info(getNow() + " ContactResponse-Importer:  Stopped"); //$NON-NLS-1$
		
	}
	
	public static void main(String[] args){
		
		if(args.length == 7){
			EmailReceiver emr = new EmailReceiver(args[0], args[1], args[2], args[3], args[4], args[5],
					args[6]);
			emr.start();
		}else{
			System.out.println("USAGE: DBHOST DBUSER DBPASS DBASE MAILHOST MAILUSER MAILPASS");
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
			
		}else{//Type 1 address
			
			address = addressFromEmail;
			
		}
		
		return address.toLowerCase();
		
	}
	
	/**
	 * Receive Email loop
	 * @param pop3Host
	 * @param storeType
	 * @param user
	 * @param password
	 * @param number
	 */
	private void receiveEmail(String pop3Host, String storeType, String user, String password, int number) {

		try {
			
			Properties properties = new Properties();
			properties.setProperty("mail.pop3.ssl.socketFactory.class", "com.thevoiceasia.email.AlwaysTrustSSLContextFactory");  //$NON-NLS-1$//$NON-NLS-2$
			properties.setProperty("mail.pop3.ssl.socketFactory.port", "110"); //$NON-NLS-1$ //$NON-NLS-2$
			properties.setProperty("mail.pop3.starttls.enable", "true");  //$NON-NLS-1$//$NON-NLS-2$
			Session emailSession = Session.getDefaultInstance(properties);

			URLName url = new URLName("pop3", pop3Host, 110, "", user, password);  //$NON-NLS-1$//$NON-NLS-2$
			POP3Store emailStore = (POP3Store) emailSession.getStore(url);
			LOGGER.info("Connecting to server " + pop3Host + " as " + user); //$NON-NLS-1$ //$NON-NLS-2$
			emailStore.connect(user, password);
			LOGGER.finest("Connected..."); //$NON-NLS-1$
			
			LOGGER.finest("Opening INBOX"); //$NON-NLS-1$
			Folder emailFolder = emailStore.getFolder("INBOX"); //$NON-NLS-1$
			emailFolder.open(Folder.READ_WRITE);
			LOGGER.finest("Opened..."); //$NON-NLS-1$
			
			int emailCount = emailFolder.getMessageCount();//number of messages in INBOX
			LOGGER.finest(emailCount + " messages found"); //$NON-NLS-1$
			
			if(emailCount > 0){
				
				//Loop through emails adding to DB
				LOGGER.info("Reading emails..."); //$NON-NLS-1$
				SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss"); //$NON-NLS-1$
				
				for(int i = emailCount; i > 0; i--){
					
					Message message = emailFolder.getMessage(i);
					
					if(!message.isSet(Flags.Flag.DELETED) && !message.isSet(Flags.Flag.SEEN)){
						
						LOGGER.finest("Email: " + i); //$NON-NLS-1$
						
						/* Deals with null pointer in recipients[0] */
						Address[] recipients = message.getAllRecipients();
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
						
						if(from != null){
						
							int j = 0;
							
							while(fromAddress == null && j < from.length){
							
								if(from[j] != null)
									fromAddress = from[j].toString();
								
							}
							
							fromAddress = getAddress(fromAddress);
							LOGGER.finest("From: " + fromAddress); //$NON-NLS-1$
							
						}
						
						Date headerDate = formatHeaderDate(message.getHeader("Received")[0]); //$NON-NLS-1$
						
						//Read message content and pick out email, name, content
						//and referrer so we know what form was used
						String html = null;
						String text = null;
						
						if(message.getContent() instanceof MimeMultipart){
							
							MimeMultipart msgContent = (MimeMultipart)message.getContent();
							
							int mimeParts = msgContent.getCount();
							
							for(int j = 0; j < mimeParts; j++){
								
								if(msgContent.getBodyPart(j).getContentType().startsWith("text/plain")) //$NON-NLS-1$
									text = msgContent.getBodyPart(j).getContent().toString();
								else if(msgContent.getBodyPart(j).getContentType().startsWith("text/html")) //$NON-NLS-1$
									html = msgContent.getBodyPart(j).getContent().toString();
								
							}
							
						}else
							text = message.getContent().toString();
						
						String messageContent = text;
						
						//Use plain text if possible else fallback to html
						if(messageContent == null)
							messageContent = html;
						
						//All messages in inbox should be processed and then deleted
						if(!DEBUG_MODE){
							
							/* Actually do something with the email here
							 * receivedDate: Date when email was sent
							 * messageContent: Email hopefully in plain text (no attachments)
							 * toAddress: Email Address of original recipient
							 * fromAddress: Email Address of person who sent this message 
							 */
							if(processEmail(headerDate, fromAddress, toAddress, messageContent))//Finished so mark message for deletion
								message.setFlag(Flags.Flag.DELETED, true);
							else
								message.setFlag(Flags.Flag.SEEN, true);
						
							if(message.isSet(Flags.Flag.DELETED))
								LOGGER.finest("Marked for Deletion"); //$NON-NLS-1$
							
						}
						
					}
					
				}
				
				LOGGER.info("Finished reading emails"); //$NON-NLS-1$
				
			}
			
			
			LOGGER.finest("Closing connections..."); //$NON-NLS-1$
			emailFolder.close(true);
			emailStore.close();
			LOGGER.finest("Connections closed"); //$NON-NLS-1$
			
		} catch (NoSuchProviderException e) {
			
			e.printStackTrace();
			LOGGER.warning("NoSuchProviderException");
			
		} catch (MessagingException e) {
			
			e.printStackTrace();
			LOGGER.warning("MessagingException");
			
		} catch (IOException e) {
		
			LOGGER.warning("Error getting message content"); //$NON-NLS-1$
			e.printStackTrace();
			
		}

	}

	private boolean processEmail(Date sentDate, String fromAddress, String toAddress,
			String messageContent) {
		// TODO Put this message into the DB/File System as appropriate
		
		return false;
		
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
