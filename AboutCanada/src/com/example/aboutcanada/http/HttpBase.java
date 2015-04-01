package com.example.aboutcanada.http;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.json.JSONTokener;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.AsyncTask;
import android.util.Base64;
import android.util.Log;

/**
 * 
 * @author Narek Karapetyan
 * 
 * The copyright of the code in this class belongs to Narek Karapetyan.
 * It is developed under private usage agreement and shall not be reused, reproduced or redistributed in full or in part.
 *
 */

@SuppressLint("DefaultLocale") 
public class HttpBase {

	private static final String TWO_HYPHENS = "--";
	private static final String BOUNDARY = "234345834357238527304ABCDEF48239413274531762";
	private static final String LINE_END = "\r\n";
	private static final String REQUEST_DIVIDER = TWO_HYPHENS + BOUNDARY + LINE_END;
	protected static final char bounds = 131;

	private static long bytesRead;
	
	//Set this property everytime a new request is created
	//No difference if the queries are using different instances of Context
	private static Context context;
	
	public static class SessionTimedOutException extends Exception {
		private static final long serialVersionUID = -5620411984247246354L;
	}
	
	static{
		// not sure if all certificates of the hosting site are valid. So trust all certificates to ensure seamless flow of request.
		// Shall not be done in real life solutions
		HttpBase.trustAllHttpsCertificates();
	}

	public static final void initBytesRead(long read) {
		if (bytesRead == 0) {
			bytesRead = read;
		}
	}

	public static final long getBytesRead() {
		return bytesRead;
	}

	/**
	 * For Android 4.0+ this method will enable the http cache of the phone/web communication channel.
	 * This method will have no effect on lower version systems.
	 * @param context - the application context
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static final void enableHttpResponseCache(Context context) {
		try {
			long httpCacheSize = (10 << 10) << 10; // 10 MiB
			File httpCacheDir = new File(context.getCacheDir(), "http");

			Class responseCache = Class.forName("android.net.http.HttpResponseCache");
			if (responseCache.getMethod("getInstalled", (Class[]) null).invoke(null, (Object[]) null) == null) {
				responseCache.getMethod("install", File.class, long.class).invoke(null, httpCacheDir, httpCacheSize);
			}
		} catch (Throwable th) {
			// Just ignore, will happen on Androids less than 4
		}
	}
	
	private static final void setupRequestMethod(HttpTransport transport){
		if(transport != null){
			if(transport.method == null){
				transport.method = HttpTransport.METHOD.GET;
			}
			if(transport.requestPayload != null && transport.requestPayload.length() > 0){
				transport.method = HttpTransport.METHOD.POST;
			} 
			if(transport.requestParameters != null){
				for(String key : transport.requestParameters.keySet()){
					HttpData data = transport.requestParameters.get(key);
					if(data instanceof FileData || data instanceof StreamData){
						transport.containsAttachments = true;
						transport.method = HttpTransport.METHOD.POST;
						break;
					}
				}
			}
		}
	}
	
	/**
	 * Constructs and returns a string of url-encoded http request parameters 
	 * from the provided transport object.
	 * @param transport - HttpTransport object
	 * @param charset - character set to use when url encoding. Can be null.
	 * @return - the string of url encoded parameters, or an empty string if something went wrong.
	 */
	public static final String getUrlEncodedRequestParameters(HttpTransport transport, String charset){
		StringBuffer sb = new StringBuffer();
		
		try{
			for(String key : transport.requestParameters.keySet()){
				HttpData data = transport.requestParameters.get(key);
				if(data instanceof StringData){
					((StringData)data).getUrlEncoded(sb, charset);
					sb.append("&");
				}
			}
			if(sb.length() > 0){
				sb.deleteCharAt(sb.length() - 1);
			}
		}catch(Throwable th){
			Log.e("OUT", th.getMessage(), th);
			transport.errors.add(th);
		}
		
		return sb.toString();
	}
	
	/**
	 * Will construct URL Connection with proper request method set for the query.
	 * If the transport object has a valid request payload and / or a request parameter which is not an ordinary string
	 * then the request method will explicitly be set to POST regardless of the specified value in the transport object.
	 * Otherwise the request method will be set to the value that is specified in the transport object.
	 * If transport object specifies null value for the request method, GET will be used instead.
	 * The request content type will be set to the most commonly expected one for non-GET requests if the value is
	 * not explicitly specified
	 * 
	 * If the final determined request method is GET then all the request parameters will be URL encoded and appended to the URL itself.
	 * @param transport
	 * @return
	 */
	public static final HttpURLConnection getHttpConnection(HttpTransport transport) {
		try {
			HttpURLConnection connection = null;
			
			setupRequestMethod(transport);
			
			String charset = transport.getRequestHeaderAcceptCharset();
			
			if(transport.method == HttpTransport.METHOD.POST){
				connection = (HttpURLConnection) (new URL(transport.url).openConnection());
				connection.setRequestMethod(transport.method.name);
				connection.setDoOutput(true);
				//connection.setChunkedStreamingMode(0);
				
				
				if(transport.containsAttachments){
					transport.setRequestHeaderContentType("multipart/form-data; boundary=" + BOUNDARY);
				} else {
					// IF the request content type was not specified explicitly then set the most commonly expected one
					if(transport.getRequestHeaderContentType() == null){
						if(charset != null){
							transport.setRequestHeaderContentType("application/x-www-form-urlencoded;charset=" + charset);
						} else {
							transport.setRequestHeaderContentType("application/x-www-form-urlencoded");
						}
					}
				}
			} else if(transport.method == HttpTransport.METHOD.GET){
				String params = getUrlEncodedRequestParameters(transport, charset);
				if(!params.trim().equals("")){
					if(transport.url.contains("?")){
						transport.url = transport.url + "&" + params;
					} else {
						transport.url = transport.url + "?" + params;
					}
				}
				connection = (HttpURLConnection) (new URL(transport.url).openConnection());
			}
			
			connection.setConnectTimeout(transport.getConnectTimeout());
			connection.setReadTimeout(transport.getReadTimeout());
			connection.setDoInput(true);
			
			return connection;
		} catch (Throwable th) {
			Log.e("OUT", th.getMessage(), th);
			transport.errors.add(th);
		}
		return null;
	}
	
	private static final void synchronizeRequestHeadersWithConnection(HttpTransport transport, HttpURLConnection httpConnection){
		if(transport != null && transport.requestHeaders != null && httpConnection != null){
			try{
				for(String key : transport.requestHeaders.keySet()){
					httpConnection.setRequestProperty(key, transport.requestHeaders.get(key));
				}
			}catch(Throwable th){
				Log.e("OUT", th.getMessage(), th);
				transport.errors.add(th);
			}
		}
	}
	
	/**
	 * Will attempt to send the request parameters using attachment or string mechanisms - whichever is applicable.
	 * If no request parameters are specified then the method will attempt to send the request payload.
	 * If the payload is not specified either, then the upstream will be closed with no data sent.
	 * @param transport - request data
	 * @param connection - connection used
	 */
	private static final void sendRequestInPost(HttpTransport transport, HttpURLConnection connection){
		try{
			String charset = transport.getRequestHeaderAcceptCharset();
			if(charset == null){
				charset = "utf-8";
			}
			
			if(transport.requestParameters.size() > 0 || transport.requestPayload != null){
				OutputStream os = connection.getOutputStream();
				
				if(transport.containsAttachments){
					PrintWriter pw = new PrintWriter(new OutputStreamWriter(os, charset), true);
					
					for(String key : transport.requestParameters.keySet()){
						transport.requestParameters.get(key).writeParameter(pw, os, charset);
					}
					
					pw.append(TWO_HYPHENS).append(BOUNDARY).append(TWO_HYPHENS).append(LINE_END);
					pw.flush();
					pw.close();
				} else {
					if(transport.requestParameters.size() > 0){
						os.write(getUrlEncodedRequestParameters(transport, charset).getBytes());
					} else if(transport.requestPayload != null){
						os.write(transport.requestPayload.toString().getBytes());
					}
					os.flush();
					os.close();
				}
			}
		}catch(Throwable th){
			Log.e("OUT", th.getMessage(), th);
			transport.errors.add(th);
		}
	}
	
	private static String getResponseCharset(String contentType){
		String charset = null;
		if(contentType != null){
			for (String param : contentType.replace(" ", "").split(";")) {
			    if (param.startsWith("charset=")) {
			        charset = param.split("=", 2)[1];
			        break;
			    }
			}
		}
		return charset;
	}
	
	private static boolean isResponseTypeText(String contentType){
		if(contentType != null){
			for (String param : contentType.replace(" ", "").split(";")) {
			    if (param.contains("text/") || param.equalsIgnoreCase("application/json") || param.equalsIgnoreCase("application/xml")) {
			        return true;
			    }
			}
		}
		return false;
	}
	
	private static HttpURLConnection queryAndLoadResponseHeaders(HttpTransport transport, boolean retainRequestData){
		try{
			HttpURLConnection connection = getHttpConnection(transport);
			synchronizeRequestHeadersWithConnection(transport, connection);
			if(transport.method == HttpTransport.METHOD.POST){
				sendRequestInPost(transport, connection);
			}
			if(!retainRequestData){
				transport.clearRequest();
			}
			transport.responseCode = connection.getResponseCode();
			
			Map<String, List<String>> responseHeaders = connection.getHeaderFields();
			for (String key : responseHeaders.keySet()) {
			   transport.setCustomResponseHeader(key, responseHeaders.get(key));
			}
			
			return connection;
		}catch(Throwable th){
			Log.e("OUT", th.getMessage(), th);
			transport.errors.add(th);
		}
		return null;
	}
	
	// The following methods are conventional HTTP interaction methods to query an end point
	// All queries will follow the below listed steps from query initialization to response delivery:
	// 1. Synchronize request headers with HttpUrlConnection object
	// 2. Synchronize request parameters with HttpUrlConnection object. The method of doing this depends on
	//    2.a. Request parameters contain attachments
	//    2.b. Request parameters are plain strings
	// 3. Send the request pay-load if necessary
	// 4. Get response code from the HttpUrlConnection object
	// 5. Receive the response pay-load if applicable
	// 6. Synchronize HttpUrlConnection object's response headers with HttpTransport object
	// 7. Serve the HttpTransport object back to the requesting code
	//--------------------------------------------------------------------------------------------------
	// 8. Requesting code is advised to clean up request attributes straight away from the HttpTransport object to preserve memory.
	//    If the request attributes will no longer be needed in the requesting code.
	// 9. Use HttpTransport object
	// 10. Discard the HttpTransport object to make sure all data and URL connections are being recycled.
	// Note: the used HttpUrlConnection will be set into the HttpTransport for requestHeader method only because of the nature of the request.
	// For the rest of the time the processing method will take care of cleaning the http Connection object before returning results.
	
	/**
	 * Interface to implement to be notified about web query progress and results.
	 * @author Narek Karapetyan
	 *
	 */
	public static interface HttpResponseListener{
		public void responseReceived(HttpTransport transport);
		public void responseProgress(String message, int read, int total);
	}
	
