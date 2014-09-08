package com.thevoiceasia.email;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.logging.Logger;

import com.thevoiceasia.database.DatabaseHelper;
import com.thevoiceasia.database.KeyValue;
import com.thevoiceasia.messages.OutgoingTemplate;

public class EmailSender {

	private static final boolean DEBUG = true;
	private static final Logger LOGGER = Logger.getLogger("com.thevoiceasia.email"); //$NON-NLS-1$
	
	private DatabaseHelper database = null;
	private HashMap<Integer, OutgoingTemplate> templates = null;
	private String host = null, user = null, password = null, 
			templateBasePath = null;
	private ArrayList<KeyValue> languages = new ArrayList<KeyValue>();
	
	public EmailSender(String emailServer, String emailUser, 
			String emailPassword, String databaseHost, String databaseUser,
			String databasePassword, String databaseName, String templatePath){
		
		this.database = new DatabaseHelper(databaseHost, databaseName, 
				databaseUser, databasePassword);
		
		this.host = emailServer;
		this.user = emailUser;
		this.password = emailPassword;
		this.templateBasePath = templatePath;
		
	}
	
	/**
	 * Reads languages from language table
	 */
	private void getLanguages(){
		
		String SQL = "SELECT * FROM `languages`"; //$NON-NLS-1$
		
		Statement selectLanguages = null;
		ResultSet results = null;
		
		try{
			
			selectLanguages = database.getConnection().createStatement();
			
			if(selectLanguages.execute(SQL)){
				
				results = selectLanguages.getResultSet();
				
				while(results.next())
					languages.add(new KeyValue("" + results.getInt("id"),   //$NON-NLS-1$//$NON-NLS-2$
							results.getString("language").toLowerCase()));  //$NON-NLS-1$
				
			}
			
		}catch(SQLException e){
			
			LOGGER.severe("SQL Error getting languages"); //$NON-NLS-1$
			e.printStackTrace();
			
		}finally{
			
			close(selectLanguages, results);
			
		}
		
	}
	/**
	 * Reads templates from the templates table
	 */
	private void getTemplates(){
		
		SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss"); //$NON-NLS-1$
		
		String SQL = "SELECT `id`, `from`, `date`, `exclusive`, `priority` " + //$NON-NLS-1$
				"FROM `templates` WHERE `date` = NULL OR `date` = " + //$NON-NLS-1$
				sdf.format(new Date()); 
		
		Statement selectTemplates = null;
		ResultSet results = null;
		
		try{
			
			selectTemplates = database.getConnection().createStatement();
			
			if(selectTemplates.execute(SQL)){
				
				results = selectTemplates.getResultSet();
				
				templates = new HashMap<Integer, OutgoingTemplate>();
				
				while(results.next()){
					
					//Read templates and add to map
					boolean exclusive = false;
					
					if(results.getString("exclusive").equals("Y"))  //$NON-NLS-1$//$NON-NLS-2$
						exclusive = true;
					
					OutgoingTemplate out = new OutgoingTemplate(
							results.getInt("id"), //$NON-NLS-1$
							results.getString("from"), //$NON-NLS-1$
							exclusive,
							results.getInt("priority"), //$NON-NLS-1$
							results.getString("date"),//$NON-NLS-1$
							templateBasePath,
							(KeyValue[])languages.toArray()); 
					
					templates.put(out.getId(), out);
					
				}
				
				if(templates.size() < 1)//reset to null if there are no templates
					templates = null;
				
			}
			
		}catch(SQLException e){
			
			LOGGER.severe("Auto Reply Sender: Unable to get outgoing templates"); //$NON-NLS-1$
			e.printStackTrace();
			
		}finally{
			
			close(selectTemplates, results);
			
		}
		
	}
	
	/**
	 * Call to process the list of outgoing messages
	 */
	public void processOutgoingMessages(){
		
		database.connect();
		getLanguages();
		getTemplates();
		
		String SQL = "SELECT `id`, `owner`, `created_by` FROM `messages` " + //$NON-NLS-1$
				"WHERE `direction` = 'O' AND `status` = 'U' " + //$NON-NLS-1$
				"ORDER BY `owner` ASC, `created` DESC"; //$NON-NLS-1$
		
		Statement selectMessages = null;
		ResultSet results = null;
		
		try{
			
			selectMessages = database.getConnection().createStatement();
			
			if(selectMessages.execute(SQL)){
				
				results = selectMessages.getResultSet();
				
				while(results.next()){
					
					//TODO do something
					
					
				}
				
			}
			
		}catch(SQLException e){
			
			LOGGER.severe("Auto Reply Sender: Unable to get outgoing messages"); //$NON-NLS-1$
			e.printStackTrace();
			
		}finally{
			
			close(selectMessages, results);
			
		}
		
		database.disconnect();
		
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
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub

	}

}
