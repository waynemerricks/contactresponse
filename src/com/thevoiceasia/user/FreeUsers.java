package com.thevoiceasia.user;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.logging.Logger;

import com.thevoiceasia.database.DatabaseHelper;

public class FreeUsers {
	
	private DatabaseHelper database = null;
	private ArrayList<User> users = null;
	private User nextUser = null;
	private int currentIndex = 0;
	
	private static final Logger LOGGER = Logger.getLogger(User.class.getName());//Logger
	
	/**
	 * Object that manages users for contact assigning purposes
	 * @param database
	 */
	public FreeUsers(DatabaseHelper database){
		
		this.database = database;
		users = new ArrayList<User>();
		populateUsers();
		
	}
	
	/**
	 * Checks whether the given user Id can accept more contacts
	 * @param userId id to look for
	 * @return true if yes, false if not
	 */
	public boolean hasFreeUser(int userId){
		
		boolean hasFree = false;
		
		int i = 0; 
		boolean found = false;
		
		while(!found && i < users.size()){
			
			if(users.get(i).getId() == userId){
				
				found = true;
				
				if(users.get(i).canHaveMoreContacts())
					hasFree = true;
				
			}
			
			i++;
			
		}
		
		return hasFree;
		
	}
	
	/**
	 * Add a contact to the specified user (just increments the current count)
	 * @param id id of user
	 */
	public void addContact(int id){
		
		boolean found = false;
		int i = 0;
		
		while(!found && i < users.size()){
			
			if(users.get(i).getId() == id){
				
				users.get(i).addCurrent(1, database.getConnection());
				found = true;
				
			}
			
			i++;
			
		}
		
	}
	
	/**
	 * Get the next user who can get new contacts (e.g. the ones who haven't 
	 * reached max contacts yet)
	 * @return User or -1 if there are no more users who can get new contacts
	 */
	public int getNextAvailableUser(){
		
		int id = -1;;
		User user = null;
		
		if(nextUser != null){
			
			user = nextUser;
			currentIndex++;//increment index to next in users array
			
			if(currentIndex >= users.size())//if we've gone over the array size start again from 0
				currentIndex = 0;
			
			while(!users.get(currentIndex).canHaveMoreContacts() && 
					currentIndex < users.size())//while we can't have more contacts increment index
				currentIndex++;
			
			if(currentIndex < users.size())
				nextUser = users.get(currentIndex);
			else
				nextUser = null;
			
		}
		
		if(user != null)
			id = user.getId();
		
		return id;
		
	}
	
	/**
	 * Read in the users from the db
	 */
	private void populateUsers(){
		
		Statement selectUsers = null;
		ResultSet results = null;
		
		//Order by current ASC should help spread the load more evenly
		String SQL = "SELECT `id`, `max_contacts`, `current_contacts` " + //$NON-NLS-1$
				"FROM `users` WHERE `active` = 'Y' AND " + //$NON-NLS-1$
				"`current_contacts` < `max_contacts` ORDER BY " + //$NON-NLS-1$
				"`current_contacts` ASC"; //$NON-NLS-1$
		
		try{
			
			selectUsers = database.getConnection().createStatement();
			
			if(selectUsers.execute(SQL)){
				
				results = selectUsers.getResultSet();
				
				while(results.next())
					users.add(new User(results.getInt("id"), //$NON-NLS-1$
							results.getInt("max_contacts"), //$NON-NLS-1$
							results.getInt("current_contacts"))); //$NON-NLS-1$
					
				if(users.size() > 0)
					nextUser = users.get(0);
				
			}
			
		}catch(SQLException e){
			
			LOGGER.severe("Error getting user list"); //$NON-NLS-1$
			e.printStackTrace();
			
		}finally{
			
			if(selectUsers != null){
				
				try{
					selectUsers.close();
				}catch(Exception e){}
				selectUsers = null;
				
			}
			
			if(results != null){
				
				try{
					results.close();
				}catch(Exception e){}
				results = null;
				
			}
			
		}
		
	}
	
}