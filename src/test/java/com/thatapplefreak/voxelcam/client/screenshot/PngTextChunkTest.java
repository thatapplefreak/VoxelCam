package com.thatapplefreak.voxelcam.client.screenshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
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
	void roundTripsASingleEntry() throws IOException {
		Map<String, String> entries = Map.of("voxelcam:dimension", "minecraft:overworld");

		byte[] spliced = PngTextChunk.embed(minimalPng(), entries);

		assertEquals(entries, PngTextChunk.read(spliced));
	}

	@Test
	void roundTripsMultipleEntriesInOrder() throws IOException {
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
	void survivesNonLatin1TextUnlikeATextChunkWould() throws IOException {
		Map<String, String> entries = Map.of("voxelcam:world", "café–base");

		byte[] spliced = PngTextChunk.embed(minimalPng(), entries);

		assertEquals(entries, PngTextChunk.read(spliced));
	}

	@Test
	void insertsImmediatelyAfterIhdrNotAtTheEnd() throws IOException {
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
	void readingATruncatedFileGivesAnEmptyMapRatherThanThrowing() throws IOException {
		byte[] spliced = PngTextChunk.embed(minimalPng(), Map.of("voxelcam:x", "1"));
		byte[] truncated = new byte[10];
		System.arraycopy(spliced, 0, truncated, 0, truncated.length);

		assertEquals(Map.of(), PngTextChunk.read(truncated));
	}

	/**
	 * The manager lists anything named {@code .png} and offers a star for it, and a
	 * half-finished save leaves exactly these behind. The refusal has to be the checked
	 * exception: that is the one the star toggle catches, where an index error would leave
	 * the click handler on the render thread.
	 */
	@Test
	void embeddingIntoAnEmptyFileIsRefusedWithAnIoException() {
		assertThrows(IOException.class, () -> PngTextChunk.embed(new byte[0], Map.of("voxelcam:starred", "true")));
	}

	@Test
	void embeddingIntoAPngTruncatedInsideIhdrIsRefusedWithAnIoException() {
		byte[] truncated = java.util.Arrays.copyOf(minimalPng(), 20);

		assertThrows(IOException.class, () -> PngTextChunk.embed(truncated, Map.of("voxelcam:starred", "true")));
	}

	/** JPEG bytes under a {@code .png} name: the "length" embed used to trust is four bytes of JFIF. */
	@Test
	void embeddingIntoAFileThatIsNotAPngIsRefusedWithAnIoException() {
		byte[] jpeg = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 'J', 'F', 'I', 'F', 0x00, 0x01,
				0x01, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00 };

		assertThrows(IOException.class, () -> PngTextChunk.embed(jpeg, Map.of("voxelcam:starred", "true")));
	}

	/** Eight good signature bytes are not enough: the splice point is the end of IHDR, so a
	 * first chunk that is something else has no place to put a tag. */
	@Test
	void embeddingIntoAPngWhoseFirstChunkIsNotIhdrIsRefusedWithAnIoException() {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.writeBytes(new byte[] { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n' });
		out.writeBytes(chunk("IDAT", new byte[13]));
		out.writeBytes(chunk("IEND", new byte[0]));

		assertThrows(IOException.class, () -> PngTextChunk.embed(out.toByteArray(), Map.of("voxelcam:starred", "true")));
	}

	/**
	 * A minimal PNG tagged {@code voxelcam:x=1}, with one damaged chunk after the tag whose
	 * length field says {@code length}, and a {@code voxelcam:starred=false} tag after that.
	 */
	private static byte[] pngWithADamagedChunkOfLength(int length) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.writeBytes(new byte[] { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n' });
		out.writeBytes(chunk("IHDR", ihdrData(64, 32)));
		out.writeBytes(PngTextChunk.iTXtChunk("voxelcam:x", "1"));
		writeInt4(out, length);
		out.writeBytes("zzZz".getBytes(StandardCharsets.US_ASCII));
		out.writeBytes(new byte[4]);
		out.writeBytes(PngTextChunk.iTXtChunk("voxelcam:starred", "false"));
		out.writeBytes(chunk("IDAT", new byte[0]));
		out.writeBytes(chunk("IEND", new byte[0]));
		return out.toByteArray();
	}

	/**
	 * A length of -12 puts the next chunk exactly where this one started, so the walk never
	 * advanced. The manager reads every visible row's tags on the render thread, and a loop
	 * that never ends throws nothing for {@code read(File)} to catch. The timeout is
	 * preemptive so that a regression fails here instead of hanging the suite.
	 */
	@Test
	void readingStopsAtAChunkWithANegativeLengthRatherThanLoopingForever() {
		byte[] png = pngWithADamagedChunkOfLength(-12);

		Map<String, String> tags = assertTimeoutPreemptively(Duration.ofSeconds(5), () -> PngTextChunk.read(png));

		assertEquals(Map.of("voxelcam:x", "1"), tags);
	}

	/**
	 * Starring walks the same chunks to strip the old flag. From a damaged chunk on, the file
	 * is copied through untouched, the way a truncated chunk already was: what follows it
	 * cannot be trusted to be chunks at all.
	 */
	@Test
	void embeddingCopiesEverythingFromAChunkWithANegativeLengthThroughUnchanged() {
		byte[] png = pngWithADamagedChunkOfLength(-12);

		byte[] result = assertTimeoutPreemptively(Duration.ofSeconds(5),
				() -> PngTextChunk.embed(png, Map.of("voxelcam:starred", "true")));

		assertEquals(Map.of("voxelcam:starred", "true", "voxelcam:x", "1"), PngTextChunk.read(result));
		assertTrue(java.util.Arrays.equals(tailFromTheDamagedChunk(png), tailOfTheSameLength(result, png)));
	}

	/** -32 used to reach {@code write} with a negative count, the unchecked throw of a damaged star. */
	@Test
	void embeddingCopiesEverythingFromAChunkWhoseNegativeLengthPointsBackwardsThroughUnchanged() {
		byte[] png = pngWithADamagedChunkOfLength(-32);

		byte[] result = assertTimeoutPreemptively(Duration.ofSeconds(5),
				() -> PngTextChunk.embed(png, Map.of("voxelcam:starred", "true")));

		assertEquals(Map.of("voxelcam:starred", "true", "voxelcam:x", "1"), PngTextChunk.read(result));
		assertTrue(java.util.Arrays.equals(tailFromTheDamagedChunk(png), tailOfTheSameLength(result, png)));
	}

	private static byte[] tailFromTheDamagedChunk(byte[] png) {
		// signature + IHDR chunk, then the one whole tag ahead of the damage
		int damagedAt = 8 + 25 + PngTextChunk.iTXtChunk("voxelcam:x", "1").length;
		return java.util.Arrays.copyOfRange(png, damagedAt, png.length);
	}

	private static byte[] tailOfTheSameLength(byte[] result, byte[] png) {
		int length = tailFromTheDamagedChunk(png).length;
		return java.util.Arrays.copyOfRange(result, result.length - length, result.length);
	}

	@Test
	void aPngWithNoTagsReadsAsEmpty() {
		assertEquals(Map.of(), PngTextChunk.read(minimalPng()));
	}

	@Test
	void embedIsANoOpForNoEntries() throws IOException {
		byte[] original = minimalPng();

		assertTrue(java.util.Arrays.equals(original, PngTextChunk.embed(original, Map.of())));
	}

	/**
	 * A toggled flag gets re-embedded into the same file repeatedly. Without stripping the
	 * old chunk first, the new value would be shadowed forever by the original one.
	 */
	@Test
	void reEmbeddingTheSameKeyOverwritesRatherThanShadowing() throws IOException {
		byte[] once = PngTextChunk.embed(minimalPng(), Map.of("voxelcam:starred", "true"));
		byte[] twice = PngTextChunk.embed(once, Map.of("voxelcam:starred", "false"));

		assertEquals(Map.of("voxelcam:starred", "false"), PngTextChunk.read(twice));
	}

	@Test
	void reEmbeddingOneKeyLeavesUnrelatedKeysIntactAndUnduplicated() throws IOException {
		byte[] first = PngTextChunk.embed(minimalPng(),
				Map.of("voxelcam:dimension", "minecraft:overworld", "voxelcam:starred", "false"));
		byte[] second = PngTextChunk.embed(first, Map.of("voxelcam:starred", "true"));

		assertEquals(Map.of("voxelcam:dimension", "minecraft:overworld", "voxelcam:starred", "true"),
				PngTextChunk.read(second));
		// Exactly one chunk per keyword: not shadowed, and not duplicated either.
		assertEquals(1, countOccurrences(second, "voxelcam:starred"));
		assertEquals(1, countOccurrences(second, "voxelcam:dimension"));
	}

	/**
	 * A chunk without a null byte anywhere in its data has no keyword strip() can read, and
	 * {@code entries.keySet()} is sometimes an immutable {@code Map.of(...)} set — whose
	 * {@code contains(null)} throws NPE rather than returning false. The malformed chunk
	 * must be kept rather than crashing the embed.
	 */
	@Test
	void reEmbeddingOverAMalformedForeignChunkDoesNotThrow() throws IOException {
		byte[] malformed = chunk("iTXt", "nonullbyteshere".getBytes(StandardCharsets.US_ASCII));
		byte[] png = minimalPng();
		// Splice the malformed chunk in right after IHDR, same place embed() itself inserts.
		byte[] withMalformed = insertAfterIhdr(png, malformed);

		byte[] result = PngTextChunk.embed(withMalformed, Map.of("voxelcam:starred", "true"));

		assertEquals(Map.of("voxelcam:starred", "true"), PngTextChunk.read(result));
	}

	private static byte[] insertAfterIhdr(byte[] png, byte[] toInsert) {
		int ihdrEnd = 8 + 25; // signature + IHDR chunk (4 len + 4 type + 13 data + 4 crc)
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write(png, 0, ihdrEnd);
		out.writeBytes(toInsert);
		out.write(png, ihdrEnd, png.length - ihdrEnd);
		return out.toByteArray();
	}

	private static int countOccurrences(byte[] png, String needle) {
		byte[] pattern = needle.getBytes(StandardCharsets.UTF_8);
		int count = 0;
		outer:
		for (int i = 0; i + pattern.length <= png.length; i++) {
			for (int j = 0; j < pattern.length; j++) {
				if (png[i + j] != pattern[j]) {
					continue outer;
				}
			}
			count++;
		}
		return count;
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
