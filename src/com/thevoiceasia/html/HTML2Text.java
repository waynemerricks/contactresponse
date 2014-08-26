package com.thevoiceasia.html;

import java.util.List;

import net.htmlparser.jericho.*;

public class HTML2Text {
	
	public static String convert(String html){
		
		Source source = new Source(html);
		return source.getRenderer().toString();
		
	}
	
	public static boolean hasHTML(String html){
		
		boolean has = false;
		
		Source source = new Source(html);
		List<Tag> tags = source.getAllTags();
		
		if(tags.size() > 0)
			has = true;
		
		return has;
		
	}
	
	public static void main(String[] args){
		
		System.out.println(hasHTML(args[0]));
		
	}
	
}
