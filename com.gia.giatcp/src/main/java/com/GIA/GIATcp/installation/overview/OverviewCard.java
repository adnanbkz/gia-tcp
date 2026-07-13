package com.GIA.GIATcp.installation.overview;

import com.GIA.GIATcp.installation.InstallationContribution;
import com.GIA.GIATcp.installation.InstallationView;
import com.GIA.GIATcp.installation.model.GiaTcp;
import com.GIA.GIATcp.locale.Texts;
import com.GIA.GIATcp.tcpcalibration.model.TCPCalibrationResult;
import com.GIA.GIATcp.util.Const;
import com.GIA.GIATcp.util.Ui;
import com.ur.urcap.api.domain.userinteraction.keyboard.KeyboardInputCallback;
import com.ur.urcap.api.domain.userinteraction.keyboard.KeyboardInputFactory;
import com.ur.urcap.api.domain.userinteraction.keyboard.KeyboardTextInput;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

public class OverviewCard extends JPanel {

	private final InstallationContribution contribution;
	private final InstallationView view;

	private final JComboBox<String> tcpCombo = new JComboBox<String>();
	private final JLabel badge = new JLabel();
	private final JLabel[] linVal = { new JLabel(), new JLabel(), new JLabel() };
	private final JLabel[] rotVal = { new JLabel(), new JLabel(), new JLabel() };
	private final JLabel sensorImage = new JLabel();
	private final JLabel status = new JLabel(" ");
	private final JButton calibrate = new JButton();
	private final JButton stop = new JButton();
	private final JButton rename = new JButton();

	private Texts t;
	private List<GiaTcp> tcps;
	private boolean updating;

	public OverviewCard(InstallationContribution contribution, InstallationView view) {
		this.contribution = contribution;
		this.view = view;
		buildUi();
	}

