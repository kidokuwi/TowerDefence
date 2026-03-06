import java.awt.Point;
import java.util.Arrays;
import java.util.List;

/**
 * Defines the waypoint path that balloons follow.
 * All coordinates are in pixels for the 800×600 game area.
 */
public class Path {
    private static final List<Point> WAYPOINTS = Arrays.asList(
            new Point(-20, 100),
            new Point(100, 100),
            new Point(100, 220),
            new Point(220, 220),
            new Point(220, 100),
            new Point(380, 100),
            new Point(380, 340),
            new Point(140, 340),
            new Point(140, 460),
            new Point(380, 460),
            new Point(380, 540),
            new Point(540, 540),
            new Point(540, 220),
            new Point(660, 220),
            new Point(660, 460),
            new Point(780, 460),
            new Point(820, 460));

    public static List<Point> getWaypoints() {
        return WAYPOINTS;
    }

    /** Returns the total pixel length of the path (used for sorting). */
    public static double totalLength() {
        double len = 0;
        List<Point> wp = WAYPOINTS;
        for (int i = 1; i < wp.size(); i++) {
            len += wp.get(i - 1).distance(wp.get(i));
        }
        return len;
    }
}
