package com.thevoiceasia.phone;

import java.util.HashMap;

public class PhoneRecord {
	
	public String name = null, address = null, email = null,  gender = null, 
			religion = null, journeyStage = null, topic = null,
			number = null, conversation = null, location = null;
	
	private int answeredBy = -1, language = -1;
	private long time = -1l;
	
	private HashMap<String, Integer> userIDs = new HashMap<String, Integer>();
	
	/**
	 * Sets the address of this object by combining address + postcode
	 * @param address
	 * @param postcode
	 */
	public void setAddress(String address, String postcode){
		
		if(address == null && postcode != null)
			this.address = postcode;
		else if(address != null && postcode == null)
			this.address = address;
		else if(address != null && postcode != null)
			this.address = address + "\n" + postcode; //$NON-NLS-1$
		
	}
	
	public String getAddress(){
		
		return address;
		
	}
	
	/**
	 * Parses 1234 (1234) (user@computer) into a matching CRS user id
	 * @param answered
	 */
	public void setAnsweredBy(String answered){
		
		//TODO parse it and find the user id
		
		answeredBy = -1;
		
	}
	
	/**
	 * Looks up the language ID for the given language e.g. English ==> 1
	 * @param lang
	 */
	public void setLanguage(String lang){
		
		//TODO parse it and find language id
		language = -1;
		
	}
	
	/**
	 * Sets up a java Date.getLong() by parsing PB Date yyyy-mm-dd hh:mm:ss.mss
	 * @param dateTime
	 */
	public void setTimeCalled(String dateTime){
		
		//TODO parse it and set time;
		
	}
	
	/**
	 * Looks up the phone call topic and sets the type id based on matches in
	 * the template table 
	 * @param type
	 */
	public void setType(String topic){
		
		//TODO parse it and find types for auto
		
	}
	
}