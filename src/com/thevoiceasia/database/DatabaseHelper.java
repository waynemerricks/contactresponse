package com.thevoiceasia.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.logging.Logger;

public class DatabaseHelper {

	/** STATICS **/
	private static final Logger LOGGER = Logger.getLogger("com.thevoiceasia.database"); //$NON-NLS-1$
	
	/** CLASS VARS **/
	private boolean connected = false;
	private String host, database, user, password;
	private Connection databaseConnection = null;
	
	public DatabaseHelper(String host, String database, String user, 
			String password){
		
		this.host = host;
		this.database = database;
		this.user = user;
		this.password = password;
		
	}
	
	public Connection getConnection(){
		
		if(databaseConnection != null && connected == false)
			connect();
		
		try {
			
			if(!databaseConnection.isValid(3)){
				
				connected = false;
				connect();
				
			}
				
							
		} catch (SQLException e) {
			
			LOGGER.severe("Error reconnecting to Database"); //$NON-NLS-1$
			e.printStackTrace();
			
		}
		
		return databaseConnection;
		
	}
	
	/**
	 * Connects to the DB, will alert if there are errors.
	 * 
	 * Does nothing if we're already connected.
	 */
	public boolean connect(){
		
		if(!connected){
			
			//System.out.println("Connecting to Database"); //$NON-NLS-1$
			
			try{
				
				Class.forName("com.mysql.jdbc.Driver").newInstance(); //$NON-NLS-1$
				databaseConnection = DriverManager.getConnection("jdbc:mysql://" +  //$NON-NLS-1$
						host + "/" + database + //$NON-NLS-1$
						"?characterEncoding=UTF-8&user=" + user + //$NON-NLS-1$
						"&password=" + password); //$NON-NLS-1$
				
				connected = true;
				LOGGER.finest("Connected to MySQL Database");
				
			}catch(SQLException e){
				
				LOGGER.severe("MySQL Connection Error"); //$NON-NLS-1$
				e.printStackTrace();
				
			}catch(Exception e){
				
				LOGGER.severe("MySQL Driver Error"); //$NON-NLS-1$
				e.printStackTrace();
				
			}
			
		}
		
		return connected;
		
	}
	
	public void disconnect(){
		
		if(connected){
			
			try {
				
				databaseConnection.close();
				LOGGER.finest("Disconnected from MySQL Database");
				connected = false;
				
			} catch (SQLException e) {
	
				LOGGER.warning("Error disconnecting from Database"); //$NON-NLS-1$
				e.printStackTrace();
				
			}
			
		}
		
	}
	
}
