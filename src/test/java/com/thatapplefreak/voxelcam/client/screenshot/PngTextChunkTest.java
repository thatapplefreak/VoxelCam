package com.thatapplefreak.voxelcam.client.screenshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.CRC32;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The splice has to produce a PNG every other reader still opens fine — a wrong chunk
 * offset or a bad CRC breaks the file for everyone, not just VoxelCam — so this pins the
 * byte layout, not just the round trip through VoxelCam's own reader.
 */
class PngTextChunkTest {

	@TempDir
	Path dir;

	/** A minimal but real PNG: signature, IHDR, an empty IDAT, IEND. Good enough to splice into. */
	private static byte[] minimalPng() {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.writeBytes(new byte[] { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n' });
		out.writeBytes(chunk("IHDR", ihdrData(64, 32)));
		out.writeBytes(chunk("IDAT", new byte[0]));
		out.writeBytes(chunk("IEND", new byte[0]));
		return out.toByteArray();
	}

	private static byte[] ihdrData(int width, int height) {
		byte[] data = new byte[13];
		writeInt(data, 0, width);
		writeInt(data, 4, height);
		data[8] = 8;
		data[9] = 2;
		return data;
	}

	private static byte[] chunk(String type, byte[] data) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		writeInt4(out, data.length);
		byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
		out.writeBytes(typeBytes);
		out.writeBytes(data);
		CRC32 crc = new CRC32();
		crc.update(typeBytes);
		crc.update(data);
		writeInt4(out, (int) crc.getValue());
		return out.toByteArray();
	}

	private static void writeInt(byte[] target, int at, int value) {
		target[at] = (byte) (value >>> 24);
		target[at + 1] = (byte) (value >>> 16);
		target[at + 2] = (byte) (value >>> 8);
		target[at + 3] = (byte) value;
	}

	private static void writeInt4(ByteArrayOutputStream out, int value) {
		out.writeBytes(new byte[] { (byte) (value >>> 24), (byte) (value >>> 16), (byte) (value >>> 8), (byte) value });
	}

	@Test
	void roundTripsASingleEntry() {
		Map<String, String> entries = Map.of("voxelcam:dimension", "minecraft:overworld");

		byte[] spliced = PngTextChunk.embed(minimalPng(), entries);

		assertEquals(entries, PngTextChunk.read(spliced));
	}

	@Test
	void roundTripsMultipleEntriesInOrder() {
		Map<String, String> entries = new LinkedHashMap<>();
		entries.put("voxelcam:dimension", "minecraft:the_nether");
		entries.put("voxelcam:x", "12");
		entries.put("voxelcam:y", "70");
		entries.put("voxelcam:z", "-45");
		entries.put("voxelcam:world", "New World");

		byte[] spliced = PngTextChunk.embed(minimalPng(), entries);

		assertEquals(entries, PngTextChunk.read(spliced));
	}

	@Test
	void survivesNonLatin1TextUnlikeATextChunkWould() {
		Map<String, String> entries = Map.of("voxelcam:world", "café–base");

		byte[] spliced = PngTextChunk.embed(minimalPng(), entries);

		assertEquals(entries, PngTextChunk.read(spliced));
	}

	@Test
	void insertsImmediatelyAfterIhdrNotAtTheEnd() {
		byte[] original = minimalPng();
		byte[] spliced = PngTextChunk.embed(original, Map.of("voxelcam:x", "1"));

		// signature (8) + IHDR chunk (4 len + 4 type + 13 data + 4 crc = 25) = 33
		int ihdrEnd = 33;
		assertEquals("iTXt", new String(spliced, ihdrEnd + 4, 4, StandardCharsets.US_ASCII));

		// Everything from the original PNG after IHDR should still be present, unmodified,
		// just pushed later in the file.
		byte[] originalTail = new byte[original.length - ihdrEnd];
		System.arraycopy(original, ihdrEnd, originalTail, 0, originalTail.length);
		byte[] splicedTail = new byte[originalTail.length];
		System.arraycopy(spliced, spliced.length - originalTail.length, splicedTail, 0, originalTail.length);
		assertTrue(java.util.Arrays.equals(originalTail, splicedTail));
	}

	@Test
	void readingATruncatedFileGivesAnEmptyMapRatherThanThrowing() {
		byte[] spliced = PngTextChunk.embed(minimalPng(), Map.of("voxelcam:x", "1"));
		byte[] truncated = new byte[10];
		System.arraycopy(spliced, 0, truncated, 0, truncated.length);

		assertEquals(Map.of(), PngTextChunk.read(truncated));
	}

	@Test
	void aPngWithNoTagsReadsAsEmpty() {
		assertEquals(Map.of(), PngTextChunk.read(minimalPng()));
	}

	@Test
	void embedIsANoOpForNoEntries() {
		byte[] original = minimalPng();

		assertTrue(java.util.Arrays.equals(original, PngTextChunk.embed(original, Map.of())));
	}

	@Test
	void fileBasedEmbedAndReadRoundTripThroughDisk() throws IOException {
		var file = dir.resolve("shot.png").toFile();
		Files.write(file.toPath(), minimalPng());
		Map<String, String> entries = Map.of("voxelcam:dimension", "minecraft:the_end", "voxelcam:x", "5");

		PngTextChunk.embed(file, entries);

		assertEquals(entries, PngTextChunk.read(file));
	}

	@Test
	void readingAMissingFileGivesAnEmptyMapRatherThanThrowing() {
		assertEquals(Map.of(), PngTextChunk.read(dir.resolve("absent.png").toFile()));
	}
}
