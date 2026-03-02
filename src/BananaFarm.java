import java.awt.*;

/**
 * Banana Farm – generates cash over time.
 * Takes 2x2 grid spaces.
 * Cost: $250
 */
public class BananaFarm extends Tower {
    private long lastProductionTime;
    private double productionAmount = 25.0; // Weaker base
    private double intervalMs = 10000.0; // every 10 seconds

    public BananaFarm(int gridX, int gridY, int cellSize) {
        this.gridX = gridX;
        this.gridY = gridY;
        // Center of 2x2 footprint (across gx, gx+1 and gy, gy+1)
        this.px = gridX * cellSize + cellSize;
        this.py = gridY * cellSize + cellSize;
        this.range = 0;
        this.damage = 0;
        this.fireRateMs = Double.MAX_VALUE;
        this.lastProductionTime = 0;
    }

    @Override
    public String getName() {
        return "Banana Farm";
    }

    @Override
    public int getSize() {
        return 2;
    }

    @Override
    public int getCost() {
        return 250;
    }

    @Override
    public int getUpgradeACost() {
        return switch (upgradeA) {
            case 0 -> 150;
            case 1 -> 450;
            case 2 -> 2000;
            default -> 0;
        };
    }

    @Override
    public int getUpgradeBCost() {
        return switch (upgradeB) {
            case 0 -> 200;
            case 1 -> 600;
            case 2 -> 1800;
            default -> 0;
        };
    }

    @Override
    public String getUpgradeAName() {
        if (upgradeA == 3)
            return "MAXED Out (A)";
        return switch (upgradeA) {
            case 0 -> "Bigger Bananas ($" + getUpgradeACost() + ")";
            case 1 -> "Banana Plantation ($" + getUpgradeACost() + ")";
            case 2 -> "Research Facility ($" + getUpgradeACost() + ")";
            default -> "";
        };
    }

    @Override
    public String getUpgradeBName() {
        if (upgradeB == 3)
            return "MAXED Out (B)";
        return switch (upgradeB) {
            case 0 -> "Faster Harvest ($" + getUpgradeBCost() + ")";
            case 1 -> "Market Dynamics ($" + getUpgradeBCost() + ")";
            case 2 -> "Global Export ($" + getUpgradeBCost() + ")";
            default -> "";
        };
    }

    @Override
    public Color getColor() {
        return new Color(240, 230, 60); // Yellow
    }

    @Override
    protected void applyUpgradeA() {
        // Levels: 1: +25 ($50), 2: +50 ($100), 3: +200 ($300)
        productionAmount += switch (upgradeA) {
            case 1 -> 25;
            case 2 -> 50;
            case 3 -> 200;
            default -> 0;
        };
    }

    @Override
    protected void applyUpgradeB() {
        // Levels: 1: -2s (8s), 2: -2s (6s), 3: -4s (2s)
        intervalMs -= switch (upgradeB) {
            case 1 -> 2000;
            case 2 -> 2000;
            case 3 -> 4000;
            default -> 0;
        };
    }

    @Override
    public void update(long nowMs, GameSession session) {
        if (lastProductionTime == 0) {
            lastProductionTime = nowMs;
            return;
        }
        if (nowMs - lastProductionTime >= intervalMs) {
            session.state.addCash((int) productionAmount);
            session.spawnFloatingText("+$" + (int) productionAmount, px - 10, py - 20, new Color(40, 200, 40));
            lastProductionTime = nowMs;
        }
    }

    @Override
    public boolean canFire(long nowMs) {
        return false;
    }
}
