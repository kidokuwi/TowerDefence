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
            case 0 -> 250; // Bigger Bombs
            case 1 -> 800; // Heavy Shells
            case 2 -> 3500; // MOAB Mauler
            default -> 0;
        };
    }

    @Override
    public int getUpgradeBCost() {
        return switch (upgradeB) {
            case 0 -> 200; // Faster Reload
            case 1 -> 1000; // Missile Launcher (Renamed from Semi-Auto)
            case 2 -> 4500; // Recursive Cluster
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
            case 1 -> "Missile Launcher ($" + getUpgradeBCost() + ")";
            case 2 -> "Recursive Cluster ($" + getUpgradeBCost() + ")";
            default -> "";
        };
    }

    @Override
    public Color getColor() {
        if (upgradeA == 3)
            return new Color(150, 50, 20); // Darker red-ish for mauler
        return new Color(210, 100, 30); // Orange-Brown
    }

    @Override
    protected void applyUpgradeA() {
        if (upgradeA == 1) {
            blastRadius = 85;
            damage = 3;
        } else if (upgradeA == 2) {
            blastRadius = 110;
            damage = 8;
        } else if (upgradeA == 3) {
            blastRadius = 150;
            damage = 45; // MOAB Mauler: Massive single-hit damage
            range = 140;
        }
    }

    @Override
    protected void applyUpgradeB() {
        if (upgradeB == 1) {
            fireRateMs = 900;
        } else if (upgradeB == 2) {
            fireRateMs = 500;
            range = 160;
        } else if (upgradeB == 3) {
            fireRateMs = 350;
            range = 200;
            clusterBombs = true;
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
