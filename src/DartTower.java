import java.awt.*;

/**
 * Dart Tower – single-target, medium range, fast fire rate.
 * Cost: $100
 */
public class DartTower extends Tower {

    public DartTower(int gridX, int gridY, int cellSize) {
        this.gridX = gridX;
        this.gridY = gridY;
        this.px = gridX * cellSize + cellSize / 2;
        this.py = gridY * cellSize + cellSize / 2;
        range = 100;
        damage = 1;
        fireRateMs = 600;
    }

    @Override
    public String getName() {
        return "Dart Tower";
    }

    @Override
    public int getCost() {
        return 100;
    }

    @Override
    public int getUpgradeACost() {
        return 75;
    }

    @Override
    public int getUpgradeBCost() {
        return 80;
    }

    @Override
    public String getUpgradeAName() {
        return upgradeA == 0 ? "Enhanced Darts ($75)" : "Enhanced ✓";
    }

    @Override
    public String getUpgradeBName() {
        return upgradeB == 0 ? "Rapid Fire ($80)" : "Rapid Fire ✓";
    }

    @Override
    public Color getColor() {
        return new Color(70, 130, 220);
    }

    @Override
    protected void applyUpgradeA() {
        damage += 1;
        range += 25;
    }

    @Override
    protected void applyUpgradeB() {
        fireRateMs = fireRateMs / 2.0;
    }

    /** Creates and returns a new Projectile aimed at the target balloon. */
    public Projectile fire(Balloon target, long nowMs) {
        lastFireTime = nowMs;
        return new Projectile(px, py, target, damage, 5.0, getColor(), false, 0);
    }
}
