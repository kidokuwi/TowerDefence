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
    private final Rectangle btnTurbo = new Rectangle();
    private final Rectangle btnSell = new Rectangle();
    private final Rectangle btnHome = new Rectangle();

    // Send balloon buttons (VS mode)
    private final Rectangle[] btnSends = new Rectangle[11]; // Levels 1-10

    private int offsetX; // px where sidebar starts

    public void setOffsetX(int x) {
        this.offsetX = x;
    }

    // ── Render ────────────────────────────────────────────────────────────────
    public void draw(Graphics2D g, GameState state, Tower selected,
            WaveManager wm, long nowMs, boolean isOpponent) {
        int x = offsetX;
        int w = WIDTH;

        // Background
        g.setColor(new Color(28, 30, 38));
        g.fillRect(x, 0, w, 1000); // Increased height for upscaling
        g.setColor(new Color(60, 65, 80));
        g.drawLine(x, 0, x, 1000);

        int cy = 10;

        // ── Home Button (Top Right) ────────────────────────────────────────
        g.setColor(new Color(180, 60, 60));
        g.fillRoundRect(x + w - 55, cy, 45, 24, 6, 6);
        g.setColor(Color.WHITE);
        g.setFont(new Font("Segoe UI", Font.BOLD, 10));
        g.drawString("HOME", x + w - 48, cy + 16);
        btnHome.setBounds(x + w - 55, cy, 45, 24);

        cy = 14;
        // ── Stats ──────────────────────────────────────────────────────────
        g.setFont(new Font("Segoe UI", Font.BOLD, 15));

        // Money Icon & Text
        drawCoin(g, x + 18, cy + 15);
        g.setColor(new Color(255, 215, 80));
        g.drawString("$" + state.cash, x + 35, cy += 20);

        // Health Icon & Text
        drawHeart(g, x + 18, cy + 17);
        g.setColor(new Color(240, 80, 80));
        g.drawString(state.lives + " lives", x + 35, cy += 22);
        // Wave Icon & Text (moved up)
        drawWave(g, x + 18, cy + 17);
        g.setColor(new Color(140, 200, 255));
        g.drawString("Wave " + state.waveNumber, x + 35, cy += 22);
        // Compact score/eco
        g.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        g.setColor(new Color(180, 180, 180));
        g.drawString("Score: " + state.score, x + 10, cy += 17);
        if (state.income > 0) {
            g.setColor(new Color(255, 215, 0));
            g.drawString("ECO: $" + state.income + " (6s)", x + 100, cy);
        }

        // Divider
        cy += 6;
        g.setColor(new Color(60, 65, 80));
        g.drawLine(x + 10, cy, x + w - 10, cy);
        cy += 6;

        // ── Tower shop ────────────────────────────────────────────────────
        g.setFont(new Font("Segoe UI", Font.BOLD, 12));
        g.setColor(new Color(200, 210, 230));
        g.drawString("BUY TOWER", x + 10, cy += 14);
        cy += 4;

        // Shop grid (2 columns)
        String[] shopTypes = { "dart", "sniper", "bomb", "farm" };
        String[] shopLabels = { "Dart $100", "Sniper $175", "Bomb $150", "Farm $450" };
        Color[] shopCols = { new Color(70, 130, 220), new Color(100, 100, 120), new Color(210, 100, 30),
                new Color(240, 230, 60) };
        Rectangle[] shopRects = { btnDart, btnSniper, btnBomb, btnFarm };
        int[] shopCosts = { 100, 175, 150, 450 };

        for (int i = 0; i < 4; i++) {
            int bx = x + 8 + (i % 2) * (w / 2 - 8);
            int by = cy + (i / 2) * 32;
            drawShopButton(g, shopLabels[i], shopCols[i], state.cash >= shopCosts[i],
                    shopTypes[i].equals(state.selectedTowerType), bx, by, w / 2 - 12, shopRects[i]);
        }
        cy += 64;

        // ── Send Balloons (Multiplayer only, only shown for player side)
        // ─────────────────
        if (!isOpponent && state.income > 0) {
            cy += 8;
            g.setColor(new Color(60, 65, 80));
            g.drawLine(x + 10, cy, x + w - 10, cy);
            cy += 14;
            g.setFont(new Font("Segoe UI", Font.BOLD, 12));
            g.setColor(new Color(255, 100, 100));
            g.drawString("SEND BALLOONS", x + 10, cy);
            cy += 6;

            // Send buttons grid: 2 columns
            int ty = cy;
            for (int i = 0; i < 8; i++) { // Levels 1-8
                int level = i + 1;
                int cost = Balloon.getSendCost(level);
                int ecoPlus = Balloon.getSendEcoChange(level);
                String label = "L" + level + " (+$" + ecoPlus + ")";
                if (level == 7)
                    label = "Lead (+$" + ecoPlus + ")";
                if (level == 8)
                    label = "Purp (+$" + ecoPlus + ")";

                int bx = x + 8 + (i % 2) * (w / 2 - 8);
                int by = ty + (i / 2) * 32;
                if (btnSends[level] == null)
                    btnSends[level] = new Rectangle();
                drawSendButton(g, label, cost, state.cash >= cost, bx, by, w / 2 - 12, btnSends[level]);
                cy = Math.max(cy, by + 32);
            }
            // MOAB (Level 10)
            int cost10 = Balloon.getSendCost(10);
            if (btnSends[10] == null)
                btnSends[10] = new Rectangle();
            cy = drawSendButton(g, "MOAB (-$25 Eco)", cost10, state.cash >= cost10, x + 8, cy + 4, w - 16,
                    btnSends[10]);
        }

        // Divider
        cy += 6;
        g.setColor(new Color(60, 65, 80));
        g.drawLine(x + 10, cy, x + w - 10, cy);
        cy += 6;

        // ── Selected tower info ───────────────────────────────────────────
        if (selected != null) {
            g.setFont(new Font("Segoe UI", Font.BOLD, 12));
            g.setColor(new Color(200, 230, 255));
            cy += 12;
            g.drawString(selected.getName(), x + 10, cy);
            g.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            g.setColor(new Color(170, 185, 200));
            g.drawString("Rng: " + (int) selected.range + " Dmg: " + (int) selected.damage, x + 10, cy += 14);

            cy += 8;

            boolean canA = selected.upgradeA < 3 && state.cash >= selected.getUpgradeACost();
            boolean canB = selected.upgradeB < 3 && state.cash >= selected.getUpgradeBCost();

            cy = drawUpgradeButton(g, selected.getUpgradeAName(),
                    selected.upgradeA == 3, canA, x + 8, cy, w - 16, btnUpgA);
            cy += 4;
            cy = drawUpgradeButton(g, selected.getUpgradeBName(),
                    selected.upgradeB == 3, canB, x + 8, cy, w - 16, btnUpgB);
            cy += 8;
            cy = drawSellButton(g, "SELL ($" + selected.getSellPrice() + ")", x + 8, cy, w - 16, btnSell);
        } else {
            btnUpgA.setSize(0, 0);
            btnUpgB.setSize(0, 0);
            btnSell.setSize(0, 0);
            g.setFont(new Font("Segoe UI", Font.ITALIC, 11));
            g.setColor(new Color(120, 130, 150));
            g.drawString("Click a tower to upgrade", x + 10, cy + 18);
        }

        if (state.income == 0) {
            // ── Turbo Mode button ─────────────────────────────────────────────
            int turboY = 600 - 100;
            g.setColor(state.turboMode ? new Color(255, 140, 0) : new Color(60, 65, 80));
            g.fillRoundRect(x + 8, turboY, w - 16, 38, 8, 8);
            g.setColor(Color.WHITE);
            g.setFont(new Font("Segoe UI", Font.BOLD, 12));
            String turboLabel = state.turboMode ? "      TURBO: 3x" : "      TURBO: OFF";
            FontMetrics fmT = g.getFontMetrics();
            g.drawString(turboLabel, x + 8 + (w - 16 - fmT.stringWidth(turboLabel)) / 2, turboY + 24);

            // Turbo Icon (Lightning)
            drawBolt(g, x + 40, turboY + 19, state.turboMode ? Color.YELLOW : Color.LIGHT_GRAY);

            btnTurbo.setBounds(x + 8, turboY, w - 16, 38);
        } else {
            btnTurbo.setSize(0, 0);
        }

        if (state.income == 0) {
            // ── Start Wave Early button ───────────────────────────────────────
            int btnY = 600 - 55;
            boolean canEarly = wm.isWavePending();
            String label = canEarly
                    ? "Start Wave Early (" + wm.secondsToNextWave(nowMs) + "s)"
                    : "Waiting for balloons...";

            Color btnCol = canEarly ? new Color(60, 180, 80) : new Color(60, 65, 80);
            g.setColor(btnCol);
            g.fillRoundRect(x + 8, btnY, w - 16, 38, 8, 8);
            g.setColor(canEarly ? new Color(100, 255, 120) : new Color(100, 105, 115));
            g.setFont(new Font("Segoe UI", Font.BOLD, 12));
            FontMetrics fm = g.getFontMetrics();
            g.drawString(label, x + 8 + (w - 16 - fm.stringWidth(label)) / 2,
                    btnY + 24);
            btnEarly.setBounds(x + 8, btnY, w - 16, 38);
        } else {
            btnEarly.setSize(0, 0);
        }
    }

    private void drawCoin(Graphics2D g, int x, int y) {
        g.setColor(new Color(255, 215, 80));
        g.fillOval(x - 7, y - 7, 14, 14);
        g.setColor(new Color(180, 140, 20));
        g.drawOval(x - 7, y - 7, 14, 14);
        g.setFont(new Font("Arial", Font.BOLD, 10));
        g.drawString("$", x - 3, y + 4);
    }

    private void drawHeart(Graphics2D g, int x, int y) {
        g.setColor(new Color(240, 80, 80));
        int[] triX = { x - 7, x + 7, x };
        int[] triY = { y - 1, y - 1, y + 7 };
        g.fillOval(x - 7, y - 6, 8, 8);
        g.fillOval(x - 1, y - 6, 8, 8);
        g.fillPolygon(triX, triY, 3);
    }

    private void drawWave(Graphics2D g, int x, int y) {
        g.setColor(new Color(140, 200, 255));
        g.setStroke(new BasicStroke(2f));
        g.drawArc(x - 6, y - 4, 6, 6, 0, 180);
        g.drawArc(x, y - 4, 6, 6, 180, 180);
        g.setStroke(new BasicStroke(1f));
    }

    private void drawBolt(Graphics2D g, int x, int y, Color color) {
        g.setColor(color);
        int[] px = { x, x + 5, x + 2, x + 8, x + 3, x + 6, x };
        int[] py = { y, y, y + 4, y + 4, y + 10, y + 4, y + 4 };
        g.fillPolygon(px, py, 7);
    }

    private int drawShopButton(Graphics2D g, String label, Color col,
            boolean affordable, boolean selected,
            int bx, int by, int bw, Rectangle out) {
        int bh = 28;
        Color bg = selected ? col.brighter() : (affordable ? col.darker() : new Color(50, 52, 60));
        g.setColor(bg);
        g.fillRoundRect(bx, by, bw, bh, 7, 7);
        if (selected) {
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(1.5f));
            g.drawRoundRect(bx, by, bw, bh, 7, 7);
            g.setStroke(new BasicStroke(1f));
        }
        g.setColor(affordable ? Color.WHITE : new Color(120, 120, 130));
        g.setFont(new Font("Segoe UI", Font.BOLD, 10));
        FontMetrics fm = g.getFontMetrics();
        g.drawString(label, bx + (bw - fm.stringWidth(label)) / 2, by + 18);
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

    private int drawSendButton(Graphics2D g, String label, int cost, boolean affordable, int bx, int by, int bw,
            Rectangle out) {
        int bh = 28;
        g.setColor(affordable ? new Color(60, 40, 40) : new Color(40, 42, 45));
        g.fillRoundRect(bx, by, bw, bh, 5, 5);
        g.setColor(affordable ? Color.WHITE : new Color(100, 100, 110));
        g.setFont(new Font("Segoe UI", Font.BOLD, 9));
        FontMetrics fm = g.getFontMetrics();
        g.drawString(label, bx + (bw - fm.stringWidth(label)) / 2, by + 13);
        g.setFont(new Font("Segoe UI", Font.PLAIN, 8));
        g.drawString("$" + cost, bx + (bw - g.getFontMetrics().stringWidth("$" + cost)) / 2, by + 23);
        out.setBounds(bx, by, bw, bh);
        return by + bh;
    }

    private int drawSellButton(Graphics2D g, String label, int bx, int by, int bw, Rectangle out) {
        int bh = 30;
        g.setColor(new Color(180, 60, 60));
        g.fillRoundRect(bx, by, bw, bh, 6, 6);
        g.setColor(Color.WHITE);
        g.setFont(new Font("Segoe UI", Font.BOLD, 11));
        FontMetrics fm = g.getFontMetrics();
        g.drawString(label, bx + (bw - fm.stringWidth(label)) / 2, by + 19);
        out.setBounds(bx, by, bw, bh);
        return by + bh;
    }

    // ── Click handling ────────────────────────────────────────────────────────
    /**
     * Returns one of: "dart","sniper","bomb","upgA","upgB","early", "send_LVL", or
     * null.
     */
    public String handleClick(int mx, int my, GameState state, Tower selected,
            WaveManager wm, boolean isPlayerSide) {
        // Shop
        if (btnDart.contains(mx, my) && state.cash >= 100)
            return "dart";
        if (btnSniper.contains(mx, my) && state.cash >= 175)
            return "sniper";
        if (btnBomb.contains(mx, my) && state.cash >= 150)
            return "bomb";
        if (btnFarm.contains(mx, my) && state.cash >= 450)
            return "farm";
        if (btnHome.contains(mx, my))
            return "home";

        // Sends (Available regardless of sidebar focus, but only if income > 0)
        if (isPlayerSide && state.income > 0) {
            for (int i = 1; i <= 10; i++) {
                if (btnSends[i] != null && btnSends[i].contains(mx, my) && state.cash >= Balloon.getSendCost(i)) {
                    return "send_" + i;
                }
            }
        }

        if (btnEarly.contains(mx, my) && wm.isWavePending())
            return "early";
        if (isPlayerSide && btnTurbo.contains(mx, my))
            return "turbo";

        if (selected != null) {
            if (btnUpgA.contains(mx, my) && selected.upgradeA < 3
                    && state.cash >= selected.getUpgradeACost())
                return "upgA";
            if (btnUpgB.contains(mx, my) && selected.upgradeB < 3
                    && state.cash >= selected.getUpgradeBCost())
                return "upgB";
            if (btnSell.contains(mx, my))
                return "sell";
        }
        return null;
    }
}
