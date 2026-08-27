package com.thatapplefreak.voxelcam.client.upload;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Uploads to catbox.moe, which needs no credentials at all: omitting the
 * userhash makes the upload anonymous. It answers with the bare URL as plain
 * text rather than JSON, so there is nothing to deserialize.
 */
public final class CatboxUploader {

	private static final String UPLOAD_URL = "https://catbox.moe/user/api.php";
	private static final Duration TIMEOUT = Duration.ofSeconds(60);

	private static final HttpClient CLIENT = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(15))
			.build();

	private CatboxUploader() {
	}

	public static CompletableFuture<String> upload(File image) {
		return upload(image, URI.create(UPLOAD_URL));
	}

	/**
	 * Same upload against an arbitrary endpoint. Exists so the tests can point it at
	 * a local stub and exercise the real request and the real response handling,
	 * including catbox's habit of refusing with a 200.
	 */
	static CompletableFuture<String> upload(File image, URI endpoint) {
		byte[] body;
		String contentType;
		try {
			MultipartBody multipart = new MultipartBody()
					.addField("reqtype", "fileupload")
					.addFile("fileToUpload", image.getName(), "image/png", Files.readAllBytes(image.toPath()));
			body = multipart.build();
			contentType = multipart.contentType();
		} catch (IOException e) {
			return CompletableFuture.failedFuture(e);
		}

		HttpRequest request = HttpRequest.newBuilder()
				.uri(endpoint)
				.header("Content-Type", contentType)
				.timeout(TIMEOUT)
				.POST(HttpRequest.BodyPublishers.ofByteArray(body))
				.build();

		return CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
				.thenApply(CatboxUploader::readLink);
	}

	/**
	 * A refusal comes back as 200 with an error sentence in the body rather than
	 * an HTTP error, so the body has to be checked for an actual URL.
	 */
	private static String readLink(HttpResponse<String> response) {
		String body = response.body() == null ? "" : response.body().trim();
		if (response.statusCode() != 200 || !body.startsWith("https://")) {
			throw new CompletionException(new IOException(
					"Catbox rejected the upload (HTTP " + response.statusCode() + "): " + body));
		}
		return body;
	}
}
