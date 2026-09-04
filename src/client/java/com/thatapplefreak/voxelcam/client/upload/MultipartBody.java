package com.thatapplefreak.voxelcam.client.upload;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Minimal multipart/form-data assembler: java.net.http ships no multipart body
 * publisher, and the upload needs a text field alongside the image.
 *
 * <p>A body can be produced two ways. {@link #build()} buffers the whole thing,
 * which is fine for small parts; {@link #streamFile} instead hands back only the
 * framing either side of a file part, so a caller can splice the file straight
 * from disk and never hold a screenshot in the heap.
 */
public final class MultipartBody {

	private static final String CRLF = "\r\n";

	private final String boundary = "VoxelCam-" + UUID.randomUUID();
	private final ByteArrayOutputStream out = new ByteArrayOutputStream();

	public String contentType() {
		return "multipart/form-data; boundary=" + boundary;
	}

	public MultipartBody addField(String name, String value) {
		write("--" + boundary + CRLF
				+ "Content-Disposition: form-data; name=\"" + quoted(name) + "\"" + CRLF + CRLF
				+ value + CRLF);
		return this;
	}

	/**
	 * Buffering form of a file part. Nothing in the mod uploads this way any more —
	 * {@link #streamFile} does — but it stays as the oracle the streamed framing is
	 * asserted byte-for-byte against, which is what keeps one set of framing tests
	 * covering both forms.
	 */
	public MultipartBody addFile(String name, String fileName, String contentType, byte[] content) {
		write(filePartHeaders(name, fileName, contentType));
		out.write(content, 0, content.length);
		write(CRLF);
		return this;
	}

	/**
	 * Framing for a body whose final part is a file: everything that precedes the
	 * file's bytes and everything that follows them. Splicing the file in between the
	 * two is equivalent to {@link #addFile} followed by {@link #build()}, but the
	 * caller can publish the bytes straight off disk instead of buffering them.
	 *
	 * <p>The body is left untouched, so this reads the accumulated parts rather than
	 * appending to them.
	 */
	public Streamed streamFile(String name, String fileName, String contentType) {
		byte[] partHeaders = filePartHeaders(name, fileName, contentType).getBytes(StandardCharsets.UTF_8);
		byte[] fields = out.toByteArray();

		byte[] prologue = new byte[fields.length + partHeaders.length];
		System.arraycopy(fields, 0, prologue, 0, fields.length);
		System.arraycopy(partHeaders, 0, prologue, fields.length, partHeaders.length);

		return new Streamed(prologue, (CRLF + "--" + boundary + "--" + CRLF).getBytes(StandardCharsets.UTF_8));
	}

	/** The two halves of a body with a file spliced between them. */
	public record Streamed(byte[] prologue, byte[] epilogue) {
	}

	private String filePartHeaders(String name, String fileName, String contentType) {
		return "--" + boundary + CRLF
				+ "Content-Disposition: form-data; name=\"" + quoted(name) + "\"; filename=\"" + quoted(fileName) + "\"" + CRLF
				+ "Content-Type: " + contentType + CRLF + CRLF;
	}

	public byte[] build() {
		byte[] withoutTerminator = out.toByteArray();
		byte[] terminator = ("--" + boundary + "--" + CRLF).getBytes(StandardCharsets.UTF_8);
		byte[] body = new byte[withoutTerminator.length + terminator.length];
		System.arraycopy(withoutTerminator, 0, body, 0, withoutTerminator.length);
		System.arraycopy(terminator, 0, body, withoutTerminator.length, terminator.length);
		return body;
	}

	/**
	 * RFC 7578 §4.2 wants these parameters as quoted-strings, and a screenshot's name
	 * is whatever the player typed — RenamePopup blocks only / \ and :, so a quote can
	 * reach here and close the string early. Control characters have no escape inside a
	 * quoted-string, so CR/LF and friends are dropped rather than encoded; leaving them
	 * in would end the header line and reframe the part's header block.
	 */
	private static String quoted(String value) {
		StringBuilder escaped = new StringBuilder(value.length());
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			if (c < 0x20 || c == 0x7F) {
				continue;
			}
			if (c == '"' || c == '\\') {
				escaped.append('\\');
			}
			escaped.append(c);
		}
		return escaped.toString();
	}

	private void write(String text) {
		byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
		out.write(bytes, 0, bytes.length);
	}
}
