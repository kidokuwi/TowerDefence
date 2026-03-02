import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Main game panel: owns the 60-FPS game loop, all entities, rendering, and
 * input.
 * Layout: 800 px game area | 200 px sidebar = 1000 × 600 window.
 */
public class GamePanel extends JPanel implements ActionListener, MouseListener {

    // ── Layout constants ──────────────────────────────────────────────────────
    public static final int GAME_W = 800;
    public static final int GAME_H = 600;
    public static final int CELL = 40; // grid cell size in pixels

    // ── Game state & entities ─────────────────────────────────────────────────
    private final GameState state = new GameState();
    private final WaveManager waveMgr = new WaveManager();
    private final Sidebar sidebar = new Sidebar();

    private final List<Balloon> balloons = new ArrayList<>();
    private final List<Tower> towers = new ArrayList<>();
    private final List<Projectile> projectiles = new ArrayList<>();

    private Tower selectedTower = null; // currently clicked placed tower

    // Double-buffer
    private BufferedImage buffer;
    private Graphics2D bg;

    // Timer (60 FPS)
    private Timer timer;
    private long startTime;

    // Path
    private final List<Point> waypoints = Path.getWaypoints();
    // Pre-computed set of path cells for placement validation
    private final boolean[][] onPath = new boolean[GAME_W / CELL + 1][GAME_H / CELL + 1];

    // ── Constructor ───────────────────────────────────────────────────────────
    public GamePanel() {
        setPreferredSize(new Dimension(GAME_W + Sidebar.WIDTH, GAME_H));
        setBackground(Color.BLACK);
        addMouseListener(this);
        sidebar.setOffsetX(GAME_W);
        markPathCells();
    }

    /**
     * Marks grid cells that lie on or very near the path so towers can't be placed
     * there.
     */
    private void markPathCells() {
        for (int i = 1; i < waypoints.size(); i++) {
            Point a = waypoints.get(i - 1);
            Point b = waypoints.get(i);
            int steps = (int) (a.distance(b) / 5) + 1;
            for (int s = 0; s <= steps; s++) {
                double t = (double) s / steps;
                int px = (int) (a.x + t * (b.x - a.x));
                int py = (int) (a.y + t * (b.y - a.y));
                int gx = px / CELL;
                int gy = py / CELL;
                for (int dx = -1; dx <= 1; dx++)
                    for (int dy = -1; dy <= 1; dy++) {
                        int nx = gx + dx, ny = gy + dy;
                        if (nx >= 0 && ny >= 0 && nx < onPath.length && ny < onPath[0].length)
                            onPath[nx][ny] = true;
                    }
            }
        }
    }

    public void startGame() {
        buffer = new BufferedImage(GAME_W + Sidebar.WIDTH, GAME_H, BufferedImage.TYPE_INT_ARGB);
        bg = buffer.createGraphics();
        bg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        startTime = System.currentTimeMillis();
        timer = new Timer(16, this); // ~60 FPS
        timer.start();
    }

    // ── Game loop ─────────────────────────────────────────────────────────────
    @Override
    public void actionPerformed(ActionEvent e) {
        long now = System.currentTimeMillis() - startTime;
        if (!state.gameOver) {
            waveMgr.tick(balloons, state, now);
            moveBalloons();
            fireTowers(now);
            updateProjectiles();
        }
        render(now);
        repaint();
    }

    private void moveBalloons() {
        Iterator<Balloon> it = balloons.iterator();
        while (it.hasNext()) {
            Balloon b = it.next();
            if (b.dead) {
                it.remove();
                continue;
            }
            b.move();
            if (b.reachedEnd) {
                state.loseLife();
                it.remove();
            }
        }
    }

    private void fireTowers(long now) {
        for (Tower t : towers) {
            if (!t.canFire(now))
                continue;
            if (t instanceof DartTower dt) {
                Balloon tgt = dt.findTarget(balloons);
                if (tgt != null)
                    projectiles.add(dt.fire(tgt, now));
            } else if (t instanceof SniperTower st) {
                List<Projectile> shots = st.fireAll(balloons, now);
                projectiles.addAll(shots);
            } else if (t instanceof BombTower bt) {
                Balloon tgt = bt.findTarget(balloons);
                if (tgt != null) {
                    Projectile bomb = bt.fire(tgt, now);
                    projectiles.add(bomb);
                    // Cluster: add 2 more aimed at nearest other balloons
                    if (bt.isCluster()) {
                        int added = 0;
                        for (Balloon b : balloons) {
                            if (b == tgt || b.dead || b.reachedEnd)
                                continue;
                            projectiles.add(new Projectile(
                                    bt.px, bt.py, b, bt.damage * 0.5,
                                    3.5, new Color(180, 80, 20), true, bt.getBlastRadius() / 2));
                            if (++added >= 2)
                                break;
                        }
                    }
                }
            }
        }
    }

    private void updateProjectiles() {
        Iterator<Projectile> it = projectiles.iterator();
        while (it.hasNext()) {
            Projectile p = it.next();
            p.update(balloons, state);
            if (p.done)
                it.remove();
        }
        // Remove dead balloons (hit by AoE that already removed them)
        balloons.removeIf(b -> b.dead);
    }

    // ── Rendering ─────────────────────────────────────────────────────────────
    private void render(long nowMs) {
        // Background
        bg.setColor(new Color(34, 40, 28));
        bg.fillRect(0, 0, GAME_W, GAME_H);

        drawGrid();
        drawPath();

        for (Tower t : towers)
            t.draw(bg, t == selectedTower);
        for (Balloon b : balloons)
            b.draw(bg);
        for (Projectile p : projectiles)
            p.draw(bg);

        sidebar.draw(bg, state, selectedTower, waveMgr, nowMs);

        if (state.gameOver)
            drawOverlay("GAME OVER", "Score: " + state.score,
                    new Color(200, 40, 40, 200));
    }

