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
        return switch (upgradeA) {
            case 0 -> 150;
            case 1 -> 600;
            case 2 -> 2000;
            default -> 0;
        };
    }

    @Override
    public int getUpgradeBCost() {
        return switch (upgradeB) {
            case 0 -> 200;
            case 1 -> 700;
            case 2 -> 2500;
            default -> 0;
        };
    }

    @Override
    public String getUpgradeAName() {
        if (upgradeA == 3)
            return "MAXED Out (A)";
        return switch (upgradeA) {
            case 0 -> "Bigger Bombs ($" + getUpgradeACost() + ")";
            case 1 -> "Heavy Shells ($" + getUpgradeACost() + ")";
            case 2 -> "MOAB Mauler ($" + getUpgradeACost() + ")";
            default -> "";
        };
    }

    @Override
    public String getUpgradeBName() {
        if (upgradeB == 3)
            return "MAXED Out (B)";
        return switch (upgradeB) {
            case 0 -> "Faster Reload ($" + getUpgradeBCost() + ")";
            case 1 -> "Semi-Auto Bomb ($" + getUpgradeBCost() + ")";
            case 2 -> "Recursive Cluster ($" + getUpgradeBCost() + ")";
            default -> "";
        };
    }

    @Override
    public Color getColor() {
        return new Color(210, 100, 30); // Orange-Brown
    }

    @Override
    protected void applyUpgradeA() {
        // Levels: 1: R 80, 2: R 120/Dmg 6, 3: R 200/Dmg 15
        if (upgradeA == 1) {
            blastRadius = 80;
        } else if (upgradeA == 2) {
            blastRadius = 120;
            damage = 6;
        } else if (upgradeA == 3) {
            blastRadius = 200;
            damage = 15;
        }
    }

    @Override
    protected void applyUpgradeB() {
        // Levels: 1: FR 1000, 2: FR 600, 3: FR 300/R 250
        if (upgradeB == 1) {
            fireRateMs = 1000;
        } else if (upgradeB == 2) {
            fireRateMs = 600;
        } else if (upgradeB == 3) {
            fireRateMs = 300;
            range = 250;
        }
    }

    public double getBlastRadius() {
        return blastRadius;
    }

    public boolean isCluster() {
        return clusterBombs;
    }

    @Override
    public Projectile fire(Balloon target, long nowMs) {
        lastFireTime = nowMs;
        Projectile p = new Projectile(px, py, target, damage, 3.5,
                new Color(40, 40, 40), true, blastRadius);
        // Maybe some pierce for late-game bombs?
        if (upgradeA == 3)
            p.setPierce(2);
        return p;
    }
}