	private void buildUi() {
		t = contribution.texts();
		setLayout(new BorderLayout(8, 8));
		setBorder(new EmptyBorder(10, 10, 10, 10));

		// Header: title + GIA logo
		JPanel header = new JPanel(new BorderLayout());
		JLabel title = new JLabel(t.t("OVERVIEW_TITLE"));
		title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
		header.add(title, BorderLayout.WEST);
		header.add(Ui.logo(), BorderLayout.EAST);
		add(header, BorderLayout.NORTH);

		// Body: a left-aligned vertical stack, pinned to the top-left, mirroring the
		// CAPTRON overview layout (rows hug the left edge instead of floating centered).
		JPanel body = new JPanel();
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

		body.add(leftRow(new JLabel(t.t("SELECT_TCP"))));
		body.add(Box.createVerticalStrut(4));

		tcpCombo.setPreferredSize(new Dimension(200, 30));
		tcpCombo.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				onSelect();
			}
		});
		JButton setup = new JButton(t.t("BTN_SETUP"));
		setup.addActionListener(e -> view.showWizard());
		rename.setText(t.t("BTN_RENAME"));
		rename.addActionListener(e -> onRename());
		body.add(leftRow(tcpCombo, setup, rename));
		body.add(Box.createVerticalStrut(6));

		JButton add = new JButton("+");
		add.addActionListener(e -> onAdd());
		JButton remove = new JButton("-");
		remove.addActionListener(e -> onRemove());
		JButton settings = new JButton(t.t("BTN_SETTINGS"));
		settings.addActionListener(e -> view.showSettings());
		JButton diagnostics = new JButton(t.t("BTN_DIAGNOSTICS"));
		diagnostics.addActionListener(e -> view.showDiagnostics());
		body.add(leftRow(add, remove, settings, diagnostics));
		body.add(Box.createVerticalStrut(18));

		body.add(hsep());
		body.add(Box.createVerticalStrut(8));

		JLabel corrTitle = new JLabel(t.t("TCP_CORRECTION"));
		corrTitle.setFont(corrTitle.getFont().deriveFont(Font.BOLD));
		badge.setOpaque(true);
		badge.setBackground(new Color(0xF2, 0xE3, 0x6B));
		body.add(leftRow(corrTitle, badge));
		body.add(Box.createVerticalStrut(8));

		body.add(leftRow(buildCorrectionTable()));
		body.add(Box.createVerticalStrut(8));

		body.add(hsep());
		body.add(Box.createVerticalStrut(14));

		calibrate.setText(t.t("BTN_CALIBRATE_TEST"));
		calibrate.addActionListener(e -> onCalibrate());
		stop.setText(t.t("BTN_STOP"));
		// Stop both stacks: the legacy referencing (primary 30001) and the live test probe
		// (secondary 30002) — before, Stop only reached the legacy path, so a running
		// "Calibrar (test)" could not be aborted from the URCap. Off the EDT: socket I/O.
		stop.addActionListener(e -> new Thread(() -> {
			contribution.stopCalibration();
			contribution.stopTestCalibration();
		}, "gia-tcp-stop").start());
		body.add(leftRow(calibrate, stop));
		body.add(Box.createVerticalStrut(8));

		body.add(leftRow(status));

		// Keep the stack at the top-left; the sensor photo sits big on the right (as CAPTRON).
		JPanel bodyHolder = new JPanel(new BorderLayout());
		bodyHolder.add(body, BorderLayout.NORTH);
		add(bodyHolder, BorderLayout.CENTER);

		sensorImage.setVerticalAlignment(JLabel.TOP);
		sensorImage.setHorizontalAlignment(JLabel.CENTER);
		sensorImage.setBorder(new EmptyBorder(10, 20, 10, 30));
		add(sensorImage, BorderLayout.EAST);
	}

	/** A left-hugging FlowLayout row that keeps its natural height inside the
	 *  vertical BoxLayout body (so rows stack top-down instead of stretching). */
	private static JPanel leftRow(Component... comps) {
		JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		for (Component comp : comps) {
			row.add(comp);
		}
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
		return row;
	}

	/** Full-width horizontal rule that keeps its 2px height inside the BoxLayout column. */
	private static JSeparator hsep() {
		JSeparator s = new JSeparator(JSeparator.HORIZONTAL);
		s.setAlignmentX(Component.LEFT_ALIGNMENT);
		s.setMaximumSize(new Dimension(Integer.MAX_VALUE, 2));
		return s;
	}

	/** The X/Y/Z (mm) + RX/RY/RZ (deg) correction readout, laid out as a tidy grid
	 *  like CAPTRON's "TCP Correction" block. Values are filled in by {@link #updateDetails}. */
	private JPanel buildCorrectionTable() {
		JPanel tbl = new JPanel(new GridLayout(3, 6, 12, 3));
		String[] lin = { "X", "Y", "Z" };
		String[] rot = { "RX", "RY", "RZ" };
		Font mono = new Font("Monospaced", Font.PLAIN, 13);
		for (int i = 0; i < 3; i++) {
			tbl.add(Ui.bold(lin[i]));
			linVal[i].setFont(mono);
			tbl.add(linVal[i]);
			tbl.add(new JLabel("mm"));
			tbl.add(Ui.bold(rot[i]));
			rotVal[i].setFont(mono);
			tbl.add(rotVal[i]);
			tbl.add(new JLabel("°"));
		}
		return tbl;
	}

	public void refresh() {
		updating = true;
		tcps = contribution.getAllTcps();
		DefaultComboBoxModel<String> cmodel = new DefaultComboBoxModel<String>();
		int selectedIndex = -1;
		int selectedId = contribution.getStore().getSelectedId();
		for (int i = 0; i < tcps.size(); i++) {
			GiaTcp t = tcps.get(i);
			cmodel.addElement(t.name);
			if (t.id == selectedId) {
				selectedIndex = i;
			}
		}
		tcpCombo.setModel(cmodel);
		if (selectedIndex >= 0) {
			tcpCombo.setSelectedIndex(selectedIndex);
		}
		updating = false;
		updateDetails();
	}

	private void updateDetails() {
		GiaTcp sel = contribution.getSelectedTcp();
		if (sel == null) {
			badge.setText(" " + t.t("BADGE_NO_TCP") + " ");
			badge.setBackground(new Color(0xF2, 0xE3, 0x6B));
			setCorrection(new double[6]);
			sensorImage.setIcon(null);
			calibrate.setEnabled(false);
			stop.setEnabled(false);
			return;
		}
		badge.setText(" " + t.t(sel.calibrated ? "BADGE_CALIBRATED" : "BADGE_NOT_CALIBRATED") + " ");
		badge.setBackground(sel.calibrated ? new Color(0x9C, 0xD9, 0x8B) : new Color(0xF2, 0xE3, 0x6B));
		setCorrection(sel.correction);
		sensorImage.setIcon(Ui.icon(sel.variant.getImageResource(), 360, 360));
		// Only allow Calibrate/Stop once the TCP is fully set up (variant, IOs, reference, center).
		boolean ready = sel.isReadyForCalibration();
		calibrate.setEnabled(ready);
		stop.setEnabled(ready);
	}

	/** Fills the X/Y/Z (mm) + RX/RY/RZ (deg) cells from a pose correction (m + rad). */
	private void setCorrection(double[] cc) {
		for (int i = 0; i < 3; i++) {
			linVal[i].setText(String.format("%.2f", cc[i] * 1000));
			rotVal[i].setText(String.format("%.1f", Math.toDegrees(cc[i + 3])));
		}
	}

	private void onSelect() {
		if (updating || tcps == null) {
			return;
		}
		int idx = tcpCombo.getSelectedIndex();
		if (idx >= 0 && idx < tcps.size()) {
			contribution.setSelectedId(tcps.get(idx).id);
			updateDetails();
		}
	}

	private void onAdd() {
		int id = contribution.createTcp();
		if (id == 0) {
			status.setText(t.t("STATUS_MAX_TCP", Const.MAX_TCP));
			return;
		}
		refresh();
		view.showWizard();
	}

	private void onRemove() {
		GiaTcp sel = contribution.getSelectedTcp();
		if (sel == null) {
			return;
		}
		int answer = JOptionPane.showConfirmDialog(this,
				t.t("CONFIRM_DELETE_MSG", sel.name),
				t.t("CONFIRM_TITLE"), JOptionPane.YES_NO_OPTION);
		if (answer == JOptionPane.YES_OPTION) {
			contribution.deleteSelected();
			refresh();
		}
	}

	private void onRename() {
		final GiaTcp sel = contribution.getSelectedTcp();
		if (sel == null) {
			return;
		}
		KeyboardInputFactory kf = contribution.getApiProvider().getUserInterfaceAPI().getUserInteraction().getKeyboardInputFactory();
		KeyboardTextInput input = kf.createStringKeyboardInput();
		input.setInitialValue(sel.name);
		input.show(rename, new KeyboardInputCallback<String>() {
			@Override
			public void onOk(String value) {
				contribution.renameSelected(value);
				refresh();
			}
		});
	}

	/**
	 * Live test flow, mirroring CAPTRON's Overview → Calibrate: activate the reference
	 * TCP, guide the user's robot to the taught centre with the guarded move screen, and
	 * only then inject the probe motions (which are small and local to the fixture).
	 * Probing straight from an arbitrary parking pose would movel blind into the fork.
	 */
	private void onCalibrate() {
		final GiaTcp sel = contribution.getSelectedTcp();
		if (sel == null) {
			return;
		}
		if (!sel.isReadyForCalibration()) {
			status.setText(t.t("STATUS_NOT_SETUP"));
			return;
		}
		if (!contribution.isRefResolvable(sel.refTcp)) {
			status.setText(t.t("REF_ERR_REF_MISSING"));
			if (contribution.isErrInterrupt()) {
				JOptionPane.showMessageDialog(this, t.t("REF_ERR_REF_MISSING"),
						t.t("BTN_CALIBRATE_TEST"), JOptionPane.ERROR_MESSAGE);
			}
			return;
		}
		calibrate.setEnabled(false);
		new Thread(new Runnable() {
			@Override
			public void run() {
				if (Boolean.FALSE.equals(contribution.isInRemoteControl())) {
					SwingUtilities.invokeLater(new Runnable() {
						@Override
						public void run() {
							status.setText(t.t("REF_LOCAL_MODE"));
							calibrate.setEnabled(true);
							if (contribution.isErrInterrupt()) {
								JOptionPane.showMessageDialog(OverviewCard.this, t.t("REF_LOCAL_MODE"),
										t.t("BTN_CALIBRATE_TEST"), JOptionPane.WARNING_MESSAGE);
							}
						}
					});
					return;
				}
				contribution.activateReferenceTcp(sel);
				SwingUtilities.invokeLater(new Runnable() {
					@Override
					public void run() {
						status.setText(t.t("TEST_MOVE_CENTER"));
						// Re-enable before the move screen: backing out of it fires no
						// callback, so the button must stay usable for a retry.
						calibrate.setEnabled(true);
						contribution.moveToCenter(sel, new Runnable() {
							@Override
							public void run() {
								startTestProbe(sel);
							}
						});
					}
				});
			}
		}, "gia-tcp-test-calibrate").start();
	}

	/** Runs the actual probe once the robot sits at the taught centre. */
	private void startTestProbe(final GiaTcp sel) {
		status.setText(t.t("STATUS_CALIBRATING"));
		calibrate.setEnabled(false);
		new Thread(new Runnable() {
			@Override
			public void run() {
				final TCPCalibrationResult result = contribution.runTestCalibration(sel);
				SwingUtilities.invokeLater(new Runnable() {
					@Override
					public void run() {
						status.setText(testStatusText(result));
						calibrate.setEnabled(true);
						refresh();
						notifyTestResult(result);
					}
				});
			}
		}, "gia-tcp-test-probe").start();
	}

	/** Localized one-line description of a calibration status. */
	private String statusText(TCPCalibrationResult.Status s) {
		switch (s) {
			case OK:                       return t.t("TC_ST_OK");
			case NO_ROBOT_REPLY:           return t.t("TC_ST_NO_REPLY");
			case NO_INTERSECT:             return t.t("TC_ST_NO_INTERSECT");
			case SEARCH_Z_FAILED:          return t.t("TC_ST_SEARCH_Z");
			case OUT_OF_TOLERANCE:         return t.t("TC_ST_OUT_TOL");
			case ORIENTATION_NOT_POSSIBLE: return t.t("TC_ST_ORIENT");
			case INPUT_LOW_AT_CENTER:      return t.t("TC_ST_INPUT_LOW");
			case IMMERSE_FAILED:           return t.t("TC_ST_IMMERSE");
			case WRONG_POINT_COUNT:        return t.t("TC_ST_WRONG_EDGES");
			case INTERSECT_TOO_FAR:        return t.t("TC_ST_INTERSECT_FAR");
			case PERSIST_FAILED:           return t.t("TC_ST_PERSIST");
			default:                       return s.name();
		}
	}

	/** Popup so a failed test is never silent; gated by the error-handling setting. */
	private void notifyTestResult(TCPCalibrationResult r) {
		if (!contribution.isErrInterrupt()) {
			return;
		}
		String title = t.t("BTN_CALIBRATE_TEST");
		if (r.status == TCPCalibrationResult.Status.OK) {
			JOptionPane.showMessageDialog(this, t.t("TC_ST_OK"), title, JOptionPane.INFORMATION_MESSAGE);
		} else if (r.status == TCPCalibrationResult.Status.NO_ROBOT_REPLY) {
			JOptionPane.showMessageDialog(this, t.t("TC_ST_NO_REPLY") + "\n\n" + t.t("REF_NO_REPLY_HELP"),
					title, JOptionPane.WARNING_MESSAGE);
		} else {
			JOptionPane.showMessageDialog(this, statusText(r.status), title, JOptionPane.ERROR_MESSAGE);
		}
	}

	/** OK/error message plus the resulting corrected TCP reference, for the test button. */
	private String testStatusText(TCPCalibrationResult r) {
		String st = statusText(r.status);
		if (r.correctedTcp != null) {
			double[] tc = r.correctedTcp;
			return String.format(
					"<html>%s<br>%s:&nbsp; X %.2f&nbsp; Y %.2f&nbsp; Z %.2f mm&nbsp;&nbsp; "
							+ "RX %.2f&nbsp; RY %.2f&nbsp; RZ %.2f&deg;</html>",
					st, t.t("TC_CORRECTED"), tc[0] * 1000, tc[1] * 1000, tc[2] * 1000,
					Math.toDegrees(tc[3]), Math.toDegrees(tc[4]), Math.toDegrees(tc[5]));
		}
		return st;
	}
}
