package com.thevoiceasia.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class DatabaseHelper {

	private boolean connected = false;
	private String host, database, user, password;
	private Connection databaseConnection = null;
	private SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"); //$NON-NLS-1$
	
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
			
			System.err.println("Error reconnecting to Database"); //$NON-NLS-1$
			e.printStackTrace();
			
		}
		
		return databaseConnection;
		
	}
	
	private String getTimeStamp(){
		
		return sdf.format(new Date());
		
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
						"?user=" + user + "&password=" + password); //$NON-NLS-1$//$NON-NLS-2$ 						
				
				connected = true;
				//System.out.println("Connected Successfully"); //$NON-NLS-1$
				
			}catch(SQLException e){
				
				System.err.println(getTimeStamp() + ": MySQL Connection Error"); //$NON-NLS-1$
				e.printStackTrace();
				
			}catch(Exception e){
				
				System.err.println(getTimeStamp() + ": MySQL Driver Error"); //$NON-NLS-1$
				e.printStackTrace();
				
			}
			
		}
		
		return connected;
		
	}
	
	public void disconnect(){
		
		if(connected){
			
			try {
				
				databaseConnection.close();
				
				connected = false;
				
			} catch (SQLException e) {
	
				System.err.println(getTimeStamp() + ": Error disconnecting from Database"); //$NON-NLS-1$
				e.printStackTrace();
				
			}
			
		}
		
	}
	
}
