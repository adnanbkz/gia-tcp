package com.GIA.GIATcp.tcpcalibration;
import com.GIA.GIATcp.tcpcalibration.model.TCPCalibrationResult;
import com.GIA.GIATcp.tcpcalibration.probe.CalibrationServer;

import com.GIA.GIATcp.installation.InstallationContribution;
import com.GIA.GIATcp.installation.model.GiaTcp;
import com.GIA.GIATcp.locale.Texts;
import com.GIA.GIATcp.util.Const;
import com.GIA.GIATcp.util.Ui;
import com.ur.urcap.api.contribution.ContributionProvider;
import com.ur.urcap.api.contribution.ViewAPIProvider;
import com.ur.urcap.api.contribution.program.swing.SwingProgramNodeView;
import com.ur.urcap.api.domain.userinteraction.keyboard.KeyboardInputFactory;

import javax.swing.Box;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * View for the GIA TCP node. Picks a configured TCP and runs the calibration at runtime
 * (Check / Validate / Recalibrate); all geometry runs in Java via the {@link CalibrationServer}.
 * Localized ES/EN like the rest of the URCap.
 */
public class TCPCalibrationView implements SwingProgramNodeView<TCPCalibrationContribution> {

	private final ViewAPIProvider viewApiProvider;

	/** Tolerance rows: X/Y/Z axes plus the probed tool diameter ("D", shown as Ø). */
	private static final String[] TOL_AXES = { "X", "Y", "Z", "D" };

	private final JComboBox<String> tcpCombo = new JComboBox<String>();
	private final JRadioButton rCheck = new JRadioButton();
	private final JRadioButton rValidate = new JRadioButton();
	private final JRadioButton rRecalibrate = new JRadioButton();

	private final JComboBox<String> speedCombo = new JComboBox<String>();
	private final JTextField fApproachZ = new JTextField(8);
	private final JTextField fImmerseZ = new JTextField(8);
	private final JCheckBox cSetAfter = new JCheckBox();
	private final JRadioButton rNoAngle = new JRadioButton();
	private final JRadioButton rAngle = new JRadioButton();
	private final JTextField fOffZ = new JTextField(8);

	private final JTextField[] tolMinField = {
			new JTextField(6), new JTextField(6), new JTextField(6), new JTextField(6) };
	private final JTextField[] tolMaxField = {
			new JTextField(6), new JTextField(6), new JTextField(6), new JTextField(6) };
	private final JLabel[] tolPrevLabel = { new JLabel(), new JLabel(), new JLabel(), new JLabel() };

	private final JRadioButton rDefaultVar = new JRadioButton();
	private final JRadioButton rCustomVar = new JRadioButton();
	private final JComboBox<String> varCombo = new JComboBox<String>();

	private final JCheckBox cErrHandling = new JCheckBox();
	private final JCheckBox cPersistRef = new JCheckBox();
	private final JButton calibrateNow = new JButton();
	private final JButton stopTest = new JButton();
	private final JLabel setupBanner = new JLabel();
	/** Set when the user pressed Stop, so the result dialog says "stopped", not "no reply". */
	private volatile boolean stopRequested;

	private Texts t;
	private ContributionProvider<TCPCalibrationContribution> provider;
	private List<GiaTcp> tcps = new ArrayList<GiaTcp>();
	private boolean updating;

	public TCPCalibrationView(ViewAPIProvider viewApiProvider) {
		this.viewApiProvider = viewApiProvider;
	}

	private KeyboardInputFactory kf() {
		return viewApiProvider.getUserInterfaceAPI().getUserInteraction().getKeyboardInputFactory();
	}

