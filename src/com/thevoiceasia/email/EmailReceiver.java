package com.thevoiceasia.email;

/* GENERAL */
import java.io.*;
import java.text.*;
import java.util.*;
import java.util.logging.Logger;

/* EMAIL */
import com.sun.mail.pop3.*;

import javax.mail.*;
import javax.mail.internet.MimeMultipart;

import org.apache.commons.validator.routines.EmailValidator;

/* TVA STUFF */
import com.thevoiceasia.html.HTML2Text;


public class EmailReceiver extends Thread{

	/* STATIC SETTINGS */
	private static final Logger LOGGER = Logger.getLogger("com.thevoiceasia.email"); //$NON-NLS-1$
	private static final boolean DEBUG_MODE = false;
	
	/* CLASS VARS */
	private String MAIL_SERVER, MAIL_USER, MAIL_PASSWORD;
	private EmailReader reader = null;
	
	/**
	 * Receives Email from the given email inbox (no subfolders)
	 * @param mailHost Email Host
	 * @param mailUser Email User
	 * @param mailPass Email Password
	 * @param reader Reader object to pass messages on to
	 */
	public EmailReceiver(String mailHost, String mailUser, String mailPass, 
		EmailReader reader){
		
		MAIL_SERVER = mailHost;
		MAIL_USER = mailUser;
		MAIL_PASSWORD = mailPass;
		this.reader = reader;
		
	}
	
