package com.thevoiceasia.phone;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

import org.apache.commons.validator.routines.EmailValidator;

import com.thevoiceasia.database.DatabaseHelper;
import com.thevoiceasia.database.FieldMap;
import com.thevoiceasia.database.KeyValue;
import com.thevoiceasia.messages.MessageArchiver;

public class PhoneReader extends MessageArchiver {

	private DatabaseHelper phoneDatabase = null;
	private HashMap<String, Integer> userIds = new HashMap<String, Integer>();
	private HashMap<String, FieldMap> customIds = 
			new HashMap<String, FieldMap>();
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
		
		phoneDatabase = new DatabaseHelper(phoneHost, phoneDbase, phoneUser, 
				phonePass);
		
		populateUserIDs();
		populateCustomIDs();
		
		valueTables.put("s", "contact_values_small"); //$NON-NLS-1$ //$NON-NLS-2$
		valueTables.put("m", "contact_values_medium");  //$NON-NLS-1$//$NON-NLS-2$
		valueTables.put("l", "contact_values_large"); //$NON-NLS-1$ //$NON-NLS-2$
		
	}

	/**
	 * Grabs all the custom fields from the contact_fields table
	 */
	private void populateCustomIDs() {

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
							results.getString("map")); //$NON-NLS-1$
					
					customIds.put(results.getString("map"), map); //$NON-NLS-1$
				
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

	/**
	 * Connects to Phone database
	 */
	public void connectToPhoneDB(){
		
		phoneDatabase.connect();
		
	}
	
	/**
	 * Disconnects from Phone database
	 */
	public void disconnectFromPhoneDB(){
		
		phoneDatabase.disconnect();
		
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
					
					archiver.connectToDB();
					archiver.getMessages();
					archiver.disconnectFromDB();
					
					LOGGER.finest("Phone Archiver: Sleeping for " + CHECK_PERIOD); //$NON-NLS-1$
					sleep(1000 * CHECK_PERIOD);
					
				} catch (InterruptedException e) {
					
					LOGGER.info("Phone Archiver: Interrupted, exiting"); //$NON-NLS-1$
					e.printStackTrace();
					go = false;
					
				} finally {
					
					archiver.disconnectFromDB();
					
				}
				
			}
			
		}else{
			
			System.out.println("USAGE: DBHOST DBUSER DBPASS DBASE MAILHOST MAILUSER MAILPASS MAILDB MESSAGESTOREPATH"); //$NON-NLS-1$
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
	 * Get Messages from the phone database
	 */
	public void getMessages() {
		
		int id = getLastReadID();
		int lastId = id;
		phoneDatabase.connect();
		
		Statement selectPhones = null;
		ResultSet results = null;
		
		try{
		
			String SQL = "SELECT * FROM `call_records` WHERE id > " + id + //$NON-NLS-1$
					" ORDER BY id ASC LIMIT " + MAX_RECORDS;  //$NON-NLS-1$
			selectPhones = phoneDatabase.getConnection().createStatement();
			
			if(selectPhones.execute(SQL)){
				
				results = selectPhones.getResultSet();
				
				while(results.next())
					id = processPhoneMessage(results);
				
			}
			
		}catch(SQLException e){
			
			LOGGER.severe("Phone Archiver: Error while reading phone records " + id); //$NON-NLS-1$
			e.printStackTrace();
			
		}finally{
			
			close(selectPhones, results);
			
			if(id != -1 && lastId != id)
				updateLastProcessedID(id);
			
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
			
			pr.gender = results.getString("gender").substring(0, 1) //$NON-NLS-1$
					.toUpperCase();
			
			//Answered By 1234 (1234) (user@pc)
			String key = results.getString("answeredBy"); //$NON-NLS-1$
			
			int index = key.indexOf("@"); //$NON-NLS-1$
			
			if(index != -1){
			
				key = key.substring(index);
				
				index = key.indexOf("("); //$NON-NLS-1$
				
				while(index != -1){
					
					key = key.substring(index);
					index = key.indexOf("("); //$NON-NLS-1$
					
				}
				
			}
			
			if(userIds.containsKey(key))
				pr.answeredBy = userIds.get(key);
			else
				pr.answeredBy = -1;
			
			
			for(int i = 0; i < STRING_FIELDS.length; i++){
				
				String value = checkNull(results.getString(STRING_FIELDS[i])
						.trim());
				
				pr.setString(STRING_FIELDS[i], value);
				
			}
			
			for(int i = 0; i < CUSTOM_FIELDS.length; i++){
				
				key = checkNull(results.getString(CUSTOM_FIELDS[i])
						.toLowerCase().trim());
			
				if(customIds.containsKey(CUSTOM_FIELDS[i])){
				
					int dataId = customIds.get(CUSTOM_FIELDS[i]).getDataId(key);
					
					if(dataId != -1)
						pr.setCustomField(CUSTOM_FIELDS[i], dataId);
					
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
	 * Checks for existing contact and updates or creates new one
	 * if not found by phone number or email
	 * 
	 * We can't rely on names so we'll have to create a new one if we only
	 * have the name to go on
	 * @param pr
	 */
	private void updateDatabase(PhoneRecord pr) {
		
		int id = -1;
		
		if(pr.getNumber() != null)
			id = getContactIdByPhone(pr.getNumber());
		
		if(id == -1 && pr.email != null)
			id = getContactIdByEmail(pr.email);
		
		if(id == -1)
			createNewContact(pr);
		else
			updateContact(id, pr);
		
	}
	
	/**
	 * Updates the contact represented by the PhoneRecord
	 * @param id id of contact we found in the contacts table
	 * @param pr these values are used to update
	 * @return
	 */
	private boolean updateContact(int id, PhoneRecord pr) {
		
		boolean success = false;
		
		//Update Contact Table
		ArrayList<KeyValue> values = pr.getNonNullValues();
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
				
				updateContact.setInt(values.size() + 2, id);
				
				if(updateContact.execute())
					success = updateCustomFields(id, pr);
				
			}catch(SQLException e){
				
				LOGGER.severe("Phone Archiver: Error while updating " + //$NON-NLS-1$
						"contact" + id); //$NON-NLS-1$
				e.printStackTrace();
				
			}finally{
				
				close(updateContact, null);
				
			}
			
		}else{
			
			success = true;
			LOGGER.info("Phone Archiver: Nothing to update for " + id); //$NON-NLS-1$
			
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
			
			try{
				
				updateCustom = database.getConnection().prepareStatement(SQL);
				updateCustom.setInt(1, id);
				updateCustom.setInt(2, customIds.get(fieldMapKey).getID());
				updateCustom.setString(3, values.get(i).value);
				updateCustom.setInt(4, id);
				
				success = updateCustom.execute();
				
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
	 * @return true if successful
	 */
	private boolean createNewContact(PhoneRecord pr) {
		
		boolean success = false;
		ArrayList<KeyValue> values = pr.getNonNullValues();
				
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
				
				for(int i = 0; i <= values.size(); i++)
					insertContact.setString(i, values.get(i).value);
				
				if(insertContact.execute()){
					
					results = insertContact.getGeneratedKeys();
					
					int id = -1;
					
					while(results.next())
						id = results.getInt(0);
					
					if(id != -1)
						success = updateCustomFields(id, pr);
					
				}
				
			}catch(SQLException e){
				
				LOGGER.severe("Phone Archiver: Error while inserting new " + //$NON-NLS-1$
						"contact" + pr.getName()); //$NON-NLS-1$
				e.printStackTrace();
				
			}finally{
				
				close(insertContact, results);
				
			}
			
		}else{
			
			success = true;
			LOGGER.info("Phone Archiver: Nothing to isnert for " + pr.getName()); //$NON-NLS-1$
			
		}
		
		return success;

	}

	/**
	 * Searches contacts table and returns record id associated with email
	 * @param email
	 * @return id or -1 if not found
	 */
	private int getContactIdByEmail(String email){
		
		int id = -1;
		
		PreparedStatement selectEmail = null;
		ResultSet results = null;
		
		try{
			
			selectEmail = database.getConnection().prepareStatement(
					"SELECT `id` FROM `contacts` WHERE `email` LIKE '%?%'"); //$NON-NLS-1$
			
			selectEmail.setString(1, email);
			
			if(selectEmail.execute()){
				
				results = selectEmail.getResultSet();
				
				while(results.next())
					id = results.getInt("id"); //$NON-NLS-1$
				
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
	 */
	private int getContactIdByPhone(String phone){
		
		int id = -1;
		
		PreparedStatement selectPhone = null;
		ResultSet results = null;
		
		try{
			
			selectPhone = database.getConnection().prepareStatement(
					"SELECT `id` FROM `contacts` WHERE `phone` LIKE '%?%'"); //$NON-NLS-1$
			
			selectPhone.setString(1, phone);
			
			if(selectPhone.execute()){
				
				results = selectPhone.getResultSet();
				
				while(results.next())
					id = results.getInt("id"); //$NON-NLS-1$
				
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