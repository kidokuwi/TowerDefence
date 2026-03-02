import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Main game panel: manages one or two GameSessions (Solo or VS mode).
 */
public class GamePanel extends JPanel implements ActionListener, MouseListener {

    public enum GameMode {
        SOLO, VS
    }

    public static final int GAME_W = 800;
    public static final int GAME_H = 600;
    public static final int CELL = 40;

    private List<GameSession> sessions = new ArrayList<>();
    private final Sidebar sidebar = new Sidebar();
    private GameMode mode = GameMode.SOLO;
    private boolean modeSelected = false;

    private BufferedImage buffer;
    private Graphics2D bg;
    private Timer timer;
    private long startTime;

    public GamePanel() {
        setBackground(Color.BLACK);
        addMouseListener(this);
        setFocusable(true);
        // Initial size for menu
        setPreferredSize(new Dimension(800, 600));
    }

    public void initGame(GameMode m) {
        this.mode = m;
        this.modeSelected = true;
        sessions.clear();

        if (mode == GameMode.SOLO) {
            sessions.add(new GameSession(GAME_W, GAME_H, CELL));
            setPreferredSize(new Dimension(GAME_W + Sidebar.WIDTH, GAME_H));
            sidebar.setOffsetX(GAME_W);
        } else {
            sessions.add(new GameSession(GAME_W, GAME_H, CELL));
            GameSession p2 = new GameSession(GAME_W, GAME_H, CELL);
            p2.waveMgr.setDifficulty(1.5); // Harder rounds for VS
            sessions.add(p2);
            // VS rounds are harder for everyone actually
            sessions.get(0).waveMgr.setDifficulty(1.5);

            setPreferredSize(new Dimension(GAME_W * 2 + Sidebar.WIDTH, GAME_H));
            sidebar.setOffsetX(GAME_W * 2);
        }

        // Revalidate and repaint for new size
        Container parent = getParent();
        if (parent instanceof JViewport vp) {
            // inside a scroll pane maybe?
        } else if (parent != null) {
            Window win = SwingUtilities.getWindowAncestor(this);
            if (win != null)
                win.pack();
        }

        buffer = new BufferedImage(getPreferredSize().width, GAME_H, BufferedImage.TYPE_INT_ARGB);
        bg = buffer.createGraphics();
        bg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        startTime = System.currentTimeMillis();
        if (timer == null) {
            timer = new Timer(16, this);
            timer.start();
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (!modeSelected)
            return;

        long now = System.currentTimeMillis() - startTime;
        for (GameSession s : sessions) {
            s.tick(now);
        }

        // Check win condition for VS
        if (mode == GameMode.VS) {
            GameSession s1 = sessions.get(0);
            GameSession s2 = sessions.get(1);
            if (s1.state.gameOver && !s2.state.gameOver)
                s2.state.victory = true;
            if (s2.state.gameOver && !s1.state.gameOver)
                s1.state.victory = true;
        }

        render(now);
        repaint();
    }

    private void render(long nowMs) {
        if (!modeSelected) {
            renderMenu();
            return;
        }

        bg.setColor(new Color(20, 22, 26));
        bg.fillRect(0, 0, buffer.getWidth(), buffer.getHeight());

        for (int i = 0; i < sessions.size(); i++) {
            Graphics2D g = (Graphics2D) bg.create(i * GAME_W, 0, GAME_W, GAME_H);
            drawSession(g, sessions.get(i), i);
            g.dispose();

            // Separator line
            if (i > 0) {
                bg.setColor(Color.DARK_GRAY);
                bg.drawLine(i * GAME_W, 0, i * GAME_W, GAME_H);
            }
        }

        // Sidebar uses the first session for purchasing? Or whichever is "active"?
        // In simple VS, maybe sidebar shows info for P1 or both?
        // Let's assume sidebar controls the first session's selection for now.
        // Actually, in VS we need two sidebars or a shared one.
        // To keep it simple, Sidebar will show stats for the last clicked session.
        GameSession active = sessions.get(0);
        for (GameSession s : sessions) {
            if (s.selectedTower != null || s.state.selectedTowerType != null)
                active = s;
        }
        sidebar.draw(bg, active.state, active.selectedTower, active.waveMgr, nowMs);
    }

    private void renderMenu() {
        if (buffer == null) {
            buffer = new BufferedImage(800, 600, BufferedImage.TYPE_INT_ARGB);
            bg = buffer.createGraphics();
        }
        bg.setColor(new Color(30, 32, 40));
        bg.fillRect(0, 0, 800, 600);
        bg.setColor(Color.WHITE);
        bg.setFont(new Font("Segoe UI", Font.BOLD, 48));
        bg.drawString("TOWER DEFENSE", 200, 150);

        drawMenuButton(bg, "SOLO MODE", 300, 250, 200, 50);
        drawMenuButton(bg, "VS BATTLES", 300, 320, 200, 50);
    }

    private void drawMenuButton(Graphics2D g, String txt, int x, int y, int w, int h) {
        g.setColor(new Color(60, 100, 180));
        g.fillRoundRect(x, y, w, h, 10, 10);
        g.setColor(Color.WHITE);
        g.setFont(new Font("Segoe UI", Font.BOLD, 20));
        FontMetrics fm = g.getFontMetrics();
        g.drawString(txt, x + (w - fm.stringWidth(txt)) / 2, y + 32);
    }

    private void drawSession(Graphics2D g, GameSession s, int id) {
        // Background
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

        if (s.state.gameOver) {
            drawOverlay(g, "DEFEAT", "Game Over", new Color(200, 40, 40, 180));
        } else if (s.state.victory) {
            drawOverlay(g, "VICTORY", "You are the winner!", new Color(40, 200, 40, 180));
        }

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
            renderMenu(); // Initial render if timer hasn't started
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        int mx = e.getX(), my = e.getY();

        if (!modeSelected) {
            if (mx >= 300 && mx <= 500) {
                if (my >= 250 && my <= 300)
                    initGame(GameMode.SOLO);
                else if (my >= 320 && my <= 370)
                    initGame(GameMode.VS);
            }
            return;
        }

        // Restart on game over
        boolean anyGameOver = false;
        for (GameSession s : sessions)
            if (s.state.gameOver || s.state.victory)
                anyGameOver = true;
        if (anyGameOver) {
            initGame(mode);
            return;
        }

        // Click inside sidebar
        if (mx >= sidebarOffset()) {
            // Which session is currently controlled? Let's say it's the one most recently
            // clicked.
            // For now, simplify: sidebar controls ALL sessions or just P1?
            // User said "VS Battles which splits the screen into 2 games that run
            // simultainlesly".
            // Typically in mobile and simple PVP, you have your own sidebar.
            // Here, let's assume P1 is the user.
            GameSession s = sessions.get(0);
            String action = sidebar.handleClick(mx, my, s.state, s.selectedTower, s.waveMgr);
            if (action == null)
                return;
            handleSidebarAction(s, action);
            return;
        }

        // Click on game area
        int sessionIdx = mx / GAME_W;
        if (sessionIdx >= sessions.size())
            return;

        GameSession s = sessions.get(sessionIdx);
        int localX = mx % GAME_W;
        int gx = localX / CELL;
        int gy = my / CELL;

        // Selection
        for (Tower t : s.towers) {
            if (t.gridX == gx && t.gridY == gy) {
                s.selectedTower = (s.selectedTower == t) ? null : t;
                s.state.selectedTowerType = null;
                return;
            }
        }

        if (s.state.selectedTowerType != null) {
            placeTower(s, gx, gy);
        } else {
            s.selectedTower = null;
        }
    }

    private int sidebarOffset() {
        return mode == GameMode.SOLO ? GAME_W : GAME_W * 2;
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
            case "upgA" -> s.selectedTower.upgrade(0, s.state);
            case "upgB" -> s.selectedTower.upgrade(1, s.state);
        }
    }

    private void placeTower(GameSession s, int gx, int gy) {
        if (gx < 0 || gy < 0 || gx >= GAME_W / CELL || gy >= GAME_H / CELL)
            return;
        if (s.onPath[gx][gy])
            return;
        for (Tower t : s.towers)
            if (t.gridX == gx && t.gridY == gy)
                return;

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

    // Unused mouse events
    public void mousePressed(MouseEvent e) {
    }

    public void mouseReleased(MouseEvent e) {
    }

    public void mouseEntered(MouseEvent e) {
    }

    public void mouseExited(MouseEvent e) {
    }
}
