package com.thatapplefreak.voxelcam.client.upload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Runs the uploader against a stub catbox on loopback rather than the real service:
 * the tests stay offline and deterministic, and they can produce the responses that
 * matter most — the refusals, which catbox sends as HTTP 200 with an error sentence
 * in the body instead of an error status.
 */
@Timeout(30)
class CatboxUploaderTest {

	@TempDir
	Path dir;

	private HttpServer server;
	private URI endpoint;

	/** What the stub will answer with, and what it last received. */
	private int status;
	private String responseBody;
	private final AtomicReference<byte[]> lastBody = new AtomicReference<>();
	private final AtomicReference<String> lastContentType = new AtomicReference<>();
	private final AtomicReference<String> lastMethod = new AtomicReference<>();

	@BeforeEach
	void startStub() throws IOException {
		status = 200;
		responseBody = "https://files.catbox.moe/abc123.png";

		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/user/api.php", this::handle);
		server.start();
		endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/user/api.php");
	}

	@AfterEach
	void stopStub() {
		server.stop(0);
	}

	private void handle(HttpExchange exchange) throws IOException {
		lastMethod.set(exchange.getRequestMethod());
		lastContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
		try (var in = exchange.getRequestBody()) {
			lastBody.set(in.readAllBytes());
		}
		byte[] out = responseBody.getBytes(StandardCharsets.UTF_8);
		exchange.sendResponseHeaders(status, out.length);
		try (var body = exchange.getResponseBody()) {
			body.write(out);
		}
	}

	private File screenshot(String name, byte[] content) throws IOException {
		File file = dir.resolve(name).toFile();
		Files.write(file.toPath(), content);
		return file;
	}

	private File screenshot() throws IOException {
		return screenshot("shot.png", new byte[] { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 42 });
	}

	@Test
	void returnsTheLinkFromTheResponseBody() throws Exception {
		String link = CatboxUploader.upload(screenshot(), endpoint).get();

		assertEquals("https://files.catbox.moe/abc123.png", link);
	}

	@Test
	void surroundingWhitespaceIsTrimmed() throws Exception {
		responseBody = "  https://files.catbox.moe/abc123.png\n";

		assertEquals("https://files.catbox.moe/abc123.png",
				CatboxUploader.upload(screenshot(), endpoint).get());
	}

	@Test
	void postsMultipartWithTheAnonymousFileuploadFields() throws Exception {
		CatboxUploader.upload(screenshot("holiday.png", new byte[] { 1, 2, 3 }), endpoint).get();

		assertEquals("POST", lastMethod.get());
		assertTrue(lastContentType.get().startsWith("multipart/form-data; boundary="), lastContentType.get());

		String sent = new String(lastBody.get(), StandardCharsets.UTF_8);
		assertTrue(sent.contains("name=\"reqtype\""), sent);
		assertTrue(sent.contains("fileupload"), sent);
		assertTrue(sent.contains("name=\"fileToUpload\"; filename=\"holiday.png\""), sent);
		assertTrue(sent.contains("Content-Type: image/png"), sent);
		// No userhash field: omitting it is what makes the upload anonymous, and
		// sending an empty one is not the same thing.
		assertTrue(!sent.contains("userhash"), "upload must stay anonymous");
	}

	@Test
	void sendsTheFileBytesUnaltered() throws Exception {
		byte[] content = { (byte) 0x89, 'P', 'N', 'G', (byte) 0xFF, 0x00, (byte) 0xC3, (byte) 0x28 };
		CatboxUploader.upload(screenshot("raw.png", content), endpoint).get();

		String sent = new String(lastBody.get(), StandardCharsets.ISO_8859_1);
		String expected = new String(content, StandardCharsets.ISO_8859_1);
		assertTrue(sent.contains(expected), "file bytes should reach the wire untouched");
	}

	/**
	 * The refusal that motivated validating the body at all: catbox answers 200 and
	 * puts the complaint in the body, so a naive client reports success and hands the
	 * user an error sentence as their "link".
	 */
	@Test
	void refusalWithHttp200IsTreatedAsAFailure() throws Exception {
		responseBody = "412 Precondition Failed: file too large";

		ExecutionException thrown = assertThrows(ExecutionException.class,
				() -> CatboxUploader.upload(screenshot(), endpoint).get());

		IOException cause = assertInstanceOf(IOException.class, rootCause(thrown));
		assertTrue(cause.getMessage().contains("file too large"), cause.getMessage());
	}

	@Test
	void nonHttpsBodyIsRejected() throws Exception {
		responseBody = "http://files.catbox.moe/abc123.png";

		assertThrows(ExecutionException.class, () -> CatboxUploader.upload(screenshot(), endpoint).get());
	}

	@Test
	void emptyBodyIsRejected() throws Exception {
		responseBody = "";

		assertThrows(ExecutionException.class, () -> CatboxUploader.upload(screenshot(), endpoint).get());
	}

	@Test
	void httpErrorStatusIsRejectedEvenWithAUrlBody() throws Exception {
		status = 503;
		responseBody = "https://files.catbox.moe/abc123.png";

		ExecutionException thrown = assertThrows(ExecutionException.class,
				() -> CatboxUploader.upload(screenshot(), endpoint).get());

		assertTrue(rootCause(thrown).getMessage().contains("503"), rootCause(thrown).getMessage());
	}

	/** A missing file must fail the future rather than throw out of upload(). */
	@Test
	void unreadableFileFailsTheFutureRatherThanThrowing() {
		File missing = dir.resolve("absent.png").toFile();

		var future = CatboxUploader.upload(missing, endpoint);

		assertTrue(future.isCompletedExceptionally());
		ExecutionException thrown = assertThrows(ExecutionException.class, future::get);
		assertInstanceOf(IOException.class, rootCause(thrown));
	}

	/** Nothing should have been sent when the file could not be read. */
	@Test
	void unreadableFileSendsNoRequest() throws Exception {
		CatboxUploader.upload(dir.resolve("absent.png").toFile(), endpoint)
				.exceptionally(e -> null)
				.get();

		Thread.sleep(Duration.ofMillis(100));
		assertTrue(lastMethod.get() == null, "no request should have reached the server");
	}

	private static Throwable rootCause(Throwable thrown) {
		Throwable cause = thrown;
		while (cause.getCause() != null) {
			cause = cause.getCause();
		}
		return cause;
	}
}