	@Override
	public void buildUI(JPanel panel, ContributionProvider<TCPCalibrationContribution> provider) {
		this.provider = provider;
		this.t = Texts.from(viewApiProvider.getSystemAPI().getSystemSettings().getLocalization());
		// PolyScope forbids setBorder() on the root URCap panel; use an inner content panel.
		panel.setLayout(new BorderLayout());
		JPanel content = new JPanel(new BorderLayout(8, 8));
		content.setBorder(new EmptyBorder(10, 10, 10, 10));
		panel.add(content, BorderLayout.CENTER);

		JPanel north = new JPanel();
		north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));

		JPanel header = new JPanel(new BorderLayout());
		header.setAlignmentX(Component.LEFT_ALIGNMENT);
		JLabel title = new JLabel("GIA TCP");
		title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
		header.add(title, BorderLayout.WEST);
		header.add(Ui.logo(), BorderLayout.EAST);
		header.setMaximumSize(new Dimension(Integer.MAX_VALUE, header.getPreferredSize().height));
		north.add(header);
		north.add(Box.createVerticalStrut(10));

		// Explains why the node is "not defined" (yellow): which setup step is still missing.
		setupBanner.setAlignmentX(Component.LEFT_ALIGNMENT);
		setupBanner.setForeground(new java.awt.Color(0xB0, 0x6A, 0x00));
		setupBanner.setVisible(false);
		north.add(setupBanner);
		north.add(Box.createVerticalStrut(6));

		JPanel selectRow = new JPanel(new BorderLayout(8, 0));
		selectRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		selectRow.add(new JLabel(t.t("ACT_SELECT_TCP")), BorderLayout.WEST);
		tcpCombo.addActionListener(e -> onTcpSelect());
		selectRow.add(tcpCombo, BorderLayout.CENTER);
		selectRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
		north.add(selectRow);
		north.add(Box.createVerticalStrut(8));

		JPanel actionRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
		actionRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		actionRow.add(new JLabel(t.t("ACT_ACTION")));
		rCheck.setText(t.t("ACT_CHECK"));
		rValidate.setText(t.t("ACT_VALIDATE"));
		rRecalibrate.setText(t.t("ACT_RECALIBRATE"));
		ButtonGroup actionGroup = new ButtonGroup();
		actionGroup.add(rCheck);
		actionGroup.add(rValidate);
		actionGroup.add(rRecalibrate);
		// The readiness gate depends on the action (Check requires a referenced TCP), so
		// the banner is re-evaluated on every action / persist change.
		rCheck.addActionListener(e -> { set(c -> c.setAction(Const.ACTION_CHECK)); refreshIssueBanner(); });
		rValidate.addActionListener(e -> { set(c -> c.setAction(Const.ACTION_VALIDATE)); refreshIssueBanner(); });
		rRecalibrate.addActionListener(e -> { set(c -> c.setAction(Const.ACTION_RECALIBRATE)); refreshIssueBanner(); });
		actionRow.add(rCheck);
		actionRow.add(rValidate);
		actionRow.add(rRecalibrate);
		actionRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
		north.add(actionRow);

		content.add(north, BorderLayout.NORTH);

		JTabbedPane tabs = new JTabbedPane();
		tabs.addTab(t.t("ACT_TAB_BASIC"), wrapTop(buildBasic()));
		tabs.addTab(t.t("ACT_TAB_TOLERANCES"), wrapTop(buildTolerances()));
		tabs.addTab(t.t("ACT_TAB_ASSIGNMENT"), wrapTop(buildAssignment()));
		content.add(tabs, BorderLayout.CENTER);

		JPanel southRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
		cErrHandling.setText(t.t("ACT_ERR_HANDLING"));
		cErrHandling.addActionListener(e -> set(c -> c.setErrHandling(cErrHandling.isSelected())));
		southRow.add(cErrHandling);
		cPersistRef.setText(t.t("ACT_PERSIST_REF"));
		cPersistRef.setToolTipText(t.t("ACT_PERSIST_REF_HINT"));
		cPersistRef.addActionListener(e -> {
			set(c -> c.setPersistReference(cPersistRef.isSelected()));
			refreshIssueBanner();
		});
		southRow.add(cPersistRef);

		// Calibrate live from the node itself: same path as the installation "test" button — a
		// self-contained program over the Secondary interface with the geometry in Java. It does
		// NOT depend on the installation preamble compiling, so it works even while Play is broken,
		// and lets you calibrate without leaving the program node. The corrected TCP is stored on
		// the selected TCP. NOTE: on e-Series, secondary-injected motion needs Remote Control mode.
		JPanel calibRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
		calibrateNow.setText(t.t("BTN_CALIBRATE_TEST"));
		calibrateNow.addActionListener(e -> runLiveCalibration());
		calibRow.add(calibrateNow);
		// Stop for the live test (same mechanism as the Overview Stop): a preempting
		// secondary program halts the probe motion and unblocks the pending reply read.
		stopTest.setText(t.t("BTN_STOP"));
		stopTest.setEnabled(false);
		stopTest.addActionListener(e -> onStopTest());
		calibRow.add(stopTest);

		JPanel south = new JPanel(new BorderLayout(0, 4));
		south.add(calibRow, BorderLayout.NORTH);
		south.add(southRow, BorderLayout.SOUTH);
		content.add(south, BorderLayout.SOUTH);
	}

	/**
	 * Live calibration from the node, mirroring the installation test flow (and CAPTRON):
	 * check Remote Control, activate the reference TCP, guide the robot to the taught
	 * centre with the guarded move screen, and only then inject the probe motions.
	 */
	private void runLiveCalibration() {
		final TCPCalibrationContribution c = provider.get();
		String issue = c.readinessIssueKey();
		if (issue != null) {
			JOptionPane.showMessageDialog(calibrateNow, t.t(issue), t.t("BTN_CALIBRATE_TEST"),
					JOptionPane.WARNING_MESSAGE);
			return;
		}
		final InstallationContribution inst = c.getInstallation();
		final GiaTcp tcp = c.getSelectedTcp();
		if (inst == null || tcp == null) {
			return;
		}
		calibrateNow.setEnabled(false);
		new Thread(() -> {
			if (Boolean.FALSE.equals(inst.isInRemoteControl())) {
				SwingUtilities.invokeLater(() -> {
					calibrateNow.setEnabled(true);
					JOptionPane.showMessageDialog(calibrateNow, t.t("REF_LOCAL_MODE"),
							t.t("BTN_CALIBRATE_TEST"), JOptionPane.WARNING_MESSAGE);
				});
				return;
			}
			inst.activateReferenceTcp(tcp);
			SwingUtilities.invokeLater(() -> {
				// Re-enable before the move screen: backing out of it fires no callback,
				// so the button must stay usable for a retry.
				calibrateNow.setEnabled(true);
				c.requestMoveToCenter(() -> startNodeProbe(inst, tcp));
			});
		}, "gia-tcp-node-calibrate").start();
	}

	/** Runs the actual probe once the robot sits at the taught centre. */
	private void startNodeProbe(final InstallationContribution inst, final GiaTcp tcp) {
		calibrateNow.setEnabled(false);
		stopRequested = false;
		stopTest.setEnabled(true);
		new Thread(() -> {
			TCPCalibrationResult r;
			try {
				r = inst.runTestCalibration(tcp);
			} catch (RuntimeException ex) {
				r = null;
			}
			final TCPCalibrationResult res = r;
			SwingUtilities.invokeLater(() -> {
				calibrateNow.setEnabled(true);
				stopTest.setEnabled(false);
				showResult(res);
			});
		}, "gia-tcp-node-probe").start();
	}

	/** Aborts the running live test; off the EDT because it opens a socket to the robot. */
	private void onStopTest() {
		final TCPCalibrationContribution c = provider.get();
		final InstallationContribution inst = c.getInstallation();
		if (inst == null) {
			return;
		}
		stopRequested = true;
		stopTest.setEnabled(false);
		new Thread(inst::stopTestCalibration, "gia-tcp-node-stop").start();
	}

	private void showResult(TCPCalibrationResult r) {
		String title = t.t("BTN_CALIBRATE_TEST");
		// A stopped probe surfaces as "no reply" (the STOP line unblocks the read with no
		// valid result); tell the user it was their stop, not a comms problem.
		if (stopRequested && (r == null || r.status == TCPCalibrationResult.Status.NO_ROBOT_REPLY)) {
			JOptionPane.showMessageDialog(calibrateNow, t.t("TC_TEST_STOPPED"), title,
					JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		if (r == null) {
			JOptionPane.showMessageDialog(calibrateNow, t.t("TC_ST_NO_REPLY"), title, JOptionPane.WARNING_MESSAGE);
			return;
		}
		String body = statusText(r.status);
		if (r.correctedTcp != null) {
			double[] tc = r.correctedTcp;
			body += "\n\n" + t.t("TC_CORRECTED")
					+ String.format("  X=%.2f  Y=%.2f  Z=%.2f mm", tc[0] * 1000, tc[1] * 1000, tc[2] * 1000);
		}
		JOptionPane.showMessageDialog(calibrateNow, body, title,
				r.isOk() ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);
	}

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

	/** Keeps a form panel at its natural height at the top of the tab. */
	private static JPanel wrapTop(JPanel inner) {
		JPanel holder = new JPanel(new BorderLayout());
		holder.add(inner, BorderLayout.NORTH);
		return holder;
	}

	private JPanel buildBasic() {
		JPanel p = new JPanel(new GridLayout(0, 2, 8, 8));
		p.add(Ui.bold(t.t("ACT_SET_SPEED")));
		speedCombo.setModel(new DefaultComboBoxModel<String>(
				new String[] { t.t("SPEED_SLOW"), t.t("SPEED_NORMAL"), t.t("SPEED_FAST") }));
		speedCombo.addActionListener(e -> set(c -> c.setSpeed(speedCombo.getSelectedIndex())));
		p.add(speedCombo);
		addNum(p, t.t("ACT_APPROACH_Z"), fApproachZ, v -> provider.get().setApproachZ(v));
		addNum(p, t.t("ACT_IMMERSE_Z"), fImmerseZ, v -> provider.get().setImmerseZ(v));
		cSetAfter.setText(t.t("ACT_SET_AFTER"));
		cSetAfter.addActionListener(e -> set(c -> c.setSetTcpAfter(cSetAfter.isSelected())));
		p.add(cSetAfter);
		p.add(new JLabel(""));
		rNoAngle.setText(t.t("ACT_NO_ANGLE"));
		rAngle.setText(t.t("ACT_ANGLE"));
		ButtonGroup g = new ButtonGroup();
		g.add(rNoAngle);
		g.add(rAngle);
		rNoAngle.addActionListener(e -> set(c -> c.setAdjustAngle(false)));
		rAngle.addActionListener(e -> set(c -> c.setAdjustAngle(true)));
		p.add(rNoAngle);
		p.add(rAngle);
		addNum(p, t.t("ACT_OFFSET_Z"), fOffZ, v -> provider.get().setOffsetZ(v));
		return p;
	}

	private JPanel buildTolerances() {
		JPanel p = new JPanel(new BorderLayout(0, 8));
		p.add(new JLabel(t.t("ACT_TOL_INFO")), BorderLayout.NORTH);

		// Framed box mirroring CAPTRON's tolerance group: asymmetric Min/Max per axis plus
		// a "Previous" column with the last measured deviation, to tune bands on real data.
		JPanel box = new JPanel(new GridBagLayout());
		box.setBorder(BorderFactory.createTitledBorder(t.t("ACT_TOL_HEADER")));
		GridBagConstraints gc = new GridBagConstraints();
		gc.insets = new Insets(6, 12, 6, 12);
		gc.anchor = GridBagConstraints.WEST;
		gc.gridy = 0;
		gc.gridx = 1;
		box.add(Ui.bold(t.t("ACT_TOL_MIN")), gc);
		gc.gridx = 2;
		box.add(Ui.bold(t.t("ACT_TOL_MAX")), gc);
		gc.gridx = 4;
		box.add(Ui.bold(t.t("ACT_TOL_PREV")), gc);
		for (int i = 0; i < TOL_AXES.length; i++) {
			final String axis = TOL_AXES[i];
			gc.gridy = i + 1;
			gc.gridx = 0;
			gc.weightx = 0;
			gc.fill = GridBagConstraints.NONE;
			box.add(Ui.bold("D".equals(axis) ? "Ø" : axis), gc);
			gc.gridx = 1;
			gc.weightx = 1;
			gc.fill = GridBagConstraints.HORIZONTAL;
			Ui.wireDouble(tolMinField[i], kf(), v -> provider.get().setTolMin(axis, v));
			box.add(tolMinField[i], gc);
			gc.gridx = 2;
			Ui.wireDouble(tolMaxField[i], kf(), v -> provider.get().setTolMax(axis, v));
			box.add(tolMaxField[i], gc);
			gc.gridx = 3;
			gc.weightx = 0;
			gc.fill = GridBagConstraints.NONE;
			box.add(new JLabel("mm"), gc);
			gc.gridx = 4;
			box.add(tolPrevLabel[i], gc);
		}
		JPanel holder = new JPanel(new BorderLayout());
		holder.add(box, BorderLayout.NORTH);
		p.add(holder, BorderLayout.CENTER);
		return p;
	}

	private JPanel buildAssignment() {
		JPanel p = new JPanel(new GridLayout(0, 1, 6, 4));
		rDefaultVar.setText(t.t("ACT_USE_DEFAULT_VAR", Const.DEFAULT_RECALIB_VAR));
		rCustomVar.setText(t.t("ACT_USE_CUSTOM_VAR"));
		ButtonGroup g = new ButtonGroup();
		g.add(rDefaultVar);
		g.add(rCustomVar);
		rDefaultVar.addActionListener(e -> set(c -> c.setUseCustomVar(false)));
		rCustomVar.addActionListener(e -> set(c -> c.setUseCustomVar(true)));
		p.add(rDefaultVar);
		p.add(rCustomVar);
		varCombo.addActionListener(e -> {
			if (!updating && varCombo.getSelectedItem() != null) {
				set(c -> c.setCustomVar(String.valueOf(varCombo.getSelectedItem())));
			}
		});
		p.add(varCombo);

		// Commissioning utilities (CAPTRON Move Start / Move Approach): guarded move-robot
		// screen to the action's start pose or its approach point.
		JPanel moveRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
		JButton moveStart = new JButton(t.t("ACT_MOVE_START"));
		JButton moveApproach = new JButton(t.t("ACT_MOVE_APPROACH"));
		moveStart.addActionListener(e -> moveToActionPose(false));
		moveApproach.addActionListener(e -> moveToActionPose(true));
		moveRow.add(moveStart);
		moveRow.add(moveApproach);
		p.add(moveRow);
		return p;
	}

	/**
	 * Opens the guarded move screen towards the action start/approach pose. The action's
	 * TCP is activated first (calibrated TCP for Check/Validate, reference for
	 * Recalibrate — same as the generated program); if that fails (Local mode), the user
	 * is warned that the move screen will use whatever TCP is currently active.
	 */
	private void moveToActionPose(final boolean approach) {
		final TCPCalibrationContribution c = provider.get();
		String issue = c.readinessIssueKey();
		if (issue != null) {
			JOptionPane.showMessageDialog(calibrateNow, t.t(issue), t.t("ACT_MOVE_START"),
					JOptionPane.WARNING_MESSAGE);
			return;
		}
		final InstallationContribution inst = c.getInstallation();
		final GiaTcp tcp = c.getSelectedTcp();
		final double[] pose = approach ? c.actionApproachPose() : c.actionStartPose();
		final double[] activeTcp = c.actionActiveTcp();
		if (inst == null || tcp == null || pose == null) {
			return;
		}
		new Thread(() -> {
			final boolean tcpActivated = inst.activateTcp(activeTcp);
			SwingUtilities.invokeLater(() -> {
				if (!tcpActivated) {
					int go = JOptionPane.showConfirmDialog(calibrateNow, t.t("ACT_MOVE_TCP_WARN", tcp.refTcp),
							t.t("ACT_MOVE_START"), JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
					if (go != JOptionPane.YES_OPTION) {
						return;
					}
				}
				c.requestMove(pose, null);
			});
		}, "gia-tcp-node-move").start();
	}

	public void refresh(TCPCalibrationContribution c) {
		updating = true;
		InstallationContribution inst = c.getInstallation();
		tcps = inst != null ? inst.getAllTcps() : new ArrayList<GiaTcp>();
		DefaultComboBoxModel<String> m = new DefaultComboBoxModel<String>();
		int sel = -1;
		for (int i = 0; i < tcps.size(); i++) {
			m.addElement(tcps.get(i).name);
			if (tcps.get(i).id == c.getTcpId()) {
				sel = i;
			}
		}
		tcpCombo.setModel(m);
		if (sel >= 0) {
			tcpCombo.setSelectedIndex(sel);
		}

		int action = c.getAction();
		rCheck.setSelected(action == Const.ACTION_CHECK);
		rValidate.setSelected(action == Const.ACTION_VALIDATE);
		rRecalibrate.setSelected(action == Const.ACTION_RECALIBRATE);

		speedCombo.setSelectedIndex(c.getSpeed());
		fApproachZ.setText(String.valueOf(c.getApproachZ()));
		fImmerseZ.setText(String.valueOf(c.getImmerseZ()));
		cSetAfter.setSelected(c.isSetTcpAfter());
		rNoAngle.setSelected(!c.isAdjustAngle());
		rAngle.setSelected(c.isAdjustAngle());
		fOffZ.setText(String.valueOf(c.getOffsetZ()));

		double[] prev = CalibrationServer.lastResultFor(c.getTcpId());
		for (int i = 0; i < TOL_AXES.length; i++) {
			tolMinField[i].setText(String.valueOf(c.getTolMin(TOL_AXES[i])));
			tolMaxField[i].setText(String.valueOf(c.getTolMax(TOL_AXES[i])));
			// Previous: X/Y/Z show the last deviation; the Ø row shows the last probed diameter.
			tolPrevLabel[i].setText(prev == null ? "---" : String.format("%.2f", prev[i]));
		}

		rDefaultVar.setSelected(!c.isUseCustomVar());
		rCustomVar.setSelected(c.isUseCustomVar());
		loadVariables(c);

		cErrHandling.setSelected(c.isErrHandling());
		cPersistRef.setSelected(c.isPersistReference());

		refreshIssueBanner();
		updating = false;
	}

	/** Re-evaluates the readiness gate (it depends on action/persist, not just Setup). */
	private void refreshIssueBanner() {
		if (provider == null) {
			return;
		}
		String issueKey = provider.get().readinessIssueKey();
		if (issueKey == null) {
			setupBanner.setVisible(false);
		} else {
			setupBanner.setText(t.t("TC_ISSUE_PREFIX", t.t(issueKey)));
			setupBanner.setVisible(true);
		}
		calibrateNow.setEnabled(issueKey == null);
	}

	private void loadVariables(TCPCalibrationContribution c) {
		DefaultComboBoxModel<String> vm = new DefaultComboBoxModel<String>();
		for (String name : c.getVariableNames()) {
			vm.addElement(name);
		}
		varCombo.setModel(vm);
		String cur = c.getCustomVar();
		if (cur != null && !cur.isEmpty()) {
			varCombo.setSelectedItem(cur);
		}
	}

	private void onTcpSelect() {
		if (updating || tcps == null) {
			return;
		}
		int idx = tcpCombo.getSelectedIndex();
		if (idx >= 0 && idx < tcps.size()) {
			final int id = tcps.get(idx).id;
			set(c -> c.setTcpId(id));
		}
	}

	private void addNum(JPanel p, String label, JTextField field, Consumer<Double> onSet) {
		p.add(Ui.bold(label));
		Ui.wireDouble(field, kf(), onSet);
		p.add(field);
	}

	private void set(Consumer<TCPCalibrationContribution> action) {
		if (!updating && provider != null) {
			action.accept(provider.get());
		}
	}
}
