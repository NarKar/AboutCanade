package com.example.aboutcanada;

import java.util.List;

import org.json.JSONObject;

import com.example.aboutcanada.dom.News;
import com.example.aboutcanada.dom.Title;
import com.example.aboutcanada.http.Env;
import com.example.aboutcanada.http.HttpBase;
import com.example.aboutcanada.http.HttpBase.HttpResponseListener;
import com.example.aboutcanada.http.HttpBase.HttpTransport;
import com.example.aboutcanada.http.HttpBase.HttpTransport.METHOD;
import com.example.aboutcanada.http.HttpBase.HttpTransport.RESPONSE_FORMAT;
import com.example.aboutcanada.http.ResourceCache;

import android.app.Activity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
	
	private ListView newslist;
	private View loading;
	private ArrayAdapter<Title> adapter;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		this.newslist = (ListView)this.findViewById(R.id.newslist);
		this.loading = this.findViewById(R.id.loading);
		
		this.newslist.setAdapter((this.adapter = new ArrayAdapter<Title>(this, 0, 0){
			public View getView(int position, View reuse, ViewGroup parent){
				if(reuse == null){
					reuse = MainActivity.this.getLayoutInflater().inflate(R.layout.title_item, parent, false);
					TitleTag tag = new TitleTag();
					tag.title = (TextView)reuse.findViewById(R.id.itemtitle);
					tag.description = (TextView)reuse.findViewById(R.id.itemdescription);
					//tag.thumbnail = (ImageView)reuse.findViewById(R.id.itemthumb);
					reuse.setTag(tag);
				}
				
				Title title = getItem(position);
				TitleTag tag = (TitleTag)reuse.getTag();
				
				tag.title.setText(title.getTitle());
				tag.description.setText(title.getDescription());
				tag.description.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_action_refresh , 0);
				tag.description.setTag(title.getImageUrl());
				if(title.getImageUrl() == null || title.getImageUrl().equalsIgnoreCase("null")){
					tag.description.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
				} else {
					ResourceCache.loadImageInto(tag.description, title.getImageUrl(), 1);
				}
				
				return reuse;
			}
		}));
		
		refreshFeed();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		int id = item.getItemId();
		if (id == R.id.action_refresh) {
			this.refreshFeed();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}
	
	private void refreshFeed(){
		this.loading.setVisibility(View.VISIBLE);
		
		HttpTransport transport = new HttpTransport(Env.FEED_URL, METHOD.GET, RESPONSE_FORMAT.JSON);
		HttpBase.setContext(this);
		HttpBase.query(transport,
			new HttpResponseListener() {
				@Override
				public void responseReceived(HttpTransport transport) {
					if(transport.getErrors().isEmpty()){
						//No errors happened. Proceed
						News news = News.parseNews((JSONObject)transport.getResponsePayload(true));
						List<Title> titles = news.getNewsTitles();
						MainActivity.this.adapter.clear();
						MainActivity.this.adapter.addAll(titles);
						MainActivity.this.adapter.notifyDataSetChanged();
						MainActivity.this.loading.setVisibility(View.GONE);
						MainActivity.this.getActionBar().setTitle(news.getTitle());
					} else {
						Toast.makeText(MainActivity.this, "Unable to load the feed, please try again by clicking Refresh at the top right corner", Toast.LENGTH_LONG).show();
					}
				}
				
				@Override
				public void responseProgress(String message, int read, int total) {}
			}, false
		);
	}
	
	private static class TitleTag{
		public TextView title;
		public TextView description;
		public ImageView thumbnail;
	}
}
