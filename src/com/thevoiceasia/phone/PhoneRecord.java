package com.thevoiceasia.phone;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import com.thevoiceasia.database.KeyValue;

public class PhoneRecord {
	
	public String email = null,  gender = null, 
			conversation = null, location = null;
	private String address = null;
	public int answeredBy = -1;
	public long time = -1l;
	
	private HashMap<String, String> stringFields = new HashMap<String, String>();
	private HashMap<String, Integer> customIDs = new HashMap<String, Integer>();
	
	/**
	 * Returns a KeyValue list of non-null values for the contacts table
	 * @return
	 */
	public ArrayList<KeyValue> getNonNullValues(){
	
		ArrayList<KeyValue> pairs = new ArrayList<KeyValue>();
		
		if(getName() != null)
			pairs.add(new KeyValue("name", getName())); //$NON-NLS-1$
		
		if(email != null)
			pairs.add(new KeyValue("email", email)); //$NON-NLS-1$
			
		if(getNumber() != null)
			pairs.add(new KeyValue("phone", getNumber())); //$NON-NLS-1$
		
		if(gender != null)
			pairs.add(new KeyValue("gender", gender)); //$NON-NLS-1$
		
		return pairs;
		
	}
	
	/**
	 * Gets the custom values related to the contact
	 * @return
	 */
	public ArrayList<KeyValue> getCustomValues(){
		
		//These will never be null like normal values so can just reel them off
		ArrayList<KeyValue> pairs = new ArrayList<KeyValue>();
		
		Iterator<String> keys = customIDs.keySet().iterator();
		
		while(keys.hasNext()){
			
			String key = keys.next();
			pairs.add(new KeyValue(key, "" + customIDs.get(key))); //$NON-NLS-1$
			
		}
		
		//Add in the address too
		if(address != null)
			pairs.add(new KeyValue("address", address)); //$NON-NLS-1$
		
		return pairs;
		
	}
	
	/**
	 * Sets the address of this object by combining address + postcode
	 * @param address
	 * @param postcode
	 */
	public void setAddress(String address, String postcode){
		
		address = checkNull(address);
		postcode = checkNull(postcode);
		
		if(address == null && postcode != null)
			this.address = postcode;
		else if(address != null && postcode == null)
			this.address = address;
		else if(address != null && postcode != null)
			this.address = address + "\n" + postcode; //$NON-NLS-1$
		
	}
	
	public String getNumber(){
		
		return stringFields.get("number"); //$NON-NLS-1$
		
	}
	
	public String getAddress(){
		
		return address;
		
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
	 * Set custom field in the hash map
	 * @param key
	 * @param value
	 */
	public void setCustomField(String key, Integer value) {

		customIDs.put(key, value);
		
	}
	
	/**
	 * Get a custom field from the hash map
	 * @param key
	 * @return
	 */
	public int getCustomField(String key){
		
		return customIDs.get(key);
		
	}
	
	/**
	 * Returns the phone number for this object
	 * @return
	 */
	public String getPhoneNumber(){
		
		return stringFields.get("number"); //$NON-NLS-1$
		
	}

	/**
	 * Sets a straight forward string value in a hash map
	 * Saves having lots of private vars in advance
	 * @param key
	 * @param value
	 */
	public void setString(String key, String value) {
		
		/* If its a phone number strip the leading 0s for international
		 * Otherwise it buggers up text messaging
		 * 
		 * Also add in 44 for numbers that are 11 long and start with 1 zero.
		 */
		if(key.equals("number") && value.equalsIgnoreCase("unknown"))  //$NON-NLS-1$//$NON-NLS-2$
			value = null;
		else if(key.equals("number")){ //$NON-NLS-1$
			
			if(value.length() > 11 && value.startsWith("0")) //$NON-NLS-1$
				while(value.startsWith("0")) //$NON-NLS-1$
					value = value.substring(1);
			else if(value.length() == 11 && value.startsWith("0") && //$NON-NLS-1$
				!value.substring(1).startsWith("0")) //$NON-NLS-1$
				value = "44" + value.substring(1); //$NON-NLS-1$
			
		}
		
		stringFields.put(key, value);
		
	}
	
	/**
	 * Returns the caller_name from this object
	 * @return
	 */
	public String getName(){
		
		return stringFields.get("caller_name"); //$NON-NLS-1$
		
	}
	
}