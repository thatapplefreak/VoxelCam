package com.thatapplefreak.voxelcam.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import com.thatapplefreak.voxelcam.client.gui.GuiAutoCaptureSettings;
import com.thatapplefreak.voxelcam.client.gui.GuiScreenShotManager;
import com.thatapplefreak.voxelcam.client.gui.GuiSettings;
import com.thatapplefreak.voxelcam.client.gui.RenamePopup;
import com.thatapplefreak.voxelcam.client.gui.ScreenshotListWidget;
import com.thatapplefreak.voxelcam.client.gui.SharePopup;
import com.thatapplefreak.voxelcam.client.screenshot.VoxelCamIO;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/**
 * Where the manager's focus lands each time it is rebuilt, played with real keys and clicks only.
 *
 * <p>The manager rebuilds on every return from a popup and on a resize, and vanilla's no-arg
 * {@code setInitialFocus()} runs after each rebuild. After an arrow key or Tab it tabs onto the
 * search field, which switches the shortcuts off. Focusing the list by itself is not the answer
 * either: a list focused with no focused row is outside vanilla's focus path, so the next
 * rebuild's {@code clearFocus()} cannot reach it and the screen keeps a list that is gone, with
 * the arrows then moving a selection the highlight no longer shows.
 *
 * <p>So after every return this checks the three things a player sees: the screen's focus is
 * the live list and its highlighted row is the manager's selection, the first arrow press moves
 * it, and Delete or F2 acts on the highlighted file. And the other side of it: a search being
 * typed survives a rebuild, text and cursor both.
 */
public class ManagerFocusTest implements FabricClientGameTest {

	private ClientGameTestContext context;
	private File dir;

	@Override
	public void runTest(ClientGameTestContext context) {
		this.context = context;
		context.waitForScreen(TitleScreen.class);
		dir = context.computeOnClient(client -> new File(client.gameDirectory, "voxelcam-focus-shots"));
		writeFixtures();
		int[] window = context.computeOnClient(client ->
				new int[] {client.getWindow().getWidth(), client.getWindow().getHeight()});

		try {
			openManager();
			theOriginalJourney();
			cancelWithAClickThenArrows();
			clickEmptySpaceThenRenameAgain();
			confirmWithAClickThenArrows();
			renameConfirmedWithEnter();
			shareAndSettings();
			resizeWithTheListFocused(window);
			searchSurvivesARebuild(window);
			emptyListRebuild(window);
			freshOpenAfterKeyboard();
		} finally {
			context.getInput().resizeWindow(window[0], window[1]);
			context.waitTicks(5);
			context.runOnClient(client -> VoxelCamIO.selectPhoto(null));
			context.setScreen(TitleScreen::new);
			context.waitForScreen(TitleScreen.class);
			// A click, so the next test starts with the mouse as the last input.
			clickAt(4, 4);
		}
	}

	/** The playtest: click a row, Down, Up, F2, Escape, then Delete has to reach the confirm. */
	private void theOriginalJourney() {
		armWithArrows(2);
		key(InputConstants.KEY_F2);
		expectScreen(RenamePopup.class, "F2");
		key(InputConstants.KEY_ESCAPE);
		assertCoherentAndArrowsMove("Escape from rename");

		deleteNamesTheHighlightedFile("Delete after Escape from rename");
		key(InputConstants.KEY_ESCAPE);
		assertCoherentAndArrowsMove("Escape from the delete confirm");
	}

	/** Route (a): the confirm cancelled with the mouse, then arrows. */
	private void cancelWithAClickThenArrows() {
		armWithArrows(2);
		key(InputConstants.KEY_F2);
		key(InputConstants.KEY_ESCAPE);
		key(InputConstants.KEY_DELETE);
		expectScreen(ConfirmScreen.class, "Delete");
		clickLabel(translated("gui.cancel"));
		assertCoherentAndArrowsMove("a click on Cancel");
		for (int i = 0; i < 3; i++) {
			key(InputConstants.KEY_DOWN);
			assertCoherent("Down #" + (i + 1) + " after a click on Cancel");
		}
		deleteNamesTheHighlightedFile("Delete after three Downs");
		key(InputConstants.KEY_ESCAPE);
	}

