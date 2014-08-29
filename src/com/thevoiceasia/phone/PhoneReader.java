package com.thevoiceasia.phone;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;

import org.apache.commons.validator.routines.EmailValidator;

import com.thevoiceasia.database.DatabaseHelper;
import com.thevoiceasia.messages.MessageArchiver;

public class PhoneReader extends MessageArchiver {

	private DatabaseHelper phoneDatabase = null;
	private HashMap<String, Integer> userIds = new HashMap<String, Integer>();
	
	public PhoneReader(String host, String user, String pass, String dbase,
			String phoneHost, String phoneUser, String phonePass, String phoneDbase,
			String archivePath){
		
		super(host, user, pass, dbase, archivePath);
		
		phoneDatabase = new DatabaseHelper(phoneHost, phoneDbase, phoneUser, 
				phonePass);
		
		populateUserIDs();
		
	}
	
	private void populateUserIDs() {
		// TODO Auto-generated method stub
		
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
			
			if(results != null){
				
				try{
					results.close();
				}catch(Exception e){}
				
				results = null;
				
			}
			
			if(selectID != null){
				
				try{
					selectID.close();
				}catch(Exception e){}
				
				selectID = null;
				
			}
			
		}
		 
		return lastID;
		
	}
	
	/**
	 * Get Messages from the phone database
	 */
	public void getMessages() {
		
		int id = getLastReadID();
		
		phoneDatabase.connect();
		
		Statement selectPhones = null;
		ResultSet results = null;
		
		try{
		
			String SQL = "SELECT * FROM `call_records` WHERE id > " + id + " ORDER BY id ASC";  //$NON-NLS-1$//$NON-NLS-2$
			selectPhones = phoneDatabase.getConnection().createStatement();
			
			if(selectPhones.execute(SQL)){
				
				results = selectPhones.getResultSet();
				
				while(results.next())
					processPhoneMessage(results);
				
			}
			
		}catch(SQLException e){
			
			LOGGER.severe("Phone Archiver: Error while reading phone records " + id); //$NON-NLS-1$
			e.printStackTrace();
			
		}finally{
			
			if(results != null){
				
				try{
					results.close();
				}catch(Exception e){}
				
				results = null;
				
			}
			
			if(selectPhones != null){
				
				try{
					selectPhones.close();
				}catch(Exception e){}
				
				selectPhones = null;
				
			}
			
		}
		
		phoneDatabase.disconnect();
		
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
	 */
	private void processPhoneMessage(ResultSet results) {
		
		int lastID = -1;
		
		try{
			
			PhoneRecord pr = new PhoneRecord();
			
			lastID = results.getInt("id"); //$NON-NLS-1$
			
			pr.name = checkNull(results.getString("caller_name")); //$NON-NLS-1$
			pr.setAddress(results.getString("address"),  //$NON-NLS-1$
					results.getString("postcode")); //$NON-NLS-1$
			
			String email = checkNull(results.getString("email")); //$NON-NLS-1$
			
			if(email != null){
				
				EmailValidator ev = EmailValidator.getInstance(false);
				
				if(ev.isValid(email))
					pr.email = email;
				
			}
			
			//Answered By 1234 (1234) (user@pc)
			String answered = results.getString("answeredBy"); //$NON-NLS-1$
			
			int index = answered.indexOf("@"); //$NON-NLS-1$
			
			if(index != -1){
			
				answered = answered.substring(index);
				
				index = answered.indexOf("("); //$NON-NLS-1$
				
				while(index != -1){
					
					answered = answered.substring(index);
					index = answered.indexOf("("); //$NON-NLS-1$
					
				}
				
			}
			
			if(userIds.containsKey(answered))
				pr.answeredBy = userIds.get(answered);
			else
				pr.answeredBy = -1;
			//TODO other fields
		}catch(SQLException e){
			
			LOGGER.severe("Phone Archiver: Error getting record information" + lastID); //$NON-NLS-1$
			e.printStackTrace();
			
		}
		
	}

}
