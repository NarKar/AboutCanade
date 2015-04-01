package com.example.aboutcanada.dom;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

public class News implements Serializable{
	
	private static final long serialVersionUID = -9191527191188847880L;

	private String title;
	
	private List<Title> newsTitles;
	
	public News(){
		this.title = "";
		this.newsTitles = new ArrayList<Title>();
	}
	
	public News(String title){
		this.setTitle(title);
		this.newsTitles = new ArrayList<Title>();
	}
	
	public News(List<Title> titles){
		this.setNewsTitles(titles);
		this.title = "";
	}
	
	public News(String title, List<Title> titles){
		this.setTitle(title);
		this.setNewsTitles(titles);
	}
	
	public Title addTitle(Title title){
		if(title != null){
			this.newsTitles.remove(title);
			this.newsTitles.add(title);
		}
		return title;
	}
	
	public Title removeTitle(Title title){
		if(title != null){
			this.newsTitles.remove(title);
		}
		return title;
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

	public List<Title> getNewsTitles() {
		return newsTitles;
	}

	public void setNewsTitles(List<Title> newsTitles) {
		this.newsTitles = newsTitles;
		if(this.newsTitles == null){
			this.newsTitles = new ArrayList<Title>();
		}
	}
	
	public static News parseNews(JSONObject object){
		if(object != null){
			News news = new News();
			
			news.setTitle(object.optString("title"));
			
			JSONArray titles = object.optJSONArray("rows");
			if(titles != null){
				for(int i = 0; i < titles.length(); ++i){
					JSONObject title = titles.optJSONObject(i);
					if(title != null){
						news.addTitle(Title.parseTitle(title));
					}
				}
			}
			
			return news;
		}
		
		return null;
	}
}
