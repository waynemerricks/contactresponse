package com.thevoiceasia.sms;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.http.client.fluent.Request;

import com.thevoiceasia.database.DatabaseHelper;
import com.thevoiceasia.html.HTML2Text;

public class SMSSender {

	private String api_id = null, from = null, user = null, password = null,
			url = null;
	protected static final Logger LOGGER = Logger.getLogger("com.thevoiceasia.sms.SMSSender"); //$NON-NLS-1$
	protected static final Level LEVEL = Level.INFO;//Logging level of this class
	
	public SMSSender(DatabaseHelper database){
		
		LOGGER.setLevel(LEVEL);
		
		Statement selectSMS = null;
		ResultSet results = null;
		
		String SQL = "SELECT `name`, `value` FROM `settings` WHERE `name` " + //$NON-NLS-1$
				"LIKE 'sms_%'"; //$NON-NLS-1$
		try{
			
			selectSMS = database.getConnection().createStatement();
			
			if(selectSMS.execute(SQL)){
				
				results = selectSMS.getResultSet();
				
				while(results.next()){
					
					if(results.getString("name").equals("sms_api")) //$NON-NLS-1$//$NON-NLS-2$
						api_id = results.getString("value"); //$NON-NLS-1$
					else if(results.getString("name").equals("sms_from")) //$NON-NLS-1$//$NON-NLS-2$
						from = results.getString("value"); //$NON-NLS-1$
					else if(results.getString("name").equals("sms_user")) //$NON-NLS-1$//$NON-NLS-2$
						user = results.getString("value"); //$NON-NLS-1$
					else if(results.getString("name").equals("sms_password")) //$NON-NLS-1$//$NON-NLS-2$
						password = results.getString("value"); //$NON-NLS-1$
					else if(results.getString("name").equals("sms_url")) //$NON-NLS-1$//$NON-NLS-2$
						url = results.getString("value"); //$NON-NLS-1$
					
				}
				
			}
			
		}catch(SQLException e){
			
			LOGGER.severe("Error looking up SMS settings"); //$NON-NLS-1$
			e.printStackTrace();
			
		}finally{
			
			close(selectSMS, results);
			
		}
				
	}
	
	/**
	 * Send to provider
	 * @param toNumber
	 * @param text
	 * @return
	 */
	private boolean send(String toNumber, String text){
		
		boolean sent = false;
		
		String smsResponse = null;
		try{
			
			text = URLEncoder.encode(text, "utf8"); //$NON-NLS-1$
			
		}catch(UnsupportedEncodingException e){
			
			e.printStackTrace();
			LOGGER.severe("Could not encode SMS for GET Request"); //$NON-NLS-1$
			
		}
		
		try{
			
			String getUrl = url + "?api_id=" + api_id +  //$NON-NLS-1$
					"&from=" + from + "&user=" + user +  //$NON-NLS-1$ //$NON-NLS-2$
					"&password=" + password + "&to=" + toNumber +  //$NON-NLS-1$ //$NON-NLS-2$
					"&text=" + text; //$NON-NLS-1$
			smsResponse = Request.Get(getUrl)
				.connectTimeout(2000).socketTimeout(2000).execute()
					.returnContent().asString();
			smsResponse = HTML2Text.convert(smsResponse).trim();
			if(smsResponse.startsWith("ID: ")) //$NON-NLS-1$
				sent = true;
			
		}catch(IOException e){
			
			LOGGER.severe("Error sending SMS Message"); //$NON-NLS-1$
			e.printStackTrace();
			
		}
		
		return sent;
		
	}
	
	/**
	 * Actually send the message.  Will automatically split by word if 
	 * the text is over size (160 chars ASCII)
	 * Due to the way SMS providers work you can't guarantee UTF8 will work
	 * so its best just to leave it as ASCII
	 * @param toNumber
	 * @param text
	 * @return
	 */
	public boolean sendSMS(String toNumber, String text){
		
		boolean sent = false;
		
		if(text.trim().length() > 160){//
			
			String[] words = text.split(" "); //$NON-NLS-1$
			
			String smsText = words[0];
			int i = 1;
			
			int finished = 0;
			
			//Loop to build message up to 160 chars
			while(finished == 0 && i < words.length){
				
				if((smsText + " " + words[i]).length() > 160){ //$NON-NLS-1$
					
					sent = send(toNumber, smsText);
					
					if(!sent)
						finished = -1;
					
					smsText = words[i];
						
				}else
					smsText += " " + words[i]; //$NON-NLS-1$
					
 				i++;
				
			}
			
			//if not finished in error and we have some text in smsText then 
			//send this too
			if(finished == 0 && smsText.length() > 0)
				sent = send(toNumber, smsText);
			
		}else
			sent = send(toNumber, text);
		
		return sent;
		
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
