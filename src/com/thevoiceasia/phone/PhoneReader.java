package com.thevoiceasia.phone;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.logging.FileHandler;

import org.apache.commons.validator.routines.EmailValidator;

import com.thevoiceasia.database.DatabaseHelper;
import com.thevoiceasia.database.FieldMap;
import com.thevoiceasia.database.KeyValue;
import com.thevoiceasia.messages.MessageArchiver;

public class PhoneReader extends MessageArchiver {

	private DatabaseHelper phoneDatabase = null;
	private HashMap<String, Integer> userIds = null;//new HashMap<String, Integer>();
	private HashMap<String, FieldMap> customIds = null;
			//new HashMap<String, FieldMap>();
	public static final String[] CUSTOM_FIELDS = {"language", "religion",  //$NON-NLS-1$ //$NON-NLS-2$
		"journeyStage", "topic"}; //$NON-NLS-1$ //$NON-NLS-2$
	public static final String[] STRING_FIELDS = {"caller_name", "number",   //$NON-NLS-1$//$NON-NLS-2$
		"conversation", "location", "topic"}; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	
	private HashMap<String, String> valueTables = new HashMap<String, String>();  
	private static final int MAX_RECORDS = 10;
	
	public PhoneReader(String host, String user, String pass, String dbase,
			String phoneHost, String phoneUser, String phonePass, String phoneDbase,
			String archivePath){
		
		super(host, user, pass, dbase, archivePath);
		
		setupLogging();
		
		phoneDatabase = new DatabaseHelper(phoneHost, phoneDbase, phoneUser, 
				phonePass);
		
		database.connect();
		
		valueTables.put("s", "contact_values_small"); //$NON-NLS-1$ //$NON-NLS-2$
		valueTables.put("m", "contact_values_medium");  //$NON-NLS-1$//$NON-NLS-2$
		valueTables.put("l", "contact_values_large"); //$NON-NLS-1$ //$NON-NLS-2$
		
	}

	/**
	 * Set the Logger object
	 */
	private void setupLogging(){
		
		LOGGER.setLevel(LEVEL);
		
		try{
			
			LOGGER.addHandler(new FileHandler("phoneresponseincoming.log")); //$NON-NLS-1$
			
		}catch(IOException e){
			
			e.printStackTrace();
			
		}
		
	}
	
	/**
	 * Grabs all the custom fields from the contact_fields table
	 */
	private void populateCustomIDs() {

		customIds = new HashMap<String, FieldMap>();
		String SQL = "SELECT * FROM `contact_fields`"; //$NON-NLS-1$
		
		Statement selectCustom = null;
		ResultSet results = null;
		
		try{
			
			selectCustom = database.getConnection().createStatement();
			
			if(selectCustom.execute(SQL)){
				
				results = selectCustom.getResultSet();
			
				while(results.next()){
				
					FieldMap map = new FieldMap(
							results.getInt("id"), //$NON-NLS-1$
							results.getString("label"), //$NON-NLS-1$
							results.getString("type"), //$NON-NLS-1$
							results.getString("data"), //$NON-NLS-1$
							results.getString("dbMap")); //$NON-NLS-1$
					
					customIds.put(results.getString("dbMap"), map); //$NON-NLS-1$
				
				}
				
			}
			
		}catch(SQLException e){
			
			LOGGER.severe("Phone Archiver: Error getting custom contact fields"); //$NON-NLS-1$
			e.printStackTrace();
			
		}finally{
			
			close(selectCustom, results);
			
		}
		
	}

