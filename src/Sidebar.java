import java.awt.*;

/**
 * Sidebar panel rendered on the right side of the game window.
 * Handles tower selection, purchase buttons, and upgrade UI.
 */
public class Sidebar {

    public static final int WIDTH = 200;

    // Button areas (absolute coords in full window space)
    private final Rectangle btnDart = new Rectangle();
    private final Rectangle btnSniper = new Rectangle();
    private final Rectangle btnBomb = new Rectangle();
    private final Rectangle btnFarm = new Rectangle();
    private final Rectangle btnUpgA = new Rectangle();
    private final Rectangle btnUpgB = new Rectangle();
    private final Rectangle btnEarly = new Rectangle();

    private int offsetX; // px where sidebar starts

    public void setOffsetX(int x) {
        this.offsetX = x;
    }

    // ── Render ────────────────────────────────────────────────────────────────
    public void draw(Graphics2D g, GameState state, Tower selected,
            WaveManager wm, long nowMs) {
        int x = offsetX;
        int w = WIDTH;

        // Background
        g.setColor(new Color(28, 30, 38));
        g.fillRect(x, 0, w, 600);
        g.setColor(new Color(60, 65, 80));
        g.drawLine(x, 0, x, 600);

        int cy = 14;

        // ── Stats ──────────────────────────────────────────────────────────
        g.setFont(new Font("Segoe UI", Font.BOLD, 15));
        g.setColor(new Color(255, 215, 80));
        g.drawString("💰  $" + state.cash, x + 10, cy += 20);
        g.setColor(new Color(240, 80, 80));
        g.drawString("❤  " + state.lives + " lives", x + 10, cy += 22);
        g.setColor(new Color(140, 200, 255));
        g.drawString("🌊  Wave " + state.waveNumber, x + 10, cy += 22);
        g.setColor(new Color(180, 180, 180));
        g.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        g.drawString("Score: " + state.score, x + 10, cy += 18);

        // Divider
        cy += 8;
        g.setColor(new Color(60, 65, 80));
        g.drawLine(x + 10, cy, x + w - 10, cy);
        cy += 10;

        // ── Tower shop ────────────────────────────────────────────────────
        g.setFont(new Font("Segoe UI", Font.BOLD, 13));
        g.setColor(new Color(200, 210, 230));
        g.drawString("BUY TOWER", x + 10, cy += 14);
        cy += 4;

        cy = drawShopButton(g, "Dart Tower  $100", new Color(70, 130, 220),
                state.cash >= 100, "dart".equals(state.selectedTowerType),
                x + 8, cy, w - 16, btnDart);
        cy += 4;
        cy = drawShopButton(g, "Sniper Tower $175", new Color(100, 100, 120),
                state.cash >= 175, "sniper".equals(state.selectedTowerType),
                x + 8, cy, w - 16, btnSniper);
        cy += 4;
        cy = drawShopButton(g, "Bomb Tower  $150", new Color(210, 100, 30),
                state.cash >= 150, "bomb".equals(state.selectedTowerType),
                x + 8, cy, w - 16, btnBomb);
        cy += 4;
        cy = drawShopButton(g, "Banana Farm  $250", new Color(240, 230, 60),
                state.cash >= 250, "farm".equals(state.selectedTowerType),
                x + 8, cy, w - 16, btnFarm);

        // Divider
        cy += 10;
        g.setColor(new Color(60, 65, 80));
        g.drawLine(x + 10, cy, x + w - 10, cy);
        cy += 8;

        // ── Selected tower info ───────────────────────────────────────────
        if (selected != null) {
            g.setFont(new Font("Segoe UI", Font.BOLD, 13));
            g.setColor(new Color(200, 230, 255));
            cy += 14;
            g.drawString(selected.getName(), x + 10, cy);
            g.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            g.setColor(new Color(170, 185, 200));
            g.drawString("Range: " + (int) selected.range +
                    "  Dmg: " + (int) selected.damage, x + 10, cy += 16);

            cy += 8;

            boolean canA = selected.upgradeA == 0 && state.cash >= selected.getUpgradeACost();
            boolean canB = selected.upgradeB == 0 && state.cash >= selected.getUpgradeBCost();

            cy = drawUpgradeButton(g, selected.getUpgradeAName(),
                    selected.upgradeA == 1, canA, x + 8, cy, w - 16, btnUpgA);
            cy += 4;
            cy = drawUpgradeButton(g, selected.getUpgradeBName(),
                    selected.upgradeB == 1, canB, x + 8, cy, w - 16, btnUpgB);
        } else {
            btnUpgA.setSize(0, 0);
            btnUpgB.setSize(0, 0);
            g.setFont(new Font("Segoe UI", Font.ITALIC, 11));
            g.setColor(new Color(120, 130, 150));
            g.drawString("Click a tower to upgrade", x + 10, cy + 18);
        }

        // ── Start Wave Early button ───────────────────────────────────────
        int btnY = 600 - 55;
        boolean canEarly = wm.isWavePending();
        String label = canEarly
                ? "▶  Start Wave Early (" + wm.secondsToNextWave(nowMs) + "s)"
                : "Waiting for balloons…";

        Color btnCol = canEarly ? new Color(60, 180, 80) : new Color(60, 65, 80);
        g.setColor(btnCol);
        g.fillRoundRect(x + 8, btnY, w - 16, 38, 8, 8);
        g.setColor(canEarly ? new Color(100, 255, 120) : new Color(100, 105, 115));
        g.setFont(new Font("Segoe UI", Font.BOLD, 11));
        FontMetrics fm = g.getFontMetrics();
        g.drawString(label, x + 8 + (w - 16 - fm.stringWidth(label)) / 2,
                btnY + 24);
        btnEarly.setBounds(x + 8, btnY, w - 16, 38);
    }

