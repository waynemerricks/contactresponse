package com.thevoiceasia.user;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.logging.Logger;

public class User {

	private int userId = -1, maxContacts = -1, currentContacts = -1;
	private static final Logger LOGGER = Logger.getLogger(User.class.getName());//Logger
	
	/**
	 * Creates a user record for keeping tally of contacts count
	 * @param id user id
	 * @param max max number of contacts
	 * @param current current number of contacts
	 */
	public User(int id, int max, int current){
		
		this.userId = id;
		this.maxContacts = max;
		this.currentContacts = current;
		
	}
	
	/**
	 * true if we have reached max number of contacts
	 * @return
	 */
	public boolean canHaveMoreContacts(){
		
		boolean more = true;
		
		if(maxContacts < currentContacts)
			more = false;
		
		return more;
		
	}
	
	/**
	 * Returns the user id
	 * @return
	 */
	public int getId(){
		
		return userId;
		
	}
	
	/**
	 * Returns max contacts
	 * @return
	 */
	public int getMaxContacts(){
		
		return maxContacts;
		
	}
	
	/**
	 * returns current contacts
	 * @return
	 */
	public int getCurrentContacts(){
		
		return currentContacts;
		
	}
	
	/**
	 * Adds the value to the current contacts field of this user
	 * @param value +/- value e.g. current_contacts = current_contacts + value
	 * @param mysql MySQL Connection to use (write needed)
	 * @return true if no errors
	 */
	public boolean addCurrent(int value, Connection mysql){
		
		boolean success = false;
		currentContacts += value;
		
		String SQL = "UPDATE `users` SET " + //$NON-NLS-1$
				"`current_contacts` = `current_contacts` + ? WHERE `id` = ?"; //$NON-NLS-1$
		
		PreparedStatement update = null;
		
		try{
			
			update = mysql.prepareStatement(SQL);
			update.setInt(1, value);
			update.setInt(2, userId);
			
			int rows = update.executeUpdate();
			
			if(rows > 0)
				success = true;
			
			LOGGER.info("Added Contact to User " + userId); //$NON-NLS-1$
			
		}catch(SQLException e){
			
			LOGGER.severe("Error while updating current contacts for user " +  //$NON-NLS-1$
					userId);
			e.printStackTrace();
			
		}finally{
			
			if(update != null){
				
				try{
					update.close();
				}catch(Exception e){}
				update = null;
				
			}
		}
		
		return success;
		
	}
	
}