	/** Route (b): back from rename, a click on empty space, rename again, then arrows. */
	private void clickEmptySpaceThenRenameAgain() {
		armWithArrows(2);
		key(InputConstants.KEY_F2);
		key(InputConstants.KEY_ESCAPE);
		int scale = context.computeOnClient(client -> client.getWindow().getGuiScale());
		clickAt(300 * scale, 8 * scale);
		key(InputConstants.KEY_F2);
		expectScreen(RenamePopup.class, "F2 after a click on empty space");
		key(InputConstants.KEY_ESCAPE);
		assertCoherentAndArrowsMove("Escape from rename with the mouse last");
		for (int i = 0; i < 3; i++) {
			key(InputConstants.KEY_DOWN);
			assertCoherent("Down #" + (i + 1) + " after a click on empty space");
		}
		String highlighted = highlightedName();
		key(InputConstants.KEY_F2);
		expectScreen(RenamePopup.class, "F2 after three Downs");
		String field = context.computeOnClient(client -> client.gui.screen().getFocused() instanceof EditBox box
				? box.getValue() : "no field");
		if (!highlighted.equals(field)) {
			throw new AssertionError("F2 should rename the highlighted " + highlighted + ", the dialog is for " + field);
		}
		key(InputConstants.KEY_ESCAPE);
	}

	/** Route (c): the confirm accepted with the mouse, then arrows must not reach the deleted file. */
	private void confirmWithAClickThenArrows() {
		armWithArrows(2);
		key(InputConstants.KEY_F2);
		key(InputConstants.KEY_ESCAPE);
		String doomed = highlightedName();
		key(InputConstants.KEY_DELETE);
		expectScreen(ConfirmScreen.class, "Delete");
		clickLabel(translated("voxelcam.delete"));
		expectScreen(GuiScreenShotManager.class, "a click on Delete");
		if (new File(dir, doomed + ".png").exists()) {
			throw new AssertionError("a click on Delete should have deleted " + doomed);
		}
		assertCoherentAndArrowsMove("a click on Delete");
		for (int i = 0; i < 2; i++) {
			key(InputConstants.KEY_UP);
			assertCoherent("Up #" + (i + 1) + " after a click on Delete");
			if (highlightedName().equals(doomed)) {
				throw new AssertionError("the arrows should not reach the deleted " + doomed);
			}
		}
	}

	private void renameConfirmedWithEnter() {
		armWithArrows(1);
		key(InputConstants.KEY_F2);
		expectScreen(RenamePopup.class, "F2");
		context.getInput().pressKey(InputConstants.KEY_END);
		context.getInput().typeChars("-x");
		key(InputConstants.KEY_RETURN);
		expectScreen(GuiScreenShotManager.class, "Enter in the rename dialog");
		if (!highlightedName().endsWith("-x")) {
			throw new AssertionError("the renamed file should stay highlighted, highlighted " + highlightedName());
		}
		assertCoherentAndArrowsMove("a rename confirmed with Enter");
	}

	private void shareAndSettings() {
		armWithArrows(1);
		clickLabel(translated("voxelcam.share"));
		expectScreen(SharePopup.class, "a click on Share");
		key(InputConstants.KEY_ESCAPE);
		assertCoherentAndArrowsMove("Escape from Share");

		clickLabel(translated("voxelcam.settings.gear"));
		expectScreen(GuiSettings.class, "a click on the gear");
		clickLabel(translated("voxelcam.settings.autocapture"));
		expectScreen(GuiAutoCaptureSettings.class, "a click on Automatic capture");
		key(InputConstants.KEY_ESCAPE);
		expectScreen(GuiSettings.class, "Escape from Automatic capture");
		key(InputConstants.KEY_ESCAPE);
		assertCoherentAndArrowsMove("Escape from Settings two levels deep");
		deleteNamesTheHighlightedFile("Delete after Settings");
		key(InputConstants.KEY_ESCAPE);
	}

	private void resizeWithTheListFocused(int[] window) {
		armWithArrows(2);
		context.getInput().resizeWindow(window[0] + 46, window[1] + 20);
		context.waitTicks(10);
		assertCoherentAndArrowsMove("a resize with the list focused");
		context.getInput().resizeWindow(window[0], window[1]);
		context.waitTicks(10);
	}

	/**
	 * Reached by Tab, so a keyboard input is the last one when the rebuild runs, and by a click.
	 * The cursor is moved off the end first, so a rebuild that put it back at the end would show.
	 */
	private void searchSurvivesARebuild(int[] window) {
		armWithArrows(2);
		for (int i = 0; i < 20 && !searchField().isFocused(); i++) {
			shiftTab();
		}
		typeResizeAndEdit("Tab", window[0] + 46, window[1] + 20);
		clearSearch();

		clickWidget(widget -> widget instanceof EditBox);
		typeResizeAndEdit("a click", window[0], window[1]);
		clearSearch();
		clickRow(1);
	}

	private void typeResizeAndEdit(String reachedBy, int width, int height) {
		if (!searchField().isFocused()) {
			throw new AssertionError("the search field should be reachable by " + reachedBy);
		}
		context.getInput().typeChars("sh");
		context.getInput().pressKey(InputConstants.KEY_LEFT);
		context.waitTicks(3);
		context.getInput().resizeWindow(width, height);
		context.waitTicks(10);
		context.getInput().typeChar('o');
		context.waitTicks(3);
		key(InputConstants.KEY_BACKSPACE);
		key(InputConstants.KEY_BACKSPACE);
		expectScreen(GuiScreenShotManager.class, "Backspace in a search reached by " + reachedBy + " and resized");
		String value = context.computeOnClient(client -> searchField().getValue());
		if (!"h".equals(value) || !searchField().isFocused()) {
			throw new AssertionError("typing after a resize should edit the search where the cursor was ('sh', Left, "
					+ "'o', two Backspaces leaves 'h'); field is '" + value + "', focused=" + searchField().isFocused());
		}
	}

