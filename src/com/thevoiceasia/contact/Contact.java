package com.thevoiceasia.contact;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.thevoiceasia.database.DatabaseHelper;

public class Contact {

	private String name = null, email = null, gender = null, phoneNumber = null,
			language = null, photo = null, status = null, autoReply = null;
	private int id = -1, languageID = -1, assignedUser = -1;
	private long created = -1, updated = -1;
	private boolean sms = false;
	private HashMap<String, String> custom = new HashMap<String, String>();
	
	private DatabaseHelper database = null;

	private static final Logger LOGGER = Logger.getLogger("com.thevoiceasia.contact.Contact"); //$NON-NLS-1$
	private static final Level LEVEL = Level.INFO;//Logging level of this class
	
	/**
	 * Creates a contact object with default values (no DB lookups)
	 */
	public Contact(){
	
		//All set at instantiation to null or -1
		
	}
	
	/**
	 * Creates a Contact and populates with info from the database 
	 * @param database
	 * @param email Email/SMS Email coming in
	 * @param name Contact name if we got it from the email
	 * @param sms true if this is an SMS message not an email
	 */
	public Contact(DatabaseHelper database, String email, String name,
			boolean sms) {
		
		this.name = name;
		this.sms = sms;
		this.database = database;
		
		/* Search via email or sms, don't use name as identifier as we have no 
		 * way to tell which Mr Singh is this Mr Singh.
		 * 
		 * This will inevitably lead to some duplicates if we have old contacts
		 * who don't have an email/sms in the db yet
		 */
		if(sms){//XpressMS = 1234567890@sms.xpressms.com
			
			phoneNumber = email.split("@")[0];  //$NON-NLS-1$
			populateByPhone();
			
		}else{
			
			this.email = email;
			populateByEmail();
			
		}
		
	}

	/**
	 * Populates contact from DB by phone number
	 * Will create if contact does not exist
	 */
	private void populateByPhone(){
	
		if(!populate("WHERE `phone` LIKE ?", "%" + phoneNumber)) //$NON-NLS-1$ //$NON-NLS-2$
			createNewContact();
		
	}
	
	/**
	 * Populates contact from DB by email
	 * Will create if contact does not exist
	 */
	private void populateByEmail(){
		
		if(!populate("WHERE `email` = ?", email)) //$NON-NLS-1$
			createNewContact();
	}
	
	/**
	 * Grabs contact info from the database
	 * @param whereClause e.g. WHERE email = ?
	 * @param searchTerm variable to bind to where clause
	 * @return true if existing contact
	 */
	private boolean populate(String whereClause, String searchTerm){
		
		boolean existing = false;
		
		Connection mysql = null;
		PreparedStatement select = null;
		ResultSet contact = null;
		
		String SQL = 
				"SELECT `contacts`.`id`, `contacts`.`name`, `contacts`.`gender`, " + //$NON-NLS-1$
				"	`languages`.`language`, `contacts`.`language_id`, " + //$NON-NLS-1$
				"	`contacts`.`phone`, " + //$NON-NLS-1$
				"	`contacts`.`email`, `contacts`.`photo`, " + //$NON-NLS-1$
				"	`contacts`.`assigned_user_id`, `contacts`.`created`, " + //$NON-NLS-1$
				"	`contacts`.`updated`, `contacts`.`status`, " + //$NON-NLS-1$
				"	`contacts`.`auto_reply`" + //$NON-NLS-1$
				"FROM `contacts` INNER JOIN `languages` ON " + //$NON-NLS-1$
				"	`contacts`.`language_id` = `languages`.`id` "; //$NON-NLS-1$
		
		try{
			
			mysql = database.getConnection();
			select = mysql.prepareStatement(SQL + whereClause);
			select.setString(1, searchTerm);
			
			if(select.execute()){
				
				contact = select.getResultSet();
				
				while(contact.next()){
					
					//ID
					id = contact.getInt("id"); //$NON-NLS-1$
					
					//NAME: update DB if we have a better name than DB
					//else use name from DB
					if(contact.getString("name").equals("Unknown") &&  //$NON-NLS-1$ //$NON-NLS-2$
							name != null)
						updateName();
					else if(!contact.getString("name").equals("Unknown")) //$NON-NLS-1$ //$NON-NLS-2$
						name = contact.getString("name"); //$NON-NLS-1$
					
					//GENDER
					gender = contact.getString("gender"); //$NON-NLS-1$
					
					//Language ID
					languageID = contact.getInt("language_id"); //$NON-NLS-1$
					
					//Language
					language = contact.getString("language"); //$NON-NLS-1$
					
					//Phone
					if(checkNull(contact.getString("phone")) == null &&  //$NON-NLS-1$
							phoneNumber != null)
						updatePhone();
					else if(checkNull(contact.getString("phone")) != null) //$NON-NLS-1$
						phoneNumber = contact.getString("phone"); //$NON-NLS-1$
					
					//Email
					if(checkNull(contact.getString("email")) == null &&  //$NON-NLS-1$
							email != null)
						updateEmail();
					else if(checkNull(contact.getString("phone")) != null) //$NON-NLS-1$
						phoneNumber = contact.getString("phone"); //$NON-NLS-1$
					
					//Photo
					photo = contact.getString("photo"); //$NON-NLS-1$
					
					//Assigned User
					assignedUser = contact.getInt("assigned_user_id"); //$NON-NLS-1$
					
					//Created
					created = contact.getLong("created"); //$NON-NLS-1$
					
					//Updated
					if(updated != -1) //Set by updateName || Phone || Email
						updated = contact.getLong("updated"); //$NON-NLS-1$
					
					//Status
					status = contact.getString("status"); //$NON-NLS-1$
					
					//Auto Reply
					autoReply = contact.getString("auto_reply"); //$NON-NLS-1$
					
					//Flag existing success
					populateCustomFields();
					existing = true;
					
				}
				
			}
			
		}catch(SQLException e){
			
			LOGGER.severe("Error while getting contact info from db for id: " + id); //$NON-NLS-1$
			e.printStackTrace();
			
		}finally{
			
			if(contact != null){
				
				try{
					contact.close();
				}catch(Exception e){}
				
				contact = null;
				
			}
			
			if(select != null){
				
				try{
					select.close();
				}catch(Exception e){}
				Contact c = new Contact();
				
				select = null;
				
			}
			
		}
		
		return existing;
		
	}
	
