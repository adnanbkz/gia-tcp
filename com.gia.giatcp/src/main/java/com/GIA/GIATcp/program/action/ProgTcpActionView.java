package com.GIA.GIATcp.program.action;

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
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
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
import java.awt.GridLayout;
import java.util.List;

public class ProgTcpActionView implements SwingProgramNodeView<ProgTcpActionContribution> {

	private final ViewAPIProvider viewApiProvider;

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
	private final JTextField fIter = new JTextField(8);
	private final JTextField fOffZ = new JTextField(8);

	private final JTextField[] tolField = new JTextField[4];

	private final JRadioButton rDefaultVar = new JRadioButton();
	private final JRadioButton rCustomVar = new JRadioButton();
	private final JComboBox<String> varCombo = new JComboBox<String>();

	private final JCheckBox cErrHandling = new JCheckBox();

	private Texts t;
	private ContributionProvider<ProgTcpActionContribution> provider;
	private List<GiaTcp> tcps;
	private boolean updating;

	public ProgTcpActionView(ViewAPIProvider viewApiProvider) {
		this.viewApiProvider = viewApiProvider;
		for (int i = 0; i < 4; i++) {
			tolField[i] = new JTextField(8);
		}
	}

	private KeyboardInputFactory kf() {
		return viewApiProvider.getUserInterfaceAPI().getUserInteraction().getKeyboardInputFactory();
	}

	@Override
	public void buildUI(JPanel panel, ContributionProvider<ProgTcpActionContribution> provider) {
		this.provider = provider;
		this.t = Texts.from(viewApiProvider.getSystemAPI().getSystemSettings().getLocalization());
		// PolyScope forbids setBorder() on the root URCap panel (it throws
		// AuthorizationException), so all content goes in an inner bordered panel.
		panel.setLayout(new BorderLayout());
		JPanel content = new JPanel(new BorderLayout(8, 8));
		content.setBorder(new EmptyBorder(10, 10, 10, 10));
		panel.add(content, BorderLayout.CENTER);

		// North: title bar + TCP selection + action selection, stacked top-down so they
		// keep their natural height and the tabs below get the vertical space.
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
		rCheck.addActionListener(e -> set(c -> c.setAction(Const.ACTION_CHECK)));
		rValidate.addActionListener(e -> set(c -> c.setAction(Const.ACTION_VALIDATE)));
		rRecalibrate.addActionListener(e -> set(c -> c.setAction(Const.ACTION_RECALIBRATE)));
		actionRow.add(rCheck);
		actionRow.add(rValidate);
		actionRow.add(rRecalibrate);
		actionRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
		north.add(actionRow);

		content.add(north, BorderLayout.NORTH);

		// Center: the settings tabs take the remaining vertical space.
		JTabbedPane tabs = new JTabbedPane();
		tabs.addTab(t.t("ACT_TAB_BASIC"), wrapTop(buildBasic()));
		tabs.addTab(t.t("ACT_TAB_TOLERANCES"), wrapTop(buildTolerances()));
		tabs.addTab(t.t("ACT_TAB_ASSIGNMENT"), wrapTop(buildAssignment()));
		content.add(tabs, BorderLayout.CENTER);

		// South: error-handling toggle.
		JPanel southRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		cErrHandling.setText(t.t("ACT_ERR_HANDLING"));
		cErrHandling.addActionListener(e -> set(c -> c.setErrHandling(cErrHandling.isSelected())));
		southRow.add(cErrHandling);
		content.add(southRow, BorderLayout.SOUTH);
	}

	/** Wraps a form panel so it keeps its natural height at the top of the tab
	 *  instead of stretching its rows to fill the whole tab area. */
	private static JPanel wrapTop(JPanel inner) {
		JPanel holder = new JPanel(new BorderLayout());
		holder.add(inner, BorderLayout.NORTH);
		return holder;
	}

