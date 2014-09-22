package com.thevoiceasia.messages;

import java.net.UnknownHostException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.logging.Logger;

import javax.mail.MessagingException;

import com.thevoiceasia.contact.Contact;
import com.thevoiceasia.database.DatabaseHelper;
import com.thevoiceasia.database.KeyValue;
import com.thevoiceasia.email.EmailSender;
import com.thevoiceasia.sms.SMSSender;

public class OutgoingWorker {

	private static final Logger LOGGER = Logger.getLogger("com.thevoiceasia.email"); //$NON-NLS-1$
	
	private DatabaseHelper database = null;
	private HashMap<Integer, OutgoingTemplate> templates = null;//<template id, template>
	private HashMap<Integer, String> users = null;//<user id, user name?>
	
	private String host = null, user = null, password = null, 
			templateBasePath = null, debugRecipient = null;
	private ArrayList<KeyValue> languages = new ArrayList<KeyValue>();
	private OutgoingQueue queue = null;
	private PreparedStatement updateMessage = null;
	private boolean DEBUG = false;
	
	/**
	 * Grabs all the unsent outgoing messages in the messages table and
	 * sends them with the relevant template 
	 * @param emailServer
	 * @param emailUser
	 * @param emailPassword
	 * @param databaseHost
	 * @param databaseUser
	 * @param databasePassword
	 * @param databaseName
	 * @param templatePath
	 * @param debug if true all messages will be sent to the debugRecipient
	 * as an email rather than out to contacts
	 */
	public OutgoingWorker(String emailServer, String emailUser, 
			String emailPassword, String databaseHost, String databaseUser,
			String databasePassword, String databaseName, String templatePath,
			boolean debug){
		
		this.database = new DatabaseHelper(databaseHost, databaseName, 
				databaseUser, databasePassword);
		
		this.host = emailServer;
		this.user = emailUser;
		this.password = emailPassword;
		this.templateBasePath = templatePath;
		this.DEBUG = debug;
		
	}
	
	/**
	 * Reads languages from language table
	 */
	private void getLanguages(){
		
		String SQL = "SELECT * FROM `languages`"; //$NON-NLS-1$
		
		Statement selectLanguages = null;
		ResultSet results = null;
		
		try{
			
			selectLanguages = database.getConnection().createStatement();
			
			if(selectLanguages.execute(SQL)){
				
				results = selectLanguages.getResultSet();
				
				while(results.next())
					languages.add(new KeyValue("" + results.getInt("id"),   //$NON-NLS-1$//$NON-NLS-2$
							results.getString("language").toLowerCase()));  //$NON-NLS-1$
				
			}
			
		}catch(SQLException e){
			
			LOGGER.severe("SQL Error getting languages"); //$NON-NLS-1$
			e.printStackTrace();
			
		}finally{
			
			close(selectLanguages, results);
			
		}
		
	}
	/**
	 * Reads templates from the templates table
	 */
	private void getTemplates(){
		
		SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss"); //$NON-NLS-1$
		
		String SQL = "SELECT `id`, `from`, `date`, `exclusive`, `priority` " + //$NON-NLS-1$
				"FROM `templates` WHERE `date` IS NULL OR `date` = " + //$NON-NLS-1$
				sdf.format(new Date()); 
		
		Statement selectTemplates = null;
		ResultSet results = null;
		
		try{
			
			selectTemplates = database.getConnection().createStatement();
			
			if(selectTemplates.execute(SQL)){
				
				results = selectTemplates.getResultSet();
				
				templates = new HashMap<Integer, OutgoingTemplate>();
				
				while(results.next()){
					
					//Read templates and add to map
					boolean exclusive = false;
					
					if(results.getString("exclusive").equals("Y"))  //$NON-NLS-1$//$NON-NLS-2$
						exclusive = true;
					
					OutgoingTemplate out = new OutgoingTemplate(
							results.getInt("id"), //$NON-NLS-1$
							results.getString("from"), //$NON-NLS-1$
							exclusive,
							results.getInt("priority"), //$NON-NLS-1$
							results.getString("date"),//$NON-NLS-1$
							templateBasePath,
							(KeyValue[])languages.toArray()); 
					
					templates.put(out.getId(), out);
					
				}
				
				if(templates.size() < 1)//reset to null if there are no templates
					templates = null;
				
			}
			
		}catch(SQLException e){
			
			LOGGER.severe("Auto Reply Sender: Unable to get outgoing templates"); //$NON-NLS-1$
			e.printStackTrace();
			
		}finally{
			
			close(selectTemplates, results);
			
		}
		
	}
	
