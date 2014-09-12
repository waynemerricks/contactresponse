package com.thevoiceasia.email;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Date;
import java.util.Properties;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

public class EmailSender {

	private String serverName = null, user = null, password = null;
	
	//TODO HTML + Attachments 
	//http://www.tutorialspoint.com/java/java_sending_email.htm 
	/**
	 * Creates an EmailSender object that simplifies sending emails to people
	 * @param serverName host name or ip
	 * @param userName email user name
	 * @param password email password 
	 */
	public EmailSender(String serverName, String userName, String password) {

		this.serverName = serverName;
		this.user = userName;
		this.password = password;
		
	}

	public boolean sendEmail(String recipient, String from, String subject, 
			String body) throws MessagingException, UnknownHostException{
		
		String[] recipients = {recipient};
		
		return postMail(recipients, from, subject, body);
		
	}
	
	private boolean postMail(String recipients[], String from, String subject, 
			String message) throws MessagingException, UnknownHostException{
		
		//Set the host smtp address
	    Properties props = new Properties();
	    props.put("mail.smtp.host", serverName); //$NON-NLS-1$
	    props.put("mail.from", from); //$NON-NLS-1$
	    props.setProperty("mail.user", user); //$NON-NLS-1$
	    props.setProperty("mail.password", password); //$NON-NLS-1$
	    String hostName = InetAddress.getLocalHost().getHostName();
	    
	    if(hostName == null)
	    	hostName = "local.tva.smb"; //$NON-NLS-1$
	    
	    if(!hostName.endsWith(".tva.smb") && !hostName.contains("\\\\."))  //$NON-NLS-1$//$NON-NLS-2$
	    	hostName = hostName + ".tva.smb"; //$NON-NLS-1$
	    
	    props.put("mail.smtp.localhost", hostName);  //$NON-NLS-1$
	    
	    // create some properties and get the default Session
	    Session session = Session.getDefaultInstance(props, null);

	    // create a message
	    Message msg = new MimeMessage(session);

	    //set the from and to address
	    InternetAddress[] addressTo = new InternetAddress[recipients.length];
	    
	    for (int i = 0; i < recipients.length; i++)
	        addressTo[i] = new InternetAddress(recipients[i]);
	    
	    msg.setRecipients(Message.RecipientType.TO, addressTo);
	    
	    //Setting the Subject and Content
	    msg.setSubject(subject);
	    msg.setSentDate(new Date());
	    msg.setText(message);
	    msg.setFrom(new InternetAddress(from));

	    Transport.send(msg);
	    
	    return true;//if we got this far without an error then it sent
	    
	}

}
