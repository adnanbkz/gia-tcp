package com.GIA.GIATcp.tcpcalibration;
import com.GIA.GIATcp.tcpcalibration.math.TCPCalibrationMaths;
import com.GIA.GIATcp.tcpcalibration.probe.CalibrationServer;

import com.GIA.GIATcp.installation.InstallationContribution;
import com.GIA.GIATcp.installation.model.CalibParams;
import com.GIA.GIATcp.installation.model.GiaTcp;
import com.GIA.GIATcp.program.errhandling.ProgErrHandlingService;
import com.GIA.GIATcp.util.Const;
import com.GIA.GIATcp.util.UrScript;
import com.ur.urcap.api.contribution.ProgramNodeContribution;
import com.ur.urcap.api.contribution.program.ProgramAPIProvider;
import com.ur.urcap.api.domain.ProgramAPI;
import com.ur.urcap.api.domain.data.DataModel;
import com.ur.urcap.api.domain.program.ProgramModel;
import com.ur.urcap.api.domain.program.nodes.ProgramNodeFactory;
import com.ur.urcap.api.domain.program.nodes.contributable.URCapProgramNode;
import com.ur.urcap.api.domain.program.structure.TreeNode;
import com.ur.urcap.api.domain.program.structure.TreeStructureException;
import com.ur.urcap.api.domain.script.ScriptWriter;
import com.ur.urcap.api.domain.undoredo.UndoRedoManager;
import com.ur.urcap.api.domain.undoredo.UndoableChanges;
import com.ur.urcap.api.domain.userinteraction.robot.movement.MovementCompleteEvent;
import com.ur.urcap.api.domain.userinteraction.robot.movement.RobotMovementCallback;
import com.ur.urcap.api.domain.value.Pose;
import com.ur.urcap.api.domain.value.simple.Angle;
import com.ur.urcap.api.domain.value.simple.Length;
import com.ur.urcap.api.domain.variable.Variable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;

/**
 * Program-node contribution for the GIA TCP node. It calibrates the TCP <b>at runtime</b>:
 * {@link #generateScript} emits a call to {@code tcpc__rtCalib}, which drives the realtime
 * {@code moveC} probing and asks the persistent {@link CalibrationServer} (Java) to do ALL the
 * geometry via {@link TCPCalibrationMaths}. The corrected TCP comes back in {@code tcpc__rtTcp}.
 *
 * <p>Full parity with the legacy action node: Check / Validate / Recalibrate, assignment to a
 * program variable, the auto-inserted "If Error" child, and per-axis tolerances. The same Java
 * maths also backs the installation "test" button (live, over the Secondary interface).
 */
public class TCPCalibrationContribution implements ProgramNodeContribution {

	private static final Logger logger = LogManager.getLogger(MethodHandles.lookup().lookupClass());

	private final ProgramAPIProvider apiProvider;
	private final ProgramAPI programAPI;
	private final ProgramModel programModel;
	private final UndoRedoManager undoRedoManager;
	private final TCPCalibrationView view;
	private final DataModel model;

	public TCPCalibrationContribution(ProgramAPIProvider apiProvider, TCPCalibrationView view, DataModel model) {
		this.apiProvider = apiProvider;
		this.programAPI = apiProvider.getProgramAPI();
		this.programModel = programAPI.getProgramModel();
		this.undoRedoManager = programAPI.getUndoRedoManager();
		this.view = view;
		this.model = model;
	}

	/**
	 * Opens the guarded move-robot screen targeting the selected TCP's taught centre and
	 * runs {@code onArrived} once the robot reaches it. Backing out of the move screen
	 * fires no callback, so callers must not lock UI state before it runs.
	 */
	public void requestMoveToCenter(final Runnable onArrived) {
		GiaTcp tcp = getSelectedTcp();
		if (tcp == null || !tcp.isCenterTaught()) {
			return;
		}
		double[] c = tcp.centerPose;
		Pose pose = programAPI.getValueFactoryProvider().getPoseFactory()
				.createPose(c[0], c[1], c[2], c[3], c[4], c[5], Length.Unit.M, Angle.Unit.RAD);
		apiProvider.getUserInterfaceAPI().getUserInteraction().getRobotMovement()
				.requestUserToMoveRobot(pose, new RobotMovementCallback() {
					@Override
					public void onComplete(MovementCompleteEvent movementCompleteEvent) {
						if (onArrived != null) {
							onArrived.run();
						}
					}
				});
	}

