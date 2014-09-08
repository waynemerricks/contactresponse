package com.thevoiceasia.messages;

import java.text.SimpleDateFormat;
import java.util.Date;

public class OutgoingTemplate {
	
	private int id = -1, priority = -1;
	private String fromEmail = null;
	private boolean exclusive = true;
	private Date date = null;
	private SimpleDateFormat mysqlTimeStamp = 
			new SimpleDateFormat("yyyyMMddHHmmss"); //$NON-NLS-1$
	
	/**
	 * Outgoing template helper object
	 * @param id
	 * @param fromEmail
	 * @param exclusive
	 * @param priority
	 * @param timestamp
	 */
	public OutgoingTemplate(int id, String fromEmail, boolean exclusive, 
			int priority, String timestamp){
		
		this.id = id;
		this.fromEmail = fromEmail;
		this.exclusive = exclusive;
		this.priority = priority;
		
		if(timestamp != null){
			
			try{
				this.date = mysqlTimeStamp.parse(timestamp);
			}catch(Exception e){//Should never happen assuming timestamp is fine
				this.date = new Date();
			}
			
		}
		
	}
	
	/**
	 * True if this is exclusive to other templates
	 * e.g. Generic should not be sent if we have a higher priority template to
	 * send instead (its exclusive per day)
	 * @return
	 */
	public boolean isExclusive(){
		
		return exclusive;
		
	}
	
	/**
	 * 
	 * @return
	 */
	public boolean isDefaultTemplate(){
		
		boolean isDefault = false;
		
		if(date == null)
			isDefault = true;
		
		return isDefault;
		
	}
	
	public int getId(){
		
		return id;
		
	}
	
	public int getPriority(){
		
		return priority;
		
	}
	
	public String getFromEmail(){
		
		return fromEmail;
		
	}
	
}