	/**
	 * Call to process the list of outgoing messages
	 */
	public void processOutgoingMessages(){
		
		database.connect();
		getLanguages();
		getTemplates();
		
		if(DEBUG)
			getDebugRecipient();
		
		queue = new OutgoingQueue(templates);
		
		String SQL = "SELECT `id`, `owner`, `created_by`, `type` FROM `messages` " + //$NON-NLS-1$
				"WHERE `direction` = 'O' AND `status` = 'U' " + //$NON-NLS-1$
				"ORDER BY `owner` ASC, `created` DESC"; //$NON-NLS-1$
		
		Statement selectMessages = null;
		ResultSet results = null;
		
		try{
			
			selectMessages = database.getConnection().createStatement();
			
			if(selectMessages.execute(SQL)){
				
				results = selectMessages.getResultSet();
				
				while(results.next()){
					
					OutgoingMessage message = new OutgoingMessage();
					message.id = results.getInt("id"); //$NON-NLS-1$
					message.owner = results.getInt("owner"); //$NON-NLS-1$
					message.createdBy = results.getInt("created_by"); //$NON-NLS-1$
					message.type = results.getInt("type"); //$NON-NLS-1$
				
					queue.addMessage(message);
					
				}
				
			}
			
		}catch(SQLException e){
			
			LOGGER.severe("Auto Reply Sender: Unable to get outgoing messages"); //$NON-NLS-1$
			e.printStackTrace();
			
		}finally{
			
			close(selectMessages, results);
			
		}
		
		//Archive Old ones
		if(!DEBUG)
			archiveMessages();
		
		//Send out messages
		sendMessages();
		
		//Close the updateMessages preparedStatement if we used it
		if(updateMessage != null){
			
			close(updateMessage, null);
			updateMessage = null;
			
		}
		
		database.disconnect();
		
	}
	
	/**
	 * Sets debugRecipient with the one in the settings table
	 */
	private void getDebugRecipient() {
		
		String SQL = "SELECT `value` FROM `settings` WHERE `name` = " + //$NON-NLS-1$
				"'debugRecipient'"; //$NON-NLS-1$
		
		Statement selectRecipient = null;
		ResultSet results = null;
		
				
		try{
			
			selectRecipient = database.getConnection().createStatement();
			
			if(selectRecipient.execute(SQL)){
				
				results = selectRecipient.getResultSet();
				
				while(results.next())
					debugRecipient = results.getString("value"); //$NON-NLS-1$
					
			}
			
		}catch(SQLException e){
			
			LOGGER.severe("Unable to read debugRecipient from Settings table"); //$NON-NLS-1$
			e.printStackTrace();
			
		}finally{
			
			close(selectRecipient, results);
			
		}
		
		debugRecipient = "waynemerricks@thevoiceasia.com"; //$NON-NLS-1$
		
	}

	/**
	 * Helper method to update status of a record in the messages table
	 * @param id id of record
	 * @param status status to set
	 * @return true if updated, false if errors
	 */
	private boolean updateMessage(int id, String status){
		
		boolean updated = false;
		
		String SQL = "UPDATE `messages` SET `status` = ? WHERE `id` = ?"; //$NON-NLS-1$
		
		try{
			
			if(updateMessage == null)//can reuse this
				updateMessage = database.getConnection().prepareStatement(SQL);
			
			updateMessage.setString(1, status);
			updateMessage.setInt(2, id);
			
			int rows = updateMessage.executeUpdate();
			
			if(rows > 0)
				updated = true;
			
		}catch(SQLException e){
			
			LOGGER.severe("Error while updating message status " + id); //$NON-NLS-1$
			e.printStackTrace();
			
		}
		
		return updated;
		
	}
	
