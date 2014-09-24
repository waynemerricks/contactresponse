package com.thevoiceasia.contact;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.validator.routines.EmailValidator;

import com.thevoiceasia.database.DatabaseHelper;
import com.thevoiceasia.database.FieldMap;
import com.thevoiceasia.user.FreeUsers;

public class Contact {

	private String name = null, email = null, gender = null, phoneNumber = null,
			  status = null, language = null, autoReply = null, 
			  mappedLanguage = null;//photo = null, 
	private int id = -1, assignedUser = -1, languageID = -1; 
	private long updated = -1;//, created = -1; 
	private boolean sms = false;
	private HashMap<String, String> custom = new HashMap<String, String>();
	private FreeUsers users = null;
	private int currentFreeUser = -1;
	private boolean error = false, idInitialise = false;
	
	private DatabaseHelper database = null;

	private static final Logger LOGGER = Logger.getLogger("com.thevoiceasia.contact.Contact"); //$NON-NLS-1$
	private static final Level LEVEL = Level.INFO;//Logging level of this class
	private static final String[] TABLES = {"contact_values_large",  //$NON-NLS-1$
		"contact_values_medium", "contact_values_small"}; //$NON-NLS-1$ //$NON-NLS-2$
	
	/**
	 * Creates a Contact and populates with info from the database 
	 * @param database
	 * @param email Email/SMS Email coming in
	 * @param name Contact name if we got it from the email
	 * @param sms true if this is an SMS message not an email
	 */
	public Contact(DatabaseHelper database, String email, String name,
			boolean sms, FreeUsers users) {
		
		LOGGER.setLevel(LEVEL);
		
		this.name = name;
		this.sms = sms;
		this.database = database;
		this.users = users;
		
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
	 * Creates a contact object with default values (no DB lookups)
	 */
	public Contact(){
	
		//All set at instantiation to null or -1
		LOGGER.setLevel(LEVEL);
		
	}
	
	/**
	 * Populate this contact by the record id
	 * @param database
	 * @param id
	 */
	public Contact(DatabaseHelper database, int id){
		
		LOGGER.setLevel(LEVEL);
		this.database = database;
		idInitialise = true;
		error = !populate("WHERE `contacts`.`id` = ?", "" + id); //$NON-NLS-1$ //$NON-NLS-2$
		
	}
	

	/**
	 * Returns languageID
	 * @return -1 by default/not set
	 *  		1 = DB default
	 *  		other = manually set
	 */
	public int getLanguageID(){
		
		return languageID;
		
	}
	
	/**
	 * Returns the language name/label
	 * @return e.g. English
	 */
	public String getLanguageName(){
		
		return language;
		
	}
	
	/**
	 * Returns the name of the language we're mapped to
	 * @return
	 */
	public String getMappedLanguage(){
		
		return mappedLanguage;
		
	}
	
	/**
	 * Checks the auto reply flag for this contact which signifies whether
	 * user has opted out of the auto replies or not
	 * @return true if contact still wants auto replies (true/Y by default)
	 */
	public boolean wantsAutoReply(){
		
		boolean wants = false;
		
		if(autoReply.equals("Y")) //$NON-NLS-1$
			wants = true;
		
		return wants;
		
	}
	
	/**
	 * Returns true if this contact has Junk sender status
	 * @return
	 */
	public boolean isJunk(){
		
		boolean junk = false;
		
		if(status != null && status.equalsIgnoreCase("J")) //$NON-NLS-1$
			junk = true;
		
		return junk;
		
	}
	
	/**
	 * Helper method to return if email is set and valid
	 * @return
	 */
	public boolean hasEmail(){
		
		boolean valid = false;
		
		if(email != null){
			
			EmailValidator ev = EmailValidator.getInstance(false);
				
			if(ev.isValid(email))
				valid = true;
			
		}
		
		return valid;
		
	}
	
	/**
	 * Gets the contacts phone number
	 * @return
	 */
	public String getNumber(){
		
		return phoneNumber;
		
	}
	
	/**
	 * Returns this contacts email address
	 * @return
	 */
	public String getEmail(){
		
		return email;
		
	}
	
	/**
	 * Helper method to return if number is set and valid for an SMS reply
	 * @return
	 */
	public boolean hasSMSNumber(){
		
		boolean valid = false;
		
		if(phoneNumber != null){
			
			if(!phoneNumber.startsWith("0") && phoneNumber.length() == 12){ //$NON-NLS-1$
				
				valid = true;//need 12 numbers for an SMS
				
				for(int i = 0; i < phoneNumber.length(); i++){
					
					char c = phoneNumber.charAt(i);
					
					if(!Character.isDigit(c))
						valid = false;//if we have odd chars in the number its not a usable SMS number
					
				}
				
			}
			
		}
		
		return valid;
		
	}
	
	/**
	 * True if errors while running populate (only initialised with 
	 * Contact(db, id))
	 * @return
	 */
	public boolean hasErrors(){
		
		return error;
		
	}
	
	/**
	 * Populates contact from DB by phone number
	 * Will create if contact does not exist
	 */
	private void populateByPhone(){
	
		if(!populate("WHERE `phone` LIKE ?", "%" + phoneNumber) && //$NON-NLS-1$ //$NON-NLS-2$
				!checkCustomPhone("%phoneNumber")) //$NON-NLS-1$
			createNewContact();
		
	}
	
	private boolean checkCustomPhone(String phone){
		
		boolean found = false;
		
		/*PreparedStatement select = null;
		ResultSet results = null;
		
		String SQL = "SELECT `owner_id` FROM `contact_values_small` " + //$NON-NLS-1$
				"WHERE `id` = 4 AND `value` LIKE ?"; //$NON-NLS-1$
		
		try{
			
			select = database.getConnection().prepareStatement(SQL);
			select.setString(1, phone);
			
			if(select.execute()){
				
				results = select.getResultSet();
				//TODO Multiple contacts with same phone number? Pick oldest?
				
			}
			
		}catch(SQLException e){
			
			LOGGER.severe("Error looking up custom phones"); //$NON-NLS-1$
			e.printStackTrace();
			
		}finally{
			
			close(select, results);
			
		}*/
		
		
		return found;
		
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
				"	`languages`.`language`, `languages`.`mappedTo`," + //$NON-NLS-1$
				"   `contacts`.`language_id`, " + //$NON-NLS-1$
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
					
					//Get Mapped Language
					if(contact.getInt("mappedTo") == 0) //$NON-NLS-1$
						mappedLanguage = language;
					else
						mappedLanguage = getMappedLanguage(contact.getInt(
							"mappedTo")); //$NON-NLS-1$
					
					//Phone
					if(checkNull(contact.getString("phone")) == null &&  //$NON-NLS-1$
							phoneNumber != null)
						updatePhone();
					else if(checkNull(contact.getString("phone")) != null) //$NON-NLS-1$
						phoneNumber = contact.getString("phone"); //$NON-NLS-1$
					
					//Email
					if(!idInitialise){
						
						if(checkNull(contact.getString("email")) == null &&  //$NON-NLS-1$
								email != null)
							updateEmail();//Else Phone
						else if(checkNull(contact.getString("phone")) != null) //$NON-NLS-1$
							phoneNumber = contact.getString("phone"); //$NON-NLS-1$
					}else{
						
						//Just read the details from the DB
						if(checkNull(contact.getString("email")) != null) //$NON-NLS-1$
							email = contact.getString("email"); //$NON-NLS-1$
						
						if(checkNull(contact.getString("phone")) != null) //$NON-NLS-1$
							phoneNumber = contact.getString("phone"); //$NON-NLS-1$
						
					}
					
					//Photo
					//photo = contact.getString("photo"); //$NON-NLS-1$
					
					//Assigned User
					assignedUser = contact.getInt("assigned_user_id"); //$NON-NLS-1$
					
					if(!idInitialise){
						
						int nextFreeUser = users.getNextAvailableUser();
						
						if(assignedUser == 0 && nextFreeUser != -1){//Update assignedUser						
							assignedUser = nextFreeUser;
							currentFreeUser = nextFreeUser;
							updateAssignedUser();
							
						}
						
					}
					//Created
					//created = contact.getLong("created"); //$NON-NLS-1$
					
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
			
			close(select, contact);
			
		}
		
		return existing;
		
	}
	
