package com.thatapplefreak.voxelcam.client.screenshot;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.zip.CRC32;

/**
 * Splices {@code iTXt} chunks into an already-encoded PNG and reads them back. {@code
 * NativeImage.writeToFile} is the STB-backed encoder and only ever emits {@code
 * IHDR}/{@code IDAT}/{@code IEND}, so embedding metadata means a post-write byte splice
 * rather than a re-encode — this operates on the file {@code NativeImage} already wrote.
 *
 * <p>{@code iTXt} rather than the simpler {@code tEXt}: {@code tEXt} is Latin-1 only, and a
 * world or server name is exactly the kind of value that might not be. Every chunk this
 * class writes is uncompressed, with an empty language tag and translated keyword — the
 * simplest legal {@code iTXt} shape.
 */
public final class PngTextChunk {

	private static final byte[] SIGNATURE = { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n' };
	private static final byte[] ITXT = "iTXt".getBytes(StandardCharsets.US_ASCII);
	private static final String IEND = "IEND";

	private PngTextChunk() {
	}

	/** Rewrites {@code file} in place, atomically, with one {@code iTXt} chunk per entry. */
	public static void embed(File file, Map<String, String> entries) throws IOException {
		if (entries.isEmpty()) {
			return;
		}
		byte[] spliced = embed(Files.readAllBytes(file.toPath()), entries);

		Path target = file.toPath();
		Path tmp = Files.createTempFile(target.toAbsolutePath().getParent(), "voxelcam-", ".png.tmp");
		try {
			Files.write(tmp, spliced);
			Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} finally {
			Files.deleteIfExists(tmp);
		}
	}

	public static Map<String, String> read(File file) {
		try {
			return read(Files.readAllBytes(file.toPath()));
		} catch (IOException | RuntimeException e) {
			return Map.of();
		}
	}

	/** Package-private seam: pure bytes in, bytes out, no file I/O to stub in a test. */
	static byte[] embed(byte[] png, Map<String, String> entries) {
		// A key already present (e.g. re-embedding a toggled flag) must be replaced, not
		// shadowed: new chunks are always inserted right after IHDR, ahead of anything
		// already there, and read() keeps the LAST value it sees for a keyword as it walks
		// front to back — so without stripping, the physically later (older) chunk would
		// keep winning over every new value written after it, forever.
		byte[] stripped = strip(png, entries.keySet());
		int insertAt = ihdrEnd(stripped);

		ByteArrayOutputStream out = new ByteArrayOutputStream(stripped.length + 64 * entries.size());
		out.write(stripped, 0, insertAt);
		for (Map.Entry<String, String> entry : entries.entrySet()) {
			out.writeBytes(iTXtChunk(entry.getKey(), entry.getValue()));
		}
		out.write(stripped, insertAt, stripped.length - insertAt);
		return out.toByteArray();
	}

	/** Removes any existing {@code iTXt} chunks whose keyword is about to be re-embedded. */
	private static byte[] strip(byte[] png, Set<String> keys) {
		if (keys.isEmpty()) {
			return png;
		}
		ByteArrayOutputStream out = new ByteArrayOutputStream(png.length);
		out.write(png, 0, SIGNATURE.length);
		int pos = SIGNATURE.length;
		while (pos + 12 <= png.length) {
			int length = readInt(png, pos);
			String type = new String(png, pos + 4, 4, StandardCharsets.US_ASCII);
			int dataStart = pos + 8;
			long dataEnd = (long) dataStart + length;
			if (dataEnd + 4 > png.length) {
				out.write(png, pos, png.length - pos);
				return out.toByteArray();
			}
			int chunkEnd = (int) dataEnd + 4;
			if (!("iTXt".equals(type) && keys.contains(keyword(png, dataStart, (int) dataEnd)))) {
				out.write(png, pos, chunkEnd - pos);
			}
			pos = chunkEnd;
		}
		return out.toByteArray();
	}

	/** @return the chunk's keyword, or null if it has none within its own data bounds — a
	 * foreign or malformed {@code iTXt} chunk like that is kept by {@link #strip}, not dropped. */
	private static String keyword(byte[] png, int dataStart, int dataEnd) {
		int keywordEnd = indexOf(png, dataStart, dataEnd, (byte) 0);
		return keywordEnd < 0 ? null : new String(png, dataStart, keywordEnd - dataStart, StandardCharsets.US_ASCII);
	}

	/** Package-private seam, mirrored with {@link #embed(byte[], Map)}. */
	static Map<String, String> read(byte[] png) {
		Map<String, String> tags = new LinkedHashMap<>();
		int pos = SIGNATURE.length;
		while (pos + 12 <= png.length) {
			int length = readInt(png, pos);
			String type = new String(png, pos + 4, 4, StandardCharsets.US_ASCII);
			int dataStart = pos + 8;
			long dataEnd = (long) dataStart + length;
			if (dataEnd + 4 > png.length) {
				break; // truncated or corrupt; stop rather than read past the array
			}
			if ("iTXt".equals(type)) {
				readITXt(png, dataStart, (int) dataEnd, tags);
			} else if (IEND.equals(type)) {
				break;
			}
			pos = (int) dataEnd + 4;
		}
		return tags;
	}

	/** The first chunk after the signature must be IHDR; this is where new chunks are inserted. */
	private static int ihdrEnd(byte[] png) {
		int dataLength = readInt(png, SIGNATURE.length);
		return SIGNATURE.length + 4 + 4 + dataLength + 4;
	}

	private static byte[] iTXtChunk(String keyword, String text) {
		byte[] keywordBytes = keyword.getBytes(StandardCharsets.US_ASCII);
		byte[] textBytes = text.getBytes(StandardCharsets.UTF_8);

		ByteArrayOutputStream data = new ByteArrayOutputStream(keywordBytes.length + textBytes.length + 5);
		data.writeBytes(keywordBytes);
		data.write(0); // keyword null separator
		data.write(0); // compression flag: uncompressed
		data.write(0); // compression method: unused when uncompressed
		data.write(0); // empty language tag, null-terminated
		data.write(0); // empty translated keyword, null-terminated
		data.writeBytes(textBytes);
		byte[] chunkData = data.toByteArray();

		CRC32 crc = new CRC32();
		crc.update(ITXT);
		crc.update(chunkData);

		ByteArrayOutputStream chunk = new ByteArrayOutputStream(12 + chunkData.length);
		chunk.writeBytes(intBytes(chunkData.length));
		chunk.writeBytes(ITXT);
		chunk.writeBytes(chunkData);
		chunk.writeBytes(intBytes((int) crc.getValue()));
		return chunk.toByteArray();
	}

	private static void readITXt(byte[] png, int start, int end, Map<String, String> tags) {
		int keywordEnd = indexOf(png, start, end, (byte) 0);
		if (keywordEnd < 0) {
			return;
		}
		String keyword = new String(png, start, keywordEnd - start, StandardCharsets.US_ASCII);

		int p = keywordEnd + 1;
		if (p + 2 > end) {
			return;
		}
		boolean compressed = png[p] != 0;
		p += 2; // compression flag, compression method

		int languageEnd = indexOf(png, p, end, (byte) 0);
		if (languageEnd < 0) {
			return;
		}
		p = languageEnd + 1;

		int translatedEnd = indexOf(png, p, end, (byte) 0);
		if (translatedEnd < 0) {
			return;
		}
		p = translatedEnd + 1;

		if (compressed) {
			return; // this class never writes a compressed chunk; skip anything it doesn't understand.
		}
		tags.put(keyword, new String(png, p, end - p, StandardCharsets.UTF_8));
	}

	private static int indexOf(byte[] data, int from, int to, byte target) {
		for (int i = from; i < to; i++) {
			if (data[i] == target) {
				return i;
			}
		}
		return -1;
	}

	private static int readInt(byte[] data, int at) {
		return ((data[at] & 0xFF) << 24) | ((data[at + 1] & 0xFF) << 16)
				| ((data[at + 2] & 0xFF) << 8) | (data[at + 3] & 0xFF);
	}

	private static byte[] intBytes(int value) {
		return new byte[] { (byte) (value >>> 24), (byte) (value >>> 16), (byte) (value >>> 8), (byte) value };
	}
}