	/** A search that matches nothing, focus moved off the field, then a rebuild. */
	private void emptyListRebuild(int[] window) {
		clickWidget(widget -> widget instanceof EditBox);
		context.getInput().typeChars("zzz");
		key(InputConstants.KEY_TAB);
		context.getInput().resizeWindow(window[0] + 46, window[1] + 20);
		context.waitTicks(10);
		String focus = context.computeOnClient(client -> {
			Screen screen = client.gui.screen();
			GuiEventListener focused = screen.getFocused();
			return focused == null || screen.children().contains(focused) ? "ok" : focused.getClass().getSimpleName();
		});
		if (!"ok".equals(focus)) {
			throw new AssertionError("a rebuild with an empty list should not leave focus on a widget that is gone: " + focus);
		}
		context.getInput().resizeWindow(window[0], window[1]);
		context.waitTicks(10);
		clearSearch();
		clickRow(1);
	}

	/** Tab on the title screen leaves a keyboard input last, which is when vanilla's hook acts. */
	private void freshOpenAfterKeyboard() {
		context.setScreen(TitleScreen::new);
		context.waitForScreen(TitleScreen.class);
		key(InputConstants.KEY_TAB);
		openManager();
		assertCoherent("a fresh open after Tab");
		deleteNamesTheHighlightedFile("Delete straight after a fresh open");
		key(InputConstants.KEY_ESCAPE);
		assertCoherentAndArrowsMove("Escape from the delete confirm after a fresh open");
	}

	// ------------------------------------------------------------------ checks

	/** Focus on the live list, on the row that is highlighted, which is the manager's selection. */
	private void assertCoherent(String after) {
		expectScreen(GuiScreenShotManager.class, after);
		String problem = context.computeOnClient(client -> {
			Screen screen = client.gui.screen();
			ScreenshotListWidget list = liveList(screen);
			if (screen.getFocused() != list) {
				return "focus is on " + describe(screen.getFocused()) + (screen.children().contains(screen.getFocused())
						? "" : " (not on the screen)") + ", not the list";
			}
			if (list.getSelected() == null || list.getFocused() != list.getSelected()) {
				return "the highlighted row is not the focused one";
			}
			File selected = VoxelCamIO.getSelectedPhoto();
			String highlighted = list.getSelected().getNarration().getString();
			if (selected == null || !selected.getName().equals(highlighted + ".png")) {
				return "highlighted " + highlighted + " but selected " + selected;
			}
			return null;
		});
		if (problem != null) {
			throw new AssertionError("after " + after + ": " + problem);
		}
	}

	/** And the first arrow press moves the selection, rather than only giving the list focus. */
	private void assertCoherentAndArrowsMove(String after) {
		assertCoherent(after);
		int before = highlightedIndex();
		int rows = context.computeOnClient(client -> liveList(client.gui.screen()).children().size());
		int arrow = before + 1 < rows ? InputConstants.KEY_DOWN : InputConstants.KEY_UP;
		int expected = before + 1 < rows ? before + 1 : before - 1;
		key(arrow);
		if (highlightedIndex() != expected) {
			throw new AssertionError("after " + after + " the first arrow press should move from row " + before
					+ " to " + expected + ", is on " + highlightedIndex());
		}
		assertCoherent("the first arrow press after " + after);
	}

	private void deleteNamesTheHighlightedFile(String step) {
		String highlighted = highlightedName();
		key(InputConstants.KEY_DELETE);
		expectScreen(ConfirmScreen.class, step);
		String message = context.computeOnClient(client -> confirmMessage(client.gui.screen()));
		if (!message.contains(highlighted + ".png")) {
			throw new AssertionError(step + " should ask about the highlighted " + highlighted + ", asks: " + message);
		}
	}

	private void expectScreen(Class<? extends Screen> expected, String after) {
		String screen = context.computeOnClient(client -> client.gui.screen() == null
				? "nothing" : client.gui.screen().getClass().getSimpleName());
		if (!expected.getSimpleName().equals(screen)) {
			throw new AssertionError("after " + after + " the screen should be " + expected.getSimpleName() + ", was " + screen);
		}
	}

	// ------------------------------------------------------------------ input

	private void openManager() {
		context.setScreen(() -> new GuiScreenShotManager(dir));
		context.waitForScreen(GuiScreenShotManager.class);
		context.waitTicks(10);
	}

	private void key(int key) {
		context.getInput().pressKey(key);
		context.waitTicks(4);
	}

	/** Clicks a row, then Down and Up: back on that row with an arrow as the last input. */
	private void armWithArrows(int row) {
		clickRow(row);
		key(InputConstants.KEY_DOWN);
		key(InputConstants.KEY_UP);
		assertCoherent("a click on row " + row + ", Down and Up");
	}

	/**
	 * The test input builds key events with no modifiers, so a held Shift never reaches
	 * {@code hasShiftDown()}. This is the same {@code KeyboardHandler.keyPress} it calls, with the
	 * Shift bit the platform would set.
	 */
	private void shiftTab() {
		context.runOnClient(client -> {
			KeyEvent tab = new KeyEvent(InputConstants.KEY_TAB, 9, 1);
			client.keyboardHandler.keyPress(client.getWindow().handle(), InputConstants.PRESS, tab);
			client.keyboardHandler.keyPress(client.getWindow().handle(), InputConstants.RELEASE, tab);
		});
		context.waitTicks(4);
	}

	private void clearSearch() {
		clickWidget(widget -> widget instanceof EditBox);
		for (int i = 0; i < 10 && !searchField().getValue().isEmpty(); i++) {
			context.getInput().pressKey(InputConstants.KEY_END);
			context.getInput().pressKey(InputConstants.KEY_BACKSPACE);
			context.waitTicks(2);
		}
	}

	private void clickAt(double rawX, double rawY) {
		context.getInput().setCursorPos(rawX, rawY);
		context.waitTicks(1);
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTicks(5);
	}

	private void clickLabel(String label) {
		clickWidget(widget -> widget.getMessage().getString().equals(label));
	}

	private void clickWidget(Predicate<AbstractWidget> which) {
		int[] at = context.computeOnClient(client -> {
			int scale = client.getWindow().getGuiScale();
			for (GuiEventListener child : client.gui.screen().children()) {
				if (child instanceof AbstractWidget widget && widget.visible && which.test(widget)) {
					return new int[] {(widget.getX() + widget.getWidth() / 2) * scale,
							(widget.getY() + widget.getHeight() / 2) * scale};
				}
			}
			throw new AssertionError("no such widget on " + client.gui.screen().getClass().getSimpleName());
		});
		clickAt(at[0], at[1]);
	}

	private void clickRow(int index) {
		int[] at = context.computeOnClient(client -> {
			ScreenshotListWidget list = liveList(client.gui.screen());
			int scale = client.getWindow().getGuiScale();
			return new int[] {(list.getRowLeft() + 60) * scale, (list.getRowTop(index) + 12) * scale};
		});
		clickAt(at[0], at[1]);
	}

	// ------------------------------------------------------------------ reading the screen

	private String highlightedName() {
		return context.computeOnClient(client -> {
			ScreenshotListWidget.ScreenshotEntry entry = liveList(client.gui.screen()).getSelected();
			return entry == null ? "nothing" : entry.getNarration().getString();
		});
	}

	private int highlightedIndex() {
		return context.computeOnClient(client -> {
			ScreenshotListWidget list = liveList(client.gui.screen());
			return list.children().indexOf(list.getSelected());
		});
	}

	private EditBox searchField() {
		return context.computeOnClient(client -> {
			for (GuiEventListener child : client.gui.screen().children()) {
				if (child instanceof EditBox box) {
					return box;
				}
			}
			throw new AssertionError("the manager should have a search field");
		});
	}

	private static ScreenshotListWidget liveList(Screen screen) {
		for (GuiEventListener child : screen.children()) {
			if (child instanceof ScreenshotListWidget list) {
				return list;
			}
		}
		throw new AssertionError("the manager should have a list");
	}

	private static String describe(GuiEventListener widget) {
		return widget == null ? "nothing" : widget.getClass().getSimpleName();
	}

	/** {@code ConfirmScreen} keeps its message private and has no getter. */
	private static String confirmMessage(Screen screen) {
		try {
			Field message = ConfirmScreen.class.getDeclaredField("message");
			message.setAccessible(true);
			return ((Component) message.get(screen)).getString();
		} catch (ReflectiveOperationException e) {
			throw new AssertionError("could not read the confirm's message", e);
		}
	}

	private String translated(String key) {
		return context.computeOnClient(client -> Component.translatable(key).getString());
	}

	private void writeFixtures() {
		try {
			ScreenshotFixtures.emptyFolder(dir);
			for (int i = 1; i <= 8; i++) {
				ScreenshotFixtures.writePng(new File(dir, String.format(java.util.Locale.ROOT, "focus-%02d.png", i)), 64, 36);
			}
		} catch (IOException e) {
			throw new AssertionError("could not write the focus fixtures", e);
		}
	}
}
