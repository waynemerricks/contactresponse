package com.thevoiceasia.messages;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Iterator;
import java.util.logging.Logger;

import com.thevoiceasia.database.DatabaseHelper;

public class Footers {

	private DatabaseHelper database = null;
	private HashMap<String, String> footers = null;
	private String templateBasePath = null;
	private static final Logger LOGGER = Logger.getLogger("com.thevoiceasia.messages"); //$NON-NLS-1$
	
	public Footers(DatabaseHelper database, String templatePath){
		
		this.database = database;
		this.templateBasePath = templatePath;
		readFootersFromDB();
		
	}
	
	/**
	 * Gets the footer from the database and reads it into a string
	 * @return
	 */
	public String getEmailFooter(String language) {
		
		String footer = null;
		language = language.toLowerCase();
		
		if(footers == null)//null so read from DB
			readFootersFromDB();
		
		if(footers.containsKey(language)){
			
			if(footers.get(language).startsWith("exec ")){ //$NON-NLS-1$
				
				try{
					
					Process p = Runtime.getRuntime().exec(
							footers.get(language).substring(5));
					p.waitFor();
					
					BufferedReader reader = new BufferedReader(
							new InputStreamReader(p.getInputStream()));
					
					String line = ""; //$NON-NLS-1$
					footer = ""; //$NON-NLS-1$
					
					while((line = reader.readLine()) != null)
						footer += line + "\n";  //$NON-NLS-1$
					
				}catch(Exception e){
					
					LOGGER.severe("Error while executing footer command"); //$NON-NLS-1$
					e.printStackTrace();
					
				}
				
			}else
				footer = footers.get(language);
			
		}else
			footer = footers.get(""); //Default //$NON-NLS-1$
		
		return footer;
		
	}
	
	/**
	 * Read footers from settings db
	 */
	private void readFootersFromDB(){
			
		String SQL = "SELECT `value`, `name` FROM `settings` WHERE `name` LIKE " + //$NON-NLS-1$
				"'emailFooter%'"; //$NON-NLS-1$
		
		Statement selectFooter = null;
		ResultSet results = null;
		
		try{
			
			selectFooter = database.getConnection().createStatement();
			
			if(selectFooter.execute(SQL)){
				
				results = selectFooter.getResultSet();
				footers = new HashMap<String, String>();
				
				while(results.next()){
					
					String name = results.getString("name"); //$NON-NLS-1$
					
					if(!name.contains("_")) //$NON-NLS-1$
						name = ""; //$NON-NLS-1$
					else
						name = name.split("_")[1]; //$NON-NLS-1$
						
					footers.put(name, results.getString("value")); //$NON-NLS-1$
					
				}
				
			}
			
			Iterator<String> languages = footers.keySet().iterator();
			
			while(languages.hasNext()){
				
				String key = languages.next();
				
				if(!footers.get(key).startsWith("exec ")){ //$NON-NLS-1$
					
					//Not a command to execute so read from template file
					//email footer
					String file = footers.get(key);
					
					if(key.length() > 0)
						file += "_" + key; //$NON-NLS-1$
					
					String temp = readTemplateFile(templateBasePath +
							System.getProperty("file.separator") +  //$NON-NLS-1$
							file);
					
					footers.put(key, temp);
					
				}
				
			}
			
		}catch(SQLException e){
			
			LOGGER.severe("Error looking up email footer"); //$NON-NLS-1$
			e.printStackTrace();
			
		}finally{
			
			close(selectFooter, results);
			
		}
			
		//End of DB Read
		
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
	 * Reads the given file and returns the content
	 * @param path path to read
	 * @return String of the content read
	 */
	private String readTemplateFile(String path){
		
		File template = new File(path);
		BufferedReader reader = null;
		String content = ""; //$NON-NLS-1$
		
		if(template.exists() && template.canRead()){
			
			try{
				
				reader = new BufferedReader(
						new FileReader(template));
				
				boolean done = false;
				
				while(!done){
					
					String line = reader.readLine();
					
					if(line == null)
						done = true;
					else
						content += line + "\n"; //$NON-NLS-1$
						
				}
				
			}catch(FileNotFoundException e){
				
				LOGGER.severe("Template file not found " + template.getName()); //$NON-NLS-1$
				e.printStackTrace();
				
			}catch(IOException e){
				
				LOGGER.severe("Template IO Error " + template.getName()); //$NON-NLS-1$
				e.printStackTrace();
				
			}finally{
				
				if(reader != null){
					
					try{
						reader.close();
					}catch(Exception e){}
					
					reader = null;
					
				}
				
			}
			
		}
		
		return content;
		
	}
	
}
