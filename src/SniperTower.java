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
            case 0 -> 400; // Point Five O
            case 1 -> 1500; // Armor Piercing
            case 2 -> 6000; // Cripple Moab
            default -> 0;
        };
    }

    @Override
    public int getUpgradeBCost() {
        return switch (upgradeB) {
            case 0 -> 350; // Fast Reload
            case 1 -> 1200; // Semi-Auto
            case 2 -> 5000; // Full-Auto
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
        // Path A: Heavy Damage
        if (upgradeA == 1)
            damage = 15; // Point Five O
        else if (upgradeA == 2)
            damage = 40; // Armor Piercing
        else if (upgradeA == 3)
            damage = 250; // Cripple Moab
    }

    @Override
    protected void applyUpgradeB() {
        // Path B: Speed
        if (upgradeB == 1)
            fireRateMs = 1200; // Fast Reload
        else if (upgradeB == 2)
            fireRateMs = 600; // Semi-Auto
        else if (upgradeB == 3) {
            fireRateMs = 100; // Full-Auto
            damage = Math.max(damage, 2); // Ensure it does at least 2 dmg
        }
    }

    @Override
    public Projectile fire(Balloon target, long nowMs) {
        lastFireTime = nowMs;
        double speed = (upgradeB == 3) ? 25.0 : 15.0;
        Projectile p = new Projectile(px, py, target, damage, speed, getColor(), false, 0);

        int pierce = 1;
        if (upgradeA == 2)
            pierce = 2;
        if (upgradeA == 3)
            pierce = 5;
        if (upgradeB == 3 && pierce < 2)
            pierce = 2;

        p.setPierce(pierce);
        return p;
    }

}
