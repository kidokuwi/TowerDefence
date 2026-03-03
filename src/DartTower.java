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
        loadImage("dart.png");
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
            case 0 -> 120; // Sharp Darts
            case 1 -> 450; // Spike-o-pult
            case 2 -> 1500; // Juggernaut
            default -> 0;
        };
    }

    @Override
    public int getUpgradeBCost() {
        return switch (upgradeB) {
            case 0 -> 150; // Quick Shots
            case 1 -> 600; // Triple Shot
            case 2 -> 15000; // Super Monkey (Very expensive!)
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
        if (upgradeB == 3)
            return new Color(180, 230, 255); // Super Monkey Light Blue
        if (upgradeA == 3)
            return new Color(80, 80, 90); // Juggernaut Dark Grey
        return new Color(70, 130, 220); // Blue
    }

    @Override
    protected void applyUpgradeA() {
        // Path A: Power and Pierce, but Slower
        if (upgradeA == 1) {
            // Sharp Darts: Simple dmg/range buff
            damage = 2;
            range = 110;
        } else if (upgradeA == 2) {
            // Spike-o-pult: Slow, higher damage, high pierce
            fireRateMs = 1200;
            damage = 5;
            range = 150;
        } else if (upgradeA == 3) {
            // Juggernaut: Very slow, massive damage, infinite-ish pierce
            fireRateMs = 2000;
            damage = 20;
            range = 220;
        }
    }

    @Override
    protected void applyUpgradeB() {
        // Path B: Speed
        if (upgradeB == 1) {
            fireRateMs = 400; // Quick Shots
        } else if (upgradeB == 2) {
            // Triple Shot: Fire rate stays same as Quick Shots, logic in fire()
            fireRateMs = 400;
        } else if (upgradeB == 3) {
            // Super Monkey: Extreme speed, single stream
            fireRateMs = 40;
            damage = 2;
            range = 250;
        }
    }

    @Override
    public Projectile fire(Balloon target, long nowMs) {
        lastFireTime = nowMs;

        // Base projectile
        double speed = (upgradeB == 3) ? 12.0 : 5.0; // Faster darts for Super Monkey

        Projectile p = new Projectile(px, py, target, damage, speed, getColor(), false, 0);

        // Customizing projectile physics/visuals based on upgrade
        if (upgradeA == 2) {
            // Spike-o-pult
            p.setPierce(10);
            p.setProjectileRadius(8);
        } else if (upgradeA == 3) {
            // Juggernaut
            p.setPierce(60);
            p.setProjectileRadius(14);
        } else {
            // Standard/Path B pierce
            int pierce = 1;
            if (upgradeA == 1)
                pierce = 2;
            if (upgradeB == 3)
                pierce = 3; // Super Monkey darts pierce a bit
            p.setPierce(pierce);
        }

        // Triple Shot logic (ONLY if not Super Monkey)
        if (upgradeB == 2) {
            double angle = Math.atan2(target.y - py, target.x - px);
            double fanAngle = Math.toRadians(15);

            // Left shot
            Projectile pLeft = new Projectile(px, py, null, damage, 5.0, getColor(), false, 0);
            pLeft.vx = Math.cos(angle - fanAngle) * 5.0;
            pLeft.vy = Math.sin(angle - fanAngle) * 5.0;
            pLeft.setPierce(p.getPierce());
            p.spawnedShots.add(pLeft);

            // Right shot
            Projectile pRight = new Projectile(px, py, null, damage, 5.0, getColor(), false, 0);
            pRight.vx = Math.cos(angle + fanAngle) * 5.0;
            pRight.vy = Math.sin(angle + fanAngle) * 5.0;
            pRight.setPierce(p.getPierce());
            p.spawnedShots.add(pRight);
        }

        return p;
    }

}