	/** 
	 * Get uesrs info from the users table
	 */
	private void populateUserIDs() {
		
		userIds = new HashMap<String, Integer>();
		Statement selectUsers = null;
		ResultSet results = null;
		
		try{
			
			selectUsers = database.getConnection().createStatement();
			
			if(selectUsers.execute("SELECT `id`, `name` FROM `users`")){ //$NON-NLS-1$
				
				results = selectUsers.getResultSet();
				
				while(results.next()){
					
					String name = results.getString("name"); //$NON-NLS-1$
					
					//Remove spaces from name and convert to lowercase so we can compare with answeredBy
					name = name.replaceAll(" ", "").toLowerCase();  //$NON-NLS-1$//$NON-NLS-2$
					
					int id = results.getInt("id"); //$NON-NLS-1$
					userIds.put(name, id);
					
				}
				
			} 
			
			
		}catch(SQLException e){
			
			LOGGER.severe("Phone Archiver: Error getting User IDs"); //$NON-NLS-1$
			e.printStackTrace();
			
		}finally{
			
			close(selectUsers, results);
			
		}
		
	}

	public void freeResources(){
		
		disconnectFromDB();
		userIds = null;//Want to be able to refresh user ids if we get new
		//users in between runs
		customIds = null;//Same deal as user Ids
		
	}
	
	/**
	 * Entry point for PhoneReader
	 * @param args 9 arguments expected:
	 *   DB_HOST DB_USER DB_PASS DBASE PHONE_HOST PHONE_USER PHONE_PASS PHONE_DBASE MESSAGE_STORE_PATH
	 */
	public static void main(String[] args){
		
		if(args.length == 9){
			
			PhoneReader archiver = new PhoneReader(args[0], args[1], args[2], args[3], args[4], args[5],
					args[6], args[7], args[8]);
			
			if(!archiver.isValidArchive()){
				System.out.println("Message Store Path invalid, doesn't exist or has no write permissions: " + args[8]); //$NON-NLS-1$
				System.exit(1);
			}
			
			LOGGER.info("Phone Archiver: Started reading from  " + args[7]); //$NON-NLS-1$
				
			boolean go = true;
			
			while(go){
				
				try {
					
					archiver.getMessages();
					archiver.freeResources();
					
					LOGGER.finest("Phone Archiver: Sleeping for " + CHECK_PERIOD); //$NON-NLS-1$
					sleep(1000 * CHECK_PERIOD);
					
				} catch (InterruptedException e) {
					
					LOGGER.info("Phone Archiver: Interrupted, exiting"); //$NON-NLS-1$
					e.printStackTrace();
					go = false;
					
				} finally {
					
					archiver.freeResources();
					
				}
				
			}
			
		}else{
			
			System.out.println("USAGE: B_HOST DB_USER DB_PASS DBASE PHONE_HOST PHONE_USER PHONE_PASS PHONE_DBASE MESSAGE_STORE_PATH"); //$NON-NLS-1$
			System.exit(1);
			
		}
		
	}

	/**
	 * Get the last id we read from the phone table
	 * @return 	-1 Error
	 * 			-2 No results
	 * 			else id
	 */
	private int getLastReadID(){
		
		int lastID = -1;
		
		String SQL = "SELECT `value` FROM `settings` WHERE `name` = 'lastPhoneID'"; //$NON-NLS-1$
		
		Statement selectID = null;
		ResultSet results = null;
		
		try{
			
			selectID = database.getConnection().createStatement();
			
			if(selectID.execute(SQL)){
				
				results = selectID.getResultSet();
			
				lastID = -2;
				
				while(results.next())
					lastID = results.getInt("value"); //$NON-NLS-1$
				
				LOGGER.finer("Phone Archiver: Got last read ID " + lastID); //$NON-NLS-1$
				
			}
			
		}catch(SQLException e){
			
			LOGGER.severe("Phone Archiver: Error getting last phone ID"); //$NON-NLS-1$
			e.printStackTrace();
			
		}finally{
			
			close(selectID, results);
			
		}
		 
		return lastID;
		
	}
	