	/** Wraps every DataModel / program-tree mutation in an UndoableChanges scope (required). */
	private void edit(final Runnable change) {
		undoRedoManager.recordChanges(new UndoableChanges() {
			@Override
			public void executeChanges() {
				change.run();
			}
		});
	}

	/** Inserts the "If Error" child node exactly once, the first time the view is opened. */
	private void ensureErrorHandlingChild() {
		if (model.get(Const.K_ACT_CHILD_DONE, false)) {
			return;
		}
		undoRedoManager.recordChanges(new UndoableChanges() {
			@Override
			public void executeChanges() {
				try {
					TreeNode root = programModel.getRootTreeNode(TCPCalibrationContribution.this);
					if (!root.getChildren().isEmpty()) {
						model.set(Const.K_ACT_CHILD_DONE, true);
						return;
					}
					ProgramNodeFactory factory = programModel.getProgramNodeFactory();
					URCapProgramNode errNode = factory.createURCapProgramNode(ProgErrHandlingService.class);
					root.addChild(errNode);
					model.set(Const.K_ACT_CHILD_DONE, true);
				} catch (TreeStructureException e) {
					logger.debug("Could not insert If Error child node", e);
				} catch (RuntimeException e) {
					logger.debug("Unexpected error inserting If Error child node", e);
				}
			}
		});
	}

	// ---------------- installation / selection ----------------

	public InstallationContribution getInstallation() {
		return programAPI.getInstallationNode(InstallationContribution.class);
	}

	public int getTcpId() {
		return model.get(Const.K_ACT_TCPID, firstTcpId());
	}

	public void setTcpId(final int id) {
		edit(() -> model.set(Const.K_ACT_TCPID, id));
	}

	private int firstTcpId() {
		InstallationContribution inst = getInstallation();
		if (inst != null) {
			int first = inst.getStore().firstExistingId();
			return first == 0 ? 1 : first;
		}
		return 1;
	}

	public GiaTcp getSelectedTcp() {
		InstallationContribution inst = getInstallation();
		return inst == null ? null : inst.getStore().load(getTcpId());
	}

	// ---------------- node options ----------------

	public int getAction() {
		return model.get(Const.K_ACT_ACTION, Const.ACTION_RECALIBRATE);
	}

	public void setAction(final int action) {
		edit(() -> model.set(Const.K_ACT_ACTION, action));
	}

	public int getSpeed() {
		return model.get(Const.K_ACT_SPEED, 1);
	}

	public void setSpeed(final int speed) {
		edit(() -> model.set(Const.K_ACT_SPEED, speed));
	}

	public double getApproachZ() {
		return model.get(Const.K_ACT_APPROACHZ, Const.DEF_APPROACHZ_MM);
	}

	public void setApproachZ(final double v) {
		edit(() -> model.set(Const.K_ACT_APPROACHZ, v));
	}

	public double getImmerseZ() {
		return model.get(Const.K_ACT_IMMERSEZ, Const.DEF_IMMERSEZ_MM);
	}

	public void setImmerseZ(final double v) {
		edit(() -> model.set(Const.K_ACT_IMMERSEZ, v));
	}

	public boolean isSetTcpAfter() {
		return model.get(Const.K_ACT_SET_AFTER, true);
	}

	public void setSetTcpAfter(final boolean v) {
		edit(() -> model.set(Const.K_ACT_SET_AFTER, v));
	}

	public boolean isAdjustAngle() {
		return model.get(Const.K_ACT_ADJANGLE, false);
	}

	public void setAdjustAngle(final boolean v) {
		edit(() -> model.set(Const.K_ACT_ADJANGLE, v));
	}

	public double getOffsetZ() {
		return model.get(Const.K_ACT_OFFZ, Const.DEF_OFFZ_MM);
	}

	public void setOffsetZ(final double v) {
		edit(() -> model.set(Const.K_ACT_OFFZ, v));
	}

	public boolean isErrHandling() {
		return model.get(Const.K_ACT_ERRH, true);
	}

	public void setErrHandling(final boolean v) {
		edit(() -> model.set(Const.K_ACT_ERRH, v));
	}

	/** When set, a successful run stores its correction as this TCP's installation reference. */
	public boolean isPersistReference() {
		return model.get(Const.K_ACT_PERSIST_REF, false);
	}

	public void setPersistReference(final boolean v) {
		edit(() -> model.set(Const.K_ACT_PERSIST_REF, v));
	}

	public boolean isUseCustomVar() {
		return model.get(Const.K_ACT_USE_CUSTOM_VAR, false);
	}

	public void setUseCustomVar(final boolean v) {
		edit(() -> model.set(Const.K_ACT_USE_CUSTOM_VAR, v));
	}

