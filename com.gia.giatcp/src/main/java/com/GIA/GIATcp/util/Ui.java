package com.GIA.GIATcp.util;

import com.ur.urcap.api.domain.userinteraction.keyboard.KeyboardInputCallback;
import com.ur.urcap.api.domain.userinteraction.keyboard.KeyboardInputFactory;
import com.ur.urcap.api.domain.userinteraction.keyboard.KeyboardNumberInput;
import com.ur.urcap.api.domain.userinteraction.keyboard.KeyboardTextInput;

import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JTextField;
import java.awt.Font;
import java.awt.Image;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;

/** Small Swing helpers: scaled icons and on-screen-keyboard wiring for fields. */
public final class Ui {

	private Ui() {
	}

	/** GIA wordmark for card headers, scaled from the high-res source keeping its
	 *  2.25:1 aspect (405x180) so it stays crisp and undistorted. */
	public static JLabel logo() {
		return iconLabel("/icons/logoGIA405_180.png", 153, 68);
	}

	/** A bold-font copy of a label, for form field captions and section headers. */
	public static JLabel bold(String text) {
		JLabel l = new JLabel(text);
		l.setFont(l.getFont().deriveFont(Font.BOLD));
		return l;
	}

	public static ImageIcon icon(String resource, int w, int h) {
		try {
			java.net.URL url = Ui.class.getResource(resource);
			if (url == null) {
				return null;
			}
			ImageIcon raw = new ImageIcon(url);
			Image scaled = raw.getImage().getScaledInstance(w, h, Image.SCALE_SMOOTH);
			return new ImageIcon(scaled);
		} catch (RuntimeException e) {
			return null;
		}
	}

	public static JLabel iconLabel(String resource, int w, int h) {
		ImageIcon ic = icon(resource, w, h);
		return ic == null ? new JLabel() : new JLabel(ic);
	}

	// Pops up the on-screen keypad on a tap (not focus), so the keypad's own close
	// does not re-trigger and loop. Each field is wired editable for the pendant.

	public static void wireDouble(final JTextField field, final KeyboardInputFactory kf, final Consumer<Double> onSet) {
		field.setEditable(false);
		field.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				KeyboardNumberInput<Double> input = kf.createDoubleKeypadInput();
				input.setInitialValue(parseDouble(field.getText()));
				input.show(field, new KeyboardInputCallback<Double>() {
					@Override
					public void onOk(Double value) {
						field.setText(String.valueOf(value));
						onSet.accept(value);
					}
				});
			}
		});
	}

	public static void wireInteger(final JTextField field, final KeyboardInputFactory kf, final Consumer<Integer> onSet) {
		field.setEditable(false);
		field.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				KeyboardNumberInput<Integer> input = kf.createIntegerKeypadInput();
				input.setInitialValue(parseInt(field.getText()));
				input.show(field, new KeyboardInputCallback<Integer>() {
					@Override
					public void onOk(Integer value) {
						field.setText(String.valueOf(value));
						onSet.accept(value);
					}
				});
			}
		});
	}

	public static void wireText(final JTextField field, final KeyboardInputFactory kf, final Consumer<String> onSet) {
		field.setEditable(false);
		field.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				KeyboardTextInput input = kf.createStringKeyboardInput();
				input.setInitialValue(field.getText());
				input.show(field, new KeyboardInputCallback<String>() {
					@Override
					public void onOk(String value) {
						field.setText(value);
						onSet.accept(value);
					}
				});
			}
		});
	}

	public static double parseDouble(String s) {
		try {
			return Double.parseDouble(s.trim());
		} catch (RuntimeException e) {
			return 0.0;
		}
	}

	public static int parseInt(String s) {
		try {
			return Integer.parseInt(s.trim());
		} catch (RuntimeException e) {
			return 0;
		}
	}
}
