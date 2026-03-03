import java.awt.*;

/**
 * Banana Farm – generates cash over time.
 * Takes 2x2 grid spaces.
 * Cost: $250
 */
public class BananaFarm extends Tower {
    private long lastProductionTime;
    private double productionAmount = 30.0; // Base income
    private double intervalMs = 12000.0; // every 12 seconds (Slower base)

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
        loadImage("farm.png");
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
        return 450; // Increased base cost
    }

    @Override
    public int getUpgradeACost() {
        return switch (upgradeA) {
            case 0 -> 400; // Bigger Bananas
            case 1 -> 900; // Plantation
            case 2 -> 4500; // Research Facility
            default -> 0;
        };
    }

    @Override
    public int getUpgradeBCost() {
        return switch (upgradeB) {
            case 0 -> 300; // Faster Harvest
            case 1 -> 1200; // Market Dynamics
            case 2 -> 6500; // Global Export
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
        // Multiplier based approach for production
        if (upgradeA == 1)
            productionAmount = 60; // Bigger Bananas
        else if (upgradeA == 2)
            productionAmount = 140; // Plantation
        else if (upgradeA == 3)
            productionAmount = 650; // Research Facility
    }

    @Override
    protected void applyUpgradeB() {
        // Reduciton based approach for interval
        if (upgradeB == 1)
            intervalMs = 9000; // Faster Harvest (12 -> 9)
        else if (upgradeB == 2)
            intervalMs = 6000; // Market Dynamics (9 -> 6)
        else if (upgradeB == 3)
            intervalMs = 2500; // Global Export (6 -> 2.5)
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
