package com.example.aboutcanada.dom;

import java.io.Serializable;

import org.json.JSONObject;

public class Title implements Serializable{
	
	private static final long serialVersionUID = -6002628224365985081L;
	private String title;
	private String description;
	private String imageUrl;
	
	public Title(){
		this.setTitle("");
		this.setDescription("");
	}
	
	public Title(String title, String description, String imageUrl){
		this.setTitle(title);
		this.setDescription(description);
		this.setImageUrl(imageUrl);
	}
	
	public String getTitle() {
		return title;
	}
	
	public void setTitle(String title) {
		this.title = title;
		if(this.title == null || this.title.equalsIgnoreCase("null")){
			this.title = "";
		}
	}
	
	public String getDescription() {
		return description;
	}
	
	public void setDescription(String description) {
		this.description = description;
		if(this.description == null || this.description.equalsIgnoreCase("null")){
			this.description = "";
		}
	}
	
	public String getImageUrl() {
		return imageUrl;
	}
	
	public void setImageUrl(String imageUrl) {
		this.imageUrl = imageUrl;
	}
	
	public static Title parseTitle(JSONObject object){
		if(object != null){
			Title title = new Title(object.optString("title"), object.optString("description"), object.optString("imageHref", null));
			if(title.title.length() == 0 && title.description.length() == 0 && (title.imageUrl == null || title.imageUrl.equalsIgnoreCase("null"))){
				return null;
			}
			
			return title;
		}
		
		return null;
	}

}
