import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Main game panel: manages one or two GameSessions (Solo or VS mode).
 * Side-by-Side Layout: [Session 1 + Sidebar 1] | [Session 2 + Sidebar 2]
 * Supports scaling to fit different screen resolutions.
 */
public class GamePanel extends JPanel implements ActionListener, MouseListener {

    public enum GameMode {
        SOLO, VS
    }

    public static final int GAME_W = 800;
    public static final int GAME_H = 600;
    public static final int CELL = 40;

    private List<GameSession> sessions = new ArrayList<>();
    private List<Sidebar> sidebars = new ArrayList<>();
    private GameMode mode = GameMode.SOLO;
    private boolean modeSelected = false;
    private double renderScale = 1.0;

    private BufferedImage buffer;
    private Graphics2D bg;
    private Timer timer;

    public GamePanel() {
        setBackground(Color.BLACK);
        addMouseListener(this);
        setFocusable(true);
        setPreferredSize(new Dimension(800, 600));
    }

    public void initGame(GameMode m) {
        this.mode = m;
        this.modeSelected = true;
        // Solo: 1.65x (1650x990) fits 1080p well. VS: 0.85x fits side-by-side.
        this.renderScale = (mode == GameMode.VS) ? 0.85 : 1.65;

        sessions.clear();
        sidebars.clear();

        long waveSeed = new Random().nextLong();

        int viewW, viewH;
        if (mode == GameMode.SOLO) {
            GameSession s1 = new GameSession(GAME_W, GAME_H, CELL);
            s1.waveMgr.setSeed(waveSeed);
            sessions.add(s1);

            Sidebar sb1 = new Sidebar();
            sb1.setOffsetX(GAME_W);
            sidebars.add(sb1);

            viewW = (int) ((GAME_W + Sidebar.WIDTH) * renderScale);
            viewH = (int) (GAME_H * renderScale);
        } else {
            // VS Players
            for (int i = 0; i < 2; i++) {
                GameSession s = new GameSession(GAME_W, GAME_H, CELL);
                s.waveMgr.setDifficulty(1.5);
                s.waveMgr.setSeed(waveSeed);
                sessions.add(s);
                Sidebar sb = new Sidebar();
                sb.setOffsetX(GAME_W);
                sidebars.add(sb);
            }
            viewW = (int) ((GAME_W + Sidebar.WIDTH) * 2 * renderScale);
            viewH = (int) (GAME_H * renderScale);
        }

        setPreferredSize(new Dimension(viewW, viewH));

        Window win = SwingUtilities.getWindowAncestor(this);
        if (win != null) {
            win.pack();
            win.setLocationRelativeTo(null);
        }

        buffer = new BufferedImage(viewW, viewH, BufferedImage.TYPE_INT_ARGB);
        bg = buffer.createGraphics();
        bg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Removed startTime = System.currentTimeMillis();
        if (timer == null) {
            timer = new Timer(16, this);
            timer.start();
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (!modeSelected)
            return;

        for (GameSession s : sessions) {
            int iterations = s.state.turboMode ? 3 : 1;
            for (int i = 0; i < iterations; i++) {
                s.logicalTime += 16;
                s.tick(s.logicalTime);
            }
        }

        if (mode == GameMode.VS) {
            GameSession s1 = sessions.get(0);
            GameSession s2 = sessions.get(1);
            if (s1.state.gameOver && !s2.state.gameOver)
                s2.state.victory = true;
            if (s2.state.gameOver && !s1.state.gameOver)
                s1.state.victory = true;
        }

        render();
        repaint();
    }

    private void render() {
        if (!modeSelected) {
            renderMenu();
            return;
        }

        bg.setColor(new Color(20, 22, 26));
        bg.fillRect(0, 0, buffer.getWidth(), buffer.getHeight());

        Graphics2D gActive = (Graphics2D) bg.create();
        gActive.scale(renderScale, renderScale);

        for (int i = 0; i < sessions.size(); i++) {
            int xOffset = i * (GAME_W + Sidebar.WIDTH);
            Graphics2D g = (Graphics2D) gActive.create(xOffset, 0, GAME_W + Sidebar.WIDTH, GAME_H);

            // Draw Game Area
            Graphics2D gGame = (Graphics2D) g.create(0, 0, GAME_W, GAME_H);
            drawSession(gGame, sessions.get(i), i);
            gGame.dispose();

            // Draw Sidebar
            sidebars.get(i).draw(g, sessions.get(i).state, sessions.get(i).selectedTower, sessions.get(i).waveMgr,
                    sessions.get(i).logicalTime);

            g.dispose();

            // Separator
            if (i > 0) {
                gActive.setColor(Color.DARK_GRAY);
                gActive.setStroke(new BasicStroke(4f / (float) renderScale));
                gActive.drawLine(xOffset, 0, xOffset, GAME_H);
                gActive.setStroke(new BasicStroke(1f));
            }
        }
        gActive.dispose();
    }

    private void renderMenu() {
        int w = getWidth() > 0 ? getWidth() : 800;
        int h = getHeight() > 0 ? getHeight() : 600;

        if (buffer == null || buffer.getWidth() != w || buffer.getHeight() != h) {
            buffer = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            bg = buffer.createGraphics();
            bg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        }

        bg.setColor(new Color(20, 22, 28));
        bg.fillRect(0, 0, w, h);

        bg.setColor(Color.WHITE);
        bg.setFont(new Font("Segoe UI", Font.BOLD, 52));
        FontMetrics fm = bg.getFontMetrics();
        String title = "TOWER DEFENSE";
        bg.drawString(title, (w - fm.stringWidth(title)) / 2, h / 2 - 120);

        // Center buttons
        int btnW = 240, btnH = 60;
        int bx = (w - btnW) / 2;
        drawMenuButton(bg, "SOLO MODE", bx, h / 2 - 40, btnW, btnH);
        drawMenuButton(bg, "VS BATTLES", bx, h / 2 + 40, btnW, btnH);
    }

    private void drawMenuButton(Graphics2D g, String txt, int x, int y, int w, int h) {
        g.setColor(new Color(50, 110, 210));
        g.fillRoundRect(x, y, w, h, 12, 12);
        g.setColor(Color.WHITE);
        g.setFont(new Font("Segoe UI", Font.BOLD, 22));
        FontMetrics fm = g.getFontMetrics();
        g.drawString(txt, x + (w - fm.stringWidth(txt)) / 2, y + h / 2 + 8);
    }

    private void drawSession(Graphics2D g, GameSession s, int id) {
        g.setColor(new Color(34, 40, 28));
        g.fillRect(0, 0, GAME_W, GAME_H);
        drawGrid(g);
        drawPath(g);
        for (Tower t : s.towers)
            t.draw(g, t == s.selectedTower);
        for (Balloon b : s.balloons)
            b.draw(g);
        for (Projectile p : s.projectiles)
            p.draw(g);
        for (FloatingText ft : s.floatingTexts)
            ft.draw(g);

        if (s.state.gameOver)
            drawOverlay(g, "DEFEAT", "Game Over", new Color(200, 40, 40, 180));
        else if (s.state.victory)
            drawOverlay(g, "VICTORY", "Winner!", new Color(40, 200, 40, 180));

        g.setColor(Color.WHITE);
        g.drawString("PLAYER " + (id + 1), 10, 20);
    }

    private void drawGrid(Graphics2D g) {
        g.setColor(new Color(42, 50, 34));
        for (int x = 0; x < GAME_W; x += CELL)
            g.drawLine(x, 0, x, GAME_H);
        for (int y = 0; y < GAME_H; y += CELL)
            g.drawLine(0, y, GAME_W, y);
    }

    private void drawPath(Graphics2D g) {
        List<Point> waypoints = Path.getWaypoints();
        g.setStroke(new BasicStroke(36, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(165, 130, 80));
        for (int i = 1; i < waypoints.size(); i++) {
            Point a = waypoints.get(i - 1), b = waypoints.get(i);
            g.drawLine(a.x, a.y, b.x, b.y);
        }
        g.setStroke(new BasicStroke(1f));
    }

    private void drawOverlay(Graphics2D g, String title, String sub, Color bg2) {
        g.setColor(bg2);
        g.fillRoundRect(GAME_W / 2 - 160, GAME_H / 2 - 70, 320, 140, 20, 20);
        g.setFont(new Font("Segoe UI", Font.BOLD, 36));
        g.setColor(Color.WHITE);
        FontMetrics fm = g.getFontMetrics();
        g.drawString(title, GAME_W / 2 - fm.stringWidth(title) / 2, GAME_H / 2 - 10);
        g.setFont(new Font("Segoe UI", Font.PLAIN, 18));
        fm = g.getFontMetrics();
        g.drawString(sub, GAME_W / 2 - fm.stringWidth(sub) / 2, GAME_H / 2 + 22);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (buffer != null)
            g.drawImage(buffer, 0, 0, null);
        else
            renderMenu();
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        int realX = e.getX(), realY = e.getY();

        // Scale mouse coords back to game space
        int mx = (int) (realX / renderScale);
        int my = (int) (realY / renderScale);

        if (!modeSelected) {
            int w = getWidth(), h = getHeight();
            int btnW = 240, btnH = 60;
            int bx = (w - btnW) / 2;

            if (realX >= bx && realX <= bx + btnW) {
                if (realY >= h / 2 - 40 && realY <= h / 2 - 40 + btnH)
                    initGame(GameMode.SOLO);
                else if (realY >= h / 2 + 40 && realY <= h / 2 + 40 + btnH)
                    initGame(GameMode.VS);
            }
            return;
        }

        boolean anyGameOver = false;
        for (GameSession s : sessions)
            if (s.state.gameOver || s.state.victory)
                anyGameOver = true;
        if (anyGameOver) {
            initGame(mode);
            return;
        }

        int fullSessionWidth = GAME_W + Sidebar.WIDTH;
        int sessionIdx = mx / fullSessionWidth;
        if (sessionIdx >= sessions.size())
            return;

        GameSession s = sessions.get(sessionIdx);
        Sidebar sb = sidebars.get(sessionIdx);
        int localX = mx % fullSessionWidth;

        // Click in sidebar
        if (localX >= GAME_W) {
            String action = sb.handleClick(localX, my, s.state, s.selectedTower, s.waveMgr);
            if (action != null)
                handleSidebarAction(s, action);
            return;
        }

        // Click in game area
        int gx = localX / CELL;
        int gy = my / CELL;

        for (Tower t : s.towers) {
            if (gx >= t.gridX && gx < t.gridX + t.getSize() &&
                    gy >= t.gridY && gy < t.gridY + t.getSize()) {
                s.selectedTower = (s.selectedTower == t) ? null : t;
                s.state.selectedTowerType = null;
                return;
            }
        }

        if (s.state.selectedTowerType != null)
            placeTower(s, gx, gy);
        else
            s.selectedTower = null;
    }

    private void handleSidebarAction(GameSession s, String action) {
        switch (action) {
            case "dart" -> {
                s.state.selectedTowerType = "dart";
                s.selectedTower = null;
            }
            case "sniper" -> {
                s.state.selectedTowerType = "sniper";
                s.selectedTower = null;
            }
            case "bomb" -> {
                s.state.selectedTowerType = "bomb";
                s.selectedTower = null;
            }
            case "farm" -> {
                s.state.selectedTowerType = "farm";
                s.selectedTower = null;
            }
            case "early" -> s.waveMgr.startNextWave(s.state);
            case "turbo" -> s.state.turboMode = !s.state.turboMode;
            case "upgA" -> {
                if (s.selectedTower != null)
                    s.selectedTower.upgrade(0, s.state);
            }
            case "upgB" -> {
                if (s.selectedTower != null)
                    s.selectedTower.upgrade(1, s.state);
            }
        }
    }

    private void placeTower(GameSession s, int gx, int gy) {
        int size = 1;
        if ("farm".equals(s.state.selectedTowerType))
            size = 2;
        if (gx < 0 || gy < 0 || gx + size > GAME_W / CELL || gy + size > GAME_H / CELL)
            return;

        for (int dx = 0; dx < size; dx++) {
            for (int dy = 0; dy < size; dy++) {
                int nx = gx + dx, ny = gy + dy;
                if (s.onPath[nx][ny])
                    return;
                for (Tower t : s.towers) {
                    if (nx >= t.gridX && nx < t.gridX + t.getSize() &&
                            ny >= t.gridY && ny < t.gridY + t.getSize())
                        return;
                }
            }
        }

        Tower newTower = switch (s.state.selectedTowerType) {
            case "dart" -> new DartTower(gx, gy, CELL);
            case "sniper" -> new SniperTower(gx, gy, CELL);
            case "bomb" -> new BombTower(gx, gy, CELL);
            case "farm" -> new BananaFarm(gx, gy, CELL);
            default -> null;
        };
        if (newTower == null || !s.state.canAfford(newTower.getCost()))
            return;
        s.state.spend(newTower.getCost());
        s.towers.add(newTower);
        s.state.selectedTowerType = null;
    }

    public void mousePressed(MouseEvent e) {
    }

    public void mouseReleased(MouseEvent e) {
    }

    public void mouseEntered(MouseEvent e) {
    }

    public void mouseExited(MouseEvent e) {
    }
}
