import java.awt.*;

/**
 * Banana Farm – generates cash over time.
 * Takes 2x2 grid spaces.
 * Cost: $250
 */
public class BananaFarm extends Tower {
    private long lastProductionTime;
    private double productionAmount = 50.0;
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
        return 150;
    }

    @Override
    public int getUpgradeBCost() {
        return 200;
    }

    @Override
    public String getUpgradeAName() {
        return upgradeA == 0 ? "Bigger Bananas ($150)" : "Bigger Bananas ✓";
    }

    @Override
    public String getUpgradeBName() {
        return upgradeB == 0 ? "Banana Plantation ($200)" : "Plantation ✓";
    }

    @Override
    public Color getColor() {
        return new Color(240, 230, 60); // Yellow
    }

    @Override
    protected void applyUpgradeA() {
        productionAmount += 30;
    }

    @Override
    protected void applyUpgradeB() {
        intervalMs -= 3000;
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