	/**
	 * Runnable start point
	 */
	public void run(){
		
		LOGGER.finest("Importer: Started on inbox " + MAIL_USER); //$NON-NLS-1$
		receiveEmail(MAIL_SERVER, MAIL_USER, MAIL_PASSWORD, 50);
		LOGGER.finest("Importer: Finished reading Emails"); //$NON-NLS-1$
		
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
			
			name = addressFromEmail.split("<")[0].trim(); //$NON-NLS-1$
		
			/* Sometimes email won't send with a "Name component"
			 * Most of the time it is Name <address>
			 * Other times its "Email Address" <address> so we need to check for this case here
			 */
			if(name.startsWith("\"") && name.endsWith("\"")){  //$NON-NLS-1$//$NON-NLS-2$
				//Remove the pre/post "
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
			LOGGER.finest("Importer: Connecting to server " + pop3Host + " as " + user); //$NON-NLS-1$ //$NON-NLS-2$
			emailStore.connect(user, password);
			LOGGER.finest("Importer: Connected..."); //$NON-NLS-1$
			
			LOGGER.finest("Importer: Opening INBOX"); //$NON-NLS-1$
			emailFolder = emailStore.getFolder("INBOX"); //$NON-NLS-1$
			emailFolder.open(Folder.READ_WRITE);
			LOGGER.finest("Importer: Opened..."); //$NON-NLS-1$
			
			int emailCount = emailFolder.getMessageCount();//number of messages in INBOX
			LOGGER.finest("Importer: " + emailCount + " messages found"); //$NON-NLS-1$
			
			if(emailCount > 0){
				
				//Loop through emails adding to DB
				LOGGER.info("Importer: Reading " + emailCount + " emails..."); //$NON-NLS-1$ //$NON-NLS-2$
				
				for(int i = emailCount; i > 0; i--){
					
					Message message = emailFolder.getMessage(i);
					
					if(!message.isSet(Flags.Flag.DELETED) && !message.isSet(Flags.Flag.SEEN)){
						
						LOGGER.finest("Importer: Email: " + i); //$NON-NLS-1$
						
						/* Deals with null pointer in recipients[0] */
						boolean skip = false;
						
						Address[] recipients = null;
						
						try{
							recipients = message.getAllRecipients();
						}catch(MessagingException e){
							
							LOGGER.warning("Importer: MESSAGING EXCEPTION: There was a problem getting this messages recipients, skipping message"); //$NON-NLS-1$
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
								LOGGER.finest("Importer: Sent To: " + toAddress); //$NON-NLS-1$
								
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
								
								
								LOGGER.finest("Importer: From: " + fromAddress); //$NON-NLS-1$
								
							}
							
							Date headerDate = formatHeaderDate(message.getHeader("Received")[0]); //$NON-NLS-1$
							
							//Read message content and pick out email, name, content
							//and referrer so we know what form was used
							String messageContent = null;
							String subject = message.getSubject();
							
							if(subject == null)
								subject = ""; //$NON-NLS-1$
							
							if(message.getContent() instanceof MimeMultipart){
								
								MimeMultipart msgContent = (MimeMultipart)message.getContent();
								messageContent = getMessageContent(msgContent);
								
							}else
								messageContent = message.getContent().toString();
								
							//All messages in inbox should be processed and then deleted
							if(!DEBUG_MODE){
								
								/* Actually do something with the email here
								 * receivedDate: Date when email was sent
								 * messageContent: Email hopefully in plain text (no attachments)
								 * toAddress: Email Address of original recipient
								 * fromAddress: Email Address of person who sent this message 
								 */
								if(messageContent != null){
									
									if(reader.receiveEmail(headerDate, fromAddress, toAddress, contactName, messageContent, subject))//Finished so mark message for deletion
										message.setFlag(Flags.Flag.DELETED, true);
									else
										message.setFlag(Flags.Flag.SEEN, true);
										
									if(message.isSet(Flags.Flag.DELETED))
										LOGGER.finest("Importer: Marked for Deletion"); //$NON-NLS-1$
									
								}else
									LOGGER.warning("Importer: Unable to get message content for " + fromAddress); //$NON-NLS-1$
								
							}else
								LOGGER.info("Importer: " + fromAddress + "/" + toAddress + "\n" + messageContent);  //$NON-NLS-1$//$NON-NLS-2$
							
						}
					}
					
				}
				
				LOGGER.finest("Importer: Finished reading emails"); //$NON-NLS-1$
				
			}else
				LOGGER.finest("Importer: Inbox Empty, nothing to do"); //$NON-NLS-1$
			
		} catch (NoSuchProviderException e) {
			
			e.printStackTrace();
			LOGGER.warning("Importer: NoSuchProviderException"); //$NON-NLS-1$
			
		} catch (MessagingException e) {
			
			e.printStackTrace();
			LOGGER.warning("Importer: MessagingException"); //$NON-NLS-1$
			
		} catch (IOException e) {
		
			LOGGER.warning("Importer: Error getting message content"); //$NON-NLS-1$
			e.printStackTrace();
			
		} finally {
			
			if(emailFolder != null && emailStore != null){
				
				LOGGER.finest("Importer: Closing connections..."); //$NON-NLS-1$
				
				try{
					
					emailFolder.close(true);
					emailStore.close();
					
				}catch(MessagingException e){
					
					LOGGER.severe("Importer: Failed to close email store and commit changes"); //$NON-NLS-1$
					
				}
				
				LOGGER.finest("Importer: Connections closed"); //$NON-NLS-1$
				
			}
			
		}

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
					
				if(messageBody.isMimeType("text/plain")){ //$NON-NLS-1$
						
					text = messageBody.getContent().toString();
					found = true;
						
				}else if(text == null && html == null){
					
					if(messageBody.isMimeType("text/html")) //$NON-NLS-1$
						html = HTML2Text.convert(messageBody.getContent().toString());//Use Jericho to render to plain text
					else if(messageBody.isMimeType("multipart/alternative")){ //$NON-NLS-1$
						
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
			LOGGER.warning("Importer: MessagingException"); //$NON-NLS-1$
			
		}catch(IOException e){
			
			LOGGER.warning("Importer: Error getting message content"); //$NON-NLS-1$
			e.printStackTrace();
			
		}
		
		return text;
		
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
			
			LOGGER.warning("Importer: Error with header: " + headerReceived); //$NON-NLS-1$
			
		}
		
		return receivedDate;
		
	}
	
}
