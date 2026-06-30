package com.GIA.GIATcp.installation;

import com.GIA.GIATcp.installation.diagnostics.DiagnosticsCard;
import com.GIA.GIATcp.installation.overview.OverviewCard;
import com.GIA.GIATcp.installation.settings.SettingsCard;
import com.GIA.GIATcp.installation.wizard.SetupWizard;
import com.ur.urcap.api.contribution.installation.swing.SwingInstallationNodeView;

import javax.swing.BoxLayout;
import javax.swing.JPanel;
import java.awt.CardLayout;

/**
 * Top-level installation view. Hosts three cards (Overview, Setup Wizard,
 * Settings) in a CardLayout and exposes navigation + refresh to the cards.
 */
public class InstallationView implements SwingInstallationNodeView<InstallationContribution> {

	private static final String OVERVIEW = "overview";
	private static final String WIZARD = "wizard";
	private static final String SETTINGS = "settings";
	private static final String DIAGNOSTICS = "diagnostics";

	private final CardLayout cardLayout = new CardLayout();
	private final JPanel cards = new JPanel(cardLayout);

	private OverviewCard overviewCard;
	private SettingsCard settingsCard;
	private SetupWizard setupWizard;
	private DiagnosticsCard diagnosticsCard;
	private String current = OVERVIEW;

	@Override
	public void buildUI(JPanel panel, InstallationContribution contribution) {
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

		overviewCard = new OverviewCard(contribution, this);
		settingsCard = new SettingsCard(contribution, this);
		setupWizard = new SetupWizard(contribution, this);
		diagnosticsCard = new DiagnosticsCard(contribution, this);

		cards.add(overviewCard, OVERVIEW);
		cards.add(setupWizard, WIZARD);
		cards.add(settingsCard, SETTINGS);
		cards.add(diagnosticsCard, DIAGNOSTICS);
		panel.add(cards);

		showOverview();
	}

	/** Stops the diagnostics live monitor; safe to call from any navigation/close. */
	private void stopDiagnostics() {
		if (diagnosticsCard != null) {
			diagnosticsCard.stopMonitor();
		}
	}

	/** Called from the contribution when the installation view closes. */
	public void onClose() {
		stopDiagnostics();
	}

	public void refresh() {
		if (OVERVIEW.equals(current) && overviewCard != null) {
			overviewCard.refresh();
		} else if (SETTINGS.equals(current) && settingsCard != null) {
			settingsCard.refresh();
		} else if (WIZARD.equals(current) && setupWizard != null) {
			setupWizard.refresh();
		} else if (DIAGNOSTICS.equals(current) && diagnosticsCard != null) {
			diagnosticsCard.refresh();
		}
	}

	public void showOverview() {
		stopDiagnostics();
		current = OVERVIEW;
		if (overviewCard != null) {
			overviewCard.refresh();
		}
		cardLayout.show(cards, OVERVIEW);
	}

	public void showSettings() {
		stopDiagnostics();
		current = SETTINGS;
		if (settingsCard != null) {
			settingsCard.refresh();
		}
		cardLayout.show(cards, SETTINGS);
	}

	/** Opens the wizard to edit the currently selected TCP. */
	public void showWizard() {
		stopDiagnostics();
		current = WIZARD;
		if (setupWizard != null) {
			setupWizard.startForSelected();
		}
		cardLayout.show(cards, WIZARD);
	}

	/** Opens the sensor diagnostics card and starts the live monitor. */
	public void showDiagnostics() {
		current = DIAGNOSTICS;
		if (diagnosticsCard != null) {
			diagnosticsCard.refresh();
		}
		cardLayout.show(cards, DIAGNOSTICS);
	}
}