	/**
	 * Gets the language which this id is mapped to e.g. Unknown = English,
	 * Punjabi = Roman Hindi etc
	 * Used solely for sending out replies so we can select an appropriate
	 * language without having to make templates for all the languages
	 * @param id to lookup in languages table
	 * @return name of the language we're mapped to
	 */
	private String getMappedLanguage(int id) {
		
		String mapped = null;
		
		Statement selectMap = null;
		ResultSet results = null;
		
		try{
			
			String SQL = "SELECT `language` FROM `languages` WHERE `id` = " + id; //$NON-NLS-1$
			selectMap = database.getConnection().createStatement();
			
			if(selectMap.execute(SQL)){
				
				results = selectMap.getResultSet();
				
				while(results.next())
					mapped = results.getString("language"); //$NON-NLS-1$
					
			}
				
		}catch(SQLException e){
			
			LOGGER.severe("Error getting mapped language: " + id); //$NON-NLS-1$
			e.printStackTrace();
			
		}
		
		return mapped;
		
	}

	/**
	 * Updates the assigned user of this contact and adds one to the contact
	 * count of the assigned user
	 */
	private void updateAssignedUser() {
		
		LOGGER.info("Updating assigned user of " + id + " to " + assignedUser);  //$NON-NLS-1$//$NON-NLS-2$
		if(updateField("assigned_user_id", "" + assignedUser))  //$NON-NLS-1$//$NON-NLS-2$
			users.addContact(currentFreeUser);
		
	}

