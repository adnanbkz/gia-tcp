package com.GIA.GIATcp.installation.settings;

import com.GIA.GIATcp.installation.InstallationContribution;
import com.GIA.GIATcp.installation.InstallationView;
import com.GIA.GIATcp.locale.Texts;
import com.GIA.GIATcp.util.Ui;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Font;

public class SettingsCard extends JPanel {

	private final InstallationContribution contribution;

	private final JRadioButton errInterrupt = new JRadioButton();
	private final JRadioButton errSilent = new JRadioButton();
	private final JRadioButton dbgNone = new JRadioButton();
	private final JRadioButton dbgImportant = new JRadioButton();
	private final JRadioButton dbgVerbose = new JRadioButton();

	public SettingsCard(InstallationContribution contribution, InstallationView view) {
		this.contribution = contribution;
		Texts t = contribution.texts();
		errInterrupt.setText(t.t("SETTINGS_ERR_INTERRUPT"));
		errSilent.setText(t.t("SETTINGS_ERR_SILENT"));
		dbgNone.setText(t.t("SETTINGS_DBG_NONE"));
		dbgImportant.setText(t.t("SETTINGS_DBG_IMPORTANT"));
		dbgVerbose.setText(t.t("SETTINGS_DBG_VERBOSE"));
		setLayout(new BorderLayout(8, 8));
		setBorder(new EmptyBorder(10, 10, 10, 10));

		JPanel header = new JPanel(new BorderLayout());
		JButton back = new JButton(t.t("BTN_OVERVIEW_BACK"));
		back.addActionListener(e -> view.showOverview());
		header.add(back, BorderLayout.WEST);
		JLabel title = new JLabel(t.t("SETTINGS_TITLE"), JLabel.CENTER);
		title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
		header.add(title, BorderLayout.CENTER);
		header.add(Ui.logo(), BorderLayout.EAST);
		add(header, BorderLayout.NORTH);

		// A compact, top-packed column (BoxLayout) so the options cluster near the
		// title instead of being stretched apart across the whole card (CAPTRON style).
		JPanel body = new JPanel();
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

		body.add(left(Ui.bold(t.t("SETTINGS_ERR_HANDLING"))));
		ButtonGroup errGroup = new ButtonGroup();
		errGroup.add(errInterrupt);
		errGroup.add(errSilent);
		errInterrupt.addActionListener(e -> contribution.setErrInterrupt(true));
		errSilent.addActionListener(e -> contribution.setErrInterrupt(false));
		body.add(left(errInterrupt));
		body.add(left(errSilent));

		body.add(Box.createVerticalStrut(16));

		body.add(left(Ui.bold(t.t("SETTINGS_DEBUG"))));
		ButtonGroup dbgGroup = new ButtonGroup();
		dbgGroup.add(dbgNone);
		dbgGroup.add(dbgImportant);
		dbgGroup.add(dbgVerbose);
		dbgNone.addActionListener(e -> contribution.setDebugLvl(0));
		dbgImportant.addActionListener(e -> contribution.setDebugLvl(1));
		dbgVerbose.addActionListener(e -> contribution.setDebugLvl(2));
		body.add(left(dbgNone));
		body.add(left(dbgImportant));
		body.add(left(dbgVerbose));

		JPanel holder = new JPanel(new BorderLayout());
		holder.add(body, BorderLayout.NORTH);
		add(holder, BorderLayout.CENTER);
	}

	/** Left-aligns a component for the vertical BoxLayout column. */
	private static Component left(JComponent c) {
		c.setAlignmentX(Component.LEFT_ALIGNMENT);
		return c;
	}

	public void refresh() {
		boolean interrupt = contribution.isErrInterrupt();
		errInterrupt.setSelected(interrupt);
		errSilent.setSelected(!interrupt);
		int lvl = contribution.getDebugLvl();
		dbgNone.setSelected(lvl == 0);
		dbgImportant.setSelected(lvl == 1);
		dbgVerbose.setSelected(lvl == 2);
	}
}
