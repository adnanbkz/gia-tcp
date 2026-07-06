package com.GIA.GIATcp.util;

/**
 * Shared constants: branding strings, data-model keys and default parameter values.
 * Centralised so the installation and program nodes agree on key names.
 */
public final class Const {

	private Const() {
	}

	// ----- Branding -----
	public static final String VENDOR = "GIA Robotics SL";
	public static final String INSTALL_TITLE = "GIA TCP Calibrator";
	public static final String ACTION_TITLE = "GIA TCP";
	public static final String ERR_TITLE = "If Error";
	public static final String DEFAULT_TCP_NAME = "GIA TCP #";

	// ----- Service ids -----
	public static final String ACTION_ID = "com.GIA.GIATcp.action";
	public static final String ERR_ID = "com.GIA.GIATcp.errhandling";

	// ----- Capacity -----
	public static final int MAX_TCP = 30;

	// ----- Installation data-model keys -----
	public static final String KEY_SELECTED = "giaSelected";
	public static final String KEY_ERR_INTERRUPT = "giaErrInterrupt";   // boolean
	public static final String KEY_DEBUG_LVL = "giaDebugLvl";           // int 0/1/2
	public static final boolean DEF_ERR_INTERRUPT = true;
	public static final int DEF_DEBUG_LVL = 1;

	// Per-TCP key prefixes (suffix is the 1-based slot id)
	public static final String K_EXISTS = "t%d.exists";
	public static final String K_NAME = "t%d.name";
	public static final String K_VARIANT = "t%d.variant";    // ordinal
	public static final String K_IOX = "t%d.iox";            // int code
	public static final String K_IOY = "t%d.ioy";            // int code
	public static final String K_REF = "t%d.ref";            // reference TCP display name
	public static final String K_CENTER = "t%d.center";      // pose csv (SI: m, rad)
	public static final String K_CALIBRATED = "t%d.calibd";  // boolean
	public static final String K_CORR = "t%d.corr";          // pose csv (SI: m, rad)
	public static final String K_DIAM = "t%d.diam";          // double mm
	public static final String K_REFPOSE = "t%d.refpose";    // pose csv (SI): pose measured at referencing (CAPTRON h())
	// params
	public static final String K_RADIUS = "t%d.radius";
	public static final String K_SPEED = "t%d.speed";
	public static final String K_ACCEL = "t%d.accel";
	public static final String K_OVERRUN = "t%d.overrun";
	public static final String K_SEARCHZ = "t%d.searchz";
	public static final String K_INVERTZ = "t%d.invertz";
	public static final String K_REALDIAM = "t%d.realdiam";
	public static final String K_ADJANGLE = "t%d.adjangle";
	public static final String K_ITER = "t%d.iter";
	public static final String K_OFFZ = "t%d.offz";
	public static final String K_ACC = "t%d.acc";
	public static final String K_MAXRX = "t%d.maxrx";
	public static final String K_MAXRY = "t%d.maxry";

	// ----- Default calibration parameters (tuned for top-down: torch pointing down into a
	//        calibrator below, 2 crossed FGL50 light barriers, probing the WIRE tip) -----
	// Radius floor: to arm the edge capture both beams must be FREE at the same time, and on
	// the circle that only happens near the 45 deg bisectors -> radius > (tool radius + beam
	// half-width + teach margin) / sin(45). Wire Ø~1.2 -> 6 mm is comfortable. Probing a
	// Ø16-20 NOZZLE instead needs radius >= ~13-16 mm: raise it per-TCP in the wizard.
	public static final double DEF_RADIUS_MM = 6.0;      // Ø12 probe circle (wire tip); ~half the lap of the old Ø20
	public static final double DEF_SPEED_MM_S = 30.0;    // gentle base; node Speed = Slow/Normal/Fast scales it
	public static final double DEF_ACCEL_MM_S2 = 100.0;
	// Must exceed the blocked-arc half-angle at the circle start (~asin(half occlusion/radius)):
	// wire on a 6 mm radius is ~10 deg, so do NOT lower this together with small radii.
	public static final double DEF_OVERRUN_DEG = 10.0;
	public static final double DEF_SEARCHZ_MM = 8.0;     // retract stroke to clear the beams (> coplanarity + beam + stop latency)
	public static final boolean DEF_INVERTZ = false;     // CAPTRON default: search uses -toolZ, immerse uses +toolZ
	public static final double DEF_REALDIAM_MM = 0.0;
	public static final boolean DEF_ADJANGLE = false;
	public static final int DEF_ITER = 1;
	public static final double DEF_OFFZ_MM = 5.0;
	public static final double DEF_ACCURACY_DEG = 0.5;
	public static final double DEF_MAXANGLE_DEG = 10.0;

