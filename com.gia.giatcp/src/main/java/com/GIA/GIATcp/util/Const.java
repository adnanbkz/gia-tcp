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
	//        calibrator below, 2 crossed FGL50 light barriers, ~Ø30 mm clear aperture) -----
	public static final double DEF_RADIUS_MM = 10.0;     // Ø20 probe circle -> ~5 mm clearance in a Ø30 aperture
	public static final double DEF_SPEED_MM_S = 30.0;    // gentle base; node Speed = Slow/Normal/Fast scales it
	public static final double DEF_ACCEL_MM_S2 = 100.0;
	public static final double DEF_OVERRUN_DEG = 10.0;
	public static final double DEF_SEARCHZ_MM = 12.0;    // retract stroke to clear the beams (limits travel into the fixture)
	public static final boolean DEF_INVERTZ = true;      // tool +Z down -> search retracts UP to clear, immerse advances DOWN
	public static final double DEF_REALDIAM_MM = 0.0;
	public static final boolean DEF_ADJANGLE = false;
	public static final int DEF_ITER = 1;
	public static final double DEF_OFFZ_MM = 5.0;
	public static final double DEF_ACCURACY_DEG = 0.5;
	public static final double DEF_MAXANGLE_DEG = 10.0;

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

	public static final double DEF_APPROACHZ_MM = 50.0;  // start 50 mm above the cross (safe approach from above)
	public static final double DEF_IMMERSEZ_MM = 5.0;    // advance up to 5 mm past the taught centre to re-find the beam plane
	public static final double DEF_TOL_MM = 1.0; // default +/- allowed deviation per axis

	// ----- Error-handling child node keys -----
	public static final String K_ERR_RETRY_ENABLED = "errRetryEnabled"; // boolean
	public static final String K_ERR_RETRY_COUNT = "errRetryCount";     // int
	public static final int DEF_ERR_RETRY_COUNT = 1;
	public static final String K_ERR_FOLDER_DONE = "errFolderDone";     // boolean: placeholder folder inserted

	// Default recalibration variable name written by the script
	public static final String DEFAULT_RECALIB_VAR = "giaActionTCP";

	// Loopback port the live calibration controller listens on for the robot's result line
	public static final int CALIB_RETURN_PORT = 5510;
}
