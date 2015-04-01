package com.example.aboutcanada.http;

import java.util.HashMap;
import java.util.Map;

import com.example.aboutcanada.R;
import com.example.aboutcanada.http.HttpBase.HttpResponseListener;
import com.example.aboutcanada.http.HttpBase.HttpTransport;
import com.example.aboutcanada.http.HttpBase.HttpTransport.METHOD;
import com.example.aboutcanada.http.HttpBase.HttpTransport.RESPONSE_FORMAT;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.os.AsyncTask;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

public class ResourceCache {
	
	private static final Map<String, Boolean> mapUrlLoading = new HashMap<String, Boolean>();
	private static final Map<String, String> mapUrlPath = new HashMap<String, String>();
	private static final Map<String, Bitmap> mapPathBitmap = new HashMap<String, Bitmap>();
	
	public static void loadImageInto(final TextView view, final String url, int recursionDepth){
		if(mapUrlPath.get(url) != null){ 
			//the resource loading has been initiated. May have loaded or not
			
			if(mapPathBitmap.get(mapUrlPath.get(url)) != null){
				
				// resource has been loaded and cached. Simply serve
				if(((String)view.getTag()).equals(url)){
					view.setCompoundDrawablesWithIntrinsicBounds(null, null, new BitmapDrawable(view.getResources(), mapPathBitmap.get(mapUrlPath.get(url))), null);
				}
			} else {
				// resource has been loaded but not cached
				// Chache it, then serve
				cacheResourceAndServe(view, url, mapUrlPath.get(url), recursionDepth);
			}
		} else {
			//resource loading has not been initiated at all. Download the resource
			downloadResourceAndServe(view, url, recursionDepth);
		}
	}
	
	private static void cacheResourceAndServe(final TextView view, final String url, final String path, final int recursionDepth){
		new AsyncTask<Void, Void, Void>(){
			@Override
			public Void doInBackground(Void... params) {
				try{
					BitmapFactory.Options options = new BitmapFactory.Options();
					options.inJustDecodeBounds = true;
					Bitmap bmp = BitmapFactory.decodeFile(path, options);
					double w = options.outWidth;
					double h = options.outHeight;
					double scaleFactor = w / 70.0;
					if(scaleFactor != 0){
						w = 70; // 70px
						h = h / scaleFactor; // aspect ratio preserved
					} else {
						w = 70; // 70px
						h = 70; // scale factor was not reported properly probably due to image metadata fault. Use fixed sizes. Not ideal
					}
					
					
					Log.e("OUT", ">>>>> Image sizes w = " + w + ", h = " + h + ", scale = " + scaleFactor);
					
					options.inJustDecodeBounds = false;
					options.outHeight = (int)h;
					options.outWidth = (int)w;
					
					bmp = BitmapFactory.decodeFile(path, options); // results a downscaled bitmap with width=70 and proportional height
					
					mapPathBitmap.put(path, bmp);
				}catch(Throwable th){
					// decoding failed
				}
				
				return null;
			}
			
			public void onPostExecute(Void param){
				if(mapPathBitmap.get(path) == null){
					// decoding has failed. hide the image
					view.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
				} else {
					//Continue onto serving
					loadImageInto(view, url, recursionDepth + 1);
				}
			}
		}.execute((Void[])null); 
	}
	
	private static void downloadResourceAndServe(final TextView view, final String url, final int recursionDepth){
		Log.e("OUT", "Requested to download image resource at "  + url + "\n\n");
		if(mapUrlLoading.get(url) == null || !mapUrlLoading.get(url)){
			// Load only if there is no previous loading task was initiated
			
			mapUrlLoading.put(url, true);
			HttpTransport transport = new HttpTransport(url, METHOD.GET, RESPONSE_FORMAT.DOWNLOAD);
			HttpBase.setContext(view.getContext());
			HttpBase.query(transport, 
				new HttpResponseListener() {
					
					@Override
					public void responseReceived(HttpTransport transport) {
						if(transport.getErrors().isEmpty()){
							//No errors. proceed
							String path = transport.getResponsePayload(true).toString();
							mapUrlPath.put(url, path);
							
							//Continue onto caching
							loadImageInto(view, url, recursionDepth + 1);
						} else {
							// not downloaded, the image won't be shown
							view.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
						}
						
						//reset the loading flag
						mapUrlLoading.put(url, false);
					}
					
					@Override
					public void responseProgress(String message, int read, int total) {}
				}, false
			);
		} else {
			//resource is downloading but not downloaded yet
			//view shall be queued
		}
	}
}
