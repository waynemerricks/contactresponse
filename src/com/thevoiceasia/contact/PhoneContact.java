package com.thevoiceasia.contact;

import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.thevoiceasia.database.DatabaseHelper;
import com.thevoiceasia.phone.PhoneRecord;

public class PhoneContact {

	private String name = null, email = null, gender = null, phoneNumber = null;//,
	//language = null, photo = null, status = null, autoReply = null;
	private int id = -1;//, languageID = -1, assignedUser = -1;
	private long updated = -1;//, created = -1; 
	private HashMap<String, String> custom = new HashMap<String, String>();
	
	private DatabaseHelper database = null;

	private static final Logger LOGGER = Logger.getLogger("com.thevoiceasia.contact.PhoneContact"); //$NON-NLS-1$
	private static final Level LEVEL = Level.INFO;//Logging level of this class
	private static final String[] TABLES = {"contact_values_large",  //$NON-NLS-1$
		"contact_values_medium", "contact_values_small"}; //$NON-NLS-1$ //$NON-NLS-2$
	
	
	/**
	 * Creates this object based on a phone record
	 * Updates as necessary
	 * @param phone
	 */
	public PhoneContact(DatabaseHelper database, PhoneRecord phone){
		
		//TODO
		LOGGER.setLevel(LEVEL);
		
		this.name = phone.getName();
		this.phoneNumber = phone.getNumber();
		this.database = database;
		this.email = phone.email;
		
		/*if(email == null && phoneNumber == null)
			createNewContact();
		else if(email != null)
			populateByEmail();
		else if(phoneNumber != null)
			populateByPhone();*/
		
		//TODO don't populate, treat like updateWithWebForm
		
	}

}