	// ----- Wizard input ranges (SensoPart FGL 50 variant; CAPTRON-style keyboard limits).
	//        Out-of-range values are clamped at input so a typo (radius 60, overrun 2)
	//        fails visibly at the field instead of far away during the probe. -----
	public static final double MIN_RADIUS_MM = 2.0, MAX_RADIUS_MM = 20.0;    // fork window Ø50 incl. nozzle
	public static final double MIN_SPEED_MM_S = 5.0, MAX_SPEED_MM_S = 100.0; // edge-capture latency vs cycle
	public static final double MIN_ACCEL_MM_S2 = 20.0, MAX_ACCEL_MM_S2 = 2000.0;
	public static final double MIN_OVERRUN_DEG = 5.0, MAX_OVERRUN_DEG = 45.0; // CAPTRON 5-45
	public static final double MIN_SEARCHZ_MM = 3.0, MAX_SEARCHZ_MM = 50.0;
	public static final double MIN_REALDIAM_MM = 0.0, MAX_REALDIAM_MM = 25.0; // 0 = disabled
	public static final int MIN_ITER = 1, MAX_ITER = 10;
	public static final double MIN_OFFZ_MM = 1.0, MAX_OFFZ_MM = 20.0;
	public static final double MIN_ACCURACY_DEG = 0.05, MAX_ACCURACY_DEG = 5.0;
	// Radius floor physics (see DEF_RADIUS_MM): beam half-width + teach margin over sin 45.
	public static final double RADIUS_RULE_MARGIN_MM = 1.5;
	public static final double RADIUS_RULE_WIRE_DIAM_MM = 1.2; // assumed tool Ø when no real diameter set

	/** Minimum probe radius (mm) for a tool of {@code toolDiamMm} to arm the edge capture. */
	public static double minRadiusForTool(double toolDiamMm) {
		double d = toolDiamMm > 0 ? toolDiamMm : RADIUS_RULE_WIRE_DIAM_MM;
		return (d / 2.0 + RADIUS_RULE_MARGIN_MM) / Math.sin(Math.toRadians(45));
	}

	// ----- Program-node (GIA TCP) data-model keys -----
	public static final String K_ACT_TCPID = "actTcpId";       // int 1..30
	public static final String K_ACT_ACTION = "actAction";     // 0 check, 1 validate, 2 recalibrate
	public static final String K_ACT_SPEED = "actSpeed";       // 0 slow, 1 normal, 2 fast
	public static final String K_ACT_APPROACHZ = "actApproachZ"; // mm
	public static final String K_ACT_IMMERSEZ = "actImmerseZ";   // mm
	public static final String K_ACT_SET_AFTER = "actSetAfter";  // boolean
	public static final String K_ACT_ADJANGLE = "actAdjAngle";   // boolean
	public static final String K_ACT_ITER = "actIter";           // int
	public static final String K_ACT_OFFZ = "actOffZ";           // mm
	public static final String K_ACT_ERRH = "actErrHandling";    // boolean
	public static final String K_ACT_PERSIST_REF = "actPersistRef"; // boolean: store result as installation reference
	public static final String K_ACT_CHILD_DONE = "actChildDone"; // boolean: If-Error child inserted
	public static final String K_ACT_USE_CUSTOM_VAR = "actUseCustomVar"; // boolean
	public static final String K_ACT_CUSTOM_VAR = "actCustomVar";        // String
	// tolerances: a single symmetric +/- allowed deviation per axis (user-friendly).
	public static final String K_TOL = "actTol%s"; // %s in {X,Y,Z,D}; +/- tolerance in mm
	public static final String K_TOL_MAXRX = "actTolMaxRX";
	public static final String K_TOL_MAXRY = "actTolMaxRY";

	public static final int ACTION_CHECK = 0;
	public static final int ACTION_VALIDATE = 1;
	public static final int ACTION_RECALIBRATE = 2;

	public static final double DEF_APPROACHZ_MM = 30.0;  // start 30 mm above the cross (safe approach from above)
	public static final double DEF_IMMERSEZ_MM = 5.0;    // advance up to 5 mm past the taught centre to re-find the beam plane
	public static final double DEF_TOL_MM = 1.0; // default +/- allowed deviation per axis
	// Diameter is measured from only 4 chords, so its noise is larger than the axis noise.
	public static final double DEF_TOL_DIAM_MM = 2.0; // default +/- allowed diameter deviation

	// ----- Error-handling child node keys -----
	public static final String K_ERR_RETRY_ENABLED = "errRetryEnabled"; // boolean
	public static final String K_ERR_RETRY_COUNT = "errRetryCount";     // int
	public static final int DEF_ERR_RETRY_COUNT = 2; // CAPTRON default for "Try again x times"
	public static final String K_ERR_FOLDER_DONE = "errFolderDone";     // boolean: placeholder folder inserted

	// Default recalibration variable name written by the script
	public static final String DEFAULT_RECALIB_VAR = "giaActionTCP";

	// Loopback port the live calibration controller listens on for the robot's result line
	public static final int CALIB_RETURN_PORT = 5510;
}
