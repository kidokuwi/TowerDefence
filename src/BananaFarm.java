import java.awt.*;

/**
 * Banana Farm – generates cash over time.
 * Cost: $250
 */
public class BananaFarm extends Tower {
    private long lastProductionTime;
    private double productionAmount = 20.0;
    private double intervalMs = 10000.0; // every 10 seconds

    public BananaFarm(int gridX, int gridY, int cellSize) {
        this.gridX = gridX;
        this.gridY = gridY;
        this.px = gridX * cellSize + cellSize / 2;
        this.py = gridY * cellSize + cellSize / 2;
        this.range = 0; // farms don't shoot
        this.damage = 0;
        this.fireRateMs = Double.MAX_VALUE; // never fires
        this.lastProductionTime = 0;
    }

    @Override
    public String getName() {
        return "Banana Farm";
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
        productionAmount += 15;
    }

    @Override
    protected void applyUpgradeB() {
        intervalMs -= 3000;
    }

    @Override
    public void update(long nowMs, GameState state) {
        if (lastProductionTime == 0) {
            lastProductionTime = nowMs;
            return;
        }
        if (nowMs - lastProductionTime >= intervalMs) {
            state.addCash((int) productionAmount);
            lastProductionTime = nowMs;
        }
    }

    @Override
    public boolean canFire(long nowMs) {
        return false;
    }
}