	public String getCustomVar() {
		return model.get(Const.K_ACT_CUSTOM_VAR, "");
	}

	public void setCustomVar(final String v) {
		edit(() -> model.set(Const.K_ACT_CUSTOM_VAR, v));
	}

	/** Symmetric +/- allowed deviation (mm) for an axis in {X,Y,Z} or the diameter ("D"). */
	public double getTol(String axis) {
		return model.get(String.format(Const.K_TOL, axis),
				"D".equals(axis) ? Const.DEF_TOL_DIAM_MM : Const.DEF_TOL_MM);
	}

	public void setTol(final String axis, final double v) {
		edit(() -> model.set(String.format(Const.K_TOL, axis), Math.abs(v)));
	}

	public List<String> getVariableNames() {
		List<String> names = new ArrayList<String>();
		for (Variable v : programAPI.getVariableModel().getAll()) {
			names.add(v.getDisplayName());
		}
		return names;
	}

	// ---------------- lifecycle ----------------

	@Override
	public void openView() {
		ensureErrorHandlingChild();
		view.refresh(this);
	}

	@Override
	public void closeView() {
	}

	@Override
	public String getTitle() {
		return TCPCalibrationService.NODE_TITLE;
	}

	@Override
	public boolean isDefined() {
		GiaTcp tcp = getSelectedTcp();
		return tcp != null && tcp.isReadyForCalibration();
	}

