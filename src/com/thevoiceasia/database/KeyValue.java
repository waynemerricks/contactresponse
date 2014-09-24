package com.thevoiceasia.database;

public class KeyValue {

	public String key = null, value = null, mapped = null;
	
	public KeyValue(String key, String value){
		
		this.key = key;
		this.value = value;
		
	}
	
	public KeyValue(String key, String value, String mapped){
		
		this.key = key;
		this.value = value;
		this.mapped = mapped;
		
	}
	
}
