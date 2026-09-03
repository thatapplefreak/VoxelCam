package com.thatapplefreak.voxelcam.client.upload;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Minimal multipart/form-data assembler: java.net.http ships no multipart body
 * publisher, and the upload needs a text field alongside the image.
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

	public MultipartBody addFile(String name, String fileName, String contentType, byte[] content) {
		write("--" + boundary + CRLF
				+ "Content-Disposition: form-data; name=\"" + quoted(name) + "\"; filename=\"" + quoted(fileName) + "\"" + CRLF
				+ "Content-Type: " + contentType + CRLF + CRLF);
		out.write(content, 0, content.length);
		write(CRLF);
		return this;
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
