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
				+ "Content-Disposition: form-data; name=\"" + name + "\"" + CRLF + CRLF
				+ value + CRLF);
		return this;
	}

	public MultipartBody addFile(String name, String fileName, String contentType, byte[] content) {
		write("--" + boundary + CRLF
				+ "Content-Disposition: form-data; name=\"" + name + "\"; filename=\"" + fileName + "\"" + CRLF
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

	private void write(String text) {
		byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
		out.write(bytes, 0, bytes.length);
	}
}
