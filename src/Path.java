import java.awt.Point;
import java.util.Arrays;
import java.util.List;

/**
 * Defines the waypoint path that balloons follow.
 * All coordinates are in pixels for the 800×600 game area.
 */
public class Path {
    private static final List<Point> WAYPOINTS = Arrays.asList(
            new Point(-20, 90),
            new Point(100, 90),
            new Point(100, 200),
            new Point(230, 200),
            new Point(230, 90),
            new Point(370, 90),
            new Point(370, 320),
            new Point(130, 320),
            new Point(130, 450),
            new Point(370, 450),
            new Point(370, 550),
            new Point(530, 550),
            new Point(530, 200),
            new Point(660, 200),
            new Point(660, 450),
            new Point(780, 450),
            new Point(820, 450));

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
