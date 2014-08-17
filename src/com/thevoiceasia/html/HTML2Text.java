package com.thevoiceasia.html;

import net.htmlparser.jericho.*;

public class HTML2Text {
	
	public static String convert(String html){
		
		Source source = new Source(html);
		return source.getRenderer().toString();
		
	}
	
	public static void main(String[] args){
		
		System.out.println(convert(args[0]));
		
	}
	
}