	/**
	 * Get Messages from the phone database
	 */
	public void getMessages() {
		
		phoneDatabase.connect();
		
		int id = getLastReadID();
		int lastId = id;
		
		Statement selectPhones = null;
		ResultSet results = null;
		
		try{
		
			String SQL = "SELECT * FROM `call_records` WHERE id > " + id + //$NON-NLS-1$
					" ORDER BY id ASC LIMIT " + MAX_RECORDS;  //$NON-NLS-1$
			selectPhones = phoneDatabase.getConnection().createStatement();
			
			if(selectPhones.execute(SQL)){
				
				results = selectPhones.getResultSet();
				
				LOGGER.finer("Phone Archiver: Got Phone Messages"); //$NON-NLS-1$
				
				while(results.next())
					id = processPhoneMessage(results);
				
			}
			
		}catch(SQLException e){
			
			LOGGER.severe("Phone Archiver: Error while reading phone records " + id); //$NON-NLS-1$
			e.printStackTrace();
			
		}finally{
			
			close(selectPhones, results);
			
			if(id != -1 && lastId != id){
				
				LOGGER.info("Phone Archiver: Added " + (id - lastId) +  //$NON-NLS-1$
						" phone calls (" + id + ")"); //$NON-NLS-1$ //$NON-NLS-2$ 
				updateLastProcessedID(id);
				
			}
			
		}
		
		phoneDatabase.disconnect();
		
	}
	
	/**
	 * Updates lastPhoneID in the settings table
	 * @param id value to update to
	 */
	private void updateLastProcessedID(int id) {
		
		PreparedStatement updateID = null;
		
		try{
			
			updateID = database.getConnection().prepareStatement(
					"UPDATE `settings` SET `value` = ? WHERE `name` = ?"); //$NON-NLS-1$
			
			updateID.setInt(1, id);
			updateID.setString(2, "lastPhoneID"); //$NON-NLS-1$
			
			updateID.execute();
			
		}catch(SQLException e){
			
			LOGGER.severe("Phone Archiver: Error updating lastPhoneID - " + id); //$NON-NLS-1$
			e.printStackTrace();
			
		}finally{
			
			close(updateID, null);
			
		}
		
	}

	/**
	 * Helper method to change db lookup of "null" to java null
	 * @param check
	 * @return
	 */
	private String checkNull(String check){
	
		if(check == null || check.equalsIgnoreCase("null") || check.trim().length() == 0) //$NON-NLS-1$
			check = null;
		
		return check;
		
	}

	/**
	 * Process the messages
	 * @param results
	 * @return id of record we processed
	 */
	private int processPhoneMessage(ResultSet results) {
		
		int lastID = -1;
		
		try{
			
			PhoneRecord pr = new PhoneRecord();
			
			pr.setAddress(results.getString("address"),  //$NON-NLS-1$
					results.getString("postcode")); //$NON-NLS-1$
			
			String email = checkNull(results.getString("email")); //$NON-NLS-1$
			
			if(email != null){
				
				EmailValidator ev = EmailValidator.getInstance(false);
				
				if(ev.isValid(email))
					pr.email = email;
				
			}
			
			String gender = results.getString("gender"); //$NON-NLS-1$
			
			if(gender != null)
				pr.gender = gender.substring(0, 1).toUpperCase();
			
			//Answered By 1234 (1234) (user@pc)
			String key = results.getString("answeredBy"); //$NON-NLS-1$
			
			int index = key.indexOf("@"); //$NON-NLS-1$
			
			if(index != -1){
			
				key = key.substring(0, index);
				
				index = key.indexOf("("); //$NON-NLS-1$
				
				while(index != -1){
					
					key = key.substring(index + 1);
					index = key.indexOf("("); //$NON-NLS-1$
					
				}
				
			}
			
			if(userIds == null){
				
				populateUserIDs();
				populateCustomIDs();
				initialiseFreeUsers();
				getAssignedUserList();
				
			}
			
			if(userIds.containsKey(key))
				pr.answeredBy = userIds.get(key);
			else
				pr.answeredBy = -1;
			
			//"caller_name", "number", "conversation", "location", "topic"}; 
			for(int i = 0; i < STRING_FIELDS.length; i++){
				
				String value = checkNull(results.getString(STRING_FIELDS[i]));
				
				if(value != null)
					pr.setString(STRING_FIELDS[i], value.trim());
				
			}
			
			//"language", "religion",  "journeyStage", "topic"
			for(int i = 0; i < CUSTOM_FIELDS.length; i++){
				
				key = checkNull(results.getString(CUSTOM_FIELDS[i]));
				
				if(key != null){
					
					key = key.toLowerCase().trim();
			
					if(customIds.containsKey(CUSTOM_FIELDS[i])){
					
						int dataId = customIds.get(CUSTOM_FIELDS[i]).getDataId(key);
						
						if(dataId != -1)
							pr.setCustomField(CUSTOM_FIELDS[i], dataId);
						
					}
					
				}
				
			}
			
			updateDatabase(pr);
			
			lastID = results.getInt("id"); //$NON-NLS-1$
			
		}catch(SQLException e){
			
			LOGGER.severe("Phone Archiver: Error getting record information" + lastID); //$NON-NLS-1$
			e.printStackTrace();
			
		}
		
		return lastID;
		
	}

