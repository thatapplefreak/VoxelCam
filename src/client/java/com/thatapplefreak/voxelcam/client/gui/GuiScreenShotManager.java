package com.thatapplefreak.voxelcam.client.gui;

import com.thatapplefreak.voxelcam.client.VoxelCamConfig;
import com.thatapplefreak.voxelcam.client.screenshot.BurstFrame;
import com.thatapplefreak.voxelcam.client.screenshot.CaptureContext;
import com.thatapplefreak.voxelcam.client.screenshot.MomentTag;
import com.thatapplefreak.voxelcam.client.screenshot.ScreenshotImageCache;
import com.thatapplefreak.voxelcam.client.screenshot.SortMode;
import com.thatapplefreak.voxelcam.client.screenshot.VoxelCamIO;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Screenshot browser: searchable list on the left, large preview and details
 * on the right, actions along the bottom.
 */
public class GuiScreenShotManager extends Screen {

	private static final int MARGIN = 10;
	private static final int GAP = 6;
	private static final int BUTTON_HEIGHT = 20;
	private static final int CONTENT_TOP = 52;
	/** Wide enough for the longest label ("Name ↓") at the default GUI scale. */
	private static final int SORT_BUTTON_WIDTH = 54;
	/** Matches the search bar and sort button's own height. */
	private static final int FILTER_BUTTON_SIZE = 18;
	/** Preview : list width ratio. */
	private static final float GOLDEN_RATIO = 1.618034F;
	/** Below this a row cannot fit its thumbnail and a usable slice of the name. */
	private static final int MIN_LIST_WIDTH = 150;

	/** Reserved at the bottom of the preview only while the selection is a burst key; a plain
	 * screenshot's preview uses the whole area. */
	private static final int FILMSTRIP_HEIGHT = 40;
	private static final int FILMSTRIP_CELL_SIZE = 34;
	private static final int FILMSTRIP_GAP = 3;
	private static final int FILMSTRIP_SCROLL_BUTTON_WIDTH = 16;
	private static final int SET_AS_KEY_WIDTH = 88;
	/** The key badge's fill colour — the same gold the star badge and star icon use, so "this
	 * marks something about the file" reads as one visual language rather than two. */
	private static final int KEY_BADGE_COLOR = 0xEEFFD700;

	private final File screenshotsDir;

	private ScreenshotListWidget list;
	private EditBox searchBar;
	private Button sortButton;
	private StarToggleButton favoriteButton;
	private Button renameButton;
	private Button deleteButton;
	private Button shareButton;
	private Button setAsKeyButton;
	private Button filmstripScrollLeftButton;
	private Button filmstripScrollRightButton;

	private String searchText = "";
	private boolean favoritesOnly = false;
	private File selected;
	/** Which frame of the selected burst the preview is showing — null means the key itself.
	 * Meaningless (and always null) unless {@code selected} is currently a burst key. */
	private File previewFrame;
	/** Index of the first filmstrip cell currently visible. Meaningless (and always 0) unless
	 * a filmstrip is showing; clamped in {@link #updateButtonStates()} rather than wherever it
	 * changes, since a window resize can shrink how many cells fit without anything else
	 * touching this field. */
	private int filmstripScroll;
	/** A failed file action, shown in place of the details line until the selection moves. */
	private Component actionError;
	/** The list after the name filter and, if active, the favorites-only filter — what the
	 * row count and the list widget both have to agree on. */
	private List<File> visibleFiles = List.of();

	private int previewX;
	private int previewY;
	private int previewWidth;
	private int previewHeight;

	public GuiScreenShotManager(File screenshotsDir) {
		super(Component.translatable("voxelcam.screenshots"));
		this.screenshotsDir = screenshotsDir;
	}

