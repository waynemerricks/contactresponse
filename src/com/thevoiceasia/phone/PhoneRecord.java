package com.thevoiceasia.phone;

public class PhoneRecord {
	
	public String name = null, email = null,  gender = null, 
			religion = null, journeyStage = null, topic = null,
			number = null, conversation = null, location = null;
	private String address = null;
	public int answeredBy = -1, language = -1;
	public long time = -1l;
	
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
	
}