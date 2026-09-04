package com.thatapplefreak.voxelcam.gametest;

import com.thatapplefreak.voxelcam.client.gui.RenamePopup;
import java.io.File;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.InputType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.TitleScreen;

/**
 * Where the rename dialog's focus actually lands, which no unit test can reach: the
 * whole mechanism lives in {@code Screen}, which needs a real client.
 *
 * The window is deliberately not resized to exercise the rebuild path as well. The
 * no-arg {@code setInitialFocus()} hook is the only focus call {@code rebuildWidgets()}
 * makes, so overriding it covers that path by construction, and resizing would disturb
 * the window size {@link CaptureTest} asserts against.
 */
public class RenamePopupFocusTest implements FabricClientGameTest {

	private static final String ORIGINAL_NAME = "rename-target";
	private static final String TYPED = "abc";

	@Override
	public void runTest(ClientGameTestContext context) {
		context.waitForScreen(TitleScreen.class);

		// The popup only reads this file's name, so nothing is written to disk.
		File dir = context.computeOnClient(client ->
				new File(client.gameDirectory, "voxelcam-gametest-shots"));

		context.setScreen(() -> {
			// Focus is only stolen when the last input was a keyboard one — with a mouse
			// last, the hook returns early and even the unfixed code passes. Set here
			// rather than by synthesizing a Tab press so no stray cursor event between
			// the press and init() can quietly make this test vacuous.
			Minecraft.getInstance().setLastInputType(InputType.KEYBOARD_TAB);
			return new RenamePopup(null, dir, new File(dir, ORIGINAL_NAME + ".png"));
		});
		context.waitForScreen(RenamePopup.class);
		context.waitTicks(5);

		assertNameFieldHasFocus(context);
		assertTypingReachesTheNameField(context);

		// Put the input type back so this test cannot change how a later one behaves,
		// whatever order the entrypoints end up in.
		context.runOnClient(client -> client.setLastInputType(InputType.MOUSE));
		context.setScreen(TitleScreen::new);
		context.waitForScreen(TitleScreen.class);
	}

	private static void assertNameFieldHasFocus(ClientGameTestContext context) {
		String focused = context.computeOnClient(client -> {
			GuiEventListener widget = client.gui.screen().getFocused();
			if (widget instanceof EditBox box) {
				return "field:" + box.getValue();
			}
			return widget == null ? "nothing" : widget.getClass().getSimpleName();
		});

		if (!("field:" + ORIGINAL_NAME).equals(focused)) {
			throw new AssertionError("rename popup should open focused on the name field, focused " + focused);
		}
	}

	/** The symptom a player actually sees: with focus on a button, typing goes nowhere. */
	private static void assertTypingReachesTheNameField(ClientGameTestContext context) {
		context.getInput().typeChars(TYPED);
		context.waitTicks(5);

		String value = context.computeOnClient(client ->
				client.gui.screen().getFocused() instanceof EditBox box ? box.getValue() : null);

		// Contains rather than an exact match: whether setValue leaves a selection that the
		// first character replaces is not something to pin the test to.
		if (value == null || !value.contains(TYPED)) {
			throw new AssertionError("typing should have reached the name field, it held " + value);
		}
	}
}