	@Override
	protected void init() {
		// Re-synced on every init(), not just the first: harmless on a popup return or resize,
		// since toggleFavoritesOnly() already wrote the same value back here, and it is what
		// picks up whatever was on disk the very first time this screen ever opens.
		favoritesOnly = VoxelCamConfig.current().favoritesOnly;

		VoxelCamIO.updateScreenShotFilesList(screenshotsDir, searchText);
		List<File> files = filteredFiles();
		// Not just "keep it if it is still listed": init() runs again on the way back from
		// every popup, and after a rename this field still names the file that was renamed
		// away, so VoxelCamIO's selection is the only one that followed it.
		selected = VoxelCamIO.selectionFor(files, selected);
		// init() runs on every popup return, including one that just moved this exact file (a
		// group rename carries a burst's frames to new paths) — a previewFrame left pointing at
		// the old path would show nothing. Simplest correct answer: always fall back to the key.
		previewFrame = null;
		filmstripScroll = 0;

		// Split the content area so preview : list == phi : 1, which holds the two
		// panes in proportion at every GUI scale instead of the preview swallowing
		// all the extra room once a capped list stops growing.
		int contentWidth = width - MARGIN * 2 - GAP * 2;
		int listWidth = Math.max(MIN_LIST_WIDTH, Math.round(contentWidth / (1F + GOLDEN_RATIO)));
		int actionsY = height - MARGIN - BUTTON_HEIGHT;
		int listBottom = actionsY - GAP;

		int searchWidth = listWidth - SORT_BUTTON_WIDTH - GAP - FILTER_BUTTON_SIZE - GAP;
		searchBar = new EditBox(font, MARGIN, 26, searchWidth, 18, Component.translatable("voxelcam.search"));
		searchBar.setHint(Component.translatable("voxelcam.search").withStyle(ChatFormatting.DARK_GRAY));
		searchBar.setValue(searchText);
		searchBar.setResponder(this::onSearchChanged);
		addRenderableWidget(searchBar);

		addRenderableWidget(new StarToggleButton(MARGIN + searchWidth + GAP, 26,
				FILTER_BUTTON_SIZE, () -> favoritesOnly, b -> toggleFavoritesOnly(),
				Component.translatable("voxelcam.tooltip.favoritesonly")));

		sortButton = addRenderableWidget(Button.builder(Component.translatable(SortMode.current().labelKey()),
						b -> cycleSort())
				.bounds(MARGIN + searchWidth + GAP + FILTER_BUTTON_SIZE + GAP, 26, SORT_BUTTON_WIDTH, 18).build());
		sortButton.setTooltip(Tooltip.create(Component.translatable("voxelcam.tooltip.sort")));

		list = new ScreenshotListWidget(minecraft, listWidth, listBottom - CONTENT_TOP, CONTENT_TOP, this::onSelected);
		list.setX(MARGIN);
		addRenderableWidget(list);

		previewX = MARGIN + listWidth + GAP * 2;
		previewY = CONTENT_TOP;
		previewWidth = width - previewX - MARGIN;
		// Leave a line under the preview for the resolution/size/date details.
		previewHeight = listBottom - CONTENT_TOP - 14;

		// The preview side of the search row is otherwise empty at this height, so the gear
		// costs no existing control any width the way a spot lower down would have.
		Button settingsButton = addRenderableWidget(Button.builder(Component.translatable("voxelcam.settings.gear"),
						b -> minecraft.setScreenAndShow(new GuiSettings(this)))
				.bounds(previewX + previewWidth - FILTER_BUTTON_SIZE, 26, FILTER_BUTTON_SIZE, FILTER_BUTTON_SIZE).build());
		settingsButton.setTooltip(Tooltip.create(Component.translatable("voxelcam.tooltip.settings")));

		// The favorite toggle is a square icon button at the start of the row; rename/delete/
		// share then divide whatever's left three ways, same as before the toggle existed.
		// It asks the metadata cache rather than VoxelCamIO.isSelectedFavorite: the tint is
		// re-read on every extracted frame, and the flag lives in an iTXt chunk, so the
		// uncached answer costs a full read of the selected PNG per frame on the render
		// thread. Still VoxelCamIO's selection rather than this screen's field, so the two
		// paths cannot drift apart.
		favoriteButton = addRenderableWidget(new StarToggleButton(previewX, actionsY,
				BUTTON_HEIGHT, () -> ScreenshotMetadata.isStarred(VoxelCamIO.getSelectedPhoto()),
				b -> toggleFavoriteSelected(),
				Component.translatable("voxelcam.tooltip.favorite")));

		int actionsX = previewX + BUTTON_HEIGHT + GAP;
		// Item actions sit under the preview they act on; folder/close are global and
		// sit under the list, so destructive per-file actions are not next to "Done".
		// Widths divide the preview column rather than taking a floor, so a narrow
		// preview shrinks these buttons instead of pushing them over the list's.
		int actionWidth = (previewX + previewWidth - actionsX - GAP * 2) / 3;
		int deleteX = actionsX + actionWidth + GAP;
		int postX = actionsX + (actionWidth + GAP) * 2;

		renameButton = addRenderableWidget(button("voxelcam.rename", "voxelcam.tooltip.rename",
				b -> renameSelected(), actionsX, actionsY, actionWidth));
		deleteButton = addRenderableWidget(button("voxelcam.delete", "voxelcam.tooltip.delete",
				b -> minecraft.setScreenAndShow(DeletePopup.create(this)), deleteX, actionsY, actionWidth));
		// Absorbs the rounding remainder so the row ends flush with the preview.
		shareButton = addRenderableWidget(button("voxelcam.share", "voxelcam.tooltip.share",
				b -> shareSelected(), postX, actionsY, previewX + previewWidth - postX));

		// Fixed bounds regardless of whether the CURRENT selection has a filmstrip: visibility
		// toggles in updateButtonStates() instead, since the selection (and so whether one shows)
		// can change without a rebuild, from clicking a different row in the list.
		int filmstripTop = previewY + previewHeight - FILMSTRIP_HEIGHT;
		int filmstripButtonY = filmstripTop + (FILMSTRIP_HEIGHT - BUTTON_HEIGHT) / 2;
		setAsKeyButton = addRenderableWidget(button("voxelcam.burst.setaskey", "voxelcam.tooltip.setaskey",
				b -> setSelectedFrameAsKey(),
				previewX + previewWidth - SET_AS_KEY_WIDTH,
				filmstripButtonY,
				SET_AS_KEY_WIDTH));
		filmstripScrollLeftButton = addRenderableWidget(button("voxelcam.burst.scrollleft",
				"voxelcam.tooltip.burstscroll", b -> scrollFilmstrip(-1),
				previewX, filmstripButtonY, FILMSTRIP_SCROLL_BUTTON_WIDTH));
		filmstripScrollRightButton = addRenderableWidget(button("voxelcam.burst.scrollright",
				"voxelcam.tooltip.burstscroll", b -> scrollFilmstrip(1),
				previewX + previewWidth - SET_AS_KEY_WIDTH - GAP - FILMSTRIP_SCROLL_BUTTON_WIDTH,
				filmstripButtonY, FILMSTRIP_SCROLL_BUTTON_WIDTH));

		int leftWidth = Math.round((listWidth - GAP) / 2F);
		addRenderableWidget(button("voxelcam.openscreenshotsfolder.short", "voxelcam.tooltip.openfolder",
				b -> Util.getPlatform().openFile(screenshotsDir), MARGIN, actionsY, leftWidth));
		addRenderableWidget(button("voxelcam.done", null, b -> onClose(),
				MARGIN + leftWidth + GAP, actionsY, listWidth - leftWidth - GAP));

		// Populated only once the action buttons exist, since selecting an entry
		// immediately calls back into updateButtonStates().
		visibleFiles = files;
		list.setScreenshots(files, selected);
		updateButtonStates();
	}

