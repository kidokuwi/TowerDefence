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
        return switch (upgradeA) {
            case 0 -> 100;
            case 1 -> 300;
            case 2 -> 1200;
            default -> 0;
        };
    }

    @Override
    public int getUpgradeBCost() {
        return switch (upgradeB) {
            case 0 -> 120;
            case 1 -> 400;
            case 2 -> 1500;
            default -> 0;
        };
    }

    @Override
    public String getUpgradeAName() {
        if (upgradeA == 3)
            return "MAXED Out (A)";
        return switch (upgradeA) {
            case 0 -> "Sharp Darts ($" + getUpgradeACost() + ")";
            case 1 -> "Spike-o-pult ($" + getUpgradeACost() + ")";
            case 2 -> "Juggernaut ($" + getUpgradeACost() + ")";
            default -> "";
        };
    }

    @Override
    public String getUpgradeBName() {
        if (upgradeB == 3)
            return "MAXED Out (B)";
        return switch (upgradeB) {
            case 0 -> "Quick Shots ($" + getUpgradeBCost() + ")";
            case 1 -> "Triple Shot ($" + getUpgradeBCost() + ")";
            case 2 -> "Super Monkey ($" + getUpgradeBCost() + ")";
            default -> "";
        };
    }

    @Override
    public Color getColor() {
        return new Color(70, 130, 220); // Blue
    }

    @Override
    protected void applyUpgradeA() {
        // Levels: 1: Dmg 2/R 120, 2: Dmg 4/R 140, 3: Dmg 10/R 200/FR 300
        if (upgradeA == 1) {
            damage = 2;
            range = 120;
        } else if (upgradeA == 2) {
            damage = 4;
            range = 140;
        } else if (upgradeA == 3) {
            damage = 10;
            range = 200;
            fireRateMs = 300;
        }
    }

    @Override
    protected void applyUpgradeB() {
        // Levels: 1: FR 300, 2: FR 150, 3: FR 80/R 180
        if (upgradeB == 1) {
            fireRateMs = 300;
        } else if (upgradeB == 2) {
            fireRateMs = 150;
        } else if (upgradeB == 3) {
            fireRateMs = 80;
            range = 180;
        }
    }

    /** Creates and returns a new Projectile aimed at the target balloon. */
    @Override
    public Projectile fire(Balloon target, long nowMs) {
        lastFireTime = nowMs;
        Projectile p = new Projectile(px, py, target, damage, 5.0, getColor(), false, 0);
        int pierce = 1;
        if (upgradeA == 1)
            pierce = 2;
        else if (upgradeA == 2)
            pierce = 5;
        else if (upgradeA == 3)
            pierce = 30;

        if (upgradeB == 3 && pierce < 3)
            pierce = 3;

        p.setPierce(pierce);
        return p;
    }
}
