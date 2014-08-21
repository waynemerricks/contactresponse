package com.thevoiceasia.email;

import java.util.Date;

public interface EmailReader {

	/**
	 * Called for a class to receive an email
	 * @param from
	 * @param to
	 * @param subject
	 * @param body
	 * @return true if you want this email deleted
	 */
	public boolean receiveEmail(Date receivedDate, String from, String to, 
			String name, String body, String subject);
	
}
