package com.GIA.GIATcp.installation.wizard;

import com.GIA.GIATcp.installation.InstallationContribution;
import com.GIA.GIATcp.installation.InstallationView;
import com.GIA.GIATcp.installation.calib.CalibrationResult;
import com.GIA.GIATcp.installation.model.GiaTcp;
import com.GIA.GIATcp.installation.model.IoOption;
import com.GIA.GIATcp.installation.model.TcpVariant;
import com.GIA.GIATcp.locale.Texts;
import com.GIA.GIATcp.util.Const;
import com.GIA.GIATcp.util.Ui;
import com.ur.urcap.api.domain.userinteraction.keyboard.KeyboardInputFactory;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.util.List;

public class SetupWizard extends JPanel {

	private static final String[] STEP_KEYS = {
			"STEP_VARIANT", "STEP_INPUTS", "STEP_REFERENCE", "STEP_CENTER",
			"STEP_PARAMS", "STEP_REFERENCING", "STEP_COMPLETE"
	};

	private final InstallationContribution contribution;
	private final InstallationView view;
	private final Texts t;

	private final CardLayout stepLayout = new CardLayout();
	private final JPanel stepPanel = new JPanel(stepLayout);
	private final JLabel stepTitle = new JLabel();
	private final JButton prev = new JButton();
	private final JButton next = new JButton();
	private final JButton cancel = new JButton();

	// Step controls
	private final JComboBox<String> variantCombo = new JComboBox<String>();
	private final JLabel variantImage = new JLabel();
	private final JComboBox<String> ioXCombo = new JComboBox<String>();
	private final JComboBox<String> ioYCombo = new JComboBox<String>();
	private final JComboBox<String> refCombo = new JComboBox<String>();
	private final JLabel centerStatus = new JLabel();
	private final JTextField fRadius = new JTextField(8);
	private final JTextField fSpeed = new JTextField(8);
	private final JTextField fAccel = new JTextField(8);
	private final JTextField fOverrun = new JTextField(8);
	private final JTextField fSearchZ = new JTextField(8);
	private final JCheckBox cInvertZ = new JCheckBox("Invert Z direction");
	private final JTextField fDiameter = new JTextField(8);
	private final JRadioButton rNoAngle = new JRadioButton("Don't adjust angle");
	private final JRadioButton rAngle = new JRadioButton("Adjust angle active");
	private final JTextField fIter = new JTextField(8);
	private final JTextField fOffZ = new JTextField(8);
	private final JTextField fAccuracy = new JTextField(8);
	private final JLabel radiusWarn = new JLabel();
	private final JLabel overrunWarn = new JLabel();
	private final JLabel refStatus = new JLabel();
	private final JButton refStart = new JButton();
	private final JLabel doneImage = new JLabel();

	private List<IoOption> ioOptions;
	private List<TcpVariant> variantOptions;
	private List<String> refNames;
	private GiaTcp tcp;
	private int step;
	private boolean updating;

	public SetupWizard(InstallationContribution contribution, InstallationView view) {
		this.contribution = contribution;
		this.view = view;
		this.t = contribution.texts();
		buildUi();
	}