	private Button button(String labelKey, String tooltipKey, Button.OnPress action,
			int x, int y, int buttonWidth) {
		Button widget = Button.builder(Component.translatable(labelKey), action)
				.bounds(x, y, buttonWidth, BUTTON_HEIGHT).build();
		if (tooltipKey != null) {
			widget.setTooltip(Tooltip.create(Component.translatable(tooltipKey)));
		}
		return widget;
	}

	private void onSearchChanged(String text) {
		if (text.equals(searchText)) {
			return;
		}
		searchText = text;
		// Only the list contents change while typing, so the search field itself is
		// left alone and keeps focus and cursor position.
		refreshFiles();
	}

	private void cycleSort() {
		SortMode.setCurrent(SortMode.current().next());
		sortButton.setMessage(Component.translatable(SortMode.current().labelKey()));
		VoxelCamConfig.saveCurrent();
		refreshFiles();
	}

	private void refreshFiles() {
		VoxelCamIO.updateScreenShotFilesList(screenshotsDir, searchText);
		List<File> files = filteredFiles();
		// Same resolution as init(), so a filter that empties the list and is then cleared
		// comes back to the file it was on rather than to the head of the list.
		selected = VoxelCamIO.selectionFor(files, selected);
		visibleFiles = files;
		list.setScreenshots(files, selected);
		updateButtonStates();
	}