	/**
	 * Updates this contacts email in the DB with the one the object holds
	 */
	private void updateEmail(){
		
		LOGGER.finer("Updating email of contact " + id + " to " + email); //$NON-NLS-1$ //$NON-NLS-2$
		updateField("email", email); //$NON-NLS-1$
		
	}
	
	/**
	 * Updates this contacts phone in the DB with the one the object holds
	 */
	private void updatePhone(){
		
		LOGGER.finer("Updating phone of contact " + id + " to " + phoneNumber); //$NON-NLS-1$ //$NON-NLS-2$
		updateField("phone", phoneNumber); //$NON-NLS-1$
		
	}
	
	/**
	 * Updates this contacts name in the DB with the one the object holds
	 */
	private void updateName(){
		
		LOGGER.finer("Updating name of contact " + id + " to " + name); //$NON-NLS-1$ //$NON-NLS-2$
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
			
			close(updateContact, null);
			
		}
		
		return success;
		
	}
	
	/**
	 * Quick identifier string prefers name, email/phone if no name or id if
	 * all else fails
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
		else
			identifier = "" + id; //$NON-NLS-1$
		
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
				insertContact.setString(i, values[i - 1]);
			
			//Execute it
			int rows = insertContact.executeUpdate();
			
			if(rows > 0){
			
				contactIDs = insertContact.getGeneratedKeys();
				
				while(contactIDs.next())
					id = contactIDs.getInt(1);
				
				inserted = true;
				
				if(currentFreeUser != -1)
					users.addContact(currentFreeUser);
				
			}
			
		}catch(SQLException e){
			
			e.printStackTrace();
			LOGGER.severe("SQL Error while creating new contact for " + //$NON-NLS-1$
					getIdentifierName()); 
			
		}finally{
			
			close(insertContact, contactIDs);
			
		}
		
		return inserted;
		
	}
	
	/**
	 * Creates a new contact using object creation info
	 * @return true if successful
	 */
	private boolean createNewContact() {
		
		String freeUser = ""; //$NON-NLS-1$
		
		int nextFreeUser = users.getNextAvailableUser();
		
		if(nextFreeUser != -1){
		
			freeUser = "" + nextFreeUser; //$NON-NLS-1$
			currentFreeUser = nextFreeUser;
			
		}
		
		String SQL = "INSERT INTO `contacts` (`email`, `assigned_user_id`) " + //$NON-NLS-1$
				"VALUES (?, ?)"; //$NON-NLS-1$
		String[] values = {email, freeUser}; 
		
		if(name != null && !sms){
			
			SQL = "INSERT INTO `contacts` (`email`, `name`, " + //$NON-NLS-1$
					"`assigned_user_id`) VALUES (?, ?, ?)"; //$NON-NLS-1$
			values = new String[]{email, name, freeUser};
			
		}else if(name == null && sms){
			
			SQL = "INSERT INTO `contacts` (`phone`, `assigned_user_id`) " + //$NON-NLS-1$
					"VALUES (?, ?)"; //$NON-NLS-1$
			values = new String[]{phoneNumber, freeUser};
			
		}else if(name != null && sms){ //I think name will always be null for an SMS but just in case
		
			SQL = "INSERT INTO `contacts` (`phone`, `name`, " + //$NON-NLS-1$
					"`assigned_user_id`) VALUES (?, ?, ?)"; //$NON-NLS-1$
			values = new String[]{phoneNumber, name, freeUser};
			
		}
			
		return insertNewContact(SQL, values);
		
	}
	
	/**
	 * Use form email to update this contact as appropriate
	 * @param form
	 */
	public void updateWithWebForm(String form){
	
		String[] lines = form.split("\n"); //$NON-NLS-1$
		
		boolean addedInfo = false;
		
		for(String temp : lines){
		
			String[] field = temp.split(":"); //$NON-NLS-1$
			
			if(field[0].toLowerCase().startsWith("gender") && //$NON-NLS-1$
					(gender == null || gender.equals("U"))){ //$NON-NLS-1$
				
				gender = field[1].trim().substring(0, 1).toUpperCase();
				addedInfo = true;
				
			}else if(field[0].toLowerCase().startsWith("name") && name == null){ //$NON-NLS-1$
				
				name = field[1].trim();
				addedInfo = true;
				
			}else if(field[0].toLowerCase().startsWith("email") && //$NON-NLS-1$
					email == null){ 
				
				email = field[1].trim();//Don't really need this but whatever
				addedInfo = true;
				
			}else if(field[0].toLowerCase().startsWith("phone") && //$NON-NLS-1$
					phoneNumber == null){ 
				
				phoneNumber = field[1].trim();
				
				while(phoneNumber.startsWith("+")) //$NON-NLS-1$
					phoneNumber = phoneNumber.substring(1);
				
				addedInfo = true;
				
			}else if(!custom.containsKey("Address")){ //$NON-NLS-1$
				
				
				if(field[0].toLowerCase().startsWith("house")){ //$NON-NLS-1$
				
					String house = field[1].trim();
					
					for(int i = 2; i < field.length; i++)
						house += " " + field[i].trim(); //$NON-NLS-1$
					
					custom.put("house", house); //$NON-NLS-1$
					
				}else if(field[0].toLowerCase().startsWith("city")){ //$NON-NLS-1$
				
				
					String city = field[1].trim();
					for(int i = 2; i < field.length; i++)
						city += " " + field[i].trim(); //$NON-NLS-1$
						
					custom.put("city", city); //$NON-NLS-1$
					
				}
				
			}else if(field[0].toLowerCase().startsWith("country") && //$NON-NLS-1$
					custom.containsKey("Country")){ //$NON-NLS-1$
				
				String country = field[1].trim();
				
				for(int i = 2; i < field.length; i++){
					
					country += " " + field[i].trim(); //$NON-NLS-1$
					
				}
				
				custom.put("Country", country); //$NON-NLS-1$
				addedInfo = true;
				
			}
			
		}
		
		if(!custom.containsKey("Address")){ //$NON-NLS-1$
			
			String address = null; 
			
			if(custom.containsKey("house")){ //$NON-NLS-1$
				
				address = custom.get("house"); //$NON-NLS-1$
				custom.remove("house"); //$NON-NLS-1$
				
			}
			
			if(custom.containsKey("city")){ //$NON-NLS-1$
				
				if(address == null)
					address = custom.get("city"); //$NON-NLS-1$
				else
					address += "\n" + custom.get("city"); //$NON-NLS-1$ //$NON-NLS-2$
				
				custom.remove("city"); //$NON-NLS-1$
				
			}
				
			if(address != null){
				
				addedInfo = true;
				custom.put("Address", address); //$NON-NLS-1$
				
			}
			
		}
		
		if(addedInfo)
			update();
			
	}
	
	/**
	 * Update a contact with the objects current information
	 */
	private void update() {
		
		String SQL = "UPDATE `contacts` SET `updated` = NOW()"; //$NON-NLS-1$
		
		ArrayList<Object> values = new ArrayList<Object>();
		
		//Name
		if(name != null){
			
			SQL += ", `name` = ?"; //$NON-NLS-1$
			values.add(name);
			
		}
		
		//Gender
		if(gender != null && !gender.equals("U")){ //$NON-NLS-1$
			
			SQL += ", `gender` = ? "; //$NON-NLS-1$
			values.add(gender);
			
		}
		
		//Number
		if(phoneNumber != null){
			
			SQL += ", `phone` = ?"; //$NON-NLS-1$
			values.add(phoneNumber);
			
		}
				
		//Email
		if(email != null && !email.endsWith("@invalidEmail.com")){ //$NON-NLS-1$
			
			SQL += ", `email` = ? "; //$NON-NLS-1$
			values.add(email);
			
		}
						
		SQL += " WHERE `id` = ?"; //$NON-NLS-1$
		
		LOGGER.info("Updating Contact " + getIdentifierName()); //$NON-NLS-1$
		
		PreparedStatement updateContact = null;
		
		try{
			
			//Bind all variables to statement
			updateContact = database.getConnection().prepareStatement(SQL);
			
			updateContact.setString(1, new SimpleDateFormat(
					"yyyyMMddHHmmss").format(new Date())); //$NON-NLS-1$
			
			int nextIndex = 1;
			
			while(nextIndex - 1 < values.size()){
				
				if(values.get(nextIndex - 1) instanceof String)
					updateContact.setString(nextIndex, 
							(String)values.get(nextIndex - 1));
			
				nextIndex++;
				
			}
			
			updateContact.setInt(nextIndex, id);
			
			//Execute it
			updateContact.executeUpdate();
			
			if(custom.size() > 0)//Update Custom fields if we have some
				updateCustomFields();
			
		}catch(SQLException e){
			
			e.printStackTrace();
			LOGGER.severe("SQL Error while updating contact " + getIdentifierName()); //$NON-NLS-1$
			
		}finally{
			
			close(updateContact, null);
	    	
		}
	
	}
	
	/**
	 * Gets all the custom fields and their associated sizes (s, m l)
	 * @return HashMap with label, size
	 */
	private HashMap<String, FieldMap> getCustomMappings(){
		
		HashMap<String, FieldMap> mappings = new HashMap<String, FieldMap>();
		String SQL = "SELECT * FROM `contact_fields`"; //$NON-NLS-1$
		
		Statement selectFields = null;
		ResultSet fields = null;
		
		try{
			
			selectFields = database.getConnection().createStatement();
			selectFields.executeQuery(SQL);
			fields = selectFields.getResultSet();
			
			while(fields.next())
				mappings.put(fields.getString("label"), new FieldMap( //$NON-NLS-1$
						fields.getInt("id"), fields.getString("label"),  //$NON-NLS-1$//$NON-NLS-2$
						fields.getString("type"))); //$NON-NLS-1$
				
		}catch(SQLException e){
			
			e.printStackTrace();
			LOGGER.severe("SQL Error while looking up custom fields map"); //$NON-NLS-1$
			
		}finally{
			
			close(selectFields, fields);
	    	
		}
		
		return mappings;
		
	}
	/**
	 * Updates the custom fields stored in the hash map
	 * call Update as it will only do this if necessary.
	 * 
	 * Or at least check if custom.size() > 0
	 */
	private void updateCustomFields() {
		
		//Setup SQL Inserts ON duplicate
		String[] inserts = new String[3];
		PreparedStatement[] cachedStatements = new PreparedStatement[3];
		
		for(int i = 0; i < inserts.length; i++)
			inserts[i] = "INSERT INTO `" + TABLES[i] + "` (`owner_id`, `field_id`," + //$NON-NLS-1$ //$NON-NLS-2$
				" `value`) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE `value` = ?"; //$NON-NLS-1$
		
		HashMap<String, FieldMap> maps = getCustomMappings();
		Iterator<String> keys = custom.keySet().iterator();
		
		try{
			
			while(keys.hasNext()){
				
				String name = keys.next();
				String value = custom.get(name);
				int fieldID = maps.get(name).getID();
				int table = -1;
				
				if(maps.get(name).getType().equals("s")) //$NON-NLS-1$
					table = 2;
				else if(maps.get(name).getType().equals("m")) //$NON-NLS-1$
					table = 1;
				else if(maps.get(name).getType().equals("l")) //$NON-NLS-1$
					table = 0;
				
				if(cachedStatements[table] == null)
					cachedStatements[table] = database.getConnection()
						.prepareStatement(inserts[table]);
					
				updateCustomField(cachedStatements[table], fieldID, value);
				
			}
			
		}catch(SQLException e){
			
			LOGGER.severe("Error while updatign custom field for "//$NON-NLS-1$ 
					+ getIdentifierName()); 
			e.printStackTrace();
			
		}finally{
			
			for(int i = 0; i < cachedStatements.length; i++){
				
				if(cachedStatements[i] != null)
					close(cachedStatements[i], null);
				
			}
			
		}
		
	}

	private void updateCustomField(PreparedStatement insert,  int fieldID,
			String value) throws SQLException {
		
		//Bind all variables to statement
		insert.setInt(1, id);
		insert.setInt(2, fieldID);
		insert.setString(3, value);
		insert.setString(4, value);
		
		//Execute it
		insert.execute();
		
	}

	/**
	 * Returns the assigned user for this contact
	 * @return
	 */
	public int getAssignedUser(){
		
		return assignedUser;
		
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
	 * Reads custom fields into the custom hashmap
	 */
	private void populateCustomFields(){
		
		LOGGER.finest("Getting custom for Contact: " + getIdentifierName()); //$NON-NLS-1$
		
		for (int i = 0; i < TABLES.length; i++){
			
			String SQL = "SELECT `contact_fields`.`label`, `" + TABLES[i] +  //$NON-NLS-1$
				"`.`value` FROM `contact_fields` INNER JOIN `" + TABLES[i] + //$NON-NLS-1$
				"` ON `" + TABLES[i] + "`.`field_id` = `contact_fields`.`id` " + //$NON-NLS-1$ //$NON-NLS-2$
				"WHERE `" + TABLES[i] + "`.`owner_id` = ?"; //$NON-NLS-1$ //$NON-NLS-2$
			
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
						TABLES[i] + id); 
				
			}finally{
				
				close(selectContact, customResults);
				
			}
			
		}
		
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

}