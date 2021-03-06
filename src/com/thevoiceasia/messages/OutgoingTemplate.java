package com.thevoiceasia.messages;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.thevoiceasia.database.KeyValue;

public class OutgoingTemplate {
	
	private int id = -1, priority = -1;
	private String from = null;
	private HashMap<Integer, TemplateContainer> languageContent = 
			new HashMap<Integer, TemplateContainer>();//Language (lower case), Container
	private boolean exclusive = true;
	private Date date = null;
	private SimpleDateFormat mysqlTimeStamp = 
			new SimpleDateFormat("yyyyMMddHHmmss"); //$NON-NLS-1$
	private KeyValue[] languages = null;
	
	protected static final Logger LOGGER = Logger.getLogger("com.thevoiceasia.messages.OutgoingTemplate"); //$NON-NLS-1$
	protected static final Level LEVEL = Level.INFO;//Logging level of this class
	
	
	/**
	 * Outgoing template helper object
	 * @param id
	 * @param fromEmail
	 * @param exclusive
	 * @param priority
	 * @param timestamp
	 */
	public OutgoingTemplate(int id, String from, boolean exclusive, 
			int priority, String timestamp, String templateBasePath, 
			KeyValue[] languages){
		
		LOGGER.setLevel(LEVEL);
		
		this.languages = languages;
		this.id = id;
		this.from = from;
		this.exclusive = exclusive;
		this.priority = priority;
		
		if(timestamp != null){
			
			try{
				this.date = mysqlTimeStamp.parse(timestamp);
			}catch(Exception e){//Should never happen assuming timestamp is fine
				this.date = new Date();
			}
			
		}
		
		String base = templateBasePath + System.getProperty("file.separator") +  //$NON-NLS-1$
				id;
		
		for(int i = 0; i < languages.length; i++){
			
			TemplateContainer tc = new TemplateContainer();
			
			//EMAIL
			String languagePath = ""; //$NON-NLS-1$
			
			if(languages[i].value.length() > 0)
				languagePath += "_" + languages[i].value; //$NON-NLS-1$
			
			String[] temp = readTemplateFile(base + "_email" + languagePath);  //$NON-NLS-1$
			tc.subject = temp[0];
			tc.email = temp[1];
			
			//SMS
			temp = readTemplateFile(base + "_sms" + languagePath); //$NON-NLS-1$
			tc.sms = temp[0];
			
			//LETTER
			temp = readTemplateFile(base + "_letter" + languagePath); //$NON-NLS-1$
			tc.letter = temp[0];
			
			//Add to map
			languageContent.put(Integer.parseInt(languages[i].key), tc);
			
		}
		
	}
	
	/**
	 * Gets the mapped language id
	 * @param language language to lookup
	 * @return 0 if we are the parent language, else id of the parent language
	 */
	private int getMappedLanguage(int language){
		
		int mapped = -1;
		int i = 0;
		
		while(mapped == -1 && i < languages.length){
			
			if(languages[i].key.equals("" + language)) //$NON-NLS-1$
				mapped = Integer.parseInt(languages[i].mapped);
			
			i++;
			
		}
		
		return mapped;
		
	}
	
	/**
	 * Gets the SMS template for the given language
	 * @param language use language id in languages table
	 * @return
	 */
	public String getSMS(int language){
		
		String content = null;
		
		int mapped = getMappedLanguage(language);
		
		if(mapped == 0)
			content = languageContent.get(language).sms;
		else
			content = languageContent.get(mapped).sms;
		
		return content;
		
	}
	
	/**
	 * Gets the Email Subject template for the given language
	 * @param language use language id in languages table
	 * @return
	 */
	public String getSubject(int language){
		
		String subject = null;
		
		int mapped = getMappedLanguage(language);
		
		if(mapped == 0)
			subject = languageContent.get(language).subject;
		else
			subject = languageContent.get(mapped).subject;
		
		return subject;
		
	}
	
	/**
	 * Gets the Email Body template for the given language
	 * @param language use language id in languages table
	 * @return
	 */
	public String getEmailBody(int language){
		
		String content = null;
		
		int mapped = getMappedLanguage(language);
		
		if(mapped == 0)
			content = languageContent.get(language).email;
		else
			content = languageContent.get(mapped).email;
		
		return content;
		
	}
	
	/**
	 * Gets the Letter template for the given language
	 * @param language use language id in languages table
	 * @return
	 */
	public String getLetter(int language){
		
		//Using Email Template for now as content will be the same
		//until we find out feet
		return getEmailBody(language);//languageContent.get(language).letter;
		
	}
	
	/**
	 * Reads the given file and returns the content
	 * @param path path to read
	 * @return Array[0] = content or in emails case 0 = subject, 1 = content
	 */
	private String[] readTemplateFile(String path){
		
		int strings = 1;
		
		if(path.contains("email")) //$NON-NLS-1$
			strings = 2;
		
		String[] returnValues = new String[strings];
		
		for(int i = 0; i < strings; i++)
			returnValues[i] = ""; //$NON-NLS-1$
		
		File template = new File(path);
		BufferedReader reader = null;
		
		if(template.exists() && template.canRead()){
			
			try{
				
				reader = new BufferedReader(
						new FileReader(template));
				
				boolean done = false;
				boolean first = true;
				
				while(!done){
					
					String line = reader.readLine();
					
					if(line == null)
						done = true;
					else{
						
						if(first && strings == 2 && line.startsWith("S:")){ //$NON-NLS-1$
							
							first = false;
							returnValues[0] = line.substring(2);//Subject
							
						}else
							returnValues[returnValues.length - 1] += line + "\n"; //$NON-NLS-1$
							
					}
					
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
		
		return returnValues;
		
	}
	
	/**
	 * True if this is exclusive to other templates
	 * e.g. Generic should not be sent if we have a higher priority template to
	 * send instead (its exclusive per day)
	 * @return
	 */
	public boolean isExclusive(){
		
		return exclusive;
		
	}
	
	/**
	 * 
	 * @return
	 */
	public boolean isDefaultTemplate(){
		
		boolean isDefault = false;
		
		if(date == null)
			isDefault = true;
		
		return isDefault;
		
	}
	
	/**
	 * Return the id of this object
	 * @return
	 */
	public int getId(){
		
		return id;
		
	}
	
	/**
	 * Return the priority
	 * @return
	 */
	public int getPriority(){
		
		return priority;
		
	}
	
	/**
	 * Return the address this email/sms should be from
	 * @return
	 */
	public String getFrom(){
		
		return from;
		
	}
	
}
