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
            new Color(150, 150, 160), // 7 – Metallic Grey (Big)
            new Color(120, 0, 200), // 8 - Purple (Elite Splitter)
            new Color(150, 100, 50), // 9 - Ceramic (Tough)
            new Color(0, 150, 255), // 10 - MOAB (Massive)
            new Color(255, 50, 50), // 11 - BFB (Red Blimp)
            new Color(50, 255, 50), // 12 - ZOMG (Green Blimp)
            new Color(40, 40, 50), // 13 - DDT (Black Blimp)
            new Color(180, 50, 220) // 14 - BAD (Big Purple Blimp)
    };
    private static final int[] BASE_HP = { 1, 2, 3, 5, 8, 14, 30, 45, 60, 400, 1400, 4000, 400, 20000 };
    private static final double[] BASE_SPEED = { 1.5, 1.6, 1.75, 1.9, 2.1, 2.4, 1.8, 2.2, 2.0, 1.0, 0.8, 0.6, 2.8,
            0.4 };

    // ── Fields ───────────────────────────────────────────────────────────────
    public int level; // 1-6
    public double hp;
    public double maxHp;
    public double speed;
    public double distanceTravelled;
    public int waypointIndex; // next waypoint to head toward
    public double x, y; // pixel position
    public double vx, vy; // current velocity vector
    public double speedMultiplier;
    public boolean dead;
    public boolean reachedEnd;

    private final List<Point> waypoints;

    // ── Constructor ──────────────────────────────────────────────────────────
    public Balloon(int level, List<Point> waypoints, double speedMultiplier) {
        this.level = Math.max(1, Math.min(level, 14));
        this.waypoints = waypoints;
        int idx = this.level - 1;
        this.maxHp = BASE_HP[idx];
        this.hp = this.maxHp;
        this.speedMultiplier = speedMultiplier;
        this.speed = BASE_SPEED[idx] * speedMultiplier;
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
        if (dead || reachedEnd) {
            vx = 0;
            vy = 0;
            return;
        }

        double remaining = speed;
        // Basic velocity approximation for the current step
        double lastX = x;
        double lastY = y;

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
                    vx = 0;
                    vy = 0;
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

        // Final velocity for this frame
        vx = x - lastX;
        vy = y - lastY;
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

    public void setMaxHp(double newMax) {
        this.maxHp = newMax;
        this.hp = newMax;
    }

    public int getReward(boolean isMultiplayer) {
        double mult = isMultiplayer ? 0.4 : 1.0; // VS mode gives 40% income from pops
        int base;
        if (level == 14)
            base = 5000;
        else if (level == 12)
            base = 1500;
        else if (level == 13)
            base = 250;
        else if (level == 11)
            base = 800;
        else if (level == 10)
            base = 100;
        else
            base = level * 5;
        return (int) (base * mult);
    }

    public int getSplitLevel() {
        if (level == 14)
            return 12; // BAD splits into 2 ZOMGs (and DDTs, but simplified)
        if (level == 12)
            return 11; // ZOMG -> BFBs
        if (level == 11)
            return 10; // BFB -> MOABs
        if (level == 13)
            return 9; // DDT -> Ceramics
        if (level == 10)
            return 9; // MOAB -> Ceramics
        if (level == 9)
            return 8;
        if (level == 8)
            return 7;
        return level == 7 ? 4 : 0;
    }

    public int getSplitCount() {
        if (level == 14)
            return 2;
        if (level == 12)
            return 4;
        if (level == 11)
            return 4;
        if (level == 13)
            return 3;
        if (level == 10)
            return 4;
        if (level == 9)
            return 2;
        if (level == 8)
            return 2;
        return level == 7 ? 4 : 0;
    }

    public static int getSendCost(int level) {
        return switch (level) {
            case 1 -> 20;
            case 2 -> 40;
            case 3 -> 60;
            case 4 -> 90;
            case 5 -> 120;
            case 6 -> 200;
            case 7 -> 300;
            case 8 -> 450;
            case 10 -> 1500;
            case 11 -> 3500;
            case 12 -> 8000;
            case 13 -> 5000;
            case 14 -> 30000;
            default -> 0;
        };
    }

    public static int getSendEcoChange(int level) {
        return switch (level) {
            case 1 -> 1;
            case 2 -> 2;
            case 3 -> 3;
            case 4 -> 4;
            case 5 -> 5;
            case 6 -> 7;
            case 7 -> 12;
            case 8 -> 18;
            case 10 -> -25;
            case 11 -> -50;
            case 12 -> -150;
            case 13 -> -80;
            case 14 -> -500;
            default -> 0;
        };
    }

    public Color getColor() {
        return COLORS[level - 1];
    }

    // ── Draw ─────────────────────────────────────────────────────────────────
    public void draw(Graphics2D g) {
        if (dead || reachedEnd)
            return;
        int r;
        if (level == 14)
            r = 70;
        else if (level == 12)
            r = 60;
        else if (level == 11)
            r = 55;
        else if (level == 10)
            r = 45;
        else if (level == 9)
            r = 24;
        else if (level == 8)
            r = 26;
        else if (level == 7)
            r = 22;
        else
            r = (10 + level);
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
