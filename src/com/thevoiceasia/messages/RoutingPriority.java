package com.thevoiceasia.messages;

public class RoutingPriority {

	private String type = null, keyTerm = null;
	public int sendToUser = -1, priority = -1;
	private String[] termVariations = null;
	
	public RoutingPriority(String type, String keyTerm, int user, int priority){
		
		this.type = type;
		keyTerm = keyTerm.toLowerCase().trim();
		this.keyTerm = keyTerm;
		this.sendToUser = user;
		this.priority = priority;
		
		
		if(type.equals("K")){ //$NON-NLS-1$
		
			termVariations = new String[5];
			
			for(int i = 0; i < termVariations.length; i++)
				termVariations[i] = ""; //$NON-NLS-1$
			
			termVariations[0] = keyTerm;
			
			/* Change key words e.g. DVD into "D V D ", "D V D", "D.V.D." &
			 * "D.V.D"
			 */
			for(int i = 1; i <= keyTerm.length(); i++){
				
				if(i == keyTerm.length()){
					
					termVariations[1] += keyTerm.substring(i) + "."; //$NON-NLS-1$
					termVariations[2] += keyTerm.substring(i) + " "; //$NON-NLS-1$
				
				}else{
					
					termVariations[1] += keyTerm.substring(i, i + 1) + "."; //$NON-NLS-1$
					termVariations[2] += keyTerm.substring(i, i + 1) + " "; //$NON-NLS-1$
					
				}
			
			}
			
			termVariations[3] = termVariations[2].trim(); //"D V D"
			termVariations[4] = termVariations[1].substring(0, 
					termVariations[1].length() - 1);//D.V.D
			
		}
		
	}
	
	/**
	 * True if this object applies to the given message type
	 * e.g. emails applies to this object then true
	 * @param messageType email, sms, phone, other
	 * @return
	 */
	public boolean appliesToType(String messageType){
		
		boolean applies = false;
		messageType = messageType.toUpperCase();
		
		if(type.equals(messageType))
			applies = true;
		else if(type.equals("K") && (messageType.equals("E") ||   //$NON-NLS-1$//$NON-NLS-2$
				messageType.equals("S"))) //Keyword, SMS or Email //$NON-NLS-1$
			applies = true;
		else if(type.equals("C")) //Contact //$NON-NLS-1$
			applies = true;
		
		return applies;
		
	}
	
	/**
	 * Returns true if this is a contact type priority 
	 * e.g. true = use the contact's helper to assign this
	 * @return
	 */
	public boolean useContactDefault(){
		
		boolean contact = false;
		
		if(type.equals("C")) //$NON-NLS-1$
			contact = true;
		
		return contact;
		
	}
	
	/**
	 * true if this object is a message body keyword e.g. DVD
	 * @return
	 */
	public boolean isKeyWord(){
		
		return type.equalsIgnoreCase("K"); //$NON-NLS-1$
		
	}
	
	/**
	 * If this isn't a keyword checks to see if the address matches
	 * @param checkMe
	 * @return
	 */
	public boolean appliesToAddress(String checkMe){
		
		boolean matches = false;
		
		if(!isKeyWord()){
		
			if(checkMe.contains(keyTerm))
				matches = true;
			else if(type.equalsIgnoreCase("C")) //$NON-NLS-1$
				matches = true;
			
		}
		
		return matches;
		
	}
	
	/**
	 * Checks the string to see if it contains our keywords
	 * @param checkMe
	 * @return true if it contains
	 */
	public boolean hasKeyWord(String checkMe){
		
		boolean matches = false;
		checkMe = checkMe.toLowerCase();
		
		if(isKeyWord()){
			
			int i = 0;
			
			while(!matches && i < termVariations.length){
				
				if(checkMe.startsWith(termVariations[i]))
					matches = true;
				else if(checkMe.startsWith("S:") && checkMe.contains("\n")){  //$NON-NLS-1$//$NON-NLS-2$
					
					String[] temp = checkMe.split("\n");//$NON-NLS-1$
					
					for(int j = 1; j < temp.length; j++)
						if(temp[j].trim().length() > 0 && temp[j].trim().startsWith(termVariations[i]))
							matches = true;
					
				}
				
				i++;
				
			}
			
		}else if(type.equalsIgnoreCase("C")) //$NON-NLS-1$
			matches = true;
		
		return matches;
		
	}
	
}
