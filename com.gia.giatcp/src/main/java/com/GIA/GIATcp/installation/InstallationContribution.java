package com.GIA.GIATcp.installation;

import com.GIA.GIATcp.installation.calib.CalibrationController;
import com.GIA.GIATcp.installation.calib.CalibrationResult;
import com.GIA.GIATcp.installation.measure.RepeatabilityController;
import com.GIA.GIATcp.installation.measure.RepeatabilityResult;
import com.GIA.GIATcp.installation.model.CalibParams;
import com.GIA.GIATcp.installation.model.GiaTcp;
import com.GIA.GIATcp.installation.model.IoOption;
import com.GIA.GIATcp.installation.model.TcpStore;
import com.GIA.GIATcp.tcpcalibration.engine.CalibrationResultSink;
import com.GIA.GIATcp.tcpcalibration.math.TCPCalibrationMaths;
import com.GIA.GIATcp.tcpcalibration.probe.CalibrationServer;
import com.GIA.GIATcp.tcpcalibration.model.TCPCalibrationResult;
import com.GIA.GIATcp.tcpcalibration.engine.TCPCalibrationRunner;
import com.GIA.GIATcp.tcpcalibration.model.TCPCalibrationSpec;
import com.GIA.GIATcp.tcpcalibration.probe.SecondaryProbeTransport;
import com.GIA.GIATcp.locale.Texts;
import com.GIA.GIATcp.util.Const;
import com.GIA.GIATcp.util.ScriptResource;
import com.GIA.GIATcp.util.UrScript;
import com.GIA.GIATcp.util.comms.RobotRealtimeReader;
import com.GIA.GIATcp.util.comms.SecondaryScriptSender;
import com.ur.urcap.api.contribution.InstallationNodeContribution;
import com.ur.urcap.api.contribution.installation.CreationContext;
import com.ur.urcap.api.contribution.installation.InstallationAPIProvider;
import com.ur.urcap.api.domain.InstallationAPI;
import com.ur.urcap.api.domain.data.DataModel;
import com.ur.urcap.api.domain.io.DigitalIO;
import com.ur.urcap.api.domain.script.ScriptWriter;
import com.ur.urcap.api.domain.tcp.TCP;
import com.ur.urcap.api.domain.userinteraction.RobotPositionCallback2;
import com.ur.urcap.api.domain.userinteraction.robot.movement.MovementCompleteEvent;
import com.ur.urcap.api.domain.userinteraction.robot.movement.RobotMovement;
import com.ur.urcap.api.domain.userinteraction.robot.movement.RobotMovementCallback;
import com.ur.urcap.api.domain.value.Pose;
import com.ur.urcap.api.domain.value.robotposition.PositionParameters;
import com.ur.urcap.api.domain.value.simple.Angle;
import com.ur.urcap.api.domain.value.simple.Length;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class InstallationContribution implements InstallationNodeContribution, CalibrationResultSink {

	private static final Logger logger = LogManager.getLogger(MethodHandles.lookup().lookupClass());

	private final InstallationAPIProvider apiProvider;
	private final InstallationAPI installationAPI;
	private final InstallationView view;
	private final DataModel model;
	private final TcpStore store;
	private final CalibrationController calibration = new CalibrationController();
	private final RepeatabilityController repeatability = new RepeatabilityController();
	private final RobotRealtimeReader realtimeReader = new RobotRealtimeReader();
	private Texts texts;

	public InstallationContribution(InstallationAPIProvider apiProvider, InstallationView view, DataModel model,
			CreationContext context) {
		this.apiProvider = apiProvider;
		this.installationAPI = apiProvider.getInstallationAPI();
		this.view = view;
		this.model = model;
		this.store = new TcpStore(model);
		if (context.getNodeCreationType() == CreationContext.NodeCreationType.NEW) {
			model.set(Const.KEY_ERR_INTERRUPT, Const.DEF_ERR_INTERRUPT);
			model.set(Const.KEY_DEBUG_LVL, Const.DEF_DEBUG_LVL);
		}
		// Let a program-run calibration ("referencing" in Local mode) persist back here.
		CalibrationServer.setResultSink(this);
	}

	/**
	 * Callback the Setup wizard registers while passively waiting for a program-run
	 * referencing (Local mode, where "Start referencing" cannot inject motion). Fired on
	 * the EDT after the result has been persisted.
	 */
	public interface ReferencingListener {
		void onReferenced(int tcpId);
	}

	private volatile ReferencingListener referencingListener;

	/** Registers (or clears, with null) the passive-referencing callback. Last one wins. */
	public void setReferencingListener(ReferencingListener l) {
		this.referencingListener = l;
	}

	/**
	 * Persists a calibration measured by a running program (Play). Marshals to the EDT
	 * because it writes the DataModel and refreshes the view. {@link CalibrationResultSink}.
	 */
	@Override
	public void storeCalibration(final int tcpId, final double[] correctionSi, final double diameterMm,
			final double[] measuredPoseSi) {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				try {
					store.setCalibrationResult(tcpId, correctionSi, diameterMm, measuredPoseSi);
					view.refresh();
					ReferencingListener l = referencingListener;
					if (l != null) {
						l.onReferenced(tcpId);
					}
				} catch (RuntimeException e) {
					logger.warn("Failed to store referencing result for TCP {}", tcpId, e);
				}
			}
		});
	}

	// ---------------- lifecycle ----------------

	@Override
	public void openView() {
		view.refresh();
	}

	@Override
	public void closeView() {
		view.onClose();
	}

	@Override
	public void generateScript(ScriptWriter writer) {
		// NOTE: the legacy gia_tcp_*/gia__* library is NOT emitted here. It was only used by the
		// disabled old action node, it runs for EVERY program (preamble), and it was the source of
		// a compile failure on the controller. The active node and the installation buttons build
		// their own programs (CalibrationController/RepeatabilityController call ScriptLibrary.build
		// themselves; the program node + test path use the self-contained tcpc__ library below).
		//
		// Runtime calibration library (tcpc__*): defined once here so any number of GIA TCP
		// nodes can call tcpc__rtCalib without redefining it. ALL the geometry runs in the
		// Java CalibrationServer; the robot only does the realtime moveC + sensor capture.
		// Emit the library LINE BY LINE (writer.appendLine), NOT as one appendRaw blob. PolyScope
		// mis-tracks a multi-line appendRaw in the installation preamble: it corrupts the block
		// structure and yields a phantom "Syntax error: end" at the first def — which breaks EVERY
		// program, even one that never calls tcpc__. GIAWeld's external-axis (MotionPlus) preamble
		// hit the exact same wall and was fixed the same way (see MotionPlusPreamble.appendEthercatApi);
		// one line per appendLine is the robust pattern.
		String lib = ScriptResource.load("/scripts/tcpcalib.script");
		try (BufferedReader br = new BufferedReader(new StringReader(lib))) {
			String line;
			while ((line = br.readLine()) != null) {
				writer.appendLine(line);
			}
		} catch (IOException e) {
			logger.warn("Failed to emit the tcpc__ calibration library preamble", e);
		}
	}

	// ---------------- TCP store ----------------

	public TcpStore getStore() {
		return store;
	}

	public List<GiaTcp> getAllTcps() {
		return store.all();
	}

	public GiaTcp getSelectedTcp() {
		int id = store.getSelectedId();
		return id == 0 ? null : store.load(id);
	}

	public void setSelectedId(int id) {
		store.setSelectedId(id);
	}

	public int createTcp() {
		return store.create();
	}

	public void deleteSelected() {
		int id = store.getSelectedId();
		if (id != 0) {
			store.delete(id);
		}
	}

	public void renameSelected(String name) {
		int id = store.getSelectedId();
		if (id != 0) {
			store.setName(id, name);
		}
	}

	public void saveTcp(GiaTcp tcp) {
		store.save(tcp);
	}

	// ---------------- settings ----------------

	public boolean isErrInterrupt() {
		return model.get(Const.KEY_ERR_INTERRUPT, Const.DEF_ERR_INTERRUPT);
	}

	public void setErrInterrupt(boolean value) {
		model.set(Const.KEY_ERR_INTERRUPT, value);
	}

	public int getDebugLvl() {
		return model.get(Const.KEY_DEBUG_LVL, Const.DEF_DEBUG_LVL);
	}

	public void setDebugLvl(int value) {
		model.set(Const.KEY_DEBUG_LVL, value);
	}

	// ---------------- robot configuration helpers ----------------

	public List<IoOption> getDigitalInputs() {
		List<IoOption> options = new ArrayList<IoOption>();
		Collection<DigitalIO> ios = installationAPI.getIOModel().getIOs(DigitalIO.class);
		for (DigitalIO io : ios) {
			if (!io.isInput()) {
				continue;
			}
			int code = ioCode(io.getDefaultName());
			if (code >= 0) {
				options.add(new IoOption(code, io.getName() + " (" + io.getDefaultName() + ")"));
			}
		}
		return options;
	}

	/**
	 * Reads the current value of every usable digital input via the IO API.
	 * {@code DigitalIO.getValue()} reflects the live controller state, so this is a
	 * non-intrusive live read. Must be called on the Swing EDT (URCap API contract).
	 */
	public List<LiveInput> readDigitalInputsLive() {
		List<LiveInput> out = new ArrayList<LiveInput>();
		Collection<DigitalIO> ios = installationAPI.getIOModel().getIOs(DigitalIO.class);
		for (DigitalIO io : ios) {
			if (!io.isInput()) {
				continue;
			}
			int code = ioCode(io.getDefaultName());
			if (code >= 0) {
				out.add(new LiveInput(code, io.getName(), io.getDefaultName(), io.getValue()));
			}
		}
		return out;
	}

	/**
	 * Reads a live snapshot of the actual TCP pose from the realtime interface.
	 * Blocking socket I/O — call off the EDT.
	 *
	 * @return pose [x,y,z,rx,ry,rz] in SI units (m, rad), or {@code null} if unavailable.
	 */
	public double[] getLiveTcpPoseSi() {
		realtimeReader.readNow();
		return realtimeReader.getActualTcpPose();
	}

	/** Live state of one digital input: script code, names and current value. */
	public static final class LiveInput {
		public final int code;
		public final String name;
		public final String defaultName;
		public final boolean value;

		public LiveInput(int code, String name, String defaultName, boolean value) {
			this.code = code;
			this.name = name;
			this.defaultName = defaultName;
			this.value = value;
		}

		public String label() {
			return name + " (" + defaultName + ")";
		}
	}

	/** Maps a UR default IO name to the gia__getInput code, or -1 if not usable. */
	private static int ioCode(String defaultName) {
		if (defaultName == null) {
			return -1;
		}
		try {
			int open = defaultName.indexOf('[');
			int close = defaultName.indexOf(']');
			if (open < 0 || close < 0) {
				return -1;
			}
			int idx = Integer.parseInt(defaultName.substring(open + 1, close).trim());
			if (defaultName.startsWith("config_in")) {
				return 10 + idx;
			}
			if (defaultName.startsWith("digital_in")) {
				return idx;
			}
		} catch (NumberFormatException ignored) {
			return -1;
		}
		return -1;
	}

	public List<String> getReferenceTcpNames() {
		List<String> names = new ArrayList<String>();
		for (TCP tcp : installationAPI.getTCPModel().getTCPs()) {
			names.add(tcp.getDisplayName());
		}
		return names;
	}

	/** Resolves the named reference TCP to its offset pose in SI units (m, rad). */
	public double[] resolveTcpPoseSi(String displayName) {
		if (displayName != null) {
			for (TCP tcp : installationAPI.getTCPModel().getTCPs()) {
				if (displayName.equals(tcp.getDisplayName()) && tcp.isResolvable()) {
					return tcp.getOffset().toArray();
				}
			}
		}
		// Falling back to identity here would silently calibrate against the base frame;
		// callers gate on isRefResolvable() first, so this should only happen if the TCP
		// was renamed/deleted after setup.
		logger.warn("Reference TCP '{}' is not resolvable; using identity offset", displayName);
		return new double[6];
	}

	/** True if {@code displayName} names a TCP that currently resolves to a pose. */
	public boolean isRefResolvable(String displayName) {
		if (displayName == null || displayName.isEmpty()) {
			return false;
		}
		for (TCP tcp : installationAPI.getTCPModel().getTCPs()) {
			if (displayName.equals(tcp.getDisplayName()) && tcp.isResolvable()) {
				return true;
			}
		}
		return false;
	}

	// ---------------- teach / move ----------------

	/**
	 * Opens the move-robot panel; on confirmation stores the current TCP pose as the centre.
	 * The pose is only accepted when it was taught with the <b>reference TCP</b> active
	 * (CAPTRON parity): taught with any other TCP, the stored centre would belong to a
	 * different tool point and every later probe would silently carry that offset.
	 */
	public void teachCenter(final GiaTcp tcp) {
		apiProvider.getUserInterfaceAPI().getUserInteraction().getUserDefinedRobotPosition(new RobotPositionCallback2() {
			@Override
			public void onOk(PositionParameters positionParameters) {
				if (!isRefResolvable(tcp.refTcp)) {
					JOptionPane.showMessageDialog(null, texts().t("REF_ERR_REF_MISSING"),
							Const.INSTALL_TITLE, JOptionPane.ERROR_MESSAGE);
					return;
				}
				double[] active = positionParameters.getTCPOffset() == null
						? new double[6] : positionParameters.getTCPOffset().toArray();
				if (!sameOffset(active, resolveTcpPoseSi(tcp.refTcp))) {
					logger.warn("Centre pose rejected: taught with a TCP other than the reference '{}'", tcp.refTcp);
					JOptionPane.showMessageDialog(null, texts().t("WIZ_CENTER_WRONG_TCP", tcp.refTcp),
							Const.INSTALL_TITLE, JOptionPane.ERROR_MESSAGE);
					return;
				}
				tcp.centerPose = positionParameters.getPose().toArray();
				// A new centre invalidates the referenced pose (CAPTRON: teaching j() clears
				// h()): the old reference belongs to the old geometry, so re-reference.
				tcp.calibrated = false;
				tcp.refPose = new double[6];
				store.save(tcp);
				view.refresh();
			}
		});
	}

	/** Element-wise pose-offset equality with slack: 0.1 mm positional, ~0.06° rotational. */
	private static boolean sameOffset(double[] a, double[] b) {
		for (int i = 0; i < 3; i++) {
			if (Math.abs(a[i] - b[i]) > 1e-4) {
				return false;
			}
		}
		for (int i = 3; i < 6; i++) {
			if (Math.abs(a[i] - b[i]) > 1e-3) {
				return false;
			}
		}
		return true;
	}

	public void moveToCenter(GiaTcp tcp) {
		moveToCenter(tcp, null);
	}

	/**
	 * Opens the guarded move-robot screen targeting the taught centre and runs
	 * {@code onArrived} once the robot has reached it. If the user backs out of the move
	 * screen the callback simply never fires (the URCap API has no cancel event), so
	 * callers must not lock UI state before it runs.
	 */
	public void moveToCenter(GiaTcp tcp, final Runnable onArrived) {
		if (!tcp.isCenterTaught()) {
			return;
		}
		Pose pose = toPose(tcp.centerPose);
		RobotMovement movement = apiProvider.getUserInterfaceAPI().getUserInteraction().getRobotMovement();
		movement.requestUserToMoveRobot(pose, new RobotMovementCallback() {
			@Override
			public void onComplete(MovementCompleteEvent movementCompleteEvent) {
				if (onArrived != null) {
					onArrived.run();
				}
			}
		});
	}

	/**
	 * Activates this TCP's reference TCP on the controller (a one-line secondary program,
	 * as CAPTRON does before its guided move): the move-robot screen and the probe poses
	 * must both work with the reference TCP, not whatever TCP happens to be active.
	 * Blocking socket I/O — call off the EDT. No-op returning false if unresolvable/unreachable.
	 */
	public boolean activateReferenceTcp(GiaTcp tcp) {
		if (!isRefResolvable(tcp.refTcp)) {
			return false;
		}
		return activateTcp(resolveTcpPoseSi(tcp.refTcp));
	}

	/**
	 * Activates an arbitrary TCP offset (SI) on the robot over the Secondary interface
	 * (e.g. the node action's calibrated TCP before a guided move). Blocking socket I/O —
	 * call off the EDT. Returns false when unreachable (Local mode).
	 */
	public boolean activateTcp(double[] poseSi) {
		if (poseSi == null) {
			return false;
		}
		return new SecondaryScriptSender().send("set_tcp(" + UrScript.pose(poseSi) + ")\n");
	}

	private Pose toPose(double[] si) {
		return installationAPI.getValueFactoryProvider().getPoseFactory()
				.createPose(si[0], si[1], si[2], si[3], si[4], si[5], Length.Unit.M, Angle.Unit.RAD);
	}

	// ---------------- calibration ----------------

	/** Runs calibration/referencing on a worker thread and stores the result. */
	public CalibrationResult runCalibration(GiaTcp tcp) {
		double[] ref = resolveTcpPoseSi(tcp.refTcp);
		CalibrationResult result = calibration.calibrate(tcp, ref, isErrInterrupt(), getDebugLvl());
		if (result.isSuccess()) {
			// The wizard's run IS the referencing, so it re-bases (CAPTRON stores h() first
			// and computes against it): the legacy script's correction — measured vs the
			// hand-taught centre, i.e. teach error + deliberate teach immersion — is only
			// used to reconstruct the measured pose; the stored XYZ correction is identity.
			// A measured RX/RY (angle phase) is a real tool property and is kept.
			double[] measured = measuredPoseFrom(tcp.centerPose, result.correctionSi);
			double[] baseCorrection = { 0, 0, 0,
					result.correctionSi[3], result.correctionSi[4], result.correctionSi[5] };
			store.setCalibrationResult(tcp.id, baseCorrection, result.diameterMm, measured);
		}
		return result;
	}

	/**
	 * Reconstructs the measured beam-plane pose from a legacy-script result, which only
	 * reports the correction (measured vs the taught centre): correction = pSearchZ⁻¹·pRef
	 * with identical orientations, so pSearchZ = pRef · inv(translation of the correction).
	 */
	private static double[] measuredPoseFrom(double[] pRef, double[] correctionSi) {
		double[] translationOnly = { correctionSi[0], correctionSi[1], correctionSi[2], 0, 0, 0 };
		return TCPCalibrationMaths.poseTrans(pRef, TCPCalibrationMaths.poseInv(translationOnly));
	}

	public void stopCalibration() {
		calibration.stop();
	}

	/**
	 * Whether the controller is in Remote Control mode. {@code FALSE} means Local mode, where
	 * externally injected scripts are ignored (calibration would just time out); {@code null}
	 * means unknown (older PolyScope / dashboard unreachable) and must not block. Blocking
	 * dashboard query — call off the EDT.
	 */
	public Boolean isInRemoteControl() {
		return new com.GIA.GIATcp.util.comms.DashboardClient().isInRemoteControl();
	}

	/**
	 * Runs a live "test" calibration using the same Java maths the runtime node uses
	 * ({@link TCPCalibrationRunner} over the Secondary interface). Stores the correction so
	 * the Overview badge/readout update, and returns the full result (status + corrected TCP).
	 * Blocking — call off the EDT. Tolerances are wide here: a test only measures and reports.
	 */
	public TCPCalibrationResult runTestCalibration(GiaTcp tcp) {
		TCPCalibrationSpec spec = buildTestSpec(tcp);
		TCPCalibrationResult r = new TCPCalibrationRunner(new SecondaryProbeTransport()).calibrate(spec);
		CalibrationServer.recordResult(tcp.id, r);
		boolean measured = r.status == TCPCalibrationResult.Status.OK
				|| r.status == TCPCalibrationResult.Status.OUT_OF_TOLERANCE;
		if (measured && r.correction != null) {
			// CAPTRON parity: a manual calibration stores correction + diameter every time, but
			// only the FIRST run after teaching establishes the reference pose (referenceRun in
			// the spec: the runner reported an identity correction for it). Later tests must
			// NOT rebase it — the drift they are supposed to show would self-erase on each click.
			double[] refPoseUpdate = spec.referenceRun ? r.measuredPose : null;
			store.setCalibrationResult(tcp.id, r.correction, r.diameterMm, refPoseUpdate);
		}
		return r;
	}

	/**
	 * Aborts a live test probe (CAPTRON parity for the Stop button): a normal program sent
	 * over Secondary preempts the running probe program and stops the motion; the extra
	 * reply line unblocks the transport, which is otherwise waiting on its return socket.
	 * Blocking socket I/O — call off the EDT.
	 */
	public void stopTestCalibration() {
		SecondaryProbeTransport.sendStop(new SecondaryScriptSender());
	}

	private TCPCalibrationSpec buildTestSpec(GiaTcp tcp) {
		CalibParams p = tcp.params;
		TCPCalibrationSpec s = new TCPCalibrationSpec();
		s.pRef = tcp.correctionRefPose();
		s.pStart = tcp.centerPose;
		s.refTcp = resolveTcpPoseSi(tcp.refTcp);
		s.in1 = tcp.ioX;
		s.in2 = tcp.ioY;
		s.radiusMm = p.radiusMm;
		s.accMs2 = p.accelMmS2 / 1000.0;
		s.velMs = p.speedMmS / 1000.0;
		s.overrunDeg = p.overrunDeg;
		s.zSearchMm = p.signedSearchZMm();
		s.zImmerseMm = p.signedImmerseZMm(Const.DEF_IMMERSEZ_MM);
		s.adjustAngle = p.adjustAngle;
		s.orientationDzMm = p.offsetZMm;
		s.maxAngleRxDeg = p.maxAngleRxDeg;
		s.maxAngleRyDeg = p.maxAngleRyDeg;
		s.diamOffsetMm = 0.0;
		s.tolXYZm = new double[] { 0.999, 0.999, 0.999 }; // test = measure + report, never fail on band
		// The first run after teaching bootstraps the baseline (referencing semantics: the
		// measured pose becomes refPose and the correction is identity by construction).
		s.referenceRun = !(tcp.calibrated && tcp.hasRefPose());
		return s;
	}

	/** Runs a repeatability test (N probes, measure-only) on a worker thread. */
	public RepeatabilityResult runRepeatability(GiaTcp tcp, int runs) {
		double[] ref = resolveTcpPoseSi(tcp.refTcp);
		return repeatability.run(tcp, ref, runs, isErrInterrupt(), getDebugLvl());
	}

	public void stopRepeatability() {
		repeatability.stop();
	}

	public InstallationAPIProvider getApiProvider() {
		return apiProvider;
	}

	/** Localized UI strings for the current pendant locale (lazily built, English default). */
	public Texts texts() {
		if (texts == null) {
			texts = Texts.from(apiProvider.getSystemAPI().getSystemSettings().getLocalization());
		}
		return texts;
	}
}