	private void buildUi() {
		setLayout(new BorderLayout(8, 8));
		setBorder(new EmptyBorder(10, 10, 10, 10));

		JPanel header = new JPanel(new BorderLayout());
		JLabel hTitle = new JLabel(t.t("WIZARD_TITLE"));
		hTitle.setFont(hTitle.getFont().deriveFont(Font.BOLD, 16f));
		header.add(hTitle, BorderLayout.WEST);
		header.add(Ui.logo(), BorderLayout.EAST);
		add(header, BorderLayout.NORTH);

		stepPanel.add(buildVariantStep(), "0");
		stepPanel.add(buildIoStep(), "1");
		stepPanel.add(buildRefStep(), "2");
		stepPanel.add(buildCenterStep(), "3");
		stepPanel.add(buildParamStep(), "4");
		stepPanel.add(buildReferencingStep(), "5");
		stepPanel.add(buildCompleteStep(), "6");

		JPanel center = new JPanel(new BorderLayout(6, 6));
		stepTitle.setFont(stepTitle.getFont().deriveFont(Font.BOLD, 14f));
		center.add(stepTitle, BorderLayout.NORTH);
		// Scroll the step content so a tall step can never push the navigation bar
		// off-screen; the bar stays pinned and always reachable.
		JScrollPane stepScroll = new JScrollPane(stepPanel,
				JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		stepScroll.setBorder(null);
		stepScroll.getVerticalScrollBar().setUnitIncrement(16);
		center.add(stepScroll, BorderLayout.CENTER);
		add(center, BorderLayout.CENTER);

		// Navigation bar pinned at the bottom, separated by a top rule so it is always
		// visible: Cancel / Previous / Next (Next on the right).
		JPanel nav = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
		nav.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(0xCC, 0xCC, 0xCC)));
		prev.setText(t.t("BTN_PREVIOUS"));
		next.setText(t.t("BTN_NEXT"));
		cancel.setText(t.t("BTN_CANCEL"));
		prev.addActionListener(e -> goStep(step - 1));
		next.addActionListener(e -> onNext());
		cancel.addActionListener(e -> view.showOverview());
		nav.add(cancel);
		nav.add(prev);
		nav.add(next);
		add(nav, BorderLayout.SOUTH);
	}

	// ---------- steps ----------

	private JPanel buildVariantStep() {
		JPanel p = new JPanel(new BorderLayout(6, 6));
		variantImage.setHorizontalAlignment(JLabel.CENTER);
		variantCombo.addActionListener(e -> {
			int i = variantCombo.getSelectedIndex();
			if (!updating && tcp != null && variantOptions != null && i >= 0) {
				tcp.variant = variantOptions.get(i);
				variantImage.setIcon(Ui.icon(tcp.variant.getImageResource(), 320, 320));
			}
		});
		p.add(variantCombo, BorderLayout.NORTH);
		p.add(variantImage, BorderLayout.CENTER);
		return p;
	}

	private JPanel buildIoStep() {
		JPanel p = new JPanel(new GridBagLayout());
		GridBagConstraints c = gbc();
		c.gridx = 0;
		c.gridy = 0;
		p.add(new JLabel(t.t("WIZ_INPUT_X")), c);
		c.gridx = 1;
		ioXCombo.setPreferredSize(new Dimension(220, 30));
		p.add(ioXCombo, c);
		c.gridx = 0;
		c.gridy = 1;
		p.add(new JLabel(t.t("WIZ_INPUT_Y")), c);
		c.gridx = 1;
		ioYCombo.setPreferredSize(new Dimension(220, 30));
		p.add(ioYCombo, c);
		ioXCombo.addActionListener(e -> {
			int i = ioXCombo.getSelectedIndex();
			if (!updating && tcp != null && ioOptions != null && i >= 0) {
				tcp.ioX = ioOptions.get(i).code;
			}
		});
		ioYCombo.addActionListener(e -> {
			int i = ioYCombo.getSelectedIndex();
			if (!updating && tcp != null && ioOptions != null && i >= 0) {
				tcp.ioY = ioOptions.get(i).code;
			}
		});
		return p;
	}

	private JPanel buildRefStep() {
		JPanel p = new JPanel(new BorderLayout(6, 6));
		// Keep the combo at its natural size in a left-hugging row; placing it in CENTER
		// would stretch it to fill the whole step (giant dropdown).
		JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		row.add(new JLabel(t.t("WIZ_REFERENCE_TCP")));
		refCombo.setPreferredSize(new Dimension(280, 30));
		refCombo.addActionListener(e -> {
			int i = refCombo.getSelectedIndex();
			if (!updating && tcp != null && refNames != null && i >= 0) {
				tcp.refTcp = refNames.get(i);
			}
		});
		row.add(refCombo);
		p.add(row, BorderLayout.NORTH);
		return p;
	}

	private JPanel buildCenterStep() {
		JPanel p = new JPanel(new BorderLayout(6, 6));
		JPanel top = new JPanel();
		top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));

		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
		JButton setCenter = new JButton(t.t("WIZ_SET_CENTER"));
		setCenter.addActionListener(e -> {
			if (tcp != null) {
				contribution.teachCenter(tcp);
				updateCenterStatus();
			}
		});
		JButton moveHere = new JButton(t.t("WIZ_MOVE_HERE"));
		moveHere.addActionListener(e -> {
			if (tcp != null) {
				contribution.moveToCenter(tcp);
			}
		});
		buttons.add(setCenter);
		buttons.add(moveHere);
		buttons.setMaximumSize(new Dimension(Integer.MAX_VALUE, buttons.getPreferredSize().height));
		top.add(buttons);
		top.add(Box.createVerticalStrut(8));
		centerStatus.setAlignmentX(Component.LEFT_ALIGNMENT);
		top.add(centerStatus);

		// Keep buttons + status together at the top instead of letting the status float
		// in the middle of the empty step area.
		p.add(top, BorderLayout.NORTH);
		return p;
	}

	private JPanel buildParamStep() {
		KeyboardInputFactory kf = contribution.getApiProvider().getUserInterfaceAPI().getUserInteraction().getKeyboardInputFactory();
		JPanel grid = new JPanel(new GridLayout(0, 2, 6, 4));
		addField(grid, t.t("WIZ_RADIUS"), fRadius);
		addField(grid, t.t("WIZ_SPEED"), fSpeed);
		addField(grid, t.t("WIZ_ACCEL"), fAccel);
		addField(grid, t.t("WIZ_OVERRUN"), fOverrun);
		addField(grid, t.t("WIZ_SEARCH_Z"), fSearchZ);
		grid.add(new JLabel(""));
		cInvertZ.setText(t.t("WIZ_INVERT_Z"));
		grid.add(cInvertZ);
		addField(grid, t.t("WIZ_DIAMETER"), fDiameter);

		rNoAngle.setText(t.t("WIZ_NO_ANGLE"));
		rAngle.setText(t.t("WIZ_ANGLE"));
		ButtonGroup g = new ButtonGroup();
		g.add(rNoAngle);
		g.add(rAngle);
		grid.add(rNoAngle);
		grid.add(rAngle);
		addField(grid, t.t("WIZ_ITERATOR"), fIter);
		addField(grid, t.t("WIZ_OFFSET_Z"), fOffZ);
		addField(grid, t.t("WIZ_ACCURACY"), fAccuracy);

		// CAPTRON-style keyboard limits: out-of-range input is clamped and written back to
		// the field, so a typo fails at the field instead of far away during the probe.
		Ui.wireDouble(fRadius, kf, v -> {
			tcp.params.radiusMm = clamp(fRadius, v, Const.MIN_RADIUS_MM, Const.MAX_RADIUS_MM);
			updateRadiusWarning();
		});
		Ui.wireDouble(fSpeed, kf, v -> tcp.params.speedMmS = clamp(fSpeed, v, Const.MIN_SPEED_MM_S, Const.MAX_SPEED_MM_S));
		Ui.wireDouble(fAccel, kf, v -> tcp.params.accelMmS2 = clamp(fAccel, v, Const.MIN_ACCEL_MM_S2, Const.MAX_ACCEL_MM_S2));
		Ui.wireDouble(fOverrun, kf, v -> {
			tcp.params.overrunDeg = clamp(fOverrun, v, Const.MIN_OVERRUN_DEG, Const.MAX_OVERRUN_DEG);
			updateRadiusWarning();
		});
		Ui.wireDouble(fSearchZ, kf, v -> tcp.params.searchZMm = clamp(fSearchZ, v, Const.MIN_SEARCHZ_MM, Const.MAX_SEARCHZ_MM));
		Ui.wireDouble(fDiameter, kf, v -> {
			tcp.params.realDiameterMm = clamp(fDiameter, v, Const.MIN_REALDIAM_MM, Const.MAX_REALDIAM_MM);
			updateRadiusWarning();
		});
		Ui.wireInteger(fIter, kf, v -> {
			int c = Math.max(Const.MIN_ITER, Math.min(Const.MAX_ITER, v));
			if (c != v) {
				fIter.setText(String.valueOf(c));
			}
			tcp.params.iterator = c;
		});
		Ui.wireDouble(fOffZ, kf, v -> tcp.params.offsetZMm = clamp(fOffZ, v, Const.MIN_OFFZ_MM, Const.MAX_OFFZ_MM));
		Ui.wireDouble(fAccuracy, kf, v -> tcp.params.accuracyDeg = clamp(fAccuracy, v, Const.MIN_ACCURACY_DEG, Const.MAX_ACCURACY_DEG));
		cInvertZ.addActionListener(e -> tcp.params.invertZ = cInvertZ.isSelected());
		rNoAngle.addActionListener(e -> tcp.params.adjustAngle = false);
		rAngle.addActionListener(e -> tcp.params.adjustAngle = true);

		// Geometry rules that depend on the tool: radius floor (Ø/2 + beam/teach margin)
		// / sin 45, and the overrun needed to guarantee 4 edges per beam in any phase.
		radiusWarn.setForeground(new java.awt.Color(0xB0, 0x6A, 0x00));
		radiusWarn.setVisible(false);
		overrunWarn.setForeground(new java.awt.Color(0xB0, 0x6A, 0x00));
		overrunWarn.setVisible(false);
		JPanel warns = new JPanel();
		warns.setLayout(new BoxLayout(warns, BoxLayout.Y_AXIS));
		radiusWarn.setAlignmentX(Component.LEFT_ALIGNMENT);
		overrunWarn.setAlignmentX(Component.LEFT_ALIGNMENT);
		warns.add(radiusWarn);
		warns.add(overrunWarn);
		JPanel p = new JPanel(new BorderLayout(0, 8));
		p.add(grid, BorderLayout.NORTH);
		p.add(warns, BorderLayout.SOUTH);
		return p;
	}

	/** Clamps to [min, max]; when clamped, the corrected value is written back to the field. */
	private static double clamp(JTextField field, double v, double min, double max) {
		double c = Math.max(min, Math.min(max, v));
		if (c != v) {
			field.setText(String.valueOf(c));
		}
		return c;
	}

	/** Shows the physics rules the current radius/overrun/Ø combination violates. */
	private void updateRadiusWarning() {
		if (tcp == null) {
			return;
		}
		double minR = Const.minRadiusForTool(tcp.params.realDiameterMm);
		if (tcp.params.radiusMm < minR) {
			radiusWarn.setText(t.t("WIZ_RADIUS_WARN", String.format("%.1f", minR)));
			radiusWarn.setVisible(true);
		} else {
			radiusWarn.setVisible(false);
		}
		double minO = Const.minOverrunForTool(tcp.params.realDiameterMm, tcp.params.radiusMm);
		if (tcp.params.overrunDeg < minO) {
			overrunWarn.setText(t.t("WIZ_OVERRUN_WARN", String.format("%.0f", Math.ceil(minO))));
			overrunWarn.setVisible(true);
		} else {
			overrunWarn.setVisible(false);
		}
	}

	private JPanel buildReferencingStep() {
		JPanel p = new JPanel(new BorderLayout(6, 6));
		JPanel top = new JPanel();
		top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));

		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
		refStart.setText(t.t("WIZ_START_REFERENCING"));
		refStart.addActionListener(e -> onReferencing());
		JButton stop = new JButton(t.t("BTN_STOP"));
		stop.addActionListener(e -> {
			cancelPassiveReferencing();
			// Off the EDT: stopping opens a socket to the robot and would freeze the
			// pendant exactly when the user is trying to stop (same fix as OverviewCard).
			new Thread(contribution::stopCalibration, "gia-tcp-wizard-stop").start();
		});
		buttons.add(refStart);
		buttons.add(stop);
		buttons.setMaximumSize(new Dimension(Integer.MAX_VALUE, buttons.getPreferredSize().height));
		top.add(buttons);
		top.add(Box.createVerticalStrut(8));
		refStatus.setAlignmentX(Component.LEFT_ALIGNMENT);
		top.add(refStatus);
		top.add(Box.createVerticalStrut(8));
		JLabel localHint = new JLabel(t.t("WIZ_REF_LOCAL_HINT"));
		localHint.setAlignmentX(Component.LEFT_ALIGNMENT);
		top.add(localHint);

		p.add(top, BorderLayout.NORTH);
		return p;
	}

	private JPanel buildCompleteStep() {
		JPanel p = new JPanel(new BorderLayout());
		JButton finish = new JButton(t.t("BTN_FINISH"));
		finish.addActionListener(e -> {
			save();
			view.showOverview();
		});
		p.add(doneImage, BorderLayout.CENTER);
		JPanel south = new JPanel();
		south.add(finish);
		p.add(south, BorderLayout.SOUTH);
		return p;
	}

	// ---------- lifecycle ----------

	public void startForSelected() {
		GiaTcp selected = contribution.getSelectedTcp();
		if (selected == null) {
			view.showOverview();
			return;
		}
		this.tcp = selected;
		updating = true;
		loadOptions();
		loadValues();
		updating = false;
		goStep(0);
	}

	public void refresh() {
		if (tcp != null) {
			updating = true;
			loadValues();
			updating = false;
		}
	}

	private void loadOptions() {
		variantOptions = new java.util.ArrayList<TcpVariant>();
		DefaultComboBoxModel<String> vm = new DefaultComboBoxModel<String>();
		for (TcpVariant v : TcpVariant.values()) {
			variantOptions.add(v);
			vm.addElement(v.getDisplayName());
		}
		variantCombo.setModel(vm);

		ioOptions = contribution.getDigitalInputs();
		DefaultComboBoxModel<String> xm = new DefaultComboBoxModel<String>();
		DefaultComboBoxModel<String> ym = new DefaultComboBoxModel<String>();
		for (IoOption io : ioOptions) {
			xm.addElement(io.label);
			ym.addElement(io.label);
		}
		ioXCombo.setModel(xm);
		ioYCombo.setModel(ym);

		refNames = contribution.getReferenceTcpNames();
		DefaultComboBoxModel<String> rm = new DefaultComboBoxModel<String>();
		for (String n : refNames) {
			rm.addElement(n);
		}
		refCombo.setModel(rm);
	}

	private void loadValues() {
		variantCombo.setSelectedIndex(Math.max(0, variantOptions.indexOf(tcp.variant)));
		variantImage.setIcon(Ui.icon(tcp.variant.getImageResource(), 320, 320));
		selectIoByCode(ioXCombo, tcp.ioX);
		selectIoByCode(ioYCombo, tcp.ioY);
		if (refNames != null && tcp.refTcp != null) {
			int idx = refNames.indexOf(tcp.refTcp);
			if (idx >= 0) {
				refCombo.setSelectedIndex(idx);
			}
		}
		updateCenterStatus();
		fRadius.setText(String.valueOf(tcp.params.radiusMm));
		fSpeed.setText(String.valueOf(tcp.params.speedMmS));
		fAccel.setText(String.valueOf(tcp.params.accelMmS2));
		fOverrun.setText(String.valueOf(tcp.params.overrunDeg));
		fSearchZ.setText(String.valueOf(tcp.params.searchZMm));
		cInvertZ.setSelected(tcp.params.invertZ);
		fDiameter.setText(String.valueOf(tcp.params.realDiameterMm));
		rNoAngle.setSelected(!tcp.params.adjustAngle);
		rAngle.setSelected(tcp.params.adjustAngle);
		fIter.setText(String.valueOf(tcp.params.iterator));
		fOffZ.setText(String.valueOf(tcp.params.offsetZMm));
		fAccuracy.setText(String.valueOf(tcp.params.accuracyDeg));
		updateRadiusWarning();
		doneImage.setIcon(Ui.icon(tcp.variant.getDoneImageResource(), 220, 240));
	}

	private void selectIoByCode(JComboBox<String> combo, int code) {
		if (ioOptions == null) {
			return;
		}
		for (int i = 0; i < ioOptions.size(); i++) {
			if (ioOptions.get(i).code == code) {
				combo.setSelectedIndex(i);
				return;
			}
		}
	}

	private void updateCenterStatus() {
		centerStatus.setText(t.t(tcp != null && tcp.isCenterTaught()
				? "WIZ_CENTER_TAUGHT" : "WIZ_CENTER_NOT_TAUGHT"));
	}

	private void onNext() {
		if (step < STEP_KEYS.length - 1) {
			save();
			goStep(step + 1);
		}
	}

	private void goStep(int newStep) {
		step = Math.max(0, Math.min(STEP_KEYS.length - 1, newStep));
		stepTitle.setText(t.t("STEP_LABEL", step + 1, STEP_KEYS.length, t.t(STEP_KEYS[step])));
		stepLayout.show(stepPanel, String.valueOf(step));
		prev.setEnabled(step > 0);
		next.setEnabled(step < STEP_KEYS.length - 1);
	}

	private void onReferencing() {
		if (tcp == null) {
			return;
		}
		save();
		if (!tcp.isReadyForCalibration()) {
			String issue = wizardIssueKey();
			refStatus.setText(issue != null ? t.t("TC_ISSUE_PREFIX", t.t(issue)) : t.t("WIZ_NOT_READY"));
			return;
		}
		if (!contribution.isRefResolvable(tcp.refTcp)) {
			refStatus.setText(t.t("REF_ERR_REF_MISSING"));
			if (contribution.isErrInterrupt()) {
				JOptionPane.showMessageDialog(this, t.t("REF_ERR_REF_MISSING"),
						t.t("WIZ_START_REFERENCING"), JOptionPane.ERROR_MESSAGE);
			}
			return;
		}
		refStatus.setText(t.t("WIZ_REFERENCING"));
		refStart.setEnabled(false);
		new Thread(new Runnable() {
			@Override
			public void run() {
				if (Boolean.FALSE.equals(contribution.isInRemoteControl())) {
					// Local mode: injection is blocked, so switch to PASSIVE referencing —
					// wait for a program-run (Play) with persist to feed the 5512 sink.
					SwingUtilities.invokeLater(new Runnable() {
						@Override
						public void run() {
							startPassiveReferencing();
						}
					});
					return;
				}
				final CalibrationResult result = contribution.runCalibration(tcp);
				SwingUtilities.invokeLater(new Runnable() {
					@Override
					public void run() {
						refStatus.setText(t.t(result.statusKey()));
						if (result.isSuccess()) {
							tcp = contribution.getSelectedTcp();
						}
						refStart.setEnabled(true);
						notifyResult(result);
					}
				});
			}
		}, "gia-tcp-referencing").start();
	}

	/**
	 * Passive referencing for Local mode (the only path on robots that cannot use Remote
	 * Control, e.g. 3PE pendants): instead of injecting motion, the wizard waits for a
	 * program run with a GIA TCP node ("Save as installation reference" + Play) to feed
	 * the CalibrationServer sink, and completes this step when the result arrives.
	 * The Stop button cancels the wait.
	 */
	private void startPassiveReferencing() {
		final int waitId = tcp.id;
		refStatus.setText(t.t("WIZ_REF_PASSIVE_WAIT", tcp.name));
		refStart.setEnabled(false);
		contribution.setReferencingListener(new InstallationContribution.ReferencingListener() {
			@Override
			public void onReferenced(int tcpId) {
				if (tcpId != waitId) {
					return; // some other TCP was referenced; keep waiting for ours
				}
				contribution.setReferencingListener(null);
				// Reload the slot we were waiting for — NOT the installation selection,
				// which the user may have changed after leaving this step.
				GiaTcp updated = contribution.getStore().load(waitId);
				if (updated != null && updated.exists) {
					tcp = updated;
				}
				refStatus.setText(t.t("WIZ_REF_PASSIVE_DONE"));
				refStart.setEnabled(true);
			}
		});
	}

	/** Cancels the wait (Stop button / leaving the step) and re-enables the start button. */
	private void cancelPassiveReferencing() {
		contribution.setReferencingListener(null);
		if (!refStart.isEnabled()) {
			refStart.setEnabled(true);
			refStatus.setText(" ");
		}
	}

	/**
	 * Pops up a result notice per outcome so a failure is never silent. The no-reply
	 * case (script never ran) gets an actionable checklist; a reported error gets its
	 * decoded message. Gated by the error-handling setting; the status label is always
	 * updated regardless.
	 */
	private void notifyResult(CalibrationResult result) {
		if (!contribution.isErrInterrupt()) {
			return;
		}
		String title = t.t("WIZ_START_REFERENCING");
		if (result.isSuccess()) {
			JOptionPane.showMessageDialog(this, t.t("STATUS_CALIB_OK"), title,
					JOptionPane.INFORMATION_MESSAGE);
		} else if (!result.received) {
			JOptionPane.showMessageDialog(this,
					t.t("REF_NO_REPLY") + "\n\n" + t.t("REF_NO_REPLY_HELP"), title,
					JOptionPane.WARNING_MESSAGE);
		} else {
			JOptionPane.showMessageDialog(this,
					t.t("STATUS_CALIB_FAIL", result.status) + "\n\n" + t.t(result.statusKey()), title,
					JOptionPane.ERROR_MESSAGE);
		}
	}

	private void save() {
		if (tcp != null) {
			syncFromCombos();
			contribution.saveTcp(tcp);
		}
	}

	/**
	 * Commits whatever the dropdowns currently show into the TCP. The combos display the
	 * first option on load but the listeners only fire on an explicit pick, so without this a
	 * user who never actively clicks a dropdown leaves the value unset (variant UNKNOWN, IO -1,
	 * ref "") and the TCP reads "not configured" even though the UI looks filled in.
	 */
	private void syncFromCombos() {
		int vi = variantCombo.getSelectedIndex();
		if (variantOptions != null && vi >= 0 && vi < variantOptions.size()) {
			tcp.variant = variantOptions.get(vi);
		}
		int xi = ioXCombo.getSelectedIndex();
		if (ioOptions != null && xi >= 0 && xi < ioOptions.size()) {
			tcp.ioX = ioOptions.get(xi).code;
		}
		int yi = ioYCombo.getSelectedIndex();
		if (ioOptions != null && yi >= 0 && yi < ioOptions.size()) {
			tcp.ioY = ioOptions.get(yi).code;
		}
		int ri = refCombo.getSelectedIndex();
		if (refNames != null && ri >= 0 && ri < refNames.size()) {
			tcp.refTcp = refNames.get(ri);
		}
	}

	/** Localization key for the first missing setup item, or {@code null} when ready. */
	private String wizardIssueKey() {
		if (tcp == null || !tcp.variant.isSelectable()) {
			return "TC_ISSUE_VARIANT";
		}
		if (tcp.ioX < 0 || tcp.ioY < 0) {
			return "TC_ISSUE_INPUTS";
		}
		if (tcp.ioX == tcp.ioY) {
			return "TC_ISSUE_SAME_INPUT";
		}
		if (tcp.refTcp == null || tcp.refTcp.isEmpty()) {
			return "TC_ISSUE_REF";
		}
		if (!tcp.isCenterTaught()) {
			return "TC_ISSUE_CENTER";
		}
		return null;
	}

	// ---------- helpers ----------

	private static GridBagConstraints gbc() {
		GridBagConstraints c = new GridBagConstraints();
		c.insets = new Insets(4, 4, 4, 4);
		c.anchor = GridBagConstraints.WEST;
		return c;
	}

	private static void addField(JPanel p, String label, JTextField field) {
		p.add(new JLabel(label));
		p.add(field);
	}
}