	/**
	 * Use this method to query a remote resource for response results.
	 * @param transport - the transport object to specify the request data and retain the response data
	 * @param listener - a <code>HttpResponseListener</code> implementation to be notified about request progress and result. Can be reused.
	 * @param retainRequestData - specify true to retain request data in the <code>transport</code> object after it has been used. 
	 *        Specify false to cleanup request data as soon as they are used. Retain request data only when absolutely necessary.
	 */
	public static final void query(final HttpTransport transport, final HttpResponseListener listener, final boolean retainRequestData) {
		if (transport != null) {
			new AsyncTask<Void, Integer, Void>() {
				@Override
				protected Void doInBackground(Void... params) {
					try {
						HttpURLConnection connection = null;
						if(transport.reuseConnection){
							connection = transport.connection;
						} else {
							HttpURLConnection.setFollowRedirects(transport.isFollowRedirects());
							connection = queryAndLoadResponseHeaders(transport, retainRequestData);
						}
						
						if(transport.responseCode > 199 && transport.responseCode < 300){
							transport.responseBinary = !isResponseTypeText(transport.getResponseHeaderContentType());
							if(transport.responseBinary || transport.responseFormat == HttpTransport.RESPONSE_FORMAT.DOWNLOAD){
								// Binary data, download and save into a file. set the response payload to the absolute file path
								this.download(connection);
							} else {
								// Textual data
								this.loadResponse(connection, getResponseCharset(transport.getResponseHeaderContentType()));
							}
							transport.parseResponse();
						}
						
						if (connection != null){
							connection.disconnect();
						}
					} catch (Throwable th) {
						Log.e("OUT", th.getMessage(), th);
						transport.errors.add(th);
					}
					return null;
				}
				
				@Override
			    protected void onProgressUpdate(Integer... progress) {
					try{
						if(listener != null){
							listener.responseProgress("Loading...", progress[0], progress[1]);
						}
					}catch(Throwable th){
						Log.e("OUT", th.getMessage(), th);
						transport.errors.add(th);
					}
			    }

				@Override
				protected void onPostExecute(Void root) {
					try{
						if (listener != null) {
							listener.responseReceived(transport);
						}
					}catch(Throwable th){
						Log.e("OUT", th.getMessage(), th);
						transport.errors.add(th);
					}
				}
				
				private void download(HttpURLConnection connection){
					try{
						Integer[] progress = new Integer[2];
						progress[0] = 0;
						progress[1] = connection.getContentLength();
						
						File downloadDirectory = new File(context.getExternalFilesDir(null) + "/download/" + Env.APPLICATION_TITLE);
						downloadDirectory.mkdirs();
						
						Log.e("OUT", transport.url);
						
						String fileName = null;
						if(transport.getResponsePayload(false) != null){
							fileName = transport.getResponsePayload(false).toString();
							transport.clearResponsePayload();
						} else {
							fileName = transport.url.substring(transport.url.lastIndexOf("/")).replace("?", "_").replace("&", "_");
						}
						
						File downloadFile = new File(downloadDirectory, fileName);
						
						InputStream in = connection.getInputStream();
						DataOutputStream dos = new DataOutputStream(new FileOutputStream(downloadFile));

						byte[] chunk = new byte[1 << 16]; // 64K cache
						int readBytes = -1;
						while ((readBytes = in.read(chunk)) != -1) {
							dos.write(chunk, 0, readBytes);
							progress[0] += readBytes;
							bytesRead += readBytes;
							
							this.publishProgress(progress);
						}

						in.close();
						dos.flush();
						dos.close();

						StringBuffer payload = ((StringBuffer)transport.getResponsePayload(true));
						payload.delete(0, payload.length()).append(downloadFile.getAbsolutePath());
					}catch(Throwable th){
						Log.e("OUT", th.getMessage(), th);
						transport.errors.add(th);
					}
				}
				
				private void loadResponse(HttpURLConnection connection, String charset){
					try{
						Integer[] progress = new Integer[2];
						progress[0] = 0;
						progress[1] = connection.getContentLength();
						
						BufferedReader br = null;
						if(charset == null){
							br = new BufferedReader(new InputStreamReader(connection.getInputStream()));
						} else {
							br = new BufferedReader(new InputStreamReader(connection.getInputStream(), Charset.forName(charset)));
						}

						StringBuffer sb = ((StringBuffer)transport.getResponsePayload(true));
						
						int readBytes = 0;
						for (String line; (line = br.readLine()) != null; ) {
							readBytes = line.length();
							bytesRead += readBytes;
							progress[0] += readBytes;
							line = line.trim();
							if (line.length() > 0) {
								if(this.shallDecodeHTMLEntities()){
									decodeHTMLEntitiesAndAppend(sb, line);
								} else {
									sb.append(line);
								}
								
								sb.append("\n");
							}
							this.publishProgress(progress);
						}

						br.close();
					}catch(Throwable th){
						Log.e("OUT", th.getMessage(), th);
						transport.errors.add(th);
					}
				}
				
				private boolean shallDecodeHTMLEntities(){
					return 
						transport.responseFormat == HttpTransport.RESPONSE_FORMAT.JSON || 
						transport.responseFormat == HttpTransport.RESPONSE_FORMAT.HTML_JSON || 
						transport.responseFormat == HttpTransport.RESPONSE_FORMAT.HTML_XML || 
						transport.responseFormat == HttpTransport.RESPONSE_FORMAT.XML; 
				}
				
			}.execute((Void[]) null);
		}
	}
	
	/**
	 * Use this method to query the headers of a remote resource. No results will be populated.
	 * @param transport - the transport object to specify the request data and retain the response data
	 * @param listener - a <code>HttpResponseListener</code> implementation to be notified about request progress and result. Can be reused.
	 * @param retainRequestData - specify true to retain request data in the <code>transport</code> object after it has been used. 
	 *        Specify false to cleanup request data as soon as they are used. Retain request data only when absolutely necessary.
	 */
	public static final void queryHeader(final HttpTransport transport, final HttpResponseListener listener, final boolean retainRequestData){
		if(!(transport == null || listener == null)){
			new AsyncTask<Void, Void, Void>() {
				@Override
				protected Void doInBackground(Void... params) {
					try {
						HttpURLConnection connection = queryAndLoadResponseHeaders(transport, retainRequestData);
						transport.connection = connection;
						transport.reuseConnection = true;
					} catch (Throwable th) {
						Log.e("OUT", th.getMessage(), th);
						transport.errors.add(th);
					}
					return null;
				}

				@Override
				protected void onPostExecute(Void root) {
					try{
						if (listener != null) {
							listener.responseReceived(transport);
						}
					}catch(Throwable th){
						Log.e("OUT", th.getMessage(), th);
						transport.errors.add(th);
					}
				}
			}.execute((Void[]) null);
		}
	}
	
	/**
	 * Parses and constructs XML DOM hierarchy from the specified text
	 * @param text - the XML source
	 * @return - the root DOM element
	 */
	public static final Element getDomFromXMLSource(String text) {
		try {
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			dbf.setIgnoringElementContentWhitespace(true);
			dbf.setCoalescing(true);

			DocumentBuilder db = dbf.newDocumentBuilder();
			InputSource source = new InputSource(new StringReader(text));
			Document dom = db.parse(source);

			return dom.getDocumentElement();
		} catch (Throwable th) {
			Log.e("OUT", th.getMessage(), th);
		}
		return null;
	}
	
	/**
	 * Will extract JSON structure from HTML by using the tag/tagId that encapsulates the JSON structure.
	 * @param buffer - the HTML text
	 * @param tagName - the HTML tag to look for
	 * @param tagId - the tag id to differentiate the tag by
	 * @return - the resulting JSONTokener or null if something went wrong.
	 */
	public static final Object getJSONFromHTMLWrapper(StringBuffer buffer, String tagName, String tagId) throws SessionTimedOutException {
		try{
			return new JSONTokener(getDomFromHTMLWrapper(buffer, tagName, tagId).getTextContent().trim()).nextValue();
		}catch(Throwable th){
			Log.e("OUT", th.getMessage(), th);
		}
		return null;
	}
	
	
	/**
	 * Will extract XML structure from HTML by using the tag/tagId that encapsulates the XML structure.
	 * @param buffer - the HTML text
	 * @param tagName - the HTML tag to look for
	 * @param tagId - the tag id to differentiate the tag by
	 * @return - the resulting XML root Element or null if something went wrong.
	 */
	public static final Element getDomFromHTMLWrapper(StringBuffer buffer, String tagName, String tagId){
		try{
			String wrapperStart = "<" + tagName;
			String wrapperEnd = "</" + tagName + ">";
			
			if (buffer.indexOf(wrapperStart) == -1) {
				Log.e("OUT", "getDomFromHTMLWrapper > Could not find tag " + wrapperStart + " in buffer\n" + buffer.toString());
			} else {
				buffer.delete(0, buffer.indexOf(wrapperStart));
				buffer.delete(buffer.lastIndexOf(wrapperEnd) + wrapperEnd.length(), buffer.length());
				
				Element dom = getDomFromXMLSource("<tag>" + buffer.toString() + "</tag>");
				NodeList tags = dom.getElementsByTagName(tagName);
				
				Element xmlTag = null;
				if(tagId != null){
					for(int i = 0; i < tags.getLength(); ++i){
						Element tag = (Element)tags.item(i);
						if(tagId.equals(tag.getAttribute("id"))){
							xmlTag = tag;
							break;
						}
					}
				}
				if(xmlTag == null){
					xmlTag = (Element)tags.item(0);
				}
				
				return xmlTag;
			}
		}catch(Throwable th){
			Log.e("OUT", th.getMessage(), th);
		}
		Log.e("OUT", "getDomFromHTMLWrapper > Returning null");
		return null;
	}
	
	/**
	 * Parses and constructs JSON hierarchy from the specified text
	 * @param text - the JSON source
	 * @return - the root JSONTokener object
	 */
	public static final Object getObjectFromJsonFeed(String text) {
		try {
			return new JSONTokener(text).nextValue();
		} catch (Throwable th) {
			Log.e("OUT", th.getMessage(), th);
		}
		return null;
	}
	