	/**
	 * Updates this contacts email in the DB with the one the object holds
	 */
	private void updateEmail(){
		updateField("email", email); //$NON-NLS-1$
	}
	
	/**
	 * Updates this contacts phone in the DB with the one the object holds
	 */
	private void updatePhone(){
		updateField("phone", phoneNumber); //$NON-NLS-1$
	}
	
	/**
	 * Updates this contacts name in the DB with the one the object holds
	 */
	private void updateName(){
		updateField("name", name); //$NON-NLS-1$
	}
	
	/**
	 * Sets the name of the given contact
	 * @param id id of record to change
	 * @param name name to change it to
	 * @return true if successfully updated
	 */
	private boolean updateField(String name, String value) {
		
		boolean success = false;
		
		String SQL = "UPDATE `contacts` SET `" + name + "` = ?, `updated` = ? " +//$NON-NLS-1$ //$NON-NLS-2$
				"WHERE `id` = ?"; //$NON-NLS-1$ 
		
		LOGGER.info("Updating " + name + " for " + id + " to " + value); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		
		Connection mysql = database.getConnection();
		
		PreparedStatement updateContact = null;
		
		try{
			
			//Bind all variables to statement
			updateContact = mysql.prepareStatement(SQL);
			updateContact.setString(1, value);
			updateContact.setString(2, new SimpleDateFormat(
					"yyyyMMddHHmmss").format(new Date())); //$NON-NLS-1$
			updateContact.setInt(3, id);
			
			//Execute it
			int rows = updateContact.executeUpdate();
			
			if(rows > 0)
				success = true;
				
		}catch(SQLException e){
			
			LOGGER.severe("SQL Error while updating " + name + " on contact " + id + //$NON-NLS-1$ //$NON-NLS-2$
					" to " + value);  //$NON-NLS-1$
			e.printStackTrace();
			
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
	 * Quick identifier string prefers name, email/phone if no name
	 * @return whatever is not null
	 */
	private String getIdentifierName(){
		
		String identifier = null;
				
		if(name != null)
			identifier = name;
		else if(email != null && !sms)
			identifier = email;
		else if(phoneNumber != null && sms)
			identifier = phoneNumber;
		
		return identifier;
		
	}
	
	/**
	 * Actually runs an insert with the given SQL and values, only works
	 * if values are all strings!
	 * @param SQL 
	 * @param values
	 * @return
	 */
	private boolean insertNewContact(String SQL, String[] values){
		
		boolean inserted = false;
		
		LOGGER.info("Creating new contact for " + getIdentifierName()); //$NON-NLS-1$
		
		Connection mysql = database.getConnection();
		PreparedStatement insertContact = null;
		ResultSet contactIDs = null;
		
		try{
			
			//Bind all variables to statement
			insertContact = mysql.prepareStatement(SQL, Statement.RETURN_GENERATED_KEYS);
			
			for(int i = 1; i <= values.length; i++)
				insertContact.setString(i, values[i]);
			
			//Execute it
			int rows = insertContact.executeUpdate();
			
			if(rows > 0){
			
				contactIDs = insertContact.getGeneratedKeys();
				
				while(contactIDs.next())
					id = contactIDs.getInt(1);
				
				inserted = true;
				
			}
			
		}catch(SQLException e){
			
			e.printStackTrace();
			LOGGER.severe("SQL Error while creating new contact for " + //$NON-NLS-1$
					getIdentifierName()); 
			
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
		
		return inserted;
		
	}
	
	/**
	 * Creates a new contact using object creation info
	 * @return true if successful
	 */
	private boolean createNewContact() {
		
		String SQL = "INSERT INTO `contacts` (`email`) VALUES (?)"; //$NON-NLS-1$
		String[] values = {email};
		
		if(name != null && !sms){
			
			SQL = "INSERT INTO `contacts` (`email`, `name`) VALUES (?, ?)"; //$NON-NLS-1$
			values = new String[]{email, name};
			
		}else if(name == null && sms){
			
			SQL = "INSERT INTO `contacts` (`phone`) VALUES (?)"; //$NON-NLS-1$
			values = new String[]{phoneNumber};
			
		}else if(name != null && sms){ //I think name will always be null for an SMS but just in case
		
			SQL = "INSERT INTO `contacts` (`phone`, `name`) VALUES (?, ?)"; //$NON-NLS-1$
			values = new String[]{phoneNumber, name};
			
		}
			
		return insertNewContact(SQL, values);
		
	}
	
	/**
	 * Use form email to update this contact as appropriate
	 * @param form
	 */
	public void updateWithWebForm(String form){
	
		//TODO update based on the form
		String[] lines = form.split("\n"); //$NON-NLS-1$
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
	 * Helper method to change db lookup of "null" to java null
	 * @param check
	 * @return
	 */
	private String checkNull(String check){
	
		if(check.equalsIgnoreCase("null") || check.trim().length() == 0) //$NON-NLS-1$
			check = null;
		
		return check;
		
	}
	
	private void populateCustomFields(){
		//TODO close resultset on this and one of the other contact things
		
		String[] tables = {"contact_values_large", "contact_values_medium",  //$NON-NLS-1$ //$NON-NLS-2$
				"contact_values_small"}; //$NON-NLS-1$
		
		LOGGER.info("Getting custom for Contact: " + getIdentifierName()); //$NON-NLS-1$
		
		for (int i = 0; i < tables.length; i++){
			
			String SQL = "SELECT `contact_fields`.`label`, `" + tables[i] +  //$NON-NLS-1$
				"`.`value` FROM `contact_fields` INNER JOIN `" + tables[i] + //$NON-NLS-1$
				"` ON `" + tables[i] + "`.`field_id` = `contact_fields`.`id` " + //$NON-NLS-1$ //$NON-NLS-2$
				"WHERE `" + tables[i] + "`.`owner_id` = ?"; //$NON-NLS-1$ //$NON-NLS-2$
			
			
			PreparedStatement selectContact = null;
			ResultSet customResults = null;
			
			try{
				
				//Bind all variables to statement
				selectContact = database.getConnection().prepareStatement(SQL);
				selectContact.setInt(1, id);
				
				//Execute it
				selectContact.execute();
				customResults = selectContact.getResultSet();
				
				while(customResults.next())
					custom.put(customResults.getString("label"),  //$NON-NLS-1$
							customResults.getString("value")); //$NON-NLS-1$
					
			}catch(SQLException e){
				
				e.printStackTrace();
				LOGGER.severe("SQL Error while getting custom fields for " + //$NON-NLS-1$
						"contact " + getIdentifierName() + " on table " + //$NON-NLS-1$ //$NON-NLS-2$
						tables[i] + id); 
				
			}finally{
				
				if(selectContact != null){//Close Statement
		        	try{
		        		selectContact.close();
		        		selectContact = null;
		        	}catch(Exception e){}
		        }
		    	
			}
			
		}
		
	}
	
	private void updateCustomFields(Contact contact, Contact existing,
			Connection mysql) {
		// TODO Auto-generated method stub
		
	}

	
}