	/**
	 * The name filter already lives in {@code VoxelCamIO}; favorites-only stays here instead,
	 * since it is opt-in and a filter nobody turned on should not read a whole folder of PNGs.
	 * It goes through {@link ScreenshotMetadata} rather than {@code Favorite} directly so the
	 * one full-file read per screenshot is shared with the row badges and the toggle button
	 * instead of being repeated on every keystroke.
	 */
	private List<File> filteredFiles() {
		List<File> files = VoxelCamIO.getScreenShotFiles();
		if (!favoritesOnly) {
			return files;
		}
		return files.stream().filter(ScreenshotMetadata::isStarred).toList();
	}

	private void toggleFavoritesOnly() {
		favoritesOnly = !favoritesOnly;
		VoxelCamConfig.current().favoritesOnly = favoritesOnly;
		VoxelCamConfig.saveCurrent();
		refreshFiles();
	}

	private void toggleFavoriteSelected() {
		if (selected == null) {
			return;
		}
		VoxelCamIO.toggleSelectedFavorite();
		// This forget() is what makes the toggle visible at all: the row badge, this
		// button's tint and the favorites-only filter all read the cached flag now, so
		// nothing would repaint without dropping the entry the toggle just invalidated.
		ScreenshotMetadata.forget(selected);
		// Only the favorites-only view can change list membership on a toggle; a plain
		// star/unstar leaves order and membership alone; the row and the button both pick
		// up the new icon on their own next frame, from the re-read that forget() forces.
		if (favoritesOnly) {
			refreshFiles();
		}
	}

	private void onSelected(File file) {
		// Only a move to a different file clears the message. init() re-reports the
		// current selection through setScreenshots, and that rebuild is exactly what
		// returning from a popup triggers, so clearing unconditionally would wipe a
		// failure before its first frame.
		if (!Objects.equals(selected, file)) {
			actionError = null;
			// A different row's filmstrip has nothing to do with the one previewFrame was
			// pointing into; without this, selecting burst A, clicking one of its frames, then
			// selecting burst B would still show A's frame under B's selection.
			previewFrame = null;
			filmstripScroll = 0;
		}
		selected = file;
		VoxelCamIO.selectPhoto(file);
		updateButtonStates();
	}

	/**
	 * Set by {@link DeletePopup} on its way out, since the popup itself is gone by the
	 * time anyone could read it.
	 */
	void reportDeleteResult(File file, boolean deleted) {
		actionError = deleted ? null : Component.translatable("voxelcam.delete.failed", file.getName());
	}

	private void updateButtonStates() {
		// The list can report a selection before init() has built the buttons.
		if (renameButton == null) {
			return;
		}
		boolean hasSelection = selected != null;
		renameButton.active = hasSelection;
		deleteButton.active = hasSelection;
		shareButton.active = hasSelection;
		favoriteButton.active = hasSelection;

		boolean hasFilmstrip = hasSelection && VoxelCamIO.burstFrameCount(selected) > 0;
		setAsKeyButton.visible = hasFilmstrip;
		// Active only once a non-key frame is actually highlighted: promoting the key to itself
		// is a no-op VoxelCamIO.setAsKey already refuses, so the button starts disabled instead
		// of accepting a click that does nothing.
		setAsKeyButton.active = hasFilmstrip && previewFrame != null;

		// The key plus its frames — burstFrameCount is already the free, cached count from the
		// listing pass, so this clamps without a second directory read the way filmstripCells()
		// (used only for drawing and hit-testing, where the tag-sorted order is worth the read)
		// would cost.
		int cellCount = hasFilmstrip ? VoxelCamIO.burstFrameCount(selected) + 1 : 0;
		clampFilmstripScroll(cellCount);
		filmstripScrollLeftButton.visible = hasFilmstrip;
		filmstripScrollRightButton.visible = hasFilmstrip;
		filmstripScrollLeftButton.active = hasFilmstrip && filmstripScroll > 0;
		filmstripScrollRightButton.active = hasFilmstrip && filmstripScroll + maxFilmstripCells() < cellCount;
	}