	private JPanel buildBasic() {
		JPanel p = new JPanel(new GridLayout(0, 2, 6, 4));
		p.add(new JLabel(t.t("ACT_SET_SPEED")));
		speedCombo.setModel(new DefaultComboBoxModel<String>(
				new String[] { t.t("SPEED_SLOW"), t.t("SPEED_NORMAL"), t.t("SPEED_FAST") }));
		speedCombo.addActionListener(e -> set(c -> c.setSpeed(speedCombo.getSelectedIndex())));
		p.add(speedCombo);
		addNum(p, t.t("ACT_APPROACH_Z"), fApproachZ, v -> provider.get().setApproachZ(v));
		addNum(p, t.t("ACT_IMMERSE_Z"), fImmerseZ, v -> provider.get().setImmerseZ(v));
		cSetAfter.setText(t.t("ACT_SET_AFTER"));
		p.add(cSetAfter);
		cSetAfter.addActionListener(e -> set(c -> c.setSetTcpAfter(cSetAfter.isSelected())));
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
		addInt(p, t.t("ACT_ITERATOR"), fIter, v -> provider.get().setIterator(v));
		addNum(p, t.t("ACT_OFFSET_Z"), fOffZ, v -> provider.get().setOffsetZ(v));
		return p;
	}

	private JPanel buildTolerances() {
		JPanel p = new JPanel(new BorderLayout(0, 8));
		JLabel info = new JLabel(t.t("ACT_TOL_INFO"));
		p.add(info, BorderLayout.NORTH);

		// One symmetric +/- field per axis: passes when the measured deviation stays
		// within +/- the value. Much clearer than separate min/max with +/-999 sentinels.
		JPanel grid = new JPanel(new GridLayout(0, 2, 8, 6));
		grid.add(new JLabel(""));
		grid.add(new JLabel(t.t("ACT_TOL_HEADER")));
		String[] keys = { "X", "Y", "Z", "D" };
		String[] labels = { "X", "Y", "Z", t.t("ACT_DIAMETER") };
		for (int i = 0; i < 4; i++) {
			final String axis = keys[i];
			grid.add(new JLabel(labels[i]));
			Ui.wireDouble(tolField[i], kf(), v -> provider.get().setTol(axis, v));
			grid.add(tolField[i]);
		}
		p.add(grid, BorderLayout.CENTER);
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
		return p;
	}

	public void refresh(ProgTcpActionContribution c) {
		updating = true;
		InstallationContribution inst = c.getInstallation();
		DefaultComboBoxModel<String> cmodel = new DefaultComboBoxModel<String>();
		int selectedIndex = -1;
		tcps = inst != null ? inst.getAllTcps() : new java.util.ArrayList<GiaTcp>();
		for (int i = 0; i < tcps.size(); i++) {
			cmodel.addElement(tcps.get(i).name);
			if (tcps.get(i).id == c.getTcpId()) {
				selectedIndex = i;
			}
		}
		tcpCombo.setModel(cmodel);
		if (selectedIndex >= 0) {
			tcpCombo.setSelectedIndex(selectedIndex);
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
		fIter.setText(String.valueOf(c.getIterator()));
		fOffZ.setText(String.valueOf(c.getOffsetZ()));

		String[] axes = { "X", "Y", "Z", "D" };
		for (int i = 0; i < 4; i++) {
			tolField[i].setText(String.valueOf(c.getTol(axes[i])));
		}

		rDefaultVar.setSelected(!c.isUseCustomVar());
		rCustomVar.setSelected(c.isUseCustomVar());
		loadVariables(c);

		cErrHandling.setSelected(c.isErrHandling());
		updating = false;
	}

	private void loadVariables(ProgTcpActionContribution c) {
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

	private void addNum(JPanel p, String label, JTextField field, java.util.function.Consumer<Double> onSet) {
		p.add(new JLabel(label));
		Ui.wireDouble(field, kf(), onSet);
		p.add(field);
	}

	private void addInt(JPanel p, String label, JTextField field, java.util.function.Consumer<Integer> onSet) {
		p.add(new JLabel(label));
		Ui.wireInteger(field, kf(), onSet);
		p.add(field);
	}

	private void set(java.util.function.Consumer<ProgTcpActionContribution> action) {
		if (!updating && provider != null) {
			action.accept(provider.get());
		}
	}
}
