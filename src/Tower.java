import java.awt.*;
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
    public int upgradeA = 0; // 0 = none, 1 = purchased
    public int upgradeB = 0;

    public abstract String getName();

    public abstract int getCost();

    public abstract int getUpgradeACost();

    public abstract int getUpgradeBCost();

    public abstract String getUpgradeAName();

    public abstract String getUpgradeBName();

    protected abstract void applyUpgradeA();

    protected abstract void applyUpgradeB();

    public abstract Color getColor();

    // ── Logic ────────────────────────────────────────────────────────────────
    public boolean canFire(long nowMs) {
        return (nowMs - lastFireTime) >= fireRateMs;
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
        if (branch == 0 && upgradeA == 0 && state.canAfford(getUpgradeACost())) {
            state.spend(getUpgradeACost());
            upgradeA = 1;
            applyUpgradeA();
            return true;
        }
        if (branch == 1 && upgradeB == 0 && state.canAfford(getUpgradeBCost())) {
            state.spend(getUpgradeBCost());
            upgradeB = 1;
            applyUpgradeB();
            return true;
        }
        return false;
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
        // Base
        g.setColor(getColor().darker());
        g.fillRoundRect(px - 17, py - 17, 34, 34, 8, 8);
        g.setColor(getColor());
        g.fillRoundRect(px - 15, py - 15, 30, 30, 6, 6);
        // Barrel hint
        g.setColor(getColor().darker().darker());
        g.fillRect(px - 3, py - 20, 6, 8);
        // Selection ring
        if (selected) {
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(2.5f));
            g.drawRoundRect(px - 15, py - 15, 30, 30, 6, 6);
            g.setStroke(new BasicStroke(1f));
        }
    }
}
