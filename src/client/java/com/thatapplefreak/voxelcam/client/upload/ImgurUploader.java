package com.thatapplefreak.voxelcam.client.upload;

import com.google.gson.Gson;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Replaces the old Imgur.java/ThreadMultipartPostUpload (VoxelCommon
 * transport, IUploadCompleteCallback) with java.net.http.HttpClient and a
 * hand-built multipart body - Java has no built-in multipart client. The
 * response contract (Imgur API v3's {data,success,status} shape) is
 * unchanged from the original mod.
 */
public final class ImgurUploader {

	private static final String UPLOAD_URL = "https://api.imgur.com/3/image";
	private static final Gson GSON = new Gson();
	private static final HttpClient CLIENT = HttpClient.newHttpClient();

	private ImgurUploader() {
	}

	public static CompletableFuture<ImgurUploadResponse> upload(File image, String clientId) {
		String boundary = "VoxelCam-" + UUID.randomUUID();
		byte[] body;
		try {
			body = buildMultipartBody(image, boundary);
		} catch (IOException e) {
			return CompletableFuture.failedFuture(e);
		}

		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(UPLOAD_URL))
				.header("Authorization", "Client-ID " + clientId)
				.header("Content-Type", "multipart/form-data; boundary=" + boundary)
				.POST(HttpRequest.BodyPublishers.ofByteArray(body))
				.build();

		return CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
				.thenApply(response -> GSON.fromJson(response.body(), ImgurUploadResponse.class));
	}

	private static byte[] buildMultipartBody(File image, String boundary) throws IOException {
		String crlf = "\r\n";
		String header = "--" + boundary + crlf
				+ "Content-Disposition: form-data; name=\"image\"; filename=\"" + image.getName() + "\"" + crlf
				+ "Content-Type: image/png" + crlf + crlf;
		String footer = crlf + "--" + boundary + "--" + crlf;

		byte[] imageBytes = Files.readAllBytes(image.toPath());
		byte[] headerBytes = header.getBytes(StandardCharsets.UTF_8);
		byte[] footerBytes = footer.getBytes(StandardCharsets.UTF_8);

		byte[] body = new byte[headerBytes.length + imageBytes.length + footerBytes.length];
		System.arraycopy(headerBytes, 0, body, 0, headerBytes.length);
		System.arraycopy(imageBytes, 0, body, headerBytes.length, imageBytes.length);
		System.arraycopy(footerBytes, 0, body, headerBytes.length + imageBytes.length, footerBytes.length);
		return body;
	}
}