	/**
	 * Inserts the phone message into the messages table
	 * @param contact contact[0] id that owns this message, 
	 *                contact[1] default assigned user
	 * @param pr PhoneRecord containing data
	 */
	private void insertMessage(int[] contact, PhoneRecord pr) {
		
		String SQL = "INSERT INTO `messages` (`owner`, `type`, `direction`, " + //$NON-NLS-1$
				"`preview`, `assigned_user`) VALUES (?, ?, ?, ?, ?)"; //$NON-NLS-1$
		
		/* Potentially could make this outside of message loop and reuse */
		PreparedStatement insertMessage = null;
		ResultSet results = null;
		
		try{
			
			insertMessage = database.getConnection().prepareStatement(SQL, 
					Statement.RETURN_GENERATED_KEYS);
			insertMessage.setInt(1, contact[0]);
			insertMessage.setString(2, "P"); //$NON-NLS-1$
			insertMessage.setString(3, "I"); //$NON-NLS-1$
			
			String message = pr.getMessage();
			String preview = null;
			
			if(message != null){
				
				preview = message;
				
				if(preview.length() > 50)
					preview = preview.substring(0, 47) + "..."; //$NON-NLS-1$
				
			}
			
			//Preview can be null if we get no subject or body (does happen)
			if(preview == null)
				preview = ""; //$NON-NLS-1$
			insertMessage.setString(4, preview);
			
			//If assigned = -1 it means all our users have reached max contacts
			//Set to 0 so it will go to anyone who has default helper permission
			if(contact[1] == -1){
				
				contact[1] = 0;
				LOGGER.warning("All users have reached the maximum number of contacts\n"); //$NON-NLS-1$
				
			}
			
			//Here we need to topic check the message and assign a user as appropriate
			int assignedID = getAssignedUserID(pr, contact[1]);
			
			LOGGER.finer("Assigning message to user " + assignedID); //$NON-NLS-1$
			insertMessage.setInt(5, assignedID);
			
			boolean inserted = insertMessage.execute();
			
			if(!inserted && insertMessage.getUpdateCount() != -1 && 
					insertMessage.getUpdateCount() > 0){
				
				results = insertMessage.getGeneratedKeys();
				
				int messageID = -1;
				
				while(results.next())
					messageID = results.getInt(1);
				
				if(messageID != -1){
					
					if(!writeMessageToArchive(messageID, message))
						removeMessageFromDatabase(messageID);
					
				}
				
			}
			
		}catch(SQLException e){
			
			LOGGER.severe("Phone Archiver: Error inserting phone message to " + //$NON-NLS-1$
					"messages table for " + pr.getName()); //$NON-NLS-1$
			e.printStackTrace();
			
		}finally{
			
			close(insertMessage, results);
			
		}
		
		
	}

