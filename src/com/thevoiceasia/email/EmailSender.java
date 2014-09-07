package com.thevoiceasia.email;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Logger;

import com.thevoiceasia.database.DatabaseHelper;

public class EmailSender {

	private static final boolean DEBUG = true;
	private static final Logger LOGGER = Logger.getLogger("com.thevoiceasia.email"); //$NON-NLS-1$
	
	private DatabaseHelper database = null;
	private String host = null, user = null, password = null;
	
	public EmailSender(String emailServer, String emailUser, 
			String emailPassword, String databaseHost, String databaseUser,
			String databasePassword, String databaseName){
		
		this.database = new DatabaseHelper(databaseHost, databaseName, 
				databaseUser, databasePassword);
		
		this.host = emailServer;
		this.user = emailUser;
		this.password = emailPassword;
		
	}
	
	/**
	 * Call to process the list of outgoing messages
	 */
	public void processOutgoingMessages(){
		
		database.connect();
		
		String SQL = "SELECT * FROM `messages` WHERE `direction` = 'O' AND " + //$NON-NLS-1$
				"`status` = 'U' ORDER BY `owner` ASC, `created` DESC"; //$NON-NLS-1$
		
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
