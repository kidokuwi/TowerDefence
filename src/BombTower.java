import java.awt.*;

/**
 * Bomb Tower – AoE explosion on impact, slow, moderate damage.
 * Cost: $150
 */
public class BombTower extends Tower {

    private double blastRadius = 50;
    private boolean clusterBombs = false;

    public BombTower(int gridX, int gridY, int cellSize) {
        this.gridX = gridX;
        this.gridY = gridY;
        this.px = gridX * cellSize + cellSize / 2;
        this.py = gridY * cellSize + cellSize / 2;
        range = 110;
        damage = 2;
        fireRateMs = 1500;
    }

    @Override
    public String getName() {
        return "Bomb Tower";
    }

    @Override
    public int getCost() {
        return 150;
    }

    @Override
    public int getUpgradeACost() {
        return 100;
    }

    @Override
    public int getUpgradeBCost() {
        return 120;
    }

    @Override
    public String getUpgradeAName() {
        return upgradeA == 0 ? "Bigger Bombs ($100)" : "Bigger Bombs ✓";
    }

    @Override
    public String getUpgradeBName() {
        return upgradeB == 0 ? "Cluster Bombs ($120)" : "Cluster Bombs ✓";
    }

    @Override
    public Color getColor() {
        return new Color(210, 100, 30);
    }

    @Override
    protected void applyUpgradeA() {
        blastRadius *= 2;
    }

    @Override
    protected void applyUpgradeB() {
        clusterBombs = true;
    }

    public double getBlastRadius() {
        return blastRadius;
    }

    public boolean isCluster() {
        return clusterBombs;
    }

    public Projectile fire(Balloon target, long nowMs) {
        lastFireTime = nowMs;
        return new Projectile(px, py, target, damage, 3.5,
                new Color(40, 40, 40), true, blastRadius);
    }
}
