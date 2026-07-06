package com.GIA.GIATcp.installation.diagnostics;

import com.GIA.GIATcp.installation.InstallationContribution;
import com.GIA.GIATcp.installation.InstallationContribution.LiveInput;
import com.GIA.GIATcp.installation.InstallationView;
import com.GIA.GIATcp.installation.measure.RepeatabilityResult;
import com.GIA.GIATcp.installation.model.GiaTcp;
import com.GIA.GIATcp.locale.Texts;
import com.GIA.GIATcp.util.Ui;
import com.ur.urcap.api.domain.userinteraction.keyboard.KeyboardInputFactory;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Read-only sensor diagnostics. Shows the live state of every digital input and
 * the live TCP pose, and logs every input edge (with the TCP pose at that moment)
 * while you sweep the tool through the light barrier.
 *
 * <p>Non-intrusive: it never runs a program on the robot. Digital inputs are read
 * on the EDT via the IO API; the TCP pose is polled on a background thread from the
 * realtime interface. Edge timing is sampled at a few Hz, so this is a wiring /
 * sanity tool — metrology-grade edge capture must happen in URScript at control-loop
 * rate (follow-up).</p>
 */
public class DiagnosticsCard extends JPanel {

	private static final Color HIGH = new Color(0x4C, 0xAF, 0x50);
	private static final Color LOW = new Color(0xBD, 0xBD, 0xBD);
	private static final Color X_TAG = new Color(0x2D, 0x6C, 0xDF);
	private static final Color Y_TAG = new Color(0xE8, 0x8A, 0x1A);
	private static final int UI_PERIOD_MS = 200;
	private static final int POSE_PERIOD_MS = 150;

	private final InstallationContribution contribution;
	private final InstallationView view;

	private final JLabel tcpLabel = new JLabel(" ");
	private final JPanel ioPanel = new JPanel(new GridLayout(0, 1, 0, 2));
	private final JLabel poseLabel = new JLabel(" ");
	private final JTextArea log = new JTextArea(8, 40);
	private final JButton startBtn = new JButton();
	private final JButton stopBtn = new JButton();
	private final JTextField runsField = new JTextField("5", 3);
	private final JButton repeatBtn = new JButton();
	private Texts t;

	private final Map<Integer, JLabel> dots = new LinkedHashMap<Integer, JLabel>();
	private final Map<Integer, JLabel> values = new LinkedHashMap<Integer, JLabel>();
	private final Map<Integer, String> names = new LinkedHashMap<Integer, String>();
	private final Map<Integer, Boolean> lastState = new LinkedHashMap<Integer, Boolean>();

	private final SimpleDateFormat clock = new SimpleDateFormat("HH:mm:ss.SSS");

	private Timer uiTimer;
	private Thread poseThread;
	private volatile boolean running;
	private volatile double[] latestPose;

	public DiagnosticsCard(InstallationContribution contribution, InstallationView view) {
		this.contribution = contribution;
		this.view = view;
		buildUi();
	}

