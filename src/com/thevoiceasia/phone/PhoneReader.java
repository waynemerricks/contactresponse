package com.thevoiceasia.phone;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;

import org.apache.commons.validator.routines.EmailValidator;

import com.thevoiceasia.contact.Contact;
import com.thevoiceasia.database.DatabaseHelper;
import com.thevoiceasia.database.FieldMap;
import com.thevoiceasia.messages.MessageArchiver;

public class PhoneReader extends MessageArchiver {

	private DatabaseHelper phoneDatabase = null;
	private HashMap<String, Integer> userIds = new HashMap<String, Integer>();
	private HashMap<String, FieldMap> customIds = 
			new HashMap<String, FieldMap>();
	private static final String[] CUSTOM_FIELDS = {"language", "religion",  //$NON-NLS-1$ //$NON-NLS-2$
		"journeyStage", "topic"}; //$NON-NLS-1$ //$NON-NLS-2$
	private static final String[] STRING_FIELDS = {"caller_name", "number",   //$NON-NLS-1$//$NON-NLS-2$
		"conversation", "location", "topic"}; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	private static final int MAX_RECORDS = 10;
	
	public PhoneReader(String host, String user, String pass, String dbase,
			String phoneHost, String phoneUser, String phonePass, String phoneDbase,
			String archivePath){
		
		super(host, user, pass, dbase, archivePath);
		
		phoneDatabase = new DatabaseHelper(phoneHost, phoneDbase, phoneUser, 
				phonePass);
		
		populateUserIDs();
		populateCustomIDs();
		
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
			
			new Contact(database, pr);
			
			lastID = results.getInt("id"); //$NON-NLS-1$
			
		}catch(SQLException e){
			
			LOGGER.severe("Phone Archiver: Error getting record information" + lastID); //$NON-NLS-1$
			e.printStackTrace();
			
		}
		
		return lastID;
		
	}
	
}