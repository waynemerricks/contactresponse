package com.thevoiceasia.database;

public class FieldMap {

	private String label = null, type = null, map = null;
	private int id = -1;
	private String[] data = null;
	
	public FieldMap(int id, String label, String type){
		
		this.id = id;
		this.label = label;
		this.type = type;
		
	}
	
	public FieldMap(int id, String label, String type, String data, String map){
		
		this.id = id;
		this.label = label;
		this.type = type;
		if(data != null)
			this.data = data.split(","); //$NON-NLS-1$
		this.map = map;
		
	}
	
	public String getMap(){
		
		return map;
		
	}
	
	public String[] getData(){
		
		return data;
	}
	
	public int getDataId(String key){
		
		int index = -1;
		
		for(int i = 0; i < data.length; i++)
			if(data[i].equalsIgnoreCase(key))
				index = i;
		
		return index;
		
	}
	
	public String getData(int index){
		
		return data[index];
		
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