    private int drawShopButton(Graphics2D g, String label, Color col,
            boolean affordable, boolean selected,
            int bx, int by, int bw, Rectangle out) {
        int bh = 34;
        Color bg = selected ? col.brighter() : (affordable ? col.darker() : new Color(50, 52, 60));
        g.setColor(bg);
        g.fillRoundRect(bx, by, bw, bh, 7, 7);
        if (selected) {
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(2f));
            g.drawRoundRect(bx, by, bw, bh, 7, 7);
            g.setStroke(new BasicStroke(1f));
        }
        g.setColor(affordable ? Color.WHITE : new Color(120, 120, 130));
        g.setFont(new Font("Segoe UI", Font.BOLD, 11));
        FontMetrics fm = g.getFontMetrics();
        g.drawString(label, bx + (bw - fm.stringWidth(label)) / 2, by + 22);
        out.setBounds(bx, by, bw, bh);
        return by + bh;
    }

    private int drawUpgradeButton(Graphics2D g, String label, boolean owned,
            boolean affordable, int bx, int by, int bw, Rectangle out) {
        int bh = 32;
        Color bg = owned ? new Color(40, 120, 40) : (affordable ? new Color(70, 90, 130) : new Color(45, 48, 60));
        g.setColor(bg);
        g.fillRoundRect(bx, by, bw, bh, 6, 6);
        g.setColor(owned ? new Color(100, 220, 100) : (affordable ? Color.WHITE : new Color(90, 95, 110)));
        g.setFont(new Font("Segoe UI", affordable || owned ? Font.BOLD : Font.PLAIN, 10));
        FontMetrics fm = g.getFontMetrics();
        int tw = fm.stringWidth(label);
        if (tw > bw - 6) {
            g.setFont(new Font("Segoe UI", Font.PLAIN, 9));
            fm = g.getFontMetrics();
            tw = fm.stringWidth(label);
        }
        g.drawString(label, bx + (bw - tw) / 2, by + 21);
        out.setBounds(bx, by, bw, bh);
        return by + bh;
    }

    // ── Click handling ────────────────────────────────────────────────────────
    /** Returns one of: "dart","sniper","bomb","upgA","upgB","early", or null. */
    public String handleClick(int mx, int my, GameState state, Tower selected,
            WaveManager wm) {
        if (btnDart.contains(mx, my) && state.cash >= 100)
            return "dart";
        if (btnSniper.contains(mx, my) && state.cash >= 175)
            return "sniper";
        if (btnBomb.contains(mx, my) && state.cash >= 150)
            return "bomb";
        if (btnFarm.contains(mx, my) && state.cash >= 250)
            return "farm";
        if (btnEarly.contains(mx, my) && wm.isWavePending())
            return "early";
        if (selected != null) {
            if (btnUpgA.contains(mx, my) && selected.upgradeA == 0
                    && state.cash >= selected.getUpgradeACost())
                return "upgA";
            if (btnUpgB.contains(mx, my) && selected.upgradeB == 0
                    && state.cash >= selected.getUpgradeBCost())
                return "upgB";
        }
        return null;
    }
}