    private void drawGrid() {
        bg.setColor(new Color(42, 50, 34));
        for (int x = 0; x < GAME_W; x += CELL)
            bg.drawLine(x, 0, x, GAME_H);
        for (int y = 0; y < GAME_H; y += CELL)
            bg.drawLine(0, y, GAME_W, y);
    }

    private void drawPath() {
        // Fill path segments
        bg.setStroke(new BasicStroke(36, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        bg.setColor(new Color(165, 130, 80));
        for (int i = 1; i < waypoints.size(); i++) {
            Point a = waypoints.get(i - 1), b = waypoints.get(i);
            bg.drawLine(a.x, a.y, b.x, b.y);
        }
        // Edge lines
        bg.setColor(new Color(120, 90, 50));
        bg.setStroke(new BasicStroke(38, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        // (drawn behind – we draw highlights on top)
        bg.setStroke(new BasicStroke(1f));
    }

    private void drawOverlay(String title, String sub, Color bg2) {
        bg.setColor(bg2);
        bg.fillRoundRect(GAME_W / 2 - 160, GAME_H / 2 - 70, 320, 140, 20, 20);
        bg.setFont(new Font("Segoe UI", Font.BOLD, 36));
        bg.setColor(Color.WHITE);
        FontMetrics fm = bg.getFontMetrics();
        bg.drawString(title, GAME_W / 2 - fm.stringWidth(title) / 2, GAME_H / 2 - 10);
        bg.setFont(new Font("Segoe UI", Font.PLAIN, 18));
        fm = bg.getFontMetrics();
        bg.drawString(sub, GAME_W / 2 - fm.stringWidth(sub) / 2, GAME_H / 2 + 22);
        bg.setFont(new Font("Segoe UI", Font.ITALIC, 13));
        String restart = "Click anywhere to restart";
        fm = bg.getFontMetrics();
        bg.setColor(new Color(220, 220, 220));
        bg.drawString(restart, GAME_W / 2 - fm.stringWidth(restart) / 2, GAME_H / 2 + 50);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (buffer != null)
            g.drawImage(buffer, 0, 0, null);
    }

    // ── Mouse input ───────────────────────────────────────────────────────────
    @Override
    public void mouseClicked(MouseEvent e) {
        int mx = e.getX(), my = e.getY();

        // Restart on game over
        if (state.gameOver) {
            restartGame();
            return;
        }

        // Click inside sidebar
        if (mx >= GAME_W) {
            String action = sidebar.handleClick(mx, my, state, selectedTower, waveMgr);
            if (action == null)
                return;
            switch (action) {
                case "dart" -> {
                    state.selectedTowerType = "dart";
                    selectedTower = null;
                }
                case "sniper" -> {
                    state.selectedTowerType = "sniper";
                    selectedTower = null;
                }
                case "bomb" -> {
                    state.selectedTowerType = "bomb";
                    selectedTower = null;
                }
                case "early" -> waveMgr.startNextWave(state);
                case "upgA" -> selectedTower.upgrade(0, state);
                case "upgB" -> selectedTower.upgrade(1, state);
            }
            return;
        }

        // Click on game area
        int gx = mx / CELL;
        int gy = my / CELL;

        // First: check if we clicked an existing tower to select it
        for (Tower t : towers) {
            if (t.gridX == gx && t.gridY == gy) {
                selectedTower = (selectedTower == t) ? null : t;
                state.selectedTowerType = null;
                return;
            }
        }

        // If a tower type is selected, place it
        if (state.selectedTowerType != null) {
            placeTower(gx, gy);
        } else {
            selectedTower = null;
        }
    }

    private void placeTower(int gx, int gy) {
        // Bounds check
        if (gx < 0 || gy < 0 || gx >= GAME_W / CELL || gy >= GAME_H / CELL)
            return;
        // Not on path
        if (onPath[gx][gy])
            return;
        // Not occupied
        for (Tower t : towers)
            if (t.gridX == gx && t.gridY == gy)
                return;

        Tower newTower = switch (state.selectedTowerType) {
            case "dart" -> new DartTower(gx, gy, CELL);
            case "sniper" -> new SniperTower(gx, gy, CELL);
            case "bomb" -> new BombTower(gx, gy, CELL);
            default -> null;
        };
        if (newTower == null)
            return;

        if (!state.canAfford(newTower.getCost()))
            return;
        state.spend(newTower.getCost());
        towers.add(newTower);
        state.selectedTowerType = null;
    }

    private void restartGame() {
        state.reset();
        balloons.clear();
        towers.clear();
        projectiles.clear();
        selectedTower = null;
        startTime = System.currentTimeMillis();
        // Recreate wave manager
        waveMgr.getClass(); // just a no-op; reflection on primitive fields would be complex
        // Easier: reset via a fresh WaveManager field isn't possible (field is final)
        // so we work around it using a flag — add a reset() to WaveManager instead
        waveMgr.reset();
    }

    // Unused mouse events
    @Override
    public void mousePressed(MouseEvent e) {
    }

    @Override
    public void mouseReleased(MouseEvent e) {
    }

    @Override
    public void mouseEntered(MouseEvent e) {
    }

    @Override
    public void mouseExited(MouseEvent e) {
    }
}
