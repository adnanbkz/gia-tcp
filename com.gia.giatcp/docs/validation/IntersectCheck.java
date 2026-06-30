// Standalone validation of the URScript gia__calc2DIntersect algebra (gl.script).
// Reproduces it exactly and checks known cases. Run: java /tmp/IntersectCheck.java
public class IntersectCheck {

    // Faithful port of gia__calc2DIntersect: returns {ix,iy} or null when parallel.
    static double[] calc2DIntersect(double[] pA, double[] pB, double[] pC, double[] pD) {
        double ax1 = pA[0], ay1 = pA[1];
        double dax = pB[0] - pA[0], day = pB[1] - pA[1];
        double bx1 = pC[0], by1 = pC[1];
        double dbx = pD[0] - pC[0], dby = pD[1] - pC[1];
        double denom = (dax * dby) - (day * dbx);
        if (Math.abs(denom) < 1e-9) return null; // URScript returns "0"
        double t = (((bx1 - ax1) * dby) - ((by1 - ay1) * dbx)) / denom;
        return new double[]{ ax1 + t * dax, ay1 + t * day };
    }

    static int passed = 0, failed = 0;

    static void check(String name, double[] got, Double ex, Double ey) {
        boolean ok;
        if (ex == null) { ok = (got == null); }
        else { ok = (got != null) && Math.abs(got[0]-ex) < 1e-9 && Math.abs(got[1]-ey) < 1e-9; }
        System.out.printf("[%s] %-34s got=%s%n", ok ? "PASS" : "FAIL", name,
                got == null ? "parallel/none" : String.format("(%.4f, %.4f)", got[0], got[1]));
        if (ok) passed++; else failed++;
    }

    public static void main(String[] a) {
        // 1) X axis chord x Y axis chord -> origin
        check("axes through origin",
                calc2DIntersect(new double[]{-1,0}, new double[]{1,0}, new double[]{0,-1}, new double[]{0,1}),
                0.0, 0.0);
        // 2) Horizontal y=2 x Vertical x=3 -> (3,2)
        check("offset cross (3,2)",
                calc2DIntersect(new double[]{-1,2}, new double[]{1,2}, new double[]{3,-1}, new double[]{3,5}),
                3.0, 2.0);
        // 3) Two parallel horizontals -> none
        check("parallel lines",
                calc2DIntersect(new double[]{0,0}, new double[]{1,0}, new double[]{0,1}, new double[]{1,1}),
                null, null);
        // 4) 45-degree line y=x  x  line y=-x+4 -> (2,2)
        check("diagonals meet (2,2)",
                calc2DIntersect(new double[]{0,0}, new double[]{1,1}, new double[]{0,4}, new double[]{4,0}),
                2.0, 2.0);
        // 5) Non-axis-aligned chords through a known centre (5,-3):
        //    line A dir (2,1) through centre; line B dir (1,-3) through centre.
        check("arbitrary chords (5,-3)",
                calc2DIntersect(new double[]{5-2,-3-1}, new double[]{5+2,-3+1},
                                new double[]{5-1,-3+3}, new double[]{5+1,-3-3}),
                5.0, -3.0);

        System.out.printf("%n%d passed, %d failed%n", passed, failed);
        if (failed > 0) System.exit(1);
    }
}