	/**
	 * Decodes special escape sequences to clear text characters. Uses automatons, runs highly effectively.
	 * @param sb - buffer to append the decoded line to
	 * @param line - the line to decode
	 */
	public static final void decodeHTMLEntitiesAndAppend(StringBuffer sb, String line) {
		if (sb != null && line != null) {
			String amp = "&amp;";

			int size = line.length();

			int index = 0;
			while (index < size) {
				char ch = line.charAt(index);
				if (ch == 65279) {
					++index;
				} else if (ch == '&') {
					if (index + 1 < size) {
						char ch_1 = line.charAt(index + 1);
						if (index + 3 < size && line.charAt(index + 2) == 't' && line.charAt(index + 3) == ';') {
							if (ch_1 == 'l') {
								sb.append("<");
							} else if (ch_1 == 'g') {
								sb.append(">");
							}
							index += 4;
						} else {
							sb.append(amp);
							if (index + 4 < size && ch_1 == 'a' && line.charAt(index + 2) == 'm' && line.charAt(index + 3) == 'p' && line.charAt(index + 4) == ';') {
								index += 5;
							} else {
								++index;
							}
						}
					}
				} else {
					sb.append(ch);
					++index;
				}
			}
		}
	}

	
	/**
	 * base-64 encodes the specified value
	 * @param value - value to encode
	 * @return - encoded value
	 */
	public static final String encodeBase64(String value) {
		return Base64.encodeToString(value.getBytes(), Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
	}

	
	/**
	 * base-64 decodes the specified value
	 * @param value - value to decode
	 * @return - decoded value
	 */
	public static final String decodeBase64(String value) {
		return new String(Base64.decode(value.getBytes(), Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING));
	}

	/**
	 * Http multipart form request parameter interface. Implement this interface if a specific type of data needs to be sent upstream.
	 * Note that at the end of the day all data is sent using byte arrays, so most of the time the existing implementations should be enough.
	 * @author narekkarapetyan
	 *
	 */
	public static interface HttpData {
		public void writeParameter(PrintWriter pw, OutputStream os, String charset);
	};

	
	/**
	 * Convenience implementation of <code>HttpData</code> to send textual parameters in a multipart form requests.
	 * @author narekkarapetyan
	 *
	 */
	public static class StringData implements HttpData {
		public String name;
		public String value;

		public StringData(String name, String value) {
			this.name = name;
			this.value = value;
		}

		@Override
		public void writeParameter(PrintWriter pw, OutputStream os, String charset) {
			try {
				if (pw != null) {
					pw.append(REQUEST_DIVIDER);
					pw.append(HttpTransport.RESPONSE_HEADER.CONTENT_DISPOSITION.name + ": form-data; name=\"" + this.name + "\"");
					pw.append(LINE_END);
					if(charset == null){
						pw.append(HttpTransport.REQUEST_HEADER.CONTENT_TYPE.name + ": text/plain;");
					} else {
						pw.append(HttpTransport.REQUEST_HEADER.CONTENT_TYPE.name + ": text/plain; charset=" + charset);
					}
					pw.append(LINE_END);
					pw.append(LINE_END);
					pw.append(this.value);
					pw.append(LINE_END);
					pw.flush();
				}
			} catch (Throwable th) {
				Log.e("OUT", th.getMessage(), th);
			}
		}
		
		@SuppressWarnings("deprecation")
		public void getUrlEncoded(StringBuffer sb, String charset){
			try{
				if(charset == null){
					sb.append(this.name).append("=").append(URLEncoder.encode(this.value));
				} else {
					sb.append(this.name).append("=").append(URLEncoder.encode(this.value, charset));
				}
			}catch(Throwable th){
				Log.e("OUT", th.getMessage(), th);
			}
		}
		
		@SuppressWarnings("deprecation")
		public String getUrlEncoded(String charset){
			try{
				if(charset == null){
					return this.name + "=" + URLEncoder.encode(this.value);
				} else {
					return this.name + "=" + URLEncoder.encode(this.value, charset);
				}
			}catch(Throwable th){
				Log.e("OUT", th.getMessage(), th);
			}
			return null;
		}
	}

	
	/**
	 * Convenience implementation of <code>HttpData</code> to send stream in a multipart form requests.
	 * Stream is opened via the provided local path. Content disposition type is <code>form-data; name="media-file-data"</code>
	 * @author Narek Karapetyan
	 *
	 */
	public static class StreamData implements HttpData {
		public String name;
		public InputStream stream;

		public StreamData(String name, InputStream stream) {
			this.name = name;
			this.stream = stream;
		}

		@Override
		public void writeParameter(PrintWriter pw, OutputStream os, String charset) {
			try {
				if (pw != null) {
					pw.append(REQUEST_DIVIDER);
					pw.append(HttpTransport.RESPONSE_HEADER.CONTENT_DISPOSITION.name + ": form-data; name=\"" + this.name + "\"; filename=\"" + this.name + "\"");
					pw.append(LINE_END);
					pw.append(HttpTransport.REQUEST_HEADER.CONTENT_TYPE.name + ": application/octet-stream");
					pw.append(LINE_END);
					pw.append("Content-Transfer-Encoding: binary");
					pw.append(LINE_END);
					pw.flush();
					
					DataInputStream dis = new DataInputStream(this.stream);
					byte[] chunk = new byte[8192];
					int bytesRead = -1;
					while ((bytesRead = dis.read(chunk)) != -1) {
						os.write(chunk, 0, bytesRead);
					}
					os.flush();
					
					dis.close();
					pw.append(LINE_END);
					pw.flush();
				}
			} catch (Throwable th) {
				Log.e("OUT", th.getMessage(), th);
			}
		}
	}
	
	
	/**
	 * Convenience implementation of <code>HttpData</code> to send files in a multipart form requests.
	 * Stream is opened via the provided local path. Content disposition type is <code>attachment;</code>
	 * @author Narek Karapetyan
	 *
	 */
	public static class FileData implements HttpData{
		public String name;
		public String path;
		
		public FileData(String name, String path) {
			this.name = name;
			this.path = path;
		}
		
		@Override
		public void writeParameter(PrintWriter pw, OutputStream os, String charset) {
			try {
				if (pw != null) {
					File file = new File(this.path);
					pw.append(REQUEST_DIVIDER);
					pw.append(HttpTransport.RESPONSE_HEADER.CONTENT_DISPOSITION.name + ": form-data; name=\"" + this.name + "\"; filename=\"" + file.getName() + "\"");
					pw.append(LINE_END);
					pw.append(HttpTransport.REQUEST_HEADER.CONTENT_TYPE.name + ": " + URLConnection.guessContentTypeFromName(file.getName()));
					pw.append(LINE_END);
					pw.append("Content-Transfer-Encoding: binary");
					pw.append(LINE_END);
					pw.flush();
					
					DataInputStream dis = new DataInputStream(new FileInputStream(file));
					byte[] chunk = new byte[8192];
					int bytesRead = -1;
					while ((bytesRead = dis.read(chunk)) != -1) {
						os.write(chunk, 0, bytesRead);
					}
					os.flush();
					
					dis.close();
					pw.append(LINE_END);
					pw.flush();
				}
			} catch (Throwable th) {
				Log.e("OUT", th.getMessage(), th);
			}
		}
	}
	
	public static class HttpTransport{
		private static final String HEADER_LIST_DELIMITER = "\n{([({.:DELIMITER:.})])}\n";
		
		public static enum METHOD{
			GET("GET"),
			POST("POST"),
			PUT("PUT"),
			DELETE("DELETE"),
			PATCH("PATCH"),
			HEAD("HEAD"),
			OPTIONS("OPTIONS");
			
			public final String name;
			METHOD(String name){
				this.name = name;
			}
		}
		
		public static enum REQUEST_HEADER{
			ACCEPT("Accept"),
			ACCEPT_CHARSET("Accept-Charset"),
			ACCEPT_ENCODING("Accept-Encoding"),
			ACCEPT_LANGUAGE("Accept-Language"),
			ACCEPT_DATE_TIME("Accept-Datetime"),
			AUTHORIZATION("Authorization"),
			CACHE_CONTROL("Cache-Control"),
			CONNECTION("Connection"),
			COOKIE("Cookie"),
			CONTENT_LENGTH("Content-Length"),
			CONTENT_MD5("Content-MD5"),
			CONTENT_TYPE("Content-Type"),
			DATE("Date"),
			EXPECT("Expect"),
			FROM("From"),
			HOST("Host"),
			IF_MATCH("If-Match"),
			IF_MODIFIED_SINCE("If-Modified-Since"),
			IF_NONE_MATCH("If-None-Match"),
			IF_RANGE("If-Range"),
			IF_UNMODIFIED_SINCE("If-Unmodified-Since"),
			MAX_FORWARDS("Max-Forwards"),
			ORIGIN("Origin"),
			PRAGMA("Pragma"),
			PROXY_AUTHORIZATION("Proxy-Authorization"),
			RANGE("Range"),
			REFERER("Referer"),
			TE("TE"),
			UPGRADE("Upgrade"),
			USER_AGENT("User-Agent"),
			VIA("Via"),
			WARNING("Warning"),
			X_REQUESTED_WITH("X-Requested-With"),
			DNT("DNT"),
			X_FORWARDED_FOR("X-Forwarded-For"),
			X_FORWARDED_PROTO("X-Forwarded-Proto"),
			FRONT_END_HTTPS("Front-End-Https"),
			X_ATT_DEVICEID("X-ATT-DeviceId"),
			X_WAP_PROFILE("X-Wap-Profile"),
			PROXY_CONNECTION("Proxy-Connection");
			
			private static final Map<String, REQUEST_HEADER> nameMapping = new HashMap<String, REQUEST_HEADER>();
			static{
				for(REQUEST_HEADER header : REQUEST_HEADER.values()){
					nameMapping.put(header.name, header);
				}
			}
			
			public final String name;
			
			REQUEST_HEADER(String name){
				this.name = name;
			}
			
			public REQUEST_HEADER getByName(String name){
				return nameMapping.get(name);
			}
		}
		
		public static enum RESPONSE_HEADER{
			ACCESS_CONTROL_ALLOW_ORIGIN("Access-Control-Allow-Origin"),
			ACCEPT_RANGES("Accept-Ranges"),
			AGE("Age"),
			ALLOW("Allow"),
			CACHE_CONTROL("Cache-Control"),
			CONNECTION("Connection"),
			CONTENT_ENCODING("Content-Encoding"),
			CONTENT_LANGUAGE("Content-Language"),
			CONTENT_LENGTH("Content-Length"),
			CONTENT_LOCATION("Content-Location"),
			CONTENT_MD5("Content-MD5"),
			CONTENT_DISPOSITION("Content-Disposition"),
			CONTENT_RANGE("Content-Range"),
			CONTENT_TYPE("Content-Type"),
			DATE("Date"),
			ETAG("ETag"),
			EXPIRES("Expires"),
			LAST_MODIFIED("Last-Modified"),
			LINK("Link"),
			LOCATION("Location"),
			P3P("P3P"),
			PRAGMA("Pragma"),
			PROXY_AUTHENTICATE("Proxy-Authenticate"),
			REFRESH("Refresh"),
			RETRY_AFTER("Retry-After"),
			SERVER("Server"),
			SET_COOKIE("Set-Cookie"),
			STATUS("Status"),
			STRICT_TRANSPORT_SECURITY("Strict-Transport-Security"),
			TRAILER("Trailer"),
			TRANSFER_ENCODING("Transfer-Encoding"),
			VARY("Vary"),
			VIA("Via"),
			WARNING("Warning"),
			WWW_AUTHENTICATE("WWW-Authenticate"),
			X_FRAME_OPTIONS("X-Frame-Options"),
			X_XSS_PROTECTION("X-XSS-Protection"),
			CONTENT_SECURITY_POLICY("Content-Security-Policy"),
			X_CONTENT_SECURITY_POLICY("X-Content-Security-Policy"),
			X_WEBKIT_CSP("X-WebKit-CSP"),
			X_CONTENT_TYPE_OPTIONS("X-Content-Type-Options"),
			X_POWERED_BY("X-Powered-By"),
			X_UA_COMPATIBLE("X-UA-Compatible");
			
			
			private static final Map<String, RESPONSE_HEADER> nameMapping = new HashMap<String, RESPONSE_HEADER>();
			static{
				for(RESPONSE_HEADER header : RESPONSE_HEADER.values()){
					nameMapping.put(header.name, header);
				}
			}
			
			public final String name;
			
			RESPONSE_HEADER(String name){
				this.name = name;
			}
			
			public RESPONSE_HEADER getByName(String name){
				return nameMapping.get(name);
			}
		}
		
		public static enum RESPONSE_FORMAT{
			/**
			 * The expected response contains pure JSON structure. The values of the JSON are (or are considered as) plain texts.
			 * If the values are expected to be XML-related structures use <code>JSON_HTML</code> mode instead.
			 * This mode will convert &lt; into < sign, &gt; into > sign, and & into &amp; to address any potential encoding 
			 * inconsistencies that may happen down the transfer path. 
			 */
			JSON,
			
			/**
			 * The expected response contains pure XML structure. The values of the XML are (or are considered as) plain texts.
			 * If the values are expected to be XML-related structures (XML or HTML) use <code>XML_HTML</code> mode instead.
			 * This mode will convert &lt; into < sign, &gt; into > sign, and & into &amp; to address any potential encoding 
			 * inconsistencies that may happen down the transfer path. 
			 */
			XML,
			
			/**
			 * The expected response is in HTML wrapper containing JSON structure. 
			 * The values of the JSON structure will be handled as plain texts.
			 * This mode will convert &lt; into < sign, &gt; into > sign, and & into &amp; to address any potential encoding 
			 * inconsistencies that may happen down the transfer path. 
			 */
			HTML_JSON,
			
			/**
			 * The expected response is in HTML wrapper containing XML structure.
			 * The values of the XML structure will be handled as plain texts.
			 * This mode will convert &lt; into < sign, &gt; into > sign, and & into &amp; to address any potential encoding 
			 * inconsistencies that may happen down the transfer path. 
			 */
			HTML_XML,
			
			/**
			 * The expected response is a JSON object that will contain HTML structure. 
			 * This mode WILL NOT do any character escaping. Any character escaping issues must be handled on the back-end.
			 */
			JSON_HTML,
			
			/**
			 * The expected response is an XML object that will contain HTML structure.
			 * This mode WILL NOT do any character escaping. Any character escaping issues must be handled on the back-end.
			 */
			XML_HTML,
			
			/**
			 * The response will be treated as plain text as is. No conversion will be done.
			 */
			PLAIN,
			
			/**
			 * The response is a binary stream, which shall be downloaded and saved onto device storage. 
			 * The download path will be the result of the query. 
			 */
			DOWNLOAD;
		}
		
		private String url;
		private METHOD method;
		private HttpURLConnection connection;
		private int responseCode;
		
		private Map<String, String> requestHeaders;
		private Map<String, String> responseHeaders;
		private Map<String, HttpData> requestParameters;
		private StringBuffer requestPayload;
		private Object responsePayload;
		
		private boolean containsAttachments;
		private boolean responseBinary;
		private boolean reuseConnection;
		private RESPONSE_FORMAT responseFormat;
		private String htmlResponseTagName;
		private String htmlResponseTagId;
		private List<Throwable> errors;
		private boolean followRedirects;
		
		private int connectTimeout;
		private int readTimeout;
		
		public HttpTransport(){
			this.requestHeaders = new HashMap<String, String>();
			this.responseHeaders = new HashMap<String, String>();
			this.requestParameters = new HashMap<String, HttpData>();
			this.responseFormat = RESPONSE_FORMAT.PLAIN;
			this.errors = new ArrayList<Throwable>();
			this.followRedirects = true;
		}
		
		public HttpTransport(String url, METHOD method){
			this();
			this.url = url;
			this.method = method;
		}
		
		public HttpTransport(String url, METHOD method, RESPONSE_FORMAT responseFormat){
			this();
			this.url = url;
			this.method = method;
			this.responseFormat = responseFormat;
		}
		
		public HttpTransport(String url, METHOD method, RESPONSE_FORMAT responseFormat, String htmlTagName, String htmlTagId){
			this();
			this.url = url;
			this.method = method;
			this.responseFormat = responseFormat;
			this.htmlResponseTagName = htmlTagName;
			this.htmlResponseTagId = htmlTagId;
		}
		
		public void setUrl(String url){
			this.url = url;
		}
		
		public String getUrl(){
			return this.url;
		}
		
		public METHOD getMethod() {
			return this.method != null ? this.method : METHOD.GET;
		}

		public void setMethod(METHOD method) {
			this.method = method;
		}
		
		public String getHtmlResponseTagName() {
			return htmlResponseTagName;
		}

		public void setHtmlResponseTagName(String htmlResponseTagName) {
			this.htmlResponseTagName = htmlResponseTagName;
		}

		public String getHtmlResponseTagId() {
			return htmlResponseTagId;
		}

		public void setHtmlResponseTagId(String htmlResponseTagId) {
			this.htmlResponseTagId = htmlResponseTagId;
		}
		
		public void clearRequestHeaders(){
			this.requestHeaders.clear();
		}
		
		public void clearResponseHeaders(){
			this.responseHeaders.clear();
		}
		
		public void clearRequestParameters(){
			this.requestParameters.clear();
		}
		
		public void clearRequestPayload(){
			this.requestPayload = null;
		}
		
		public void clearResponsePayload(){
			this.responsePayload = null;
		}
		
		public void clearRequest(){
			this.requestHeaders.clear();
			this.requestParameters.clear();
			this.requestPayload = null;
		}
		
		public void clearResponse(){
			this.responseHeaders.clear();
			this.responsePayload = null;
		}
		
		public void destroy(){
			this.requestHeaders.clear();
			this.responseHeaders.clear();
			this.requestParameters.clear();
			this.errors.clear();
			this.requestPayload = null;
			this.responsePayload = null;
			this.connection = null;
			this.errors = null;
		}
		
		private void parseResponse() throws SessionTimedOutException {
			try {
				if(this.responseFormat == RESPONSE_FORMAT.JSON){
					this.responsePayload = new JSONTokener(this.responsePayload.toString()).nextValue();
				} 
				else if(this.responseFormat == RESPONSE_FORMAT.XML){
					this.responsePayload = getDomFromXMLSource(this.responsePayload.toString());
				} 
				else if(this.responseFormat == RESPONSE_FORMAT.HTML_JSON){
					this.responsePayload = getJSONFromHTMLWrapper((StringBuffer)this.responsePayload, this.htmlResponseTagName, this.htmlResponseTagId);
				} 
				else if(this.responseFormat == RESPONSE_FORMAT.HTML_XML){
					this.responsePayload = getDomFromHTMLWrapper((StringBuffer)this.responsePayload, this.htmlResponseTagName, this.htmlResponseTagId);
				} 
				else if(this.responseFormat == RESPONSE_FORMAT.JSON_HTML){
					this.responsePayload = new JSONTokener(this.responsePayload.toString()).nextValue();
				} 
				else if(this.responseFormat == RESPONSE_FORMAT.XML_HTML){
					this.responsePayload = getDomFromXMLSource(this.responsePayload.toString());
				} 
				else if(this.responseFormat == RESPONSE_FORMAT.PLAIN){
					this.responsePayload = this.responsePayload.toString();
				} 
				else if(this.responseFormat == RESPONSE_FORMAT.DOWNLOAD){
					this.responsePayload = this.responsePayload.toString();
				}
			} catch (SessionTimedOutException e) {
				throw e;
			} catch (Throwable th) {
				Log.e("OUT", th.getMessage(), th);
			}
		}
		
		public void setRequestParameter(String name, HttpData value){
			if(name != null){
				if(value == null){
					this.requestParameters.remove(name);
				} else {
					this.requestParameters.put(name, value);
				}
			}
		}
		
		public HttpData getRequestParameter(String name){
			if(name != null){
				return this.requestParameters.get(name);
			}
			return null;
		}
		
		//Request header methods
		public void setRequestHeader(REQUEST_HEADER header, String value){
			if(header != null){
				if(value == null){
					this.requestHeaders.remove(header.name);
				} else {
					this.requestHeaders.put(header.name, value);
				}
			}
		}
		
		public String getRequestHeader(REQUEST_HEADER header){
			if(header != null){
				if(this.requestHeaders.containsKey(header.name)){
					return this.requestHeaders.get(header.name);
				}
				return this.requestHeaders.get(header.name.toCharArray());
			}
			return null;
		}
		
		public void setCustomRequestHeader(String header, String value){
			if(header != null){
				if(value == null){
					this.requestHeaders.remove(header);
				} else {
					this.requestHeaders.put(header, value);
				}
			}
		}
		
		public String getCustomRequestHeader(String header){
			if(header != null){
				if(this.requestHeaders.containsKey(header)){
					return this.requestHeaders.get(header);
				}
				return this.requestHeaders.get(header.toLowerCase());
			}
			return null;
		}
		
		/**
		 * Force update/download of the resource on the client side, local cache
		 * will not be used. Will be effective in android 4.0+
		 */
		 public final void setNoCacheOnClient() {
			 this.requestHeaders.put(HttpTransport.REQUEST_HEADER.CACHE_CONTROL.name, "no-cache");
		 }
		

		/**
		 * Force update/download of the resource on the server side, server will do
		 * the validation. Will be effective in android 4.0+
		 */
		public final void setNoCacheOnServer() {
			this.requestHeaders.put(HttpTransport.REQUEST_HEADER.CACHE_CONTROL.name, "max-age=0");
		}

		/**
		 * Use this directive if stale response is better than no response. Will be
		 * effective in android 4.0+
		 * @param stalePeriod - the stale period in milliseconds
		 */
		public final void setTolerateStale(int stalePeriod) {
			if (stalePeriod > 0) {
				this.requestHeaders.put(HttpTransport.REQUEST_HEADER.CACHE_CONTROL.name, "max-stale=" + stalePeriod);
			}
		}
		
		public void setRequestHeaderAccept(String value){
			this.requestHeaders.put(REQUEST_HEADER.ACCEPT.name, value);
		}
		
		public String getRequestHeaderAccept(){
			if(this.requestHeaders.containsKey(REQUEST_HEADER.ACCEPT.name)){
				return this.requestHeaders.get(REQUEST_HEADER.ACCEPT.name);
			}
			return this.requestHeaders.get(REQUEST_HEADER.ACCEPT.name.toLowerCase());
		}
		
		public void setRequestHeaderAcceptCharset(String value){
			this.requestHeaders.put(REQUEST_HEADER.ACCEPT_CHARSET.name, value);
		}
		
		public String getRequestHeaderAcceptCharset(){
			if(this.requestHeaders.containsKey(REQUEST_HEADER.ACCEPT_CHARSET.name)){
				return this.requestHeaders.get(REQUEST_HEADER.ACCEPT_CHARSET.name);
			}
			return this.requestHeaders.get(REQUEST_HEADER.ACCEPT_CHARSET.name.toLowerCase());
		}
		
		public void setRequestHeaderAcceptEncoding(String value){
			this.requestHeaders.put(REQUEST_HEADER.ACCEPT_ENCODING.name, value);
		}
		
		public String getRequestHeaderAcceptEncoding(){
			if(this.requestHeaders.containsKey(REQUEST_HEADER.ACCEPT_ENCODING.name)){
				return this.requestHeaders.get(REQUEST_HEADER.ACCEPT_ENCODING.name);
			}
			return this.requestHeaders.get(REQUEST_HEADER.ACCEPT_ENCODING.name.toLowerCase());
		}
		
		public void setRequestHeaderAcceptLanguage(String value){
			this.requestHeaders.put(REQUEST_HEADER.ACCEPT_LANGUAGE.name, value);
		}
		
		public String getRequestHeaderAcceptLanguage(){
			if(this.requestHeaders.containsKey(REQUEST_HEADER.ACCEPT_LANGUAGE.name)){
				return this.requestHeaders.get(REQUEST_HEADER.ACCEPT_LANGUAGE.name);
			}
			return this.requestHeaders.get(REQUEST_HEADER.ACCEPT_LANGUAGE.name.toLowerCase());
		}
		
		public void setRequestHeaderAcceptDateTime(String value){
			this.requestHeaders.put(REQUEST_HEADER.ACCEPT_DATE_TIME.name, value);
		}
		
		public String getRequestHeaderAcceptDateTime(){
			if(this.requestHeaders.containsKey(REQUEST_HEADER.ACCEPT_DATE_TIME.name)){
				return this.requestHeaders.get(REQUEST_HEADER.ACCEPT_DATE_TIME.name);
			}
			return this.requestHeaders.get(REQUEST_HEADER.ACCEPT_DATE_TIME.name.toLowerCase());
		}
		
		public void setRequestHeaderAuthorization(String value){
			this.requestHeaders.put(REQUEST_HEADER.AUTHORIZATION.name, value);
		}
		
		public String getRequestHeaderAuthorization(){
			if(this.requestHeaders.containsKey(REQUEST_HEADER.AUTHORIZATION.name)){
				return this.requestHeaders.get(REQUEST_HEADER.AUTHORIZATION.name);
			}
			return this.requestHeaders.get(REQUEST_HEADER.AUTHORIZATION.name.toLowerCase());
		}
		
		public void setRequestHeaderCacheControl(String value){
			this.requestHeaders.put(REQUEST_HEADER.CACHE_CONTROL.name, value);
		}
		
		public String getRequestHeaderCacheControl(){
			if(this.requestHeaders.containsKey(REQUEST_HEADER.CACHE_CONTROL.name)){
				return this.requestHeaders.get(REQUEST_HEADER.CACHE_CONTROL.name);
			}
			return this.requestHeaders.get(REQUEST_HEADER.CACHE_CONTROL.name.toLowerCase());
		}
		
		public void setRequestHeaderConnection(String value){
			this.requestHeaders.put(REQUEST_HEADER.CONNECTION.name, value);
		}
		
		public String getRequestHeaderConnection(){
			if(this.requestHeaders.containsKey(REQUEST_HEADER.CONNECTION.name)){
				return this.requestHeaders.get(REQUEST_HEADER.CONNECTION.name);
			}
			return this.requestHeaders.get(REQUEST_HEADER.CONNECTION.name.toLowerCase());
		}
		
		public void setRequestHeaderCookie(String value){
			this.requestHeaders.put(REQUEST_HEADER.COOKIE.name, value);
		}
		
		public String getRequestHeaderCookie(){
			if(this.requestHeaders.containsKey(REQUEST_HEADER.COOKIE.name)){
				return this.requestHeaders.get(REQUEST_HEADER.COOKIE.name);
			}
			return this.requestHeaders.get(REQUEST_HEADER.COOKIE.name.toLowerCase());
		}
		
		public void setRequestHeaderContentLength(String value){
			this.requestHeaders.put(REQUEST_HEADER.CONTENT_LENGTH.name, value);
		}
		
		public String getRequestHeaderContentLength(){
			if(this.requestHeaders.containsKey(REQUEST_HEADER.CONTENT_LENGTH.name)){
				return this.requestHeaders.get(REQUEST_HEADER.CONTENT_LENGTH.name);
			}
			return this.requestHeaders.get(REQUEST_HEADER.CONTENT_LENGTH.name.toLowerCase());
		}
		
		public void setRequestHeaderContentMD5(String value){
			this.requestHeaders.put(REQUEST_HEADER.CONTENT_MD5.name, value);
		}
		
		public String getRequestHeaderContentMD5(){
			if(this.requestHeaders.containsKey(REQUEST_HEADER.CONTENT_MD5.name)){
				return this.requestHeaders.get(REQUEST_HEADER.CONTENT_MD5.name);
			}
			return this.requestHeaders.get(REQUEST_HEADER.CONTENT_MD5.name.toLowerCase());
		}
		
		public void setRequestHeaderContentType(String value){
			this.requestHeaders.put(REQUEST_HEADER.CONTENT_TYPE.name, value);
		}
		
		public String getRequestHeaderContentType(){
			if(this.requestHeaders.containsKey(REQUEST_HEADER.CONTENT_TYPE.name)){
				return this.requestHeaders.get(REQUEST_HEADER.CONTENT_TYPE.name);
			}
			return this.requestHeaders.get(REQUEST_HEADER.CONTENT_TYPE.name.toLowerCase());
		}
		
		public void setRequestHeaderDate(String value){
			this.requestHeaders.put(REQUEST_HEADER.DATE.name, value);
		}
		
		public String getRequestHeaderDate(){
			if(this.requestHeaders.containsKey(REQUEST_HEADER.DATE.name)){
				return this.requestHeaders.get(REQUEST_HEADER.DATE.name);
			}
			return this.requestHeaders.get(REQUEST_HEADER.DATE.name.toLowerCase());
		}
		
		public void setRequestHeaderExpect(String value){
			this.requestHeaders.put(REQUEST_HEADER.EXPECT.name, value);
		}
		
		public String getRequestHeaderExpect(){
			if(this.requestHeaders.containsKey(REQUEST_HEADER.EXPECT.name)){
				return this.requestHeaders.get(REQUEST_HEADER.EXPECT.name);
			}
			return this.requestHeaders.get(REQUEST_HEADER.EXPECT.name.toLowerCase());
		}
		
		public void setRequestHeaderFrom(String value){
			this.requestHeaders.put(REQUEST_HEADER.FROM.name, value);
		}
		
		public String getRequestHeaderFrom(){
			if(this.requestHeaders.containsKey(REQUEST_HEADER.FROM.name)){
				return this.requestHeaders.get(REQUEST_HEADER.FROM.name);
			}
			return this.requestHeaders.get(REQUEST_HEADER.FROM.name.toLowerCase());
		}
		
		public void setRequestHeaderHost(String value){
			this.requestHeaders.put(REQUEST_HEADER.HOST.name, value);
		}
		
		public String getRequestHeaderHost(){
			if(this.requestHeaders.containsKey(REQUEST_HEADER.HOST.name)){
				return this.requestHeaders.get(REQUEST_HEADER.HOST.name);
			}
			return this.requestHeaders.get(REQUEST_HEADER.HOST.name.toLowerCase());
		}
		
		public void setRequestHeaderIfMatch(String value){
			this.requestHeaders.put(REQUEST_HEADER.IF_MATCH.name, value);
		}
		
		public String getRequestHeaderIfMatch(){
			if(this.requestHeaders.containsKey(REQUEST_HEADER.IF_MATCH.name)){
				return this.requestHeaders.get(REQUEST_HEADER.IF_MATCH.name);
			}
			return this.requestHeaders.get(REQUEST_HEADER.IF_MATCH.name.toLowerCase());
		}
		
		public void setRequestHeaderIfModifiedSince(String value){
			this.requestHeaders.put(REQUEST_HEADER.IF_MODIFIED_SINCE.name, value);
		}
		
		public String getRequestHeaderIfModifiedSince(){
			if(this.requestHeaders.containsKey(REQUEST_HEADER.IF_MODIFIED_SINCE.name)){
				return this.requestHeaders.get(REQUEST_HEADER.IF_MODIFIED_SINCE.name);
			}
			return this.requestHeaders.get(REQUEST_HEADER.IF_MODIFIED_SINCE.name.toLowerCase());
		}
		
		public void setRequestHeaderIfNoneMatch(String value){
			this.requestHeaders.put(REQUEST_HEADER.IF_NONE_MATCH.name, value);
		}
		
		public String getRequestHeaderIfNoneMatch(){
			if(this.requestHeaders.containsKey(REQUEST_HEADER.IF_NONE_MATCH.name)){
				return this.requestHeaders.get(REQUEST_HEADER.IF_NONE_MATCH.name);
			}
			return this.requestHeaders.get(REQUEST_HEADER.IF_NONE_MATCH.name.toLowerCase());
		}
		
		public void setRequestHeaderIfRange(String value){
			this.requestHeaders.put(REQUEST_HEADER.IF_RANGE.name, value);
		}
		
		public String getRequestHeaderIfRange(){
			if(this.requestHeaders.containsKey(REQUEST_HEADER.IF_RANGE.name)){
				return this.requestHeaders.get(REQUEST_HEADER.IF_RANGE.name);
			}
			return this.requestHeaders.get(REQUEST_HEADER.IF_RANGE.name.toLowerCase());
		}
		
		public void setRequestHeaderIfUnmodifiedSince(String value){
			this.requestHeaders.put(REQUEST_HEADER.IF_UNMODIFIED_SINCE.name, value);
		}
		
		public String getRequestHeaderIfUnmodifiedSince(){
			if(this.requestHeaders.containsKey(REQUEST_HEADER.IF_UNMODIFIED_SINCE.name)){
				return this.requestHeaders.get(REQUEST_HEADER.IF_UNMODIFIED_SINCE.name);
			}
			return this.requestHeaders.get(REQUEST_HEADER.IF_UNMODIFIED_SINCE.name.toLowerCase());
		}
		
		public void setRequestHeaderMaxForwards(String value){
			this.requestHeaders.put(REQUEST_HEADER.MAX_FORWARDS.name, value);
		}
		
		public String getRequestHeaderMaxForwards(){
			if(this.requestHeaders.containsKey(REQUEST_HEADER.MAX_FORWARDS.name)){
				return this.requestHeaders.get(REQUEST_HEADER.MAX_FORWARDS.name);
			}
			return this.requestHeaders.get(REQUEST_HEADER.MAX_FORWARDS.name.toLowerCase());
		}
		
		public void setRequestHeaderOrigin(String value){
			this.requestHeaders.put(REQUEST_HEADER.ORIGIN.name, value);
		}
		
		public String getRequestHeaderOrigin(){
			if(this.requestHeaders.containsKey(REQUEST_HEADER.ORIGIN.name)){
				return this.requestHeaders.get(REQUEST_HEADER.ORIGIN.name);
			}
			return this.requestHeaders.get(REQUEST_HEADER.ORIGIN.name.toLowerCase());
		}
		
		public void setRequestHeaderPragma(String value){
			this.requestHeaders.put(REQUEST_HEADER.PRAGMA.name, value);
		}
		
		public String getRequestHeaderPragma(){
			if(this.requestHeaders.containsKey(REQUEST_HEADER.PRAGMA.name)){
				return this.requestHeaders.get(REQUEST_HEADER.PRAGMA.name);
			}
			return this.requestHeaders.get(REQUEST_HEADER.PRAGMA.name.toLowerCase());
		}
		
		public void setRequestHeaderProxyAuthorization(String value){
			this.requestHeaders.put(REQUEST_HEADER.PROXY_AUTHORIZATION.name, value);
		}
		
		public String getRequestHeaderProxyAuthorization(){
			if(this.requestHeaders.containsKey(REQUEST_HEADER.PROXY_AUTHORIZATION.name)){
				return this.requestHeaders.get(REQUEST_HEADER.PROXY_AUTHORIZATION.name);
			}
			return this.requestHeaders.get(REQUEST_HEADER.PROXY_AUTHORIZATION.name.toLowerCase());
		}
		
		public void setRequestHeaderRange(String value){
			this.requestHeaders.put(REQUEST_HEADER.RANGE.name, value);
		}
		
		public String getRequestHeaderRange(){
			if(this.requestHeaders.containsKey(REQUEST_HEADER.RANGE.name)){
				return this.requestHeaders.get(REQUEST_HEADER.RANGE.name);
			}
			return this.requestHeaders.get(REQUEST_HEADER.RANGE.name.toLowerCase());
		}
		
		public void setRequestHeaderReferer(String value){
			this.requestHeaders.put(REQUEST_HEADER.REFERER.name, value);
		}
		
		public String getRequestHeaderReferer(){
			if(this.requestHeaders.containsKey(REQUEST_HEADER.REFERER.name)){
				return this.requestHeaders.get(REQUEST_HEADER.REFERER.name);
			}
			return this.requestHeaders.get(REQUEST_HEADER.REFERER.name.toLowerCase());
		}
		
		public void setRequestHeaderTE(String value){
			this.requestHeaders.put(REQUEST_HEADER.TE.name, value);
		}
		
		public String getRequestHeaderTE(){
			if(this.requestHeaders.containsKey(REQUEST_HEADER.TE.name)){
				return this.requestHeaders.get(REQUEST_HEADER.TE.name);
			}
			return this.requestHeaders.get(REQUEST_HEADER.TE.name.toLowerCase());
		}
		
		public void setRequestHeaderUpgrade(String value){
			this.requestHeaders.put(REQUEST_HEADER.UPGRADE.name, value);
		}
		
		public String getRequestHeaderUpgrade(){
			if(this.requestHeaders.containsKey(REQUEST_HEADER.UPGRADE.name)){
				return this.requestHeaders.get(REQUEST_HEADER.UPGRADE.name);
			}
			return this.requestHeaders.get(REQUEST_HEADER.UPGRADE.name.toLowerCase());
		}
		
		public void setRequestHeaderUserAgent(String value){
			this.requestHeaders.put(REQUEST_HEADER.USER_AGENT.name, value);
		}
		
		public String getRequestHeaderUserAgent(){
			if(this.requestHeaders.containsKey(REQUEST_HEADER.USER_AGENT.name)){
				return this.requestHeaders.get(REQUEST_HEADER.USER_AGENT.name);
			}
			return this.requestHeaders.get(REQUEST_HEADER.USER_AGENT.name.toLowerCase());
		}
		
		public void setRequestHeaderVia(String value){
			this.requestHeaders.put(REQUEST_HEADER.VIA.name, value);
		}
		
		public String getRequestHeaderVia(){
			if(this.requestHeaders.containsKey(REQUEST_HEADER.VIA.name)){
				return this.requestHeaders.get(REQUEST_HEADER.VIA.name);
			}
			return this.requestHeaders.get(REQUEST_HEADER.VIA.name.toLowerCase());
		}
		
		public void setRequestHeaderWarning(String value){
			this.requestHeaders.put(REQUEST_HEADER.WARNING.name, value);
		}
		
		public String getRequestHeaderWarning(){
			if(this.requestHeaders.containsKey(REQUEST_HEADER.WARNING.name)){
				return this.requestHeaders.get(REQUEST_HEADER.WARNING.name);
			}
			return this.requestHeaders.get(REQUEST_HEADER.WARNING.name.toLowerCase());
		}
		
		public void setRequestHeaderXRequestedWith(String value){
			this.requestHeaders.put(REQUEST_HEADER.X_REQUESTED_WITH.name, value);
		}
		
		public String getRequestHeaderXRequestedWith(){
			if(this.requestHeaders.containsKey(REQUEST_HEADER.X_REQUESTED_WITH.name)){
				return this.requestHeaders.get(REQUEST_HEADER.X_REQUESTED_WITH.name);
			}
			return this.requestHeaders.get(REQUEST_HEADER.X_REQUESTED_WITH.name.toLowerCase());
		}
		
		public void setRequestHeaderDNT(String value){
			this.requestHeaders.put(REQUEST_HEADER.DNT.name, value);
		}
		
		public String getRequestHeaderDNT(){
			if(this.requestHeaders.containsKey(REQUEST_HEADER.DNT.name)){
				return this.requestHeaders.get(REQUEST_HEADER.DNT.name);
			}
			return this.requestHeaders.get(REQUEST_HEADER.DNT.name.toLowerCase());
		}
		
		public void setRequestHeaderXForwardedFor(String value){
			this.requestHeaders.put(REQUEST_HEADER.X_FORWARDED_FOR.name, value);
		}
		
		public String getRequestHeaderXForwardedFor(){
			if(this.requestHeaders.containsKey(REQUEST_HEADER.X_FORWARDED_FOR.name)){
				return this.requestHeaders.get(REQUEST_HEADER.X_FORWARDED_FOR.name);
			}
			return this.requestHeaders.get(REQUEST_HEADER.X_FORWARDED_FOR.name.toLowerCase());
		}
		
		public void setRequestHeaderXForwardedProto(String value){
			this.requestHeaders.put(REQUEST_HEADER.X_FORWARDED_PROTO.name, value);
		}
		
		public String getRequestHeaderXForwardedProto(){
			if(this.requestHeaders.containsKey(REQUEST_HEADER.X_FORWARDED_PROTO.name)){
				return this.requestHeaders.get(REQUEST_HEADER.X_FORWARDED_PROTO.name);
			}
			return this.requestHeaders.get(REQUEST_HEADER.X_FORWARDED_PROTO.name.toLowerCase());
		}
		
		public void setRequestHeaderFrontEndHttps(String value){
			this.requestHeaders.put(REQUEST_HEADER.FRONT_END_HTTPS.name, value);
		}
		
		public String getRequestHeaderFrontEndHttps(){
			if(this.requestHeaders.containsKey(REQUEST_HEADER.FRONT_END_HTTPS.name)){
				return this.requestHeaders.get(REQUEST_HEADER.FRONT_END_HTTPS.name);
			}
			return this.requestHeaders.get(REQUEST_HEADER.FRONT_END_HTTPS.name.toLowerCase());
		}
		
		public void setRequestHeaderXAttDeviceId(String value){
			this.requestHeaders.put(REQUEST_HEADER.X_ATT_DEVICEID.name, value);
		}
		
		public String getRequestHeaderXAttDeviceId(){
			if(this.requestHeaders.containsKey(REQUEST_HEADER.X_ATT_DEVICEID.name)){
				return this.requestHeaders.get(REQUEST_HEADER.X_ATT_DEVICEID.name);
			}
			return this.requestHeaders.get(REQUEST_HEADER.X_ATT_DEVICEID.name.toLowerCase());
		}
		
		public void setRequestHeaderXWapProfile(String value){
			this.requestHeaders.put(REQUEST_HEADER.X_WAP_PROFILE.name, value);
		}
		
		public String getRequestHeaderXWapProfile(){
			if(this.requestHeaders.containsKey(REQUEST_HEADER.X_WAP_PROFILE.name)){
				return this.requestHeaders.get(REQUEST_HEADER.X_WAP_PROFILE.name);
			}
			return this.requestHeaders.get(REQUEST_HEADER.X_WAP_PROFILE.name.toLowerCase());
		}
		
		public void setRequestHeaderProxyConnection(String value){
			this.requestHeaders.put(REQUEST_HEADER.PROXY_CONNECTION.name, value);
		}
		
		public String getRequestHeaderProxyConnection(){
			if(this.requestHeaders.containsKey(REQUEST_HEADER.PROXY_CONNECTION.name)){
				return this.requestHeaders.get(REQUEST_HEADER.PROXY_CONNECTION.name);
			}
			return this.requestHeaders.get(REQUEST_HEADER.PROXY_CONNECTION.name.toLowerCase());
		}
		
		// Response header methods
		public void setResponseHeader(RESPONSE_HEADER header, String value){
			if(header != null){
				this.responseHeaders.put(header.name, value);
			}
		}
		
		public String getResponseHeader(RESPONSE_HEADER header){
			if(header != null){
				if(this.responseHeaders.containsKey(header.name)){
					return this.responseHeaders.get(header.name);
				}
				return this.responseHeaders.get(header.name.toLowerCase());
			}
			return null;
		}
		
		public void setCustomResponseHeader(String header, String value){
			if(header != null){
				if(value == null){
					this.responseHeaders.remove(header);
				} else {
					this.responseHeaders.put(header, value);
				}
			}
		}
		
		public void setCustomResponseHeader(String header, List<String> values){
			if(header != null && values != null){
				StringBuffer sb = new StringBuffer();
				for(String value : values){
					sb.append(value).append(HEADER_LIST_DELIMITER);
				}
				int length = sb.length();
				if(length > 0){
					sb.delete(length - HEADER_LIST_DELIMITER.length(), length);
				}
				this.responseHeaders.put(header, sb.toString());
			}
		}
		
		public String getCustomResponseHeader(String header){
			if(header != null){
				if(this.responseHeaders.containsKey(header)){
					return this.responseHeaders.get(header);
				}
				return this.responseHeaders.get(header.toLowerCase());
			}
			return null;
		}
		
		public List<String> getCustomResponseHeader(String header, List<String> value){
			if(header != null && value != null){
				String stringValue = this.responseHeaders.get(header);
				if(stringValue ==  null){
					stringValue = this.responseHeaders.get(header.toLowerCase());
				}
				if(stringValue != null){
					String[] values = stringValue.split(HEADER_LIST_DELIMITER);
					for(String val : values){
						value.add(val);
					}
				}
			}
			return value;
		}
		
		public void setResponseHeaderAccessControlAllowOrigin(String value){
			this.responseHeaders.put(RESPONSE_HEADER.ACCESS_CONTROL_ALLOW_ORIGIN.name, value);
		}
		
		public String getResponseHeaderAccessControlAllowOrigin(){
			if(this.responseHeaders.containsKey(RESPONSE_HEADER.ACCESS_CONTROL_ALLOW_ORIGIN.name)){
				return this.responseHeaders.get(RESPONSE_HEADER.ACCESS_CONTROL_ALLOW_ORIGIN.name);
			}
			return this.responseHeaders.get(RESPONSE_HEADER.ACCESS_CONTROL_ALLOW_ORIGIN.name.toLowerCase());
		}
		
		public void setResponseHeaderAcceptRanges(String value){
			this.responseHeaders.put(RESPONSE_HEADER.ACCEPT_RANGES.name, value);
		}
		
		public String getResponseHeaderAcceptRanges(){
			if(this.responseHeaders.containsKey(RESPONSE_HEADER.ACCEPT_RANGES.name)){
				return this.responseHeaders.get(RESPONSE_HEADER.ACCEPT_RANGES.name);
			}
			return this.responseHeaders.get(RESPONSE_HEADER.ACCEPT_RANGES.name.toLowerCase());
		}
		
		public void setResponseHeaderAge(String value){
			this.responseHeaders.put(RESPONSE_HEADER.AGE.name, value);
		}
		
		public String getResponseHeaderAge(){
			if(this.responseHeaders.containsKey(RESPONSE_HEADER.AGE.name)){
				return this.responseHeaders.get(RESPONSE_HEADER.AGE.name);
			}
			return this.responseHeaders.get(RESPONSE_HEADER.AGE.name.toLowerCase());
		}
		
		public void setResponseHeaderAllow(String value){
			this.responseHeaders.put(RESPONSE_HEADER.ALLOW.name, value);
		}
		
		public String getResponseHeaderAllow(){
			if(this.responseHeaders.containsKey(RESPONSE_HEADER.ALLOW.name)){
				return this.responseHeaders.get(RESPONSE_HEADER.ALLOW.name);
			}
			return this.responseHeaders.get(RESPONSE_HEADER.ALLOW.name.toLowerCase());
		}
		
		public void setResponseHeaderCacheControl(String value){
			this.responseHeaders.put(RESPONSE_HEADER.CACHE_CONTROL.name, value);
		}
		
		public String getResponseHeaderCacheControl(){
			if(this.responseHeaders.containsKey(RESPONSE_HEADER.CACHE_CONTROL.name)){
				return this.responseHeaders.get(RESPONSE_HEADER.CACHE_CONTROL.name);
			}
			return this.responseHeaders.get(RESPONSE_HEADER.CACHE_CONTROL.name.toLowerCase());
		}
		
		public void setResponseHeaderConnection(String value){
			this.responseHeaders.put(RESPONSE_HEADER.CONNECTION.name, value);
		}
		
		public String getResponseHeaderConnection(){
			if(this.responseHeaders.containsKey(RESPONSE_HEADER.CONNECTION.name)){
				return this.responseHeaders.get(RESPONSE_HEADER.CONNECTION.name);
			}
			return this.responseHeaders.get(RESPONSE_HEADER.CONNECTION.name.toLowerCase());
		}
		
		public void setResponseHeaderContentEncoding(String value){
			this.responseHeaders.put(RESPONSE_HEADER.CONTENT_ENCODING.name, value);
		}
		
		public String getResponseHeaderContentEncoding(){
			if(this.responseHeaders.containsKey(RESPONSE_HEADER.CONTENT_ENCODING.name)){
				return this.responseHeaders.get(RESPONSE_HEADER.CONTENT_ENCODING.name);
			}
			return this.responseHeaders.get(RESPONSE_HEADER.CONTENT_ENCODING.name.toLowerCase());
		}
		
		public void setResponseHeaderContentLanguage(String value){
			this.responseHeaders.put(RESPONSE_HEADER.CONTENT_LANGUAGE.name, value);
		}
		
		public String getResponseHeaderContentLanguage(){
			if(this.responseHeaders.containsKey(RESPONSE_HEADER.CONTENT_LANGUAGE.name)){
				return this.responseHeaders.get(RESPONSE_HEADER.CONTENT_LANGUAGE.name);
			}
			return this.responseHeaders.get(RESPONSE_HEADER.CONTENT_LANGUAGE.name.toLowerCase());
		}
		
		public void setResponseHeaderContentLength(String value){
			this.responseHeaders.put(RESPONSE_HEADER.CONTENT_LENGTH.name, value);
		}
		
		public String getResponseHeaderContentLength(){
			if(this.responseHeaders.containsKey(RESPONSE_HEADER.CONTENT_LENGTH.name)){
				return this.responseHeaders.get(RESPONSE_HEADER.CONTENT_LENGTH.name);
			}
			return this.responseHeaders.get(RESPONSE_HEADER.CONTENT_LENGTH.name.toLowerCase());
		}
		
		public void setResponseHeaderContentLocation(String value){
			this.responseHeaders.put(RESPONSE_HEADER.CONTENT_LOCATION.name, value);
		}
		
		public String getResponseHeaderContentLocation(){
			if(this.responseHeaders.containsKey(RESPONSE_HEADER.CONTENT_LOCATION.name)){
				return this.responseHeaders.get(RESPONSE_HEADER.CONTENT_LOCATION.name);
			}
			return this.responseHeaders.get(RESPONSE_HEADER.CONTENT_LOCATION.name.toLowerCase());
		}
		
		public void setResponseHeaderContentMD5(String value){
			this.responseHeaders.put(RESPONSE_HEADER.CONTENT_MD5.name, value);
		}
		
		public String getResponseHeaderContentMD5(){
			if(this.responseHeaders.containsKey(RESPONSE_HEADER.CONTENT_MD5.name)){
				return this.responseHeaders.get(RESPONSE_HEADER.CONTENT_MD5.name);
			}
			return this.responseHeaders.get(RESPONSE_HEADER.CONTENT_MD5.name.toLowerCase());
		}
		
		public void setResponseHeaderContentDisposition(String value){
			this.responseHeaders.put(RESPONSE_HEADER.CONTENT_DISPOSITION.name, value);
		}
		
		public String getResponseHeaderContentDisposition(){
			if(this.responseHeaders.containsKey(RESPONSE_HEADER.CONTENT_DISPOSITION.name)){
				return this.responseHeaders.get(RESPONSE_HEADER.CONTENT_DISPOSITION.name);
			}
			return this.responseHeaders.get(RESPONSE_HEADER.CONTENT_DISPOSITION.name.toLowerCase());
		}
		
		public void setResponseHeaderContentRange(String value){
			this.responseHeaders.put(RESPONSE_HEADER.CONTENT_RANGE.name, value);
		}
		
		public String getResponseHeaderContentRange(){
			if(this.responseHeaders.containsKey(RESPONSE_HEADER.CONTENT_RANGE.name)){
				return this.responseHeaders.get(RESPONSE_HEADER.CONTENT_RANGE.name);
			}
			return this.responseHeaders.get(RESPONSE_HEADER.CONTENT_RANGE.name.toLowerCase());
		}
		
		public void setResponseHeaderContentType(String value){
			this.responseHeaders.put(RESPONSE_HEADER.CONTENT_TYPE.name, value);
		}
		
		public String getResponseHeaderContentType(){
			if(this.responseHeaders.containsKey(RESPONSE_HEADER.CONTENT_TYPE.name)){
				return this.responseHeaders.get(RESPONSE_HEADER.CONTENT_TYPE.name);
			}
			return this.responseHeaders.get(RESPONSE_HEADER.CONTENT_TYPE.name.toLowerCase());
		}
		
		public void setResponseHeaderDate(String value){
			this.responseHeaders.put(RESPONSE_HEADER.DATE.name, value);
		}
		
		public String getResponseHeaderDate(){
			if(this.responseHeaders.containsKey(RESPONSE_HEADER.DATE.name)){
				return this.responseHeaders.get(RESPONSE_HEADER.DATE.name);
			}
			return this.responseHeaders.get(RESPONSE_HEADER.DATE.name.toLowerCase());
		}
		
		public void setResponseHeaderETag(String value){
			this.responseHeaders.put(RESPONSE_HEADER.ETAG.name, value);
		}
		
		public String getResponseHeaderETag(){
			if(this.responseHeaders.containsKey(RESPONSE_HEADER.ETAG.name)){
				return this.responseHeaders.get(RESPONSE_HEADER.ETAG.name);
			}
			return this.responseHeaders.get(RESPONSE_HEADER.ETAG.name.toLowerCase());
		}
		
		public void setResponseHeaderExpires(String value){
			this.responseHeaders.put(RESPONSE_HEADER.EXPIRES.name, value);
		}
		
		public String getResponseHeaderExpires(){
			if(this.responseHeaders.containsKey(RESPONSE_HEADER.EXPIRES.name)){
				return this.responseHeaders.get(RESPONSE_HEADER.EXPIRES.name);
			}
			return this.responseHeaders.get(RESPONSE_HEADER.EXPIRES.name.toLowerCase());
		}
		
		public void setResponseHeaderLastModified(String value){
			this.responseHeaders.put(RESPONSE_HEADER.LAST_MODIFIED.name, value);
		}
		
		public String getResponseHeaderLastModified(){
			if(this.responseHeaders.containsKey(RESPONSE_HEADER.LAST_MODIFIED.name)){
				return this.responseHeaders.get(RESPONSE_HEADER.LAST_MODIFIED.name);
			}
			return this.responseHeaders.get(RESPONSE_HEADER.LAST_MODIFIED.name.toLowerCase());
		}
		
		public void setResponseHeaderLink(String value){
			this.responseHeaders.put(RESPONSE_HEADER.LINK.name, value);
		}
		
		public String getResponseHeaderLink(){
			if(this.responseHeaders.containsKey(RESPONSE_HEADER.LINK.name)){
				return this.responseHeaders.get(RESPONSE_HEADER.LINK.name);
			}
			return this.responseHeaders.get(RESPONSE_HEADER.LINK.name.toLowerCase());
		}
		
		public void setResponseHeaderLocation(String value){
			this.responseHeaders.put(RESPONSE_HEADER.LOCATION.name, value);
		}
		
		public String getResponseHeaderLocation(){
			if(this.responseHeaders.containsKey(RESPONSE_HEADER.LOCATION.name)){
				return this.responseHeaders.get(RESPONSE_HEADER.LOCATION.name);
			}
			return this.responseHeaders.get(RESPONSE_HEADER.LOCATION.name.toLowerCase());
		}
		
		public void setResponseHeaderP3P(String value){
			this.responseHeaders.put(RESPONSE_HEADER.P3P.name, value);
		}
		
		public String getResponseHeaderP3P(){
			if(this.responseHeaders.containsKey(RESPONSE_HEADER.P3P.name)){
				return this.responseHeaders.get(RESPONSE_HEADER.P3P.name);
			}
			return this.responseHeaders.get(RESPONSE_HEADER.P3P.name.toLowerCase());
		}
		
		public void setResponseHeaderPragma(String value){
			this.responseHeaders.put(RESPONSE_HEADER.PRAGMA.name, value);
		}
		
		public String getResponseHeaderPragma(){
			if(this.responseHeaders.containsKey(RESPONSE_HEADER.PRAGMA.name)){
				return this.responseHeaders.get(RESPONSE_HEADER.PRAGMA.name);
			}
			return this.responseHeaders.get(RESPONSE_HEADER.PRAGMA.name.toLowerCase());
		}
		
		public void setResponseHeaderProxyAuthenticate(String value){
			this.responseHeaders.put(RESPONSE_HEADER.PROXY_AUTHENTICATE.name, value);
		}
		
		public String getResponseHeaderProxyAuthenticate(){
			if(this.responseHeaders.containsKey(RESPONSE_HEADER.PROXY_AUTHENTICATE.name)){
				return this.responseHeaders.get(RESPONSE_HEADER.PROXY_AUTHENTICATE.name);
			}
			return this.responseHeaders.get(RESPONSE_HEADER.PROXY_AUTHENTICATE.name.toLowerCase());
		}
		
		public void setResponseHeaderRefresh(String value){
			this.responseHeaders.put(RESPONSE_HEADER.REFRESH.name, value);
		}
		
		public String getResponseHeaderRefresh(){
			if(this.responseHeaders.containsKey(RESPONSE_HEADER.REFRESH.name)){
				return this.responseHeaders.get(RESPONSE_HEADER.REFRESH.name);
			}
			return this.responseHeaders.get(RESPONSE_HEADER.REFRESH.name.toLowerCase());
		}
		
		public void setResponseHeaderRetryAfter(String value){
			this.responseHeaders.put(RESPONSE_HEADER.RETRY_AFTER.name, value);
		}
		
		public String getResponseHeaderRetryAfter(){
			if(this.responseHeaders.containsKey(RESPONSE_HEADER.RETRY_AFTER.name)){
				return this.responseHeaders.get(RESPONSE_HEADER.RETRY_AFTER.name);
			}
			return this.responseHeaders.get(RESPONSE_HEADER.RETRY_AFTER.name.toLowerCase());
		}
		
		public void setResponseHeaderServer(String value){
			this.responseHeaders.put(RESPONSE_HEADER.SERVER.name, value);
		}
		
		public String getResponseHeaderServer(){
			if(this.responseHeaders.containsKey(RESPONSE_HEADER.SERVER.name)){
				return this.responseHeaders.get(RESPONSE_HEADER.SERVER.name);
			}
			return this.responseHeaders.get(RESPONSE_HEADER.SERVER.name.toLowerCase());
		}
		
		public void setResponseHeaderSetCookie(String value){
			this.responseHeaders.put(RESPONSE_HEADER.SET_COOKIE.name, value);
		}
		
		public String getResponseHeaderSetCookie(){
			if(this.responseHeaders.containsKey(RESPONSE_HEADER.SET_COOKIE.name)){
				return this.responseHeaders.get(RESPONSE_HEADER.SET_COOKIE.name);
			}
			return this.responseHeaders.get(RESPONSE_HEADER.SET_COOKIE.name.toLowerCase());
		}
		
		public void setResponseHeaderStatus(String value){
			this.responseHeaders.put(RESPONSE_HEADER.STATUS.name, value);
		}
		
		public String getResponseHeaderStatus(){
			if(this.responseHeaders.containsKey(RESPONSE_HEADER.STATUS.name)){
				return this.responseHeaders.get(RESPONSE_HEADER.STATUS.name);
			}
			return this.responseHeaders.get(RESPONSE_HEADER.STATUS.name.toLowerCase());
		}
		
		public void setResponseHeaderStrictTransportSecurity(String value){
			this.responseHeaders.put(RESPONSE_HEADER.STRICT_TRANSPORT_SECURITY.name, value);
		}
		
		public String getResponseHeaderStrictTransportSecurity(){
			if(this.responseHeaders.containsKey(RESPONSE_HEADER.STRICT_TRANSPORT_SECURITY.name)){
				return this.responseHeaders.get(RESPONSE_HEADER.STRICT_TRANSPORT_SECURITY.name);
			}
			return this.responseHeaders.get(RESPONSE_HEADER.STRICT_TRANSPORT_SECURITY.name.toLowerCase());
		}
		
		public void setResponseHeaderTrailer(String value){
			this.responseHeaders.put(RESPONSE_HEADER.TRAILER.name, value);
		}
		
		public String getResponseHeaderTrailer(){
			if(this.responseHeaders.containsKey(RESPONSE_HEADER.TRAILER.name)){
				return this.responseHeaders.get(RESPONSE_HEADER.TRAILER.name);
			}
			return this.responseHeaders.get(RESPONSE_HEADER.TRAILER.name.toLowerCase());
		}
		
		public void setResponseHeaderTransferEncoding(String value){
			this.responseHeaders.put(RESPONSE_HEADER.TRANSFER_ENCODING.name, value);
		}
		
		public String getResponseHeaderTransferEncoding(){
			if(this.responseHeaders.containsKey(RESPONSE_HEADER.TRANSFER_ENCODING.name)){
				return this.responseHeaders.get(RESPONSE_HEADER.TRANSFER_ENCODING.name);
			}
			return this.responseHeaders.get(RESPONSE_HEADER.TRANSFER_ENCODING.name.toLowerCase());
		}
		
		public void setResponseHeaderVary(String value){
			this.responseHeaders.put(RESPONSE_HEADER.VARY.name, value);
		}
		
		public String getResponseHeaderVary(){
			if(this.responseHeaders.containsKey(RESPONSE_HEADER.VARY.name)){
				return this.responseHeaders.get(RESPONSE_HEADER.VARY.name);
			}
			return this.responseHeaders.get(RESPONSE_HEADER.VARY.name.toLowerCase());
		}
		
		public void setResponseHeaderVia(String value){
			this.responseHeaders.put(RESPONSE_HEADER.VIA.name, value);
		}
		
		public String getResponseHeaderVia(){
			if(this.responseHeaders.containsKey(RESPONSE_HEADER.VIA.name)){
				return this.responseHeaders.get(RESPONSE_HEADER.VIA.name);
			}
			return this.responseHeaders.get(RESPONSE_HEADER.VIA.name.toLowerCase());
		}
		
		public void setResponseHeaderWarning(String value){
			this.responseHeaders.put(RESPONSE_HEADER.WARNING.name, value);
		}
		
		public String getResponseHeaderWarning(){
			if(this.responseHeaders.containsKey(RESPONSE_HEADER.WARNING.name)){
				return this.responseHeaders.get(RESPONSE_HEADER.WARNING.name);
			}
			return this.responseHeaders.get(RESPONSE_HEADER.WARNING.name.toLowerCase());
		}
		
		public void setResponseHeaderWWWAuthenticate(String value){
			this.responseHeaders.put(RESPONSE_HEADER.WWW_AUTHENTICATE.name, value);
		}
		
		public String getResponseHeaderWWWAuthenticate(){
			if(this.responseHeaders.containsKey(RESPONSE_HEADER.WWW_AUTHENTICATE.name)){
				return this.responseHeaders.get(RESPONSE_HEADER.WWW_AUTHENTICATE.name);
			}
			return this.responseHeaders.get(RESPONSE_HEADER.WWW_AUTHENTICATE.name.toLowerCase());
		}
		
		public void setResponseHeaderXFrameOptions(String value){
			this.responseHeaders.put(RESPONSE_HEADER.X_FRAME_OPTIONS.name, value);
		}
		
		public String getResponseHeaderXFrameOptions(){
			if(this.responseHeaders.containsKey(RESPONSE_HEADER.X_FRAME_OPTIONS.name)){
				return this.responseHeaders.get(RESPONSE_HEADER.X_FRAME_OPTIONS.name);
			}
			return this.responseHeaders.get(RESPONSE_HEADER.X_FRAME_OPTIONS.name.toLowerCase());
		}
		
		public void setResponseHeaderXXSSProtection(String value){
			this.responseHeaders.put(RESPONSE_HEADER.X_XSS_PROTECTION.name, value);
		}
		
		public String getResponseHeaderXXSSProtection(){
			if(this.responseHeaders.containsKey(RESPONSE_HEADER.X_XSS_PROTECTION.name)){
				return this.responseHeaders.get(RESPONSE_HEADER.X_XSS_PROTECTION.name);
			}
			return this.responseHeaders.get(RESPONSE_HEADER.X_XSS_PROTECTION.name.toLowerCase());
		}
		
		public void setResponseHeaderContentSecurityPolicy(String value){
			this.responseHeaders.put(RESPONSE_HEADER.CONTENT_SECURITY_POLICY.name, value);
		}
		
		public String getResponseHeaderContentSecurityPolicy(){
			if(this.responseHeaders.containsKey(RESPONSE_HEADER.CONTENT_SECURITY_POLICY.name)){
				return this.responseHeaders.get(RESPONSE_HEADER.CONTENT_SECURITY_POLICY.name);
			}
			return this.responseHeaders.get(RESPONSE_HEADER.CONTENT_SECURITY_POLICY.name.toLowerCase());
		}
		
		public void setResponseHeaderXContentSecurityPolicy(String value){
			this.responseHeaders.put(RESPONSE_HEADER.X_CONTENT_SECURITY_POLICY.name, value);
		}
		
		public String getResponseHeaderXContentSecurityPolicy(){
			if(this.responseHeaders.containsKey(RESPONSE_HEADER.X_CONTENT_SECURITY_POLICY.name)){
				return this.responseHeaders.get(RESPONSE_HEADER.X_CONTENT_SECURITY_POLICY.name);
			}
			return this.responseHeaders.get(RESPONSE_HEADER.X_CONTENT_SECURITY_POLICY.name.toLowerCase());
		}
		
		public void setResponseHeaderXWebKitCSP(String value){
			this.responseHeaders.put(RESPONSE_HEADER.X_WEBKIT_CSP.name, value);
		}
		
		public String getResponseHeaderXWebKitCSP(){
			if(this.responseHeaders.containsKey(RESPONSE_HEADER.X_WEBKIT_CSP.name)){
				return this.responseHeaders.get(RESPONSE_HEADER.X_WEBKIT_CSP.name);
			}
			return this.responseHeaders.get(RESPONSE_HEADER.X_WEBKIT_CSP.name.toLowerCase());
		}
		
		public void setResponseHeaderXContentTypeOptions(String value){
			this.responseHeaders.put(RESPONSE_HEADER.X_CONTENT_TYPE_OPTIONS.name, value);
		}
		
		public String getResponseHeaderXContentTypeOptions(){
			if(this.responseHeaders.containsKey(RESPONSE_HEADER.X_CONTENT_TYPE_OPTIONS.name)){
				return this.responseHeaders.get(RESPONSE_HEADER.X_CONTENT_TYPE_OPTIONS.name);
			}
			return this.responseHeaders.get(RESPONSE_HEADER.X_CONTENT_TYPE_OPTIONS.name.toLowerCase());
		}
		
		public void setResponseHeaderXPoweredBy(String value){
			this.responseHeaders.put(RESPONSE_HEADER.X_POWERED_BY.name, value);
		}
		
		public String getResponseHeaderXPoweredBy(){
			if(this.responseHeaders.containsKey(RESPONSE_HEADER.X_POWERED_BY.name)){
				return this.responseHeaders.get(RESPONSE_HEADER.X_POWERED_BY.name);
			}
			return this.responseHeaders.get(RESPONSE_HEADER.X_POWERED_BY.name.toLowerCase());
		}
		
		public void setResponseHeaderXUACompatible(String value){
			this.responseHeaders.put(RESPONSE_HEADER.X_UA_COMPATIBLE.name, value);
		}
		
		public String getResponseHeaderXUACompatible(){
			if(this.responseHeaders.containsKey(RESPONSE_HEADER.X_UA_COMPATIBLE.name)){
				return this.responseHeaders.get(RESPONSE_HEADER.X_UA_COMPATIBLE.name);
			}
			return this.responseHeaders.get(RESPONSE_HEADER.X_UA_COMPATIBLE.name.toLowerCase());
		}

		public StringBuffer getRequestPayload() {
			if(this.requestPayload == null){
				this.requestPayload = new StringBuffer();
			}
			return this.requestPayload;
		}

		public void setRequestPayload(StringBuffer requestPayload) {
			this.requestPayload = requestPayload;
		}
		
		public Object getResponsePayload(boolean initIfNecessary) {
			if(this.responsePayload == null && initIfNecessary){
				this.responsePayload = new StringBuffer();
			}
			return this.responsePayload;
		}

		public HttpURLConnection getConnection() {
			return connection;
		}

		public void setConnection(HttpURLConnection connection) {
			this.connection = connection;
		}

		public int getResponseCode() {
			return responseCode;
		}

		public void setResponseCode(int responseCode) {
			this.responseCode = responseCode;
		}

		public boolean isResponseBinary() {
			return responseBinary;
		}

		public RESPONSE_FORMAT getResponseFormat() {
			return responseFormat;
		}

		public void setResponseFormat(RESPONSE_FORMAT responseFormat) {
			if(responseFormat == null){
				this.responseFormat = RESPONSE_FORMAT.PLAIN;
			} else {
				this.responseFormat = responseFormat;
			}
		}

		public List<Throwable> getErrors() {
			return errors;
		}

		public boolean isFollowRedirects() {
			return followRedirects;
		}

		public void setFollowRedirects(boolean followRedirects) {
			this.followRedirects = followRedirects;
		}

		public int getConnectTimeout() {
			if(this.connectTimeout == 0){
				return 20000;
			}
			return connectTimeout;
		}

		public void setConnectTimeout(int connectTimeout) {
			this.connectTimeout = connectTimeout;
		}

		public int getReadTimeout() {
			if(this.readTimeout == 0){
				return 10000;
			}
			return readTimeout;
		}

		public void setReadTimeout(int readTimeout) {
			this.readTimeout = readTimeout;
		}
	}
	
	//TODO This is used for testing purposes only. Make sure no production code is produced with this class in use.
	public static final class AllTrustedManager implements javax.net.ssl.TrustManager, javax.net.ssl.X509TrustManager {
		
		public java.security.cert.X509Certificate[] getAcceptedIssuers() {
		    return null;
		}
		
		public boolean isServerTrusted(java.security.cert.X509Certificate[] certs) {
		    return true;
		}
		
		public boolean isClientTrusted(java.security.cert.X509Certificate[] certs) {
		    return true;
		}
		
		public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) throws java.security.cert.CertificateException {
		    return;
		}
		
		public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) throws java.security.cert.CertificateException {
		    return;
		}
	}
	
	private static void trustAllHttpsCertificates(){
		try{
			// Create a trust manager that does not validate certificate chains:
	        javax.net.ssl.TrustManager[] trustAllCerts = new javax.net.ssl.TrustManager[1];
	        javax.net.ssl.TrustManager tm = new AllTrustedManager();
	        trustAllCerts[0] = tm;
	        javax.net.ssl.SSLContext sc = javax.net.ssl.SSLContext.getInstance("SSL");
	        sc.init(null, trustAllCerts, null);
	        javax.net.ssl.HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
		}catch(Throwable th){
			Log.e("OUT", th.getMessage(), th);
		}
    }

	public static void setContext(Context context) {
		HttpBase.context = context;
	}
}