	/**
	 * Sets messages in the queue flagged as Archive to status = A in messages 
	 * DB
	 */
	private void archiveMessages() {
		
		Iterator<Integer> keys = queue.getIterator();
		
		while(keys.hasNext()){
			
			ArrayList<OutgoingMessage> contact = queue.get(keys.next());
			
			for(int i = 0; i < contact.size(); i++)
				if(contact.get(i).archive)
					updateMessage(contact.get(i).id, "A"); //$NON-NLS-1$
			
		}
		
		//Close Connections
		close(updateMessage, null);
		
	}
	
	/**
	 * Actually sends the messages out from here.  This will find out the
	 * contacts details and using the preferred language and contact method
	 * send them either an SMS or an email.
	 * 
	 * If this user is flagged as not wanting Auto Replies, they are skipped.
	 */
	private void sendMessages() {
		
		EmailSender email = new EmailSender(host, user, password);
		SMSSender sms = new SMSSender(database);
		
		Iterator<Integer> contactId = queue.getIterator();
		
		while(contactId.hasNext()){
			
			int id = contactId.next();
			
			ArrayList<OutgoingMessage> toSend = queue.get(id);
			
			for(int i = 0; i < toSend.size(); i++){
				
				if(!toSend.get(i).archive){
					
					if(users == null)
						populateUsers();
					
					//Need to send this one
					OutgoingMessage message = toSend.get(i);
					
					/* Get contact from message.owner figure out email or
					 * sms and populate template with it.
					 * 
					 * Then send the thing and mark as sent/fail in DB
					 */
					Contact contact = new Contact(database, id);
					boolean sentSMS = false;
					
					if(!contact.hasErrors() && contact.wantsAutoReply()){
						
						boolean sent = false;
						OutgoingTemplate out = templates.get(message.type);
						String from = out.getFrom();
						
						//Contact hasn't opted out so send them the template
						if(contact.hasEmail()){//TODO what if contact prefers SMS
							
							String body = out.getEmailBody(
									contact.getLanguageID());
							body = substituteFields(body, contact, 
									users.get(message.createdBy));
							
							String subject = out.getSubject(
									contact.getLanguageID());
							subject = substituteFields(subject, contact, 
									users.get(message.createdBy));
							
							String to = contact.getEmail();
							
							if(DEBUG){
								
								body = "DEBUG: Recipient " + to + "\n" + body; //$NON-NLS-1$ //$NON-NLS-2$
								to = debugRecipient;
								
							}
								
							try{
								
								if(email.sendEmail(to, from, 
										subject, body))
									sent = true;
								
							}catch(UnknownHostException e){
								
								LOGGER.warning("Unknown Host Exception"); //$NON-NLS-1$
								e.printStackTrace();
								
							}catch(MessagingException e){
								
								LOGGER.warning("Messaging Exception"); //$NON-NLS-1$
								e.printStackTrace();
								
							}
								
						}else if(contact.hasSMSNumber()){
							
							//Use SMS template (this costs money)
							String text = out.getSMS(contact.getLanguageID());
							text = substituteFields(text, contact, 
									users.get(message.createdBy));
							
							if(!DEBUG){
								if(sms.sendSMS(contact.getNumber(), text))
									sent = true;
							}else{
								
								sentSMS = true;
								//Send it as an email to Debug
								try{
									
									if(email.sendEmail(debugRecipient, from, 
											"SMS TO " + contact.getNumber(), //$NON-NLS-1$
											text))
										sent = true;
									
								}catch(UnknownHostException e){
									
									LOGGER.warning("Unknown Host Exception"); //$NON-NLS-1$
									e.printStackTrace();
									
								}catch(MessagingException e){
									
									LOGGER.warning("Messaging Exception"); //$NON-NLS-1$
									e.printStackTrace();
									
								}
								
							}
							
						}
						
						if(sent){
							
							//Flag db as sent
							String sentStatus = "S"; //$NON-NLS-1$
							
							if(sentSMS)
								sentStatus = "T"; //$NON-NLS-1$
							
							updateMessage(message.id, sentStatus);
							
						}else{
							
							//Log contact as undeliverable and report to
							//admin
							String errorStatus = "E"; //$NON-NLS-1$
							
							if(sentSMS)
								errorStatus = "F"; //$NON-NLS-1$
							
							updateMessage(message.id, errorStatus);
							
						}
						
					}
					
				}
				
			}
			
		}
		
	}

