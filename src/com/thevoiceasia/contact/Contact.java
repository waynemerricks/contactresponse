package com.thevoiceasia.contact;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;

import com.thevoiceasia.database.DatabaseHelper;

public class Contact {

	private String name = null, email = null, gender = null, phoneNumber = null, 
			address = null, city = null, country = null;
	private int id = -1;
	
	public Contact(DatabaseHelper database, String from, String name2,
			boolean sms) {
		// TODO Auto-generated constructor stub
	}

	public void updateWithWebForm(String form){
	
		//TODO update based on the form
		
	}
	
	/**
	 * Returns the record id of this contact in the contacts table
	 * @return
	 */
	public int getID(){
		
		return id;
		
	}
	
	/**
	 * Returns the "name" of this contact e.g. Fred Rogers
	 * @return name or null if not set
	 */
	public String getName(){
		
		return name;
		
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
	
	private void updateCustomFields(Contact contact, Contact existing,
			Connection mysql) {
		// TODO Auto-generated method stub
		
	}

	
}
