import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Sniper Tower – very long range, slow, high damage, pierces all balloons in a
 * line.
 * Cost: $175
 */
public class SniperTower extends Tower {

    private boolean critChance = false; // upgraded with Long Shot

    public SniperTower(int gridX, int gridY, int cellSize) {
        this.gridX = gridX;
        this.gridY = gridY;
        this.px = gridX * cellSize + cellSize / 2;
        this.py = gridY * cellSize + cellSize / 2;
        range = 200;
        damage = 3;
        fireRateMs = 2000;
    }

    @Override
    public String getName() {
        return "Sniper Tower";
    }

    @Override
    public int getCost() {
        return 175;
    }

    @Override
    public int getUpgradeACost() {
        return 100;
    }

    @Override
    public int getUpgradeBCost() {
        return 90;
    }

    @Override
    public String getUpgradeAName() {
        return upgradeA == 0 ? "Armor Piercing ($100)" : "Armor Piercing ✓";
    }

    @Override
    public String getUpgradeBName() {
        return upgradeB == 0 ? "Long Shot ($90)" : "Long Shot ✓";
    }

    @Override
    public Color getColor() {
        return new Color(80, 80, 90);
    }

    @Override
    protected void applyUpgradeA() {
        damage *= 3;
    }

    @Override
    protected void applyUpgradeB() {
        range *= 1.5;
        critChance = true;
    }

    /**
     * Fires at all balloons in a line toward the primary target.
     * Returns list of new Projectiles (one per balloon hit in the line, or just
     * primary).
     */
    public List<Projectile> fireAll(List<Balloon> balloons, long nowMs) {
        lastFireTime = nowMs;
        List<Projectile> shots = new ArrayList<>();
        Balloon primary = findTarget(balloons);
        if (primary == null)
            return shots;

        double dmg = damage;
        if (critChance && Math.random() < 0.25)
            dmg *= 2;

        // Shoot one projectile that will pierce; we model it as hitting all in-line
        // balloons.
        // For simplicity, create one fast-moving projectile per balloon roughly on the
        // line.
        double angle = Math.atan2(primary.y - py, primary.x - px);
        for (Balloon b : balloons) {
            if (b.dead || b.reachedEnd)
                continue;
            double bAngle = Math.atan2(b.y - py, b.x - px);
            double angleDiff = Math.abs(angle - bAngle);
            double dist = Math.sqrt((b.x - px) * (b.x - px) + (b.y - py) * (b.y - py));
            if (angleDiff < 0.3 && dist <= range) {
                shots.add(new Projectile(px, py, b, dmg, 8.0,
                        new Color(200, 200, 200), false, 0));
            }
        }
        if (shots.isEmpty()) {
            shots.add(new Projectile(px, py, primary, dmg, 8.0,
                    new Color(200, 200, 200), false, 0));
        }
        return shots;
    }
}
