import java.awt.*;
import java.util.List;

/**
 * A balloon entity that follows the path waypoints.
 * Level determines HP, speed, colour and reward.
 */
public class Balloon {

    // ── Constants ────────────────────────────────────────────────────────────
    private static final Color[] COLORS = {
            new Color(220, 50, 50), // 1 – Red
            new Color(70, 120, 220), // 2 – Blue
            new Color(60, 180, 60), // 3 – Green
            new Color(230, 200, 30), // 4 – Yellow
            new Color(220, 100, 180), // 5 – Pink
            new Color(40, 40, 40), // 6 – Black
    };
    private static final int[] BASE_HP = { 1, 2, 3, 5, 8, 14 };
    private static final double[] BASE_SPEED = { 1.5, 1.6, 1.75, 1.9, 2.1, 2.4 };

    // ── Fields ───────────────────────────────────────────────────────────────
    public int level; // 1-6
    public double hp;
    public double maxHp;
    public double speed;
    public double distanceTravelled;
    public int waypointIndex; // next waypoint to head toward
    public double x, y; // pixel position
    public boolean dead;
    public boolean reachedEnd;

    private final List<Point> waypoints;

    // ── Constructor ──────────────────────────────────────────────────────────
    public Balloon(int level, List<Point> waypoints) {
        this.level = Math.max(1, Math.min(level, 6));
        this.waypoints = waypoints;
        int idx = this.level - 1;
        this.maxHp = BASE_HP[idx];
        this.hp = this.maxHp;
        this.speed = BASE_SPEED[idx];
        this.waypointIndex = 0;
        this.distanceTravelled = 0;
        this.dead = false;
        this.reachedEnd = false;

        // Start at the first waypoint
        Point start = waypoints.get(0);
        this.x = start.x;
        this.y = start.y;
    }

    // ── Update ───────────────────────────────────────────────────────────────
    public void move() {
        if (dead || reachedEnd)
            return;

        double remaining = speed;
        while (remaining > 0 && waypointIndex < waypoints.size()) {
            Point target = waypoints.get(waypointIndex);
            double dx = target.x - x;
            double dy = target.y - y;
            double dist = Math.sqrt(dx * dx + dy * dy);
            if (dist <= remaining) {
                x = target.x;
                y = target.y;
                distanceTravelled += dist;
                remaining -= dist;
                waypointIndex++;
                if (waypointIndex >= waypoints.size()) {
                    reachedEnd = true;
                    return;
                }
            } else {
                double scale = remaining / dist;
                x += dx * scale;
                y += dy * scale;
                distanceTravelled += remaining;
                remaining = 0;
            }
        }
    }

    /** Deals damage; marks as dead if HP ≤ 0. Returns true if popped. */
    public boolean takeDamage(double dmg) {
        hp -= dmg;
        if (hp <= 0) {
            dead = true;
            return true;
        }
        return false;
    }

    public int getReward() {
        return level * 10;
    }

    public Color getColor() {
        return COLORS[level - 1];
    }

    // ── Draw ─────────────────────────────────────────────────────────────────
    public void draw(Graphics2D g) {
        if (dead || reachedEnd)
            return;
        int r = 10 + level;
        // Shadow
        g.setColor(new Color(0, 0, 0, 60));
        g.fillOval((int) (x - r + 2), (int) (y - r + 2), r * 2, r * 2);
        // Body
        g.setColor(getColor());
        g.fillOval((int) (x - r), (int) (y - r), r * 2, r * 2);
        // Highlight
        g.setColor(new Color(255, 255, 255, 80));
        g.fillOval((int) (x - r / 2), (int) (y - r * 0.7), r / 2, r / 2);
        // Outline
        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke(1.5f));
        g.drawOval((int) (x - r), (int) (y - r), r * 2, r * 2);
        g.setStroke(new BasicStroke(1f));

        // HP bar (only if damaged)
        if (hp < maxHp) {
            int barW = r * 2;
            int barH = 4;
            int bx = (int) (x - r);
            int by = (int) (y - r - 7);
            g.setColor(new Color(200, 0, 0));
            g.fillRect(bx, by, barW, barH);
            g.setColor(new Color(0, 200, 0));
            g.fillRect(bx, by, (int) (barW * hp / maxHp), barH);
            g.setColor(Color.BLACK);
            g.drawRect(bx, by, barW, barH);
        }
    }
}