	private void buildUi() {
		t = contribution.texts();
		setLayout(new BorderLayout(8, 8));
		setBorder(new EmptyBorder(10, 10, 10, 10));

		JPanel header = new JPanel(new BorderLayout());
		JButton back = new JButton(t.t("BTN_BACK"));
		back.addActionListener(e -> view.showOverview());
		header.add(back, BorderLayout.WEST);
		JLabel title = new JLabel(t.t("DIAG_TITLE"), JLabel.CENTER);
		title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
		header.add(title, BorderLayout.CENTER);
		header.add(Ui.logo(), BorderLayout.EAST);
		add(header, BorderLayout.NORTH);

		JPanel center = new JPanel();
		center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));

		tcpLabel.setFont(tcpLabel.getFont().deriveFont(Font.BOLD));
		tcpLabel.setAlignmentX(LEFT_ALIGNMENT);
		center.add(tcpLabel);
		center.add(spacer(6));

		JLabel ioTitle = new JLabel(t.t("DIAG_INPUTS_LIVE"));
		ioTitle.setFont(ioTitle.getFont().deriveFont(Font.BOLD));
		ioTitle.setAlignmentX(LEFT_ALIGNMENT);
		center.add(ioTitle);
		ioPanel.setAlignmentX(LEFT_ALIGNMENT);
		ioPanel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
		// Cap the (up to 16) input rows in a scroll pane so they don't push the live
		// pose, buttons and edge log off the bottom of the screen.
		JScrollPane ioScroll = new JScrollPane(ioPanel,
				JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		ioScroll.setBorder(BorderFactory.createEmptyBorder());
		ioScroll.setAlignmentX(LEFT_ALIGNMENT);
		ioScroll.getVerticalScrollBar().setUnitIncrement(16);
		ioScroll.setPreferredSize(new Dimension(440, 160));
		ioScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 160));
		center.add(ioScroll);
		center.add(spacer(8));

		poseLabel.setFont(new Font("Monospaced", Font.PLAIN, 13));
		poseLabel.setAlignmentX(LEFT_ALIGNMENT);
		center.add(new boldLeft(t.t("DIAG_LIVE_POSE")));
		center.add(poseLabel);
		center.add(spacer(8));

		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		startBtn.setText(t.t("DIAG_START"));
		startBtn.addActionListener(e -> startMonitor());
		stopBtn.setText(t.t("BTN_STOP"));
		stopBtn.addActionListener(e -> stopMonitor());
		JButton moveBtn = new JButton(t.t("DIAG_MOVE_CENTER"));
		moveBtn.addActionListener(e -> onMoveToCenter());
		JButton clearBtn = new JButton(t.t("DIAG_CLEAR_LOG"));
		clearBtn.addActionListener(e -> log.setText(""));
		buttons.add(startBtn);
		buttons.add(stopBtn);
		buttons.add(moveBtn);
		buttons.add(clearBtn);
		buttons.setAlignmentX(LEFT_ALIGNMENT);
		center.add(buttons);

		JPanel measRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		measRow.add(new JLabel(t.t("DIAG_RUNS")));
		Ui.wireInteger(runsField, kf(), v -> { });
		measRow.add(runsField);
		repeatBtn.setText(t.t("DIAG_REPEAT"));
		repeatBtn.addActionListener(e -> onRepeatability());
		measRow.add(repeatBtn);
		measRow.add(new JLabel(t.t("DIAG_REPEAT_HINT")));
		measRow.setAlignmentX(LEFT_ALIGNMENT);
		center.add(measRow);

		add(center, BorderLayout.CENTER);

		JPanel south = new JPanel(new BorderLayout(4, 4));
		south.add(new boldLeft(t.t("DIAG_EDGE_LOG")), BorderLayout.NORTH);
		log.setEditable(false);
		log.setFont(new Font("Monospaced", Font.PLAIN, 11));
		south.add(new JScrollPane(log), BorderLayout.CENTER);
		JLabel note = new JLabel(t.t("DIAG_NOTE"));
		south.add(note, BorderLayout.SOUTH);
		add(south, BorderLayout.SOUTH);
	}

	private JPanel spacer(int h) {
		JPanel p = new JPanel();
		p.setPreferredSize(new Dimension(1, h));
		p.setMaximumSize(new Dimension(Integer.MAX_VALUE, h));
		p.setOpaque(false);
		p.setAlignmentX(LEFT_ALIGNMENT);
		return p;
	}

	/** Tiny bold left-aligned label helper for the BoxLayout column. */
	private static class boldLeft extends JLabel {
		boldLeft(String text) {
			super(text);
			setFont(getFont().deriveFont(Font.BOLD));
			setAlignmentX(LEFT_ALIGNMENT);
		}
	}

	// ---------------- lifecycle ----------------

	/** Called when the card becomes visible: rebuild rows for the current config and start. */
	public void refresh() {
		buildIoRows();
		updateTcpLabel();
		startMonitor();
	}

	private void buildIoRows() {
		ioPanel.removeAll();
		dots.clear();
		values.clear();
		names.clear();
		lastState.clear();

		GiaTcp sel = contribution.getSelectedTcp();
		int ioX = sel != null ? sel.ioX : -1;
		int ioY = sel != null ? sel.ioY : -1;

		List<LiveInput> inputs = contribution.readDigitalInputsLive();
		if (inputs.isEmpty()) {
			ioPanel.add(new JLabel(t.t("DIAG_NO_INPUTS")));
		}
		for (LiveInput in : inputs) {
			JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 1));

			JLabel dot = new JLabel("  ");
			dot.setOpaque(true);
			dot.setBackground(LOW);
			dot.setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY));
			dot.setPreferredSize(new Dimension(18, 18));
			row.add(dot);

			JLabel value = new JLabel("LOW");
			value.setFont(new Font("Monospaced", Font.PLAIN, 12));
			value.setPreferredSize(new Dimension(46, 18));
			row.add(value);

			row.add(new JLabel(in.label()));

			if (in.code == ioX) {
				row.add(tag("X", X_TAG));
			}
			if (in.code == ioY) {
				row.add(tag("Y", Y_TAG));
			}

			ioPanel.add(row);
			dots.put(in.code, dot);
			values.put(in.code, value);
			names.put(in.code, in.defaultName);
			lastState.put(in.code, in.value);
			applyState(in.code, in.value);
		}
		ioPanel.revalidate();
		ioPanel.repaint();
	}

	private JLabel tag(String text, Color bg) {
		JLabel l = new JLabel(" " + text + " ");
		l.setOpaque(true);
		l.setBackground(bg);
		l.setForeground(Color.WHITE);
		l.setFont(l.getFont().deriveFont(Font.BOLD));
		return l;
	}

	private void updateTcpLabel() {
		GiaTcp sel = contribution.getSelectedTcp();
		if (sel == null) {
			tcpLabel.setText(t.t("DIAG_NO_TCP_SELECTED"));
			return;
		}
		tcpLabel.setText("TCP: " + sel.name + "    IO X = " + codeName(sel.ioX) + "    IO Y = " + codeName(sel.ioY));
	}

	private String codeName(int code) {
		if (code < 0) {
			return "(unset)";
		}
		String n = names.get(code);
		return n != null ? n : "code " + code;
	}

	public void startMonitor() {
		if (running) {
			return;
		}
		if (dots.isEmpty()) {
			buildIoRows();
		}
		running = true;
		startBtn.setEnabled(false);
		stopBtn.setEnabled(true);

		poseThread = new Thread(new Runnable() {
			@Override
			public void run() {
				while (running) {
					double[] p = contribution.getLiveTcpPoseSi();
					if (p != null) {
						latestPose = p;
					}
					try {
						Thread.sleep(POSE_PERIOD_MS);
					} catch (InterruptedException e) {
						return;
					}
				}
			}
		}, "gia-tcp-diag-pose");
		poseThread.setDaemon(true);
		poseThread.start();

		uiTimer = new Timer(UI_PERIOD_MS, e -> tick());
		uiTimer.start();
	}

	public void stopMonitor() {
		running = false;
		if (uiTimer != null) {
			uiTimer.stop();
			uiTimer = null;
		}
		if (poseThread != null) {
			poseThread.interrupt();
			poseThread = null;
		}
		startBtn.setEnabled(true);
		stopBtn.setEnabled(false);
	}

	private void tick() {
		// IO via the IO API (EDT-safe).
		List<LiveInput> inputs = contribution.readDigitalInputsLive();
		for (LiveInput in : inputs) {
			Boolean prev = lastState.get(in.code);
			if (prev == null) {
				// New input appeared (config change) — rebuild rows next refresh; track it now.
				lastState.put(in.code, in.value);
				continue;
			}
			if (prev.booleanValue() != in.value) {
				logEdge(in.code, in.value);
				lastState.put(in.code, in.value);
			}
			applyState(in.code, in.value);
		}
		// Pose from the cached realtime snapshot.
		double[] p = latestPose;
		if (p == null) {
			poseLabel.setText("X --- Y --- Z ---  mm    RX --- RY --- RZ ---  deg");
		} else {
			poseLabel.setText(String.format(Locale.US,
					"X %8.2f Y %8.2f Z %8.2f  mm    RX %7.2f RY %7.2f RZ %7.2f  deg",
					p[0] * 1000, p[1] * 1000, p[2] * 1000,
					Math.toDegrees(p[3]), Math.toDegrees(p[4]), Math.toDegrees(p[5])));
		}
	}

	private void applyState(int code, boolean value) {
		JLabel dot = dots.get(code);
		JLabel val = values.get(code);
		if (dot != null) {
			dot.setBackground(value ? HIGH : LOW);
		}
		if (val != null) {
			val.setText(value ? "HIGH" : "LOW");
		}
	}

	private void logEdge(int code, boolean value) {
		double[] p = latestPose;
		String dir = value ? "0->1 HIGH" : "1->0 LOW ";
		String where;
		if (p == null) {
			where = "pose n/a";
		} else {
			where = String.format(Locale.US, "X=%.2f Y=%.2f Z=%.2f mm",
					p[0] * 1000, p[1] * 1000, p[2] * 1000);
		}
		String line = String.format("%s  %-16s %s  @ %s%n",
				clock.format(new Date()), names.get(code), dir, where);
		log.append(line);
		log.setCaretPosition(log.getDocument().getLength());
	}

	private KeyboardInputFactory kf() {
		return contribution.getApiProvider().getUserInterfaceAPI().getUserInteraction().getKeyboardInputFactory();
	}

	private void onRepeatability() {
		final GiaTcp sel = contribution.getSelectedTcp();
		if (sel == null || !sel.isReadyForCalibration()) {
			log.append("Repeatability test: select a fully set-up TCP first (variant, IOs, reference, center).\n");
			return;
		}
		final int runs = Math.max(1, Ui.parseInt(runsField.getText()));
		repeatBtn.setEnabled(false);
		log.append("Starting repeatability test: " + runs + " probe(s). The robot will move...\n");
		new Thread(new Runnable() {
			@Override
			public void run() {
				// Injected primary program: in Local mode the controller ignores it and the
				// test would just burn the 120 s timeout, so fail fast like the other flows.
				if (Boolean.FALSE.equals(contribution.isInRemoteControl())) {
					SwingUtilities.invokeLater(new Runnable() {
						@Override
						public void run() {
							log.append(t.t("REF_LOCAL_MODE") + "\n");
							log.setCaretPosition(log.getDocument().getLength());
							repeatBtn.setEnabled(true);
						}
					});
					return;
				}
				final RepeatabilityResult result = contribution.runRepeatability(sel, runs);
				SwingUtilities.invokeLater(new Runnable() {
					@Override
					public void run() {
						log.append(result.report());
						log.setCaretPosition(log.getDocument().getLength());
						repeatBtn.setEnabled(true);
					}
				});
			}
		}, "gia-tcp-repeatability").start();
	}

	private void onMoveToCenter() {
		final GiaTcp sel = contribution.getSelectedTcp();
		if (sel == null || !sel.isCenterTaught()) {
			SwingUtilities.invokeLater(new Runnable() {
				@Override
				public void run() {
					log.append("Move to center: no taught center for the selected TCP.\n");
				}
			});
			return;
		}
		contribution.moveToCenter(sel);
	}
}