	/**
	 * Checks for existing contact and updates or creates new one
	 * if not found by phone number or email
	 * 
	 * We can't rely on names so we'll have to create a new one if we only
	 * have the name to go on
	 * @param pr
	 */
	private void updateDatabase(PhoneRecord pr) {
		
		/* Here we will decide where this contact/message needs to go */
		if(pr.answeredBy == -1)
			pr.assignedTo = getFreeUsers().getNextAvailableUser();
		else{
			
			if(getFreeUsers().hasFreeUser(pr.answeredBy))
				pr.assignedTo = pr.answeredBy;//if the person who answered the call is available assign them to the message
			else
				pr.assignedTo = getFreeUsers().getNextAvailableUser();//else get the next available person
			
		}
		
		//0: contactId, 1: contact assigned to user id
		int[] contact = null;
		
		if(pr.getNumber() != null)
			contact = getContactIdByPhone(pr.getNumber());
		
		if(contact == null || (contact[0] == -1 && pr.email != null))
			contact = getContactIdByEmail(pr.email);
		
		int insertedId = -1;
		
		if(contact == null || contact[0] == -1)
			insertedId = createNewContact(pr);
		else{
		
			updateContact(contact, pr);
			
			if(contact[1] == -1)
				contact[1] = pr.assignedTo;
			
		}
		
		if(insertedId != -1){//we created a new contact so setup contact array
			
			contact = new int[2];
			contact[0] = insertedId;
			contact[1] = pr.assignedTo;
			
		}
		
		if(contact != null && contact[0] != -1)
			insertMessage(contact, pr);
		
	}
	
	/**
	 * Updates the contact represented by the PhoneRecord
	 * @param ids id of contact we found in the contacts table + person we 
	 * should assign the contact to if necessary (contact default value)
	 * @param pr these values are used to update
	 * @return
	 */
	private boolean updateContact(int[] ids, PhoneRecord pr) {
		
		boolean success = false;
		LOGGER.finer("Phone Archiver: Updating contact " + pr.getName()); //$NON-NLS-1$
		
		//Update Contact Table
		ArrayList<KeyValue> values = pr.getNonNullValues();
		int updateFreeUsers = -1;
		
		if(ids[1] == -1){//this contact hasn't been assigned to anyone so do it
			
			values.add(new KeyValue("assigned_user_id", "" + pr.assignedTo));  //$NON-NLS-1$//$NON-NLS-2$
			LOGGER.info("Assigned contact " + ids[0] + " to " +  //$NON-NLS-1$//$NON-NLS-2$
					pr.assignedTo); 
			updateFreeUsers = pr.assignedTo;
			
		}
		
		SimpleDateFormat mysqlTime = new SimpleDateFormat("yyyyMMddHHmmss");//$NON-NLS-1$
		
		if(values.size() > 0){
			
			String SQL = "UPDATE `contacts` SET `updated` = ?";  //$NON-NLS-1$
			
			if(values.size() > 0)
				for(int i = 0; i < values.size(); i++)
					SQL += ", `" + values.get(i).key + "` = ?";  //$NON-NLS-1$//$NON-NLS-2$
			
			SQL += " WHERE `id` = ?"; //$NON-NLS-1$
			
			PreparedStatement updateContact = null;
			
			try{
				
				updateContact = database.getConnection().prepareStatement(SQL);
				updateContact.setString(1, mysqlTime.format(new Date()));
				
				for(int i = 2; i <= values.size() + 1; i++)
					updateContact.setString(i, values.get(i - 2).value);
				
				updateContact.setInt(values.size() + 2, ids[0]);
				
				updateContact.execute();
				
				int rowsUpdated = updateContact.getUpdateCount();
				
				if(rowsUpdated > 0){
					
					success = updateCustomFields(ids[0], pr);
					
					//If we had to assign this contact to a user update the
					//count here
					if(updateFreeUsers != -1)
						getFreeUsers().addContact(updateFreeUsers);
					
				}
				
			}catch(SQLException e){
				
				LOGGER.severe("Phone Archiver: Error while updating " + //$NON-NLS-1$
						"contact" + ids[0]); //$NON-NLS-1$
				e.printStackTrace();
				
			}finally{
				
				close(updateContact, null);
				
			}
			
		}else{
			
			success = true;
			LOGGER.info("Phone Archiver: Nothing to update for " + ids[0]); //$NON-NLS-1$
			
		}
			
		return success;
		
	}