	/** Keeps the scroll offset in range as the visible cell count changes — a window resize
	 * shrinking {@link #maxFilmstripCells()}, or a different, shorter burst getting selected. */
	private void clampFilmstripScroll(int cellCount) {
		int maxScroll = Math.max(0, cellCount - maxFilmstripCells());
		filmstripScroll = Math.max(0, Math.min(filmstripScroll, maxScroll));
	}

	private void scrollFilmstrip(int cells) {
		filmstripScroll += cells;
		// Re-clamps immediately rather than waiting for the next frame's draw, so the button's
		// own active state (and a second click before a frame renders) sees the real range.
		updateButtonStates();
	}

	private void renameSelected() {
		if (selected != null) {
			minecraft.setScreenAndShow(new RenamePopup(this, screenshotsDir, selected));
		}
	}

	private void shareSelected() {
		if (selected != null) {
			minecraft.setScreenAndShow(new SharePopup(this, selected));
		}
	}

	/**
	 * Promotes whichever frame the filmstrip is currently showing to be the group's key —
	 * the iPhone "set key photo" gesture. Swaps the two files' contents rather than their tags,
	 * so capture order stays intact for a future export; see {@code VoxelCamIO.setAsKey}.
	 */
	private void setSelectedFrameAsKey() {
		if (selected == null || previewFrame == null) {
			return;
		}
		File frame = previewFrame;
		if (!VoxelCamIO.setAsKey(frame)) {
			actionError = Component.translatable("voxelcam.burst.setaskey.failed");
			return;
		}
		// Both paths now hold different bytes than a moment ago — dimensions, capture context
		// and the starred flag all have to be re-read, unlike a star toggle's forget(), which
		// only rewrites a chunk and leaves the rest of the file alone.
		ScreenshotMetadata.forgetFile(selected);
		ScreenshotMetadata.forgetFile(frame);
		// selected's path now holds what previewFrame was showing, so "no frame highlighted"
		// already points at the promoted content.
		previewFrame = null;
		updateButtonStates();
	}

	/**
	 * The cells a burst's filmstrip draws and hit-tests, ordered by original capture index
	 * rather than by which path currently holds the key. Shared between drawing and click
	 * handling so the two can never compute different geometry for the same row.
	 *
	 * <p>Sorting by the embedded {@code voxelcam:burstindex} tag rather than putting {@code
	 * selected} first is what keeps the strip in one stable, chronological order across a "Set
	 * as key" swap: the swap moves bytes between two paths without touching either file's tags,
	 * so the tag is the one thing here that still says which frame was captured when. Sorting
	 * by path (the old approach) made whichever frame was just promoted jump to the front,
	 * because {@code selected}'s path is always the key's, unconditionally.
	 */
	private List<File> filmstripCells() {
		if (selected == null || VoxelCamIO.burstFrameCount(selected) == 0) {
			return List.of();
		}
		List<File> frames = VoxelCamIO.burstFrames(screenshotsDir, selected);
		List<File> cells = new ArrayList<>(frames.size() + 1);
		cells.add(selected);
		cells.addAll(frames);
		cells.sort(Comparator.comparingInt(this::captureIndexOf));
		return cells;
	}

	/** A file with no burst tags at all — should not happen for anything this is asked about —
	 * sorts as if it were the earliest frame rather than throwing. */
	private int captureIndexOf(File file) {
		BurstFrame frame = ScreenshotMetadata.burstFrame(file);
		return frame != null ? frame.index() : 0;
	}

