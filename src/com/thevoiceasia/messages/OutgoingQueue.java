package com.thevoiceasia.messages;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

public class OutgoingQueue {
	
	private HashMap<Integer, ArrayList<OutgoingMessage>> queue = 
			new HashMap<Integer, ArrayList<OutgoingMessage>>();
	private HashMap<Integer, OutgoingTemplate> templates = null;
	
	/**
	 * Outgoing queue object handles all message adding logic
	 * @param templates
	 */
	public OutgoingQueue(HashMap<Integer, OutgoingTemplate> templates){
		
		this.templates = templates;
		
	}
	
	public ArrayList<OutgoingMessage> get(int contactId){
		
		return queue.get(contactId);
		
	}
	
	public Iterator<Integer> getIterator(){
		
		return queue.keySet().iterator();
		
	}
	
	/**
	 * Checks for contact in queue
	 * @param id
	 * @return
	 */
	private boolean hasContact(int id){
		
		boolean hasContact = false;
		
		if(queue.containsKey(id))
			hasContact = true;
		
		return hasContact;
		
	}
	
	/**
	 * Adds a message to the queue if required otherwise ignores it
	 * @param message
	 */
	public void addMessage(OutgoingMessage message){
		
		if(!hasContact(message.owner)){
			
			ArrayList<OutgoingMessage> newContact = 
					new ArrayList<OutgoingMessage>();
			
			newContact.add(message);
			queue.put(message.owner, newContact);
			
		}else{
			
			//if higher priority keep else archive
			if(isExclusive(message))
				addCheckPriority(message);
			else if(containsSameType(message.owner, message.type)){
				
				message.archive = true;//already has a same type message so just archive this one
				
				//Add message to list
				queue.get(message.owner).add(message);
					
			}else //Just add it because we are not exclusive and we don't have this message type yet
				queue.get(message.owner).add(message);
			
		}
		
	}
	
	/**
	 * Checks priority of the templates against the existing messages
	 * archives anything of lower priority
	 * @param message
	 */
	private void addCheckPriority(OutgoingMessage message){
	
		ArrayList<OutgoingMessage> contactMessages = queue.get(message.owner);
		
		boolean done = false;
		int i = 0;
		int messagePriority = templates.get(message.type).getPriority();
		
		while(!done && i < contactMessages.size()){
			
			if(!contactMessages.get(i).archive){
				
				if(templates.get(contactMessages.get(i).type).getPriority() > 
							messagePriority){
					
					contactMessages.get(i).archive = true;
					contactMessages.add(message);
					done = true;
					
				}else{
					
					message.archive = true;
					contactMessages.add(message);
					done = true;
					
				}
				
			}
			
			i++;
			
		}
		
	}
	
	/**
	 * Checks for the same type non-exclusive message in a contacts list
	 * @param contact
	 * @param type
	 * @return
	 */
	private boolean containsSameType(int contact, int type){
		
		ArrayList<OutgoingMessage> contactMessages = queue.get(contact);
		
		boolean found = false;
		int i = 0;
		
		while(!found && i < contactMessages.size()){
			
			if(contactMessages.get(i).type == type)
				found = true;
			
			i++;
			
		}
		
		return found;
		
	}
	
	/**
	 * If this template is exclusive returns true 
	 * else adds the message to the non-exclusive queue iff it is of a different
	 * type to any that are existing for the same contact
	 * @param message
	 * @return
	 */
	private boolean isExclusive(OutgoingMessage message){
		
		return templates.get(message.type).isExclusive();
			
	}

}
