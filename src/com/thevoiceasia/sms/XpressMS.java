package com.thevoiceasia.sms;

public class XpressMS {

	public static String toMessage(String parseMe){
		
		//SMS Format
		/*	Date:2014-08-17 07:00:55
			To:Our Number
			From No:Contacts number
	
			Message:Blah blah blah blah blah
	
	
	
			Message delivery powered by www.xpressms.com
	
		 */
		boolean gotMessageLine = false;
		
		String[] lines = parseMe.split("\n"); //$NON-NLS-1$
		int i = 0;
		boolean done = false;
		String message = null;
		
		while(i < lines.length && !done){
			
			if(!gotMessageLine && lines[i].startsWith("Message:")){ //$NON-NLS-1$
				
				gotMessageLine = true;
				message = lines[i].substring("Message:".length()) + "\n";  //$NON-NLS-1$//$NON-NLS-2$
				
			}else if(gotMessageLine && lines[i].startsWith("Message delivery powered by www.xpressms.com")) //$NON-NLS-1$
				done = true;
			else if(gotMessageLine)
				message += lines[i] + "\n"; //$NON-NLS-1$
			
			i++;
				
		}
		
		return message.trim();
		
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		
		String content = toMessage(args[0]);
		
		if(!args[1].equals("Y")) //$NON-NLS-1$
			System.out.println(content);
		
		if(args[1].equals("Y")) //$NON-NLS-1$
			if(content.length() > 50)
				System.out.println(content.substring(0, 47) + "..."); //$NON-NLS-1$
			else
				System.out.println(content);

	}

}