	/** How many cells the strip has room for between its two scroll buttons and the Set as key
	 * button — a burst longer than this needs {@link #filmstripScroll} to reach the rest. */
	private int maxFilmstripCells() {
		int scrollButtons = (FILMSTRIP_SCROLL_BUTTON_WIDTH + GAP) * 2;
		int available = previewWidth - SET_AS_KEY_WIDTH - GAP - scrollButtons;
		return Math.max(1, (available + FILMSTRIP_GAP) / (FILMSTRIP_CELL_SIZE + FILMSTRIP_GAP));
	}

	/** {@code visibleIndex} is relative to the left scroll button and to {@link
	 * #filmstripScroll} — callers add the scroll offset to a cell list index themselves. */
	private int filmstripCellX(int visibleIndex) {
		return previewX + FILMSTRIP_SCROLL_BUTTON_WIDTH + GAP + visibleIndex * (FILMSTRIP_CELL_SIZE + FILMSTRIP_GAP);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
		if (super.mouseClicked(event, doubled)) {
			return true;
		}
		return handleFilmstripClick(event.x(), event.y());
	}

	private boolean handleFilmstripClick(double mouseX, double mouseY) {
		List<File> cells = filmstripCells();
		if (cells.isEmpty()) {
			return false;
		}
		int cellY = previewY + previewHeight - FILMSTRIP_HEIGHT + (FILMSTRIP_HEIGHT - FILMSTRIP_CELL_SIZE) / 2;
		if (mouseY < cellY || mouseY >= cellY + FILMSTRIP_CELL_SIZE) {
			return false;
		}

		int shown = Math.min(cells.size() - filmstripScroll, maxFilmstripCells());
		for (int i = 0; i < shown; i++) {
			int x = filmstripCellX(i);
			if (mouseX >= x && mouseX < x + FILMSTRIP_CELL_SIZE) {
				File cell = cells.get(filmstripScroll + i);
				previewFrame = cell.equals(selected) ? null : cell;
				updateButtonStates();
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean keyPressed(KeyEvent input) {
		// Shortcuts only make sense when not typing a search query.
		if (!searchBar.isFocused() && selected != null) {
			switch (input.input()) {
				case GLFW.GLFW_KEY_DELETE, GLFW.GLFW_KEY_BACKSPACE -> {
					minecraft.setScreenAndShow(DeletePopup.create(this));
					return true;
				}
				case GLFW.GLFW_KEY_F2 -> {
					renameSelected();
					return true;
				}
				case GLFW.GLFW_KEY_LEFT -> {
					if (VoxelCamIO.burstFrameCount(selected) > 0) {
						scrollFilmstrip(-1);
						return true;
					}
				}
				case GLFW.GLFW_KEY_RIGHT -> {
					if (VoxelCamIO.burstFrameCount(selected) > 0) {
						scrollFilmstrip(1);
						return true;
					}
				}
				default -> {
				}
			}
		}
		return super.keyPressed(input);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
		// Textures decoded on the loader threads can only be uploaded here, on the
		// render thread. Extraction still runs on it, even though what it records
		// is submitted to the GPU later.
		ScreenshotImageCache.uploadPending();

		// The preview panel's dark background has to render before the widgets — including the
		// filmstrip's Set as key and scroll buttons, which sit inside the preview's own bounds —
		// or it paints over them afterward, at the same darkening alpha whether a button is
		// active or not, reading as "greyed out" for buttons that are actually fully usable.
		if (previewWidth > 0 && previewHeight > 0) {
			context.fill(previewX, previewY, previewX + previewWidth, previewY + previewHeight, 0x66000000);
		}

		// Do NOT call extractBackground() here: extractRenderStateWithTooltipAndSubtitles
		// already does, and its blur pass throws "Can only blur once per frame" if repeated.
		super.extractRenderState(context, mouseX, mouseY, delta);

		context.centeredText(font, title, width / 2, 14, 0xFFFFFFFF);

		// Right-aligned to clear the gear button's own corner rather than width - MARGIN, which
		// the gear button also anchors to — the two used to overlap directly.
		int countRight = previewX + previewWidth - FILTER_BUTTON_SIZE - GAP;
		int count = visibleFiles.size();
		Component countText = Component.translatable(count == 1 ? "voxelcam.count.one" : "voxelcam.count.many", count);
		context.text(font, countText.copy().withStyle(ChatFormatting.GRAY),
				countRight - font.width(countText), 31, 0xFFA0A0A0);

		extractPreview(context);
	}

	private void extractPreview(GuiGraphicsExtractor context) {
		if (previewWidth <= 0 || previewHeight <= 0) {
			return;
		}

		if (selected == null) {
			// "No screenshots yet" would be misleading here: the favorites filter can empty
			// the list out of a folder that has plenty, just none of them starred.
			String emptyKey = favoritesOnly ? "voxelcam.nofavorites" : "voxelcam.noscreenshots";
			context.centeredText(font, Component.translatable(emptyKey),
					previewX + previewWidth / 2, previewY + previewHeight / 2 - 4, 0xFF808080);
			return;
		}

		// A burst key reserves a band at the bottom of the preview for its filmstrip; a plain
		// screenshot's preview uses the whole area, exactly as before this feature existed.
		boolean hasFilmstrip = VoxelCamIO.burstFrameCount(selected) > 0;
		int imageHeight = previewHeight - (hasFilmstrip ? FILMSTRIP_HEIGHT : 0);
		// null means "the key" — the ordinary case, and the only one possible when there is no
		// filmstrip to have highlighted anything else in.
		File shown = previewFrame != null ? previewFrame : selected;

		ScreenshotImageCache.Loaded image = ScreenshotImageCache.get(shown, false);
		if (image == null) {
			Component status = ScreenshotImageCache.hasFailed(shown, false)
					? Component.translatable("voxelcam.loadfailed")
					: Component.translatable("voxelcam.loading");
			context.centeredText(font, status,
					previewX + previewWidth / 2, previewY + imageHeight / 2 - 4, 0xFF808080);
		} else {
			// Letterbox: scale to fit, preserving aspect ratio, then centre.
			float scale = Math.min((float) previewWidth / image.width(), (float) imageHeight / image.height());
			int drawWidth = Math.max(1, Math.round(image.width() * scale));
			int drawHeight = Math.max(1, Math.round(image.height() * scale));
			context.blit(RenderPipelines.GUI_TEXTURED, image.id(),
					previewX + (previewWidth - drawWidth) / 2, previewY + (imageHeight - drawHeight) / 2,
					0F, 0F, drawWidth, drawHeight, image.width(), image.height(), image.width(), image.height());
		}

		if (hasFilmstrip) {
			extractFilmstrip(context);
		}

		if (actionError != null) {
			// The details line is the only row free at every GUI scale, and it sits under
			// the preview of the file the failure is about.
			context.centeredText(font, actionError.copy().withStyle(ChatFormatting.RED),
					previewX + previewWidth / 2, previewY + previewHeight + 4, 0xFFFF5555);
			return;
		}

		// The list row already carries the capture time, so this line stays short
		// enough not to be trimmed: the filename (which the row may show as a
		// friendly time instead) plus the facts the row has no space for.
		StringBuilder details = new StringBuilder(selected.getName());
		ScreenshotMetadata.Dimensions size = ScreenshotMetadata.dimensions(selected);
		if (size != null) {
			details.append("  ·  ").append(size.width()).append('×').append(size.height());
		}
		details.append("  ·  ").append(ScreenshotMetadata.fileSize(selected));
		CaptureContext captureContext = ScreenshotMetadata.captureContext(selected);
		if (captureContext != null) {
			details.append("  ·  ").append(captureContext.describeLocation());
		}
		// Last, and only for the handful of screenshots that have one: an automatic capture is the
		// only kind whose existence needs explaining after the fact.
		MomentTag moment = ScreenshotMetadata.moment(selected);
		if (moment != null) {
			details.append("  ·  ").append(moment.describe().getString());
		}
		context.centeredText(font,
				Component.literal(font.plainSubstrByWidth(details.toString(), previewWidth)).withStyle(ChatFormatting.GRAY),
				previewX + previewWidth / 2, previewY + previewHeight + 4, 0xFFA0A0A0);
	}

	/**
	 * The key plus its frames, in capture order, as a strip of thumbnails along the bottom of
	 * the preview. A small gold "K" plate marks the key's cell; a 1px border marks whichever
	 * cell the main image above is currently showing — the two can disagree while "Set as key"
	 * is being considered, and agree again once it is clicked. Neither is a text caption: the
	 * 40px band has no room left for one once the cells and the two side buttons both fit in it.
	 * {@link #maxFilmstripCells()} bounds how many are drawn at once; {@link #filmstripScroll}
	 * (moved by the two scroll buttons, or Left/Right in {@link #keyPressed}) is what reaches
	 * the rest of a burst longer than that.
	 */
	private void extractFilmstrip(GuiGraphicsExtractor context) {
		List<File> cells = filmstripCells();
		int cellY = previewY + previewHeight - FILMSTRIP_HEIGHT + (FILMSTRIP_HEIGHT - FILMSTRIP_CELL_SIZE) / 2;
		File highlighted = previewFrame != null ? previewFrame : selected;

		int shown = Math.min(cells.size() - filmstripScroll, maxFilmstripCells());
		for (int i = 0; i < shown; i++) {
			File cell = cells.get(filmstripScroll + i);
			int x = filmstripCellX(i);
			context.fill(x, cellY, x + FILMSTRIP_CELL_SIZE, cellY + FILMSTRIP_CELL_SIZE, 0xFF1A1A1A);

			ScreenshotImageCache.Loaded thumb = ScreenshotImageCache.get(cell, true);
			if (thumb != null) {
				float scale = Math.min((float) FILMSTRIP_CELL_SIZE / thumb.width(),
						(float) FILMSTRIP_CELL_SIZE / thumb.height());
				int drawWidth = Math.max(1, Math.round(thumb.width() * scale));
				int drawHeight = Math.max(1, Math.round(thumb.height() * scale));
				context.blit(RenderPipelines.GUI_TEXTURED, thumb.id(),
						x + (FILMSTRIP_CELL_SIZE - drawWidth) / 2, cellY + (FILMSTRIP_CELL_SIZE - drawHeight) / 2,
						0F, 0F, drawWidth, drawHeight, thumb.width(), thumb.height(), thumb.width(), thumb.height());
			}

			// Marks which cell is the group's key, independent of the highlight border below:
			// "Set as key" points them at different cells right up until the player clicks it,
			// and without a persistent marker there was nothing distinguishing "the key" from
			// "whatever I'm previewing" once the two disagreed.
			if (cell.equals(selected)) {
				String badge = "K";
				int badgeWidth = font.width(badge);
				context.fill(x + 1, cellY + 1, x + 3 + badgeWidth, cellY + 10, KEY_BADGE_COLOR);
				context.text(font, Component.literal(badge), x + 2, cellY + 2, 0xFF101010);
			}

			if (cell.equals(highlighted)) {
				context.fill(x, cellY, x + FILMSTRIP_CELL_SIZE, cellY + 1, 0xFFFFFFFF);
				context.fill(x, cellY + FILMSTRIP_CELL_SIZE - 1, x + FILMSTRIP_CELL_SIZE, cellY + FILMSTRIP_CELL_SIZE, 0xFFFFFFFF);
				context.fill(x, cellY, x + 1, cellY + FILMSTRIP_CELL_SIZE, 0xFFFFFFFF);
				context.fill(x + FILMSTRIP_CELL_SIZE - 1, cellY, x + FILMSTRIP_CELL_SIZE, cellY + FILMSTRIP_CELL_SIZE, 0xFFFFFFFF);
			}
		}
	}

	/**
	 * Called on resize and when this screen is re-shown after a popup closes (the
	 * screen is already initialized, so init() would not run again on its own).
	 * A full rebuild both repositions widgets and picks up files renamed or
	 * deleted while the popup was open.
	 */
	@Override
	protected void repositionElements() {
		rebuildWidgets();
	}

	@Override
	public void onClose() {
		ScreenshotImageCache.releaseAll();
		ScreenshotMetadata.forgetAll();
		minecraft.setScreenAndShow(null);
	}
}