	/**
	 * Updates the custom fields of this PhoneRecord for the contact
	 * @param id id of the contact
	 * @param pr record to get values from
	 * @return true if successful
	 */
	private boolean updateCustomFields(int id, PhoneRecord pr) {
		
		boolean success = false;
		
		ArrayList<KeyValue> values = pr.getCustomValues();
		
		for(int i = 0; i < values.size(); i++){
		
			String fieldMapKey = values.get(i).key;
			String tableType = customIds.get(fieldMapKey).getType();
			
			String SQL = "INSERT INTO `" + valueTables.get(tableType) +  //$NON-NLS-1$
					"` (`owner_id`, `field_id`, `value`) VALUES (?, ?, ?) " + //$NON-NLS-1$
					"ON DUPLICATE KEY UPDATE `value` = ?"; //$NON-NLS-1$ 
			
			PreparedStatement updateCustom = null;
			
			/* We have a choice, create three statements for each table and 
			 * reuse for the entire loop
			 * or create a statement and destroy it for each loop
			 * 
			 * Probably should create three from a performance point of view
			 * Will test and see how it goes 
			 */
			try{
				
				updateCustom = database.getConnection().prepareStatement(SQL);
				updateCustom.setInt(1, id);
				updateCustom.setInt(2, customIds.get(fieldMapKey).getID());
				updateCustom.setString(3, values.get(i).value);
				updateCustom.setInt(4, id);
				
				success = updateCustom.execute();
				
				if(!success){//Check for updated rows, only get true if there was a result set
					
					int rowCount = updateCustom.getUpdateCount();
					
					if(rowCount > 0)
						success = true;
					
				}
				
			}catch(SQLException e){
				
				LOGGER.severe("Phone Archiver: Error while updating " + //$NON-NLS-1$
						"contact with custom fields " + id); //$NON-NLS-1$
				e.printStackTrace();
				
			}finally{
				
				close(updateCustom, null);
				
			}
			
		}
		
		return success;
		
	}

	/**
	 * Creates a new contact in the contacts table and adds any custom fields
	 * into the contact_values tables
	 * @param pr Phone record to work on
	 * @return inserted id
	 */
	private int createNewContact(PhoneRecord pr) {
		
		LOGGER.info("Phone Archiver: Creating new contact " + pr.getName()); //$NON-NLS-1$
		
		int id = -1;
		
		ArrayList<KeyValue> values = pr.getNonNullValues();
		values.add(new KeyValue("assigned_user_id", "" + pr.assignedTo));  //$NON-NLS-1$//$NON-NLS-2$
		
		//but retain new id
		//Custom fields use update as it will INSERT on DUP update
		if(values.size() > 0){
			
			String SQL = "INSERT INTO `contacts` (`" + values.get(0).key + "`";  //$NON-NLS-1$//$NON-NLS-2$
			String placeHolders = "?"; //$NON-NLS-1$
			
			if(values.size() > 1)
				for(int i = 1; i < values.size(); i++){
					
					SQL += ", `" + values.get(i).key + "`";  //$NON-NLS-1$//$NON-NLS-2$
					placeHolders += ", ?"; //$NON-NLS-1$
					
				}
			
			SQL += ") VALUES (" + placeHolders + ")"; //$NON-NLS-1$ //$NON-NLS-2$
			
			PreparedStatement insertContact = null;
			ResultSet results = null;
			
			try{
				
				insertContact = database.getConnection().prepareStatement(SQL, 
						Statement.RETURN_GENERATED_KEYS);
				
				for(int i = 1; i <= values.size(); i++)
					insertContact.setString(i, values.get(i - 1).value);
				
				boolean inserted = insertContact.execute();
				
				if(inserted || insertContact.getUpdateCount() > 0){
					
					results = insertContact.getGeneratedKeys();
					
					while(results.next())
						id = results.getInt(1);
					
					if(id != -1){
						
						updateCustomFields(id, pr);
						//add contact to user
						getFreeUsers().addContact(pr.assignedTo);
						
					}
					
				}
				
			}catch(SQLException e){
				
				LOGGER.severe("Phone Archiver: Error while inserting new " + //$NON-NLS-1$
						"contact " + pr.getName()); //$NON-NLS-1$
				e.printStackTrace();
				
			}finally{
				
				close(insertContact, results);
				
			}
			
		}else{
			
			LOGGER.info("Phone Archiver: Nothing to isnert for " + pr.getName()); //$NON-NLS-1$
			
		}
		
		return id;

	}

