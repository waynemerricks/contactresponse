package com.thevoiceasia.database;

public class FieldMap {

	private String label = null, type = null;
	private int id = -1;
	
	public FieldMap(int id, String label, String type){
		
		this.id = id;
		this.label = label;
		this.type = type;
		
	}
	
	public String getLabel(){
		
		return label;
		
	}
	
	public String getType(){
		
		return type;
		
	}
	
	public int getID(){
		
		return id;
		
	}
	
}