	/**
	 * Why the node is "not defined" (yellow), as a localization key, or {@code null} when
	 * everything is in place. Lets the view tell the user exactly which setup step is missing
	 * instead of leaving them with an unexplained yellow node.
	 */
	public String readinessIssueKey() {
		InstallationContribution inst = getInstallation();
		if (inst == null) {
			return "TC_ISSUE_NO_INSTALL";
		}
		GiaTcp tcp = getSelectedTcp();
		if (tcp == null || !tcp.exists) {
			return "TC_ISSUE_NO_TCP";
		}
		if (!tcp.variant.isSelectable()) {
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
		if (!inst.isRefResolvable(tcp.refTcp)) {
			return "TC_ISSUE_REF_UNRESOLVABLE";
		}
		return null;
	}

	// ---------------- runtime script ----------------

	@Override
	public void generateScript(ScriptWriter writer) {
		InstallationContribution inst = getInstallation();
		GiaTcp tcp = getSelectedTcp();
		if (inst == null || tcp == null || !tcp.isReadyForCalibration()) {
			writer.appendLine("popup(\"GIA TCP: TCP not fully set up\", title=\"GIA TCP\", blocking=True)");
			writer.appendLine("halt");
			return;
		}
		if (!inst.isRefResolvable(tcp.refTcp)) {
			// Resolving would fall back to identity and silently calibrate against the base frame.
			writer.appendLine("popup(\"GIA TCP: reference TCP not found (renamed or deleted)\", title=\"GIA TCP\", blocking=True)");
			writer.appendLine("halt");
			return;
		}
		CalibParams p = tcp.params;
		double[] refPose = inst.resolveTcpPoseSi(tcp.refTcp);

		double factor = getSpeed() == 0 ? 0.5 : (getSpeed() == 2 ? 1.5 : 1.0);
		String acc = UrScript.num(p.accelMmS2 / 1000.0);
		String vel = UrScript.num(p.speedMmS * factor / 1000.0);
		String radius = UrScript.num(p.radiusMm);
		String overrun = UrScript.num(p.overrunDeg);
		String zSearch = UrScript.num(p.signedSearchZMm());
		String zImmerse = UrScript.num(p.signedImmerseZMm(getImmerseZ()));
		String approachZ = UrScript.num(p.signedApproachZMm(getApproachZ()) / 1000.0);
		int adj = isAdjustAngle() ? 1 : 0;
		boolean persist = isPersistReference();
		// Referencing measures + stores a baseline, so it must never fail on the tolerance band.
		String tolX = persist ? "999" : UrScript.num(getTol("X"));
		String tolY = persist ? "999" : UrScript.num(getTol("Y"));
		String tolZ = persist ? "999" : UrScript.num(getTol("Z"));
		// Diameter (CAPTRON semantics): referencing stores the RAW measurement (offset 0, no
		// band); runtime corrects the sensor bias with real - referenced and checks the band
		// against the real diameter (or against the referenced one when no real is configured).
		// Without a referenced baseline the bias is unknown, so the band stays disabled.
		double diamOffMm = !persist && p.realDiameterMm > 0 && tcp.calibrated && tcp.diameterMm > 0
				? p.realDiameterMm - tcp.diameterMm : 0.0;
		double diamNomMm = tcp.calibrated
				? (p.realDiameterMm > 0 ? p.realDiameterMm : tcp.diameterMm) : 0.0;
		String tolD = persist ? "0" : UrScript.num(getTol("D"));

		// Handshake line the Java CalibrationServer parses (mm + bare-CSV poses).
		// INIT;pRef;refTcp;radiusMm;tolXmm;tolYmm;tolZmm;adj;offZmm;maxRx;maxRy;diamOffMm;tcpId;persist;tolDmm;diamNomMm;pStart
		// pRef (correction reference) is the pose measured at referencing when there is one
		// (CAPTRON h()); the circle still runs around the taught centre (pStart, CAPTRON j()).
		String initLine = "INIT;" + UrScript.poseCsv(tcp.correctionRefPose()) + ";" + UrScript.poseCsv(refPose)
				+ ";" + UrScript.num(p.radiusMm) + ";" + tolX + ";" + tolY
				+ ";" + tolZ + ";" + adj + ";" + UrScript.num(getOffsetZ())
				+ ";" + UrScript.num(p.maxAngleRxDeg) + ";" + UrScript.num(p.maxAngleRyDeg)
				+ ";" + UrScript.num(diamOffMm)
				+ ";" + getTcpId() + ";" + (persist ? 1 : 0)
				+ ";" + tolD + ";" + UrScript.num(diamNomMm)
				+ ";" + UrScript.poseCsv(tcp.centerPose);

		writer.appendLine("# --- GIA TCP (runtime calibration, maths in Java) ---");
		// CAPTRON parity: Check/Validate must not leak the reference TCP into the rest of
		// the program, so the previous TCP is backed up and restored at the end. Recalibrate
		// intentionally does not restore (its point is to continue with the corrected TCP).
		boolean restoreTcp = getAction() != Const.ACTION_RECALIBRATE;
		if (restoreTcp) {
			writer.appendLine("giaTcpBak = get_tcp_offset()");
		}
		// Probe with the reference TCP active and start above the cross for a safe approach.
		writer.appendLine("set_tcp(" + UrScript.pose(refPose) + ")");
		writer.appendLine("giaTcpApproach = pose_trans(" + UrScript.pose(tcp.centerPose)
				+ ", p[0,0," + approachZ + ",0,0,0])");
		writer.appendLine("movel(giaTcpApproach, a=" + acc + ", v=" + vel + ")");
		writer.appendLine("movel(" + UrScript.pose(tcp.centerPose) + ", a=" + acc + ", v=" + vel + ")");
		writer.appendLine("tcpc__rtCalib(\"127.0.0.1\", " + CalibrationServer.PORT + ", \"" + initLine + "\", "
				+ tcp.ioX + ", " + tcp.ioY + ", " + radius + ", " + acc + ", " + vel + ", " + overrun + ", "
				+ zSearch + ", " + zImmerse + ")");
		// CAPTRON parity: retract to the approach height after the action (ok or not), so the
		// program never continues its path from between the sensor forks.
		writer.appendLine("movel(giaTcpApproach, a=" + acc + ", v=" + vel + ")");

		// Status: 0 = OK, 4 = OUT_OF_TOLERANCE (measured, but outside the band). Check accepts both
		// (tool found); Validate/Recalibrate require an in-tolerance result.
		String okExpr = getAction() == Const.ACTION_CHECK
				? "(tcpc__rtStatus == 0 or tcpc__rtStatus == 4)" : "(tcpc__rtStatus == 0)";
		writer.appendLine("global giaTcpOk = " + okExpr);

		if (getAction() == Const.ACTION_RECALIBRATE) {
			String var = isUseCustomVar() && !getCustomVar().isEmpty() ? getCustomVar() : Const.DEFAULT_RECALIB_VAR;
			writer.appendLine("if (tcpc__rtStatus == 0):");
			writer.appendLine("  global " + var + " = tcpc__rtTcp");
			if (isSetTcpAfter()) {
				writer.appendLine("  set_tcp(tcpc__rtTcp)");
			}
			writer.appendLine("end");
		}

		if (isErrHandling()) {
			writer.ifCondition("not giaTcpOk");
			writer.writeChildren();
			writer.end();
		}

		// Restore last (as CAPTRON): the If-Error children still run with the reference
		// TCP active, which is what centre-relative recovery motion needs.
		if (restoreTcp) {
			writer.appendLine("set_tcp(giaTcpBak)");
		}
	}
}
