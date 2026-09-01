package com.thatapplefreak.voxelcam.client.screenshot;

import com.thatapplefreak.voxelcam.client.VoxelCamClient;
import java.io.File;
import java.io.IOException;
import java.util.Map;

/**
 * A per-file starred flag, embedded as a PNG {@code iTXt} chunk the same way
 * {@link CaptureContext} is — so it survives rename, copy, and share without a sidecar file
 * that {@code VoxelCamIO.rename()}/{@code delete()} would have to keep in sync.
 */
public final class Favorite {

	private static final String STARRED_KEY = "voxelcam:starred";

	private Favorite() {
	}

	public static boolean isStarred(File file) {
		return "true".equals(PngTextChunk.read(file).get(STARRED_KEY));
	}

	/**
	 * Rewrites the flag in place. Splicing a chunk bumps mtime like any write does, so the
	 * original is restored afterward — otherwise starring or unstarring a shot would jump it
	 * to the top of the list under the default newest-first sort. The flag itself is already
	 * durable by the time that restore runs, so a failure there is logged rather than thrown.
	 */
	public static void setStarred(File file, boolean starred) throws IOException {
		long modified = file.lastModified();
		PngTextChunk.embed(file, Map.of(STARRED_KEY, Boolean.toString(starred)));
		if (!file.setLastModified(modified)) {
			VoxelCamClient.LOGGER.warn("Could not restore mtime on {} after toggling its favorite flag", file);
		}
	}
}
