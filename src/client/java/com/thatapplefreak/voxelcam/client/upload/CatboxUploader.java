package com.thatapplefreak.voxelcam.client.upload;

import com.thatapplefreak.voxelcam.client.share.NativeShare;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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

	/**
	 * The largest file catbox.moe takes: 200 MB. Read as decimal megabytes, the smaller of the two
	 * readings: a file just under a binary limit that is refused here loses less than one just over
	 * a decimal limit that uploads for minutes and is then refused.
	 */
	public static final long MAX_UPLOAD_BYTES = 200_000_000L;

	/** How long an upload may take whatever its size: what a silent server costs a small file. */
	private static final Duration BASE_TIMEOUT = Duration.ofSeconds(60);
	/** 512 kbit/s, slower than the uplinks players are likely to have. See {@link #timeoutFor}. */
	private static final long SLOWEST_UPLOAD_BYTES_PER_SECOND = 64_000L;

	private static final HttpClient CLIENT = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(15))
			.build();

	private CatboxUploader() {
	}

	/** A file over {@link #MAX_UPLOAD_BYTES}, refused before any of it was sent. */
	public static final class TooLargeException extends IOException {

		private final long bytes;

		TooLargeException(long bytes) {
			super("File is " + bytes + " bytes, over catbox's limit of " + MAX_UPLOAD_BYTES);
			this.bytes = bytes;
		}

		public long bytes() {
			return bytes;
		}
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
		return upload(image, endpoint, timeoutFor(image.length()));
	}

	/**
	 * How long an upload of this size may take before it is given up on.
	 *
	 * <p>{@code HttpRequest.timeout} runs until the response arrives, and catbox answers only once
	 * it has the whole body, so this bounds the time spent sending the file. A flat 60 seconds was
	 * therefore a cap on file size, about 37 MB at 5 Mbit/s, and it failed the same way on every
	 * retry. The allowance grows with the file instead, at a speed slow enough that a working
	 * connection finishes inside it. It is kept rather than dropped because it is also the only
	 * thing that ends an upload to a server that accepted the connection and then went silent;
	 * {@code connectTimeout} only covers a host that cannot be reached at all.
	 */
	static Duration timeoutFor(long bytes) {
		return BASE_TIMEOUT.plusSeconds(Math.max(0L, bytes) / SLOWEST_UPLOAD_BYTES_PER_SECOND);
	}

	static boolean exceedsLimit(long bytes) {
		return bytes > MAX_UPLOAD_BYTES;
	}

	/** The upload itself, with the timeout as a parameter so a test can use one shorter than a
	 * minute. */
	static CompletableFuture<String> upload(File image, URI endpoint, Duration timeout) {
		// Before anything is sent: catbox would take the whole file and then refuse it, and the
		// popup could say no more than that the link failed.
		long bytes = image.length();
		if (exceedsLimit(bytes)) {
			return CompletableFuture.failedFuture(new TooLargeException(bytes));
		}

		MultipartBody multipart = new MultipartBody().addField("reqtype", "fileupload");
		String contentType = "webp".equals(NativeShare.extensionOf(image)) ? "image/webp" : "image/png";
		MultipartBody.Streamed framing = multipart.streamFile("fileToUpload", image.getName(), contentType);

		// The image is published straight from disk rather than read here: an oversized
		// capture is tens of megabytes, and this runs on the thread that pressed the
		// button. ofFile leaves the read to the client's own executor, so the only
		// blocking work left on the caller is its existence check.
		HttpRequest.BodyPublisher body;
		try {
			body = HttpRequest.BodyPublishers.concat(
					HttpRequest.BodyPublishers.ofByteArray(framing.prologue()),
					HttpRequest.BodyPublishers.ofFile(image.toPath()),
					HttpRequest.BodyPublishers.ofByteArray(framing.epilogue()));
		} catch (IOException e) {
			return CompletableFuture.failedFuture(e);
		}

		HttpRequest request = HttpRequest.newBuilder()
				.uri(endpoint)
				.header("Content-Type", multipart.contentType())
				.timeout(timeout)
				.POST(body)
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
