package com.thatapplefreak.voxelcam.upload.imgur;

import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.thevoxelbox.common.util.upload.IUploadCompleteCallback;
import com.thevoxelbox.common.util.upload.ThreadMultipartPostUpload;


/**
 * Base class for objects which perform manipulation actions against the imgur
 * API
 * 
 * @author Mumfrey
 */
public abstract class Imgur implements IUploadCompleteCallback {
	/**
	 * Method for request
	 * 
	 * @author Mumfrey
	 */
	protected enum Method {
		GET, POST, PUT, DELETE
	}

	/**
	 * Base url for the imgur API
	 */
	public static final String IMGUR_API_BASEURL = "https://api.imgur.com";

	/**
	 * imgur API version to call
	 */
	public static final int IMGUR_API_VERSION = 3;

	/**
	 * Our client id
	 */
	public static final String IMGUR_API_CLIENTID = "b0118040d2b06e2";

	/**
	 * Gson deserialiser
	 */
	private static Gson gson = new Gson();

	/**
	 * True if this process was started
	 */
	private boolean started = false;

	/**
	 * URL to get/post to
	 */
	private final String url;

	/**
	 * HTTP method
	 */
	private final Method method;

	/**
	 * Upload thread
	 */
	protected ThreadMultipartPostUpload uploadThread;

	/**
	 * HTTP POST data
	 */
	protected Map<String, Object> postData = new HashMap<String, Object>();

	/**
	 * True if the process completed
	 */
	private boolean isComplete;

	/**
	 * Object to call back
	 */
	private ImgurCallback callback;

	/**
	 * Class to wrap JSON responses in
	 */
	private Class<? extends ImgurResponse> responseClass;

	/**
	 * Parsed JSON response
	 */
	private ImgurResponse response;

	public Imgur(Method method, String route, Class<? extends ImgurResponse> responseClass) {
		this(method, route, responseClass, null);
	}

	public Imgur(Method method, String route, Class<? extends ImgurResponse> responseClass, String get) {
		this.method = method;
		this.url = String.format("%s/%d/%s%s%s", Imgur.IMGUR_API_BASEURL, Imgur.IMGUR_API_VERSION, route.toLowerCase(), get != null ? "/" : "", get != null ? get : "");
		this.responseClass = responseClass;
	}

	protected abstract void assemble();

	public void start(ImgurCallback callback) {
		if (this.started) {
			throw new IllegalStateException("Task already started");
		}

		this.started = true;
		this.callback = callback;

		this.assemble();

		String authorization = String.format("Client-ID %s", Imgur.IMGUR_API_CLIENTID);
		this.uploadThread = new ThreadMultipartPostUpload(this.method.toString(), this.url, this.postData, authorization, this);
		this.uploadThread.start();
	}

	@Override
	public void onUploadComplete(String responseData) {
		this.isComplete = true;

		if (this.uploadThread.httpResponseCode / 100 != 2) {
			if (this.callback != null) {
				this.callback.onHTTPFailure(this.uploadThread.httpResponseCode, this.uploadThread.httpResponse);
			}

			return;
		}

		this.response = Imgur.gson.fromJson(responseData, this.responseClass);

		if (this.callback != null) {
			this.callback.onCompleted(this.response);
		}
	}

	public boolean isComplete() {
		return this.isComplete;
	}

	public ImgurResponse getResponse() {
		return this.response;
	}
}