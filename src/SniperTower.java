import java.awt.*;

/**
 * Sniper Tower – very long range, slow, high damage, pierces all balloons in a
 * line.
 * Cost: $175
 */
public class SniperTower extends Tower {

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
        return switch (upgradeA) {
            case 0 -> 250;
            case 1 -> 800;
            case 2 -> 2500;
            default -> 0;
        };
    }

    @Override
    public int getUpgradeBCost() {
        return switch (upgradeB) {
            case 0 -> 200;
            case 1 -> 700;
            case 2 -> 2000;
            default -> 0;
        };
    }

    @Override
    public String getUpgradeAName() {
        if (upgradeA == 3)
            return "MAXED Out (A)";
        return switch (upgradeA) {
            case 0 -> "Point Five O ($" + getUpgradeACost() + ")";
            case 1 -> "Armor Piercing ($" + getUpgradeACost() + ")";
            case 2 -> "Cripple Moab ($" + getUpgradeACost() + ")";
            default -> "";
        };
    }

    @Override
    public String getUpgradeBName() {
        if (upgradeB == 3)
            return "MAXED Out (B)";
        return switch (upgradeB) {
            case 0 -> "Fast Reload ($" + getUpgradeBCost() + ")";
            case 1 -> "Semi-Auto ($" + getUpgradeBCost() + ")";
            case 2 -> "Full-Auto ($" + getUpgradeBCost() + ")";
            default -> "";
        };
    }

    @Override
    public Color getColor() {
        return new Color(100, 100, 120); // Grey/Lead
    }

    @Override
    protected void applyUpgradeA() {
        if (upgradeA == 1)
            damage = Math.max(damage, 25);
        else if (upgradeA == 2)
            damage = Math.max(damage, 75);
        else if (upgradeA == 3)
            damage = Math.max(damage, 300);
    }

    @Override
    protected void applyUpgradeB() {
        if (upgradeB == 1)
            fireRateMs = Math.min(fireRateMs, 1400);
        else if (upgradeB == 2)
            fireRateMs = Math.min(fireRateMs, 800);
        else if (upgradeB == 3)
            fireRateMs = Math.min(fireRateMs, 300);
    }

    @Override
    public Projectile fire(Balloon target, long nowMs) {
        lastFireTime = nowMs;
        Projectile p = new Projectile(px, py, target, damage, 15.0, getColor(), false, 0);
        int pierce = 1;
        if (upgradeA == 3)
            pierce = 5;
        else if (upgradeB == 3)
            pierce = 2;
        p.setPierce(pierce);
        return p;
    }
}
