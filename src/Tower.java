import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * Abstract base class for all tower types.
 */
public abstract class Tower {

    // ── Placed position (grid cell top-left pixel) ──────────────────────────
    public int gridX, gridY; // grid col/row
    public int px, py; // pixel centre

    // ── Stats ────────────────────────────────────────────────────────────────
    protected double range;
    protected double damage;
    protected double fireRateMs; // milliseconds between shots
    protected long lastFireTime;

    // ── Upgrade state ────────────────────────────────────────────────────────
    public int upgradeA = 0; // Level 0-3
    public int upgradeB = 0;
    public int totalMoneySpent = 0;

    protected BufferedImage image;

    public abstract String getName();

    public abstract int getCost();

    public abstract int getUpgradeACost();

    public abstract int getUpgradeBCost();

    public abstract String getUpgradeAName();

    public abstract String getUpgradeBName();

    protected abstract void applyUpgradeA();

    protected abstract void applyUpgradeB();

    public abstract Color getColor();

    public int getSize() {
        return 1; // 1x1 cells by default
    }

    protected void loadImage(String path) {
        this.image = AssetLoader.loadSprite(path);
    }

    // ── Logic ────────────────────────────────────────────────────────────────
    public boolean canFire(long nowMs) {
        return (nowMs - lastFireTime) >= fireRateMs;
    }

    /**
     * Called every frame to allow towers to perform logic (e.g., economic
     * production).
     */
    public void update(long nowMs, GameSession session) {
        // Default: do nothing
    }

    /**
     * Returns the balloon that is furthest along the path within range, or null.
     */
    public Balloon findTarget(List<Balloon> balloons) {
        Balloon best = null;
        for (Balloon b : balloons) {
            if (b.dead || b.reachedEnd)
                continue;
            double dx = b.x - px;
            double dy = b.y - py;
            if (dx * dx + dy * dy <= range * range) {
                if (best == null || b.distanceTravelled > best.distanceTravelled) {
                    best = b;
                }
            }
        }
        return best;
    }

    public boolean upgrade(int branch, GameState state) {
        if (branch == 0) {
            // Path A
            if (upgradeA >= 3)
                return false;
            // Rule: If Path B is 3, Path A can only go to 2
            if (upgradeB == 3 && upgradeA >= 2)
                return false;

            if (state.canAfford(getUpgradeACost())) {
                state.spend(getUpgradeACost());
                totalMoneySpent += getUpgradeACost();
                upgradeA++;
                applyUpgradeA();
                return true;
            }
        } else if (branch == 1) {
            // Path B
            if (upgradeB >= 3)
                return false;
            // Rule: If Path A is 3, Path B can only go to 2
            if (upgradeA == 3 && upgradeB >= 2)
                return false;

            if (state.canAfford(getUpgradeBCost())) {
                state.spend(getUpgradeBCost());
                totalMoneySpent += getUpgradeBCost();
                upgradeB++;
                applyUpgradeB();
                return true;
            }
        }
        return false;
    }

    public int getSellPrice() {
        return (int) (totalMoneySpent * 0.7);
    }

    public Projectile fire(Balloon target, long nowMs) {
        return null;
    }

    /**
     * Calculates the point where a projectile with speed s will intercept
     * a balloon moving with current velocity (target.vx, target.vy).
     */
    protected Point2D.Double calculateIntercept(Balloon target, double projectileSpeed) {
        double tx = target.x;
        double ty = target.y;
        double tvx = target.vx;
        double tvy = target.vy;

        double relX = tx - px;
        double relY = ty - py;

        // Quadratic coefficients: a*t^2 + b*t + c = 0
        double a = tvx * tvx + tvy * tvy - projectileSpeed * projectileSpeed;
        double b = 2 * (tvx * relX + tvy * relY);
        double c = relX * relX + relY * relY;

        double t = -1;
        if (Math.abs(a) < 1e-6) {
            if (Math.abs(b) > 1e-6)
                t = -c / b;
        } else {
            double disc = b * b - 4 * a * c;
            if (disc >= 0) {
                double t1 = (-b + Math.sqrt(disc)) / (2 * a);
                double t2 = (-b - Math.sqrt(disc)) / (2 * a);
                if (t1 > 0 && t2 > 0)
                    t = Math.min(t1, t2);
                else if (t1 > 0)
                    t = t1;
                else if (t2 > 0)
                    t = t2;
            }
        }

        if (t < 0 || t > 5.0)
            return new Point2D.Double(tx, ty); // Cap prediction to 5 seconds

        return new Point2D.Double(tx + tvx * t, ty + tvy * t);
    }

    // ── Draw ─────────────────────────────────────────────────────────────────
    public void draw(Graphics2D g, boolean selected) {
        // Range ring (when selected)
        if (selected) {
            g.setColor(new Color(255, 255, 255, 50));
            g.fillOval((int) (px - range), (int) (py - range),
                    (int) (range * 2), (int) (range * 2));
            g.setColor(new Color(255, 255, 255, 150));
            g.setStroke(new BasicStroke(1.5f));
            g.drawOval((int) (px - range), (int) (py - range),
                    (int) (range * 2), (int) (range * 2));
            g.setStroke(new BasicStroke(1f));
        }
        // Base & Sprite
        int cellSize = getSize() * 40;
        int drawSize = cellSize - 4;

        if (image != null) {
            g.drawImage(image, px - drawSize / 2, py - drawSize / 2, drawSize, drawSize, null);
        } else {
            // Fallback base
            int baseSize = cellSize - 6;
            int innerSize = cellSize - 10;
            g.setColor(getColor().darker());
            g.fillRoundRect(px - baseSize / 2, py - baseSize / 2, baseSize, baseSize, 8, 8);
            g.setColor(getColor());
            g.fillRoundRect(px - innerSize / 2, py - innerSize / 2, innerSize, innerSize, 6, 6);

            // Barrel hint (only for 1x1 shooting towers)
            if (getSize() == 1) {
                g.setColor(getColor().darker().darker());
                g.fillRect(px - 3, py - 20, 6, 8);
            }
        }

        // Selection ring
        if (selected) {
            int innerSize = cellSize - 10;
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(2.5f));
            g.drawRoundRect(px - innerSize / 2, py - innerSize / 2, innerSize, innerSize, 6, 6);
            g.setStroke(new BasicStroke(1f));
        }
    }
}