	/**
	 * Searches contacts table and returns record id associated with email
	 * @param email
	 * @return id or -1 if not found
	 *  0 = id
	 *  1 = default contact assigned to
	 */
	private int[] getContactIdByEmail(String email){
		
		int[] id = new int[2];
		
		for(int i = 0; i < 2; i++)
			id[i] = -1;
		
		LOGGER.finer("Phone Archiver: Looking up contact by email " + email); //$NON-NLS-1$
		
		PreparedStatement selectEmail = null;
		ResultSet results = null;
		
		try{
			
			selectEmail = database.getConnection().prepareStatement(
					"SELECT `id`, `assigned_user_id` FROM `contacts` " + //$NON-NLS-1$
					"WHERE `email` LIKE ?"); //$NON-NLS-1$
			
			selectEmail.setString(1, "%" + email + "%"); //$NON-NLS-1$ //$NON-NLS-2$
			
			if(selectEmail.execute()){
				
				results = selectEmail.getResultSet();
				
				while(results.next()){
				
					id[0] = results.getInt("id"); //$NON-NLS-1$
					id[1] = results.getInt("assigned_user_id"); //$NON-NLS-1$
					
					if(id[1] == 0)
						id[1] = -1;
					
				}
				
				LOGGER.finer("Phone Archiver: Got contact id " + id); //$NON-NLS-1$
				
			}
			
		}catch(SQLException e){
			
			LOGGER.severe("Phone Archiver: Error searching for contact by " + //$NON-NLS-1$
					"email " + email); //$NON-NLS-1$
			e.printStackTrace();
			
		}finally{
			
			close(selectEmail, results);
			
		}
		
		return id;
		
	}
	
	/**
	 * Searches contacts table and returns record id associated with phone number
	 * @param phone
	 * @return id or -1 if not found 
	 *  0 = id
	 *  1 = default contact assigned to id
	 */
	private int[] getContactIdByPhone(String phone){
		
		int[] id = new int[2];
		
		for(int i = 0; i < 2; i++)
			id[i] = -1;
		
		LOGGER.finer("Phone Archiver: Looking up contact by phone " + phone); //$NON-NLS-1$
		PreparedStatement selectPhone = null;
		ResultSet results = null;
		
		try{
			
			selectPhone = database.getConnection().prepareStatement(
					"SELECT `id`, `assigned_user_id` FROM `contacts` WHERE `phone` LIKE ?"); //$NON-NLS-1$
			
			selectPhone.setString(1, "%" + phone + "%"); //$NON-NLS-1$ //$NON-NLS-2$
			
			if(selectPhone.execute()){
				
				results = selectPhone.getResultSet();
				
				while(results.next()){
					
					id[0] = results.getInt("id"); //$NON-NLS-1$
					id[1] = results.getInt("assigned_user_id"); //$NON-NLS-1$
					
					if(id[1] == 0)
						id[1] = -1;
					
				}
				
				LOGGER.finer("Phone Archiver: Got contact id " + id); //$NON-NLS-1$
				
			}
			
		}catch(SQLException e){
			
			LOGGER.severe("Phone Archiver: Error searching for contact by " + //$NON-NLS-1$
					"phone " + phone); //$NON-NLS-1$
			e.printStackTrace();
			
		}finally{
			
			close(selectPhone, results);
			
		}
		
		return id;
		
	}
	
}