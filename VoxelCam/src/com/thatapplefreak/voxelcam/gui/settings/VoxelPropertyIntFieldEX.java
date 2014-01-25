package com.thatapplefreak.voxelcam.gui.settings;

import org.lwjgl.input.Keyboard;

import com.thevoxelbox.common.interfaces.IVoxelPropertyProvider;
import com.thevoxelbox.common.util.properties.VoxelPropertyIntField;


public class VoxelPropertyIntFieldEX extends VoxelPropertyIntField {

	public VoxelPropertyIntFieldEX(IVoxelPropertyProvider parent, String binding, String text, int xPos, int yPos, int fieldOffset) {
		super(parent, binding, text, xPos, yPos, fieldOffset);
		fieldWidth = 62;
	}

	@Override
	public void keyTyped(char keyChar, int keyCode) {
		if (this.focused) {
			if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER || keyCode == Keyboard.KEY_ESCAPE) {
				onLostFocus();
			}

			if (keyCode == Keyboard.KEY_BACK && fieldValue.length() > 0) {
				fieldValue = fieldValue.substring(0, fieldValue.length() - 1);
			}

			if (keyCode == Keyboard.KEY_PERIOD && allowedCharacters.indexOf('.') >= 0) {
				if (fieldValue.indexOf(keyChar) >= 0) {
					return;
				}
				if (fieldValue.length() == 0) {
					fieldValue += "0";
				}
			}

			String lastVal = fieldValue;
			if (allowedCharacters.indexOf(keyChar) >= 0 && fieldValue.length() < 6) {
				fieldValue += keyChar;
			}

			if (checkInvalidValue()) {
				fieldValue = lastVal;
			}
		}
	}

}
