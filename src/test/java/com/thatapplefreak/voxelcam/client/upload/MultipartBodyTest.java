package com.thatapplefreak.voxelcam.client.upload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * MultipartBody is hand-rolled because java.net.http ships no multipart publisher,
 * so the framing has nothing standard checking it. catbox.moe rejects a malformed
 * body with an HTTP 200 and an error string, which is easy to misread as a network
 * fault — these tests pin the framing so that failure mode stays impossible.
 */
class MultipartBodyTest {

	private static String bodyOf(MultipartBody body) {
		return new String(body.build(), StandardCharsets.UTF_8);
	}

	/** The boundary in the header must be the one actually used to delimit the parts. */
	@Test
	void contentTypeBoundaryMatchesTheBodyDelimiters() {
		MultipartBody body = new MultipartBody().addField("reqtype", "fileupload");

		String contentType = body.contentType();
		String boundary = contentType.substring(contentType.indexOf("boundary=") + "boundary=".length());

		assertTrue(contentType.startsWith("multipart/form-data; boundary="), contentType);
		assertTrue(bodyOf(body).startsWith("--" + boundary + "\r\n"));
	}

	@Test
	void fieldUsesCrlfFramingAndClosesWithTheTerminator() {
		MultipartBody body = new MultipartBody().addField("reqtype", "fileupload");
		String contentType = body.contentType();
		String boundary = contentType.substring(contentType.indexOf("boundary=") + "boundary=".length());

		assertEquals("--" + boundary + "\r\n"
				+ "Content-Disposition: form-data; name=\"reqtype\"\r\n\r\n"
				+ "fileupload\r\n"
				+ "--" + boundary + "--\r\n",
				bodyOf(body));
	}

	@Test
	void fileCarriesFilenameAndContentType() {
		MultipartBody body = new MultipartBody()
				.addFile("fileToUpload", "shot.png", "image/png", new byte[] { 1, 2, 3 });

		assertTrue(bodyOf(body).contains(
				"Content-Disposition: form-data; name=\"fileToUpload\"; filename=\"shot.png\"\r\n"
						+ "Content-Type: image/png\r\n\r\n"),
				bodyOf(body));
	}

	/**
	 * The payload is spliced in as raw bytes rather than through a String, so bytes
	 * that are not valid UTF-8 have to survive untouched — a PNG is full of them.
	 */
	@Test
	void fileContentIsNotMangledByTextEncoding() {
		byte[] content = { (byte) 0x89, 'P', 'N', 'G', (byte) 0xFF, 0x00, (byte) 0xC3 };
		byte[] built = new MultipartBody()
				.addFile("fileToUpload", "shot.png", "image/png", content)
				.build();

		int at = indexOf(built, content);
		assertTrue(at >= 0, "raw file bytes should appear in the body verbatim");
	}

	@Test
	void fieldsAndFilesAccumulateInOrder() {
		String body = bodyOf(new MultipartBody()
				.addField("reqtype", "fileupload")
				.addFile("fileToUpload", "shot.png", "image/png", new byte[] { 42 }));

		assertTrue(body.indexOf("name=\"reqtype\"") < body.indexOf("name=\"fileToUpload\""), body);
	}

	/** A fresh boundary per body keeps one upload's delimiter out of another's payload. */
	@Test
	void eachBodyGetsItsOwnBoundary() {
		assertNotEquals(new MultipartBody().contentType(), new MultipartBody().contentType());
	}

	private static int indexOf(byte[] haystack, byte[] needle) {
		outer:
		for (int i = 0; i <= haystack.length - needle.length; i++) {
			for (int j = 0; j < needle.length; j++) {
				if (haystack[i + j] != needle[j]) {
					continue outer;
				}
			}
			return i;
		}
		return -1;
	}
}
