package com.thevoiceasia.email;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import com.thevoiceasia.database.DatabaseHelper;

public class ReloadPreviews {

	private DatabaseHelper database = null;
	
	public ReloadPreviews(String host, String db, String user, String password,
			String archivePath){
		
		database = new DatabaseHelper(host, db, user, password);
		database.connect();
		
		String SQL = "SELECT `id` FROM `messages`"; //$NON-NLS-1$
		String SQLUpdate = "UPDATE `messages` SET `preview` = ? WHERE `id` = ?"; //$NON-NLS-1$
		
		Connection mysql = database.getConnection();
		Statement select = null;
		ResultSet ids = null;
		PreparedStatement updatePreview = null;
		
		try{
			
			select = mysql.createStatement();
			updatePreview = mysql.prepareStatement(SQLUpdate);
			select.execute(SQL);
			ids = select.getResultSet();
			
			while(ids.next()){
			
				//Read file and generate preview
				File archive = new File(archivePath + 
						System.getProperty("file.separator") + ids.getInt("id"));  //$NON-NLS-1$//$NON-NLS-2$
				
				System.out.println(archive.getAbsolutePath());
				
				if(archive.exists() && archive.canRead()){
					
					String preview = ""; //$NON-NLS-1$
					boolean done = false;
					boolean first = true;
					
					BufferedReader reader = new BufferedReader(
							new FileReader(archive));
					
					while(!done && preview.length() < 48){
						
						
						String line = reader.readLine();
						
						if(line == null)
							done = true;
						else{
							
							if(first && line.startsWith("S:")){ //$NON-NLS-1$
								
								first = false;
								preview = line.substring(2) + " "; //$NON-NLS-1$
								
							}else
								preview += line + " "; //$NON-NLS-1$
								
						}
						
					}
					
					reader.close();
					
					preview = preview.trim();
					
					if(preview.length() > 47)
						preview = preview.substring(0, 47) + "..."; //$NON-NLS-1$
					
					setPreview(preview, ids.getInt("id"), updatePreview); //$NON-NLS-1$
					
				}
				
			}
			
		}catch(SQLException | IOException e){
			
			e.printStackTrace();
			
		}finally{
			
			if(updatePreview != null){
	        	try{
	        		updatePreview.close();
	        		updatePreview = null;
	        	}catch(Exception e){}
	        }
			
			if(ids != null){
	        	try{
	        		ids.close();
	        		ids = null;
	        	}catch(Exception e){}
	        }
	        	
	        if(select != null){//Close Statement
	        	try{
	        		select.close();
	        		select = null;
	        	}catch(Exception e){}
	        }
	    	
		}
		
		database.disconnect();

	}

	private void setPreview(String preview, int id, 
			PreparedStatement updatePreview) {
		
		try{
			
			//Bind all variables to statement
			updatePreview.setString(1, preview);
			updatePreview.setInt(2,  id);
			
			//Execute it
			System.out.println("Updating " + id + "\n\t" + preview); //$NON-NLS-1$ //$NON-NLS-2$
			updatePreview.executeUpdate();
			
		}catch(SQLException e){
			
			e.printStackTrace();
			
		}
		
	}
	
	public static void main(String[] args){
		
		if(args.length != 5){
			System.out.println("USAGE: DBHOST DB DBUSER DBPASS ARCHIVEPATH"); //$NON-NLS-1$
			System.exit(1);
		}else
			new ReloadPreviews(args[0], args[1], args[2], args[3], args[4]);
			
	}
	
}