	/**
	 * Reads in user ids and names from the users table
	 */
	private void populateUsers() {
		
		String SQL = "SELECT `id`, `name` FROM `users`"; //$NON-NLS-1$
		
		Statement selectUsers = null;
		ResultSet results = null;
		
		try{
			
			selectUsers = database.getConnection().createStatement();
			
			if(selectUsers.execute(SQL)){
				
				results = selectUsers.getResultSet();
				users = new HashMap<Integer, String>();
				
				while(results.next()){
					
					users.put(results.getInt("id"),  //$NON-NLS-1$
							results.getString("name")); //$NON-NLS-1$
					
				}
				
			}
			
		}catch(SQLException e){
			
			LOGGER.severe("Unable to read users from users table"); //$NON-NLS-1$
			e.printStackTrace();
			
		}finally{
			
			close(selectUsers, results);
			
		}
		
	}

	/**
	 * Replaces known fields with contact information e.g. 
	 * {{NAME}} = Contact.name
	 * @param body from template with field codes
	 * @param contact Contact object representing this recipient
	 * @param userName Name of the user who created this reply e.g. John Smith 
	 * @return body with relevant substitutes
	 */
	private String substituteFields(String body, Contact contact, 
			String userName) {
		
		// TODO Any others?
		if(body.contains("{{NAME}}")) //$NON-NLS-1$
			body.replaceAll("{{NAME}}", contact.getName()); //$NON-NLS-1$
		
		if(body.contains("{{DAY}}")){ //$NON-NLS-1$
			
			SimpleDateFormat sdf = new SimpleDateFormat("EEEE"); //$NON-NLS-1$
			body.replaceAll("{{DAY}}", sdf.format(new Date())); //$NON-NLS-1$
			
		}
		
		if(body.contains("{{DATE}}")){ //$NON-NLS-1$
			
			SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd"); //$NON-NLS-1$
			body.replaceAll("{{DATE}}", sdf.format(new Date())); //$NON-NLS-1$
			
		}
		
		if(body.contains("{{USER}}")) //$NON-NLS-1$
			body.replaceAll("{{USER}}", userName); //$NON-NLS-1$
			
		return body;
		
	}

	/**
	 * Helper method to close statements and resultsets
	 * @param statement
	 * @param results
	 */
	private void close(Statement statement, ResultSet results){
		
		if(results != null){
			
			try{
				results.close();
			}catch(Exception e){}
			
			results = null;
			
		}

		if(statement != null){
			
			try{
				statement.close();
			}catch(Exception e){}
			
			statement = null;
			
		}
		
	}
	
	/**
	 * @param args
	 * USAGE: 	EMAIL_HOST EMAIL_USER EMAIL_PASSWORD DATABASE_HOST DATABASE_USER 
	 * 			DATABASE_PASSWORD DATABASE_NAME TEMPLATE_PATH [DEBUG]
	 */
	public static void main(String[] args) {
		
		
		if(args.length == 8 || args.length == 9){
		
			boolean debug = false;
			
			if(args.length == 9 && args[8].equalsIgnoreCase("debug")) //$NON-NLS-1$
				debug = true;
				
			OutgoingWorker worker = new OutgoingWorker(args[0], args[1], 
					args[2], args[3], args[4], args[5], args[6], args[7], 
					debug);
			
			worker.processOutgoingMessages();
			
		}else{
			
			System.out.println("USAGE: EMAIL_HOST EMAIL_USER EMAIL_PASSWORD " + //$NON-NLS-1$
					"DATABASE_HOST DATABASE_USER DATABASE_PASSWORD " + //$NON-NLS-1$
					"DATABASE_NAME TEMPLATE_PATH [DEBUG]"); //$NON-NLS-1$
			System.exit(1);
			
		}
		
	}

}
