import java.awt.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Encapsulates all state and logic for a single "game instance".
 * This allows multiple games (VS mode) to run side-by-side.
 */
public class GameSession {
    public final GameState state = new GameState();
    public final WaveManager waveMgr = new WaveManager();
    public long logicalTime = 0;

    public final List<Balloon> balloons = new ArrayList<>();
    public final List<Tower> towers = new ArrayList<>();
    public final List<Projectile> projectiles = new ArrayList<>();
    public final List<FloatingText> floatingTexts = new ArrayList<>();

    public Tower selectedTower = null;
    public final boolean[][] onPath;
    private final List<Point> waypoints;
    private final int cell;

    public GameSession(int gameW, int gameH, int cellSize) {
        this.cell = cellSize;
        this.waypoints = Path.getWaypoints();
        this.onPath = new boolean[gameW / cell + 1][gameH / cell + 1];
        markPathCells();
    }

    private void markPathCells() {
        for (int i = 1; i < waypoints.size(); i++) {
            Point a = waypoints.get(i - 1);
            Point b = waypoints.get(i);
            int steps = (int) (a.distance(b) / 5) + 1;
            for (int s = 0; s <= steps; s++) {
                double t = (double) s / steps;
                int px = (int) (a.x + t * (b.x - a.x));
                int py = (int) (a.y + t * (b.y - a.y));
                int gx = px / cell;
                int gy = py / cell;
                if (gx >= 0 && gy >= 0 && gx < onPath.length && gy < onPath[0].length) {
                    onPath[gx][gy] = true;
                }
            }
        }
    }

    public void tick(long now) {
        if (!state.gameOver) {
            waveMgr.tick(balloons, state, now);
            moveBalloons();
            fireTowers(now);
            updateProjectiles();
            updateFloatingTexts();
        }
    }

    private void updateFloatingTexts() {
        Iterator<FloatingText> it = floatingTexts.iterator();
        while (it.hasNext()) {
            FloatingText ft = it.next();
            ft.update();
            if (ft.life <= 0)
                it.remove();
        }
    }

    public void spawnFloatingText(String text, double x, double y, Color color) {
        floatingTexts.add(new FloatingText(text, x, y, color));
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
            t.update(now, this); // Economic towers
            if (!t.canFire(now))
                continue;

            Balloon tgt = t.findTarget(balloons);
            if (tgt != null) {
                Projectile p = t.fire(tgt, now);
                if (p != null) {
                    projectiles.add(p);
                    // Add any extra shots (Triple shot, clusters, etc.)
                    if (!p.spawnedShots.isEmpty()) {
                        projectiles.addAll(p.spawnedShots);
                        p.spawnedShots.clear();
                    }

                    // Special case: BombTower clusters (Deprecate this in favor of internal
                    // spawnedShots?)
                    // I'll leave the old BombTower logic for now but the new way is cleaner.
                    if (t instanceof BombTower bt && bt.isCluster()) {
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

        List<Balloon> newBalloons = new ArrayList<>();
        Iterator<Balloon> bit = balloons.iterator();
        while (bit.hasNext()) {
            Balloon b = bit.next();
            if (b.dead) {
                int sl = b.getSplitLevel();
                if (sl > 0) {
                    for (int i = 0; i < b.getSplitCount(); i++) {
                        Balloon child = new Balloon(sl, waypoints, b.speedMultiplier);
                        child.x = b.x;
                        child.y = b.y;
                        child.waypointIndex = b.waypointIndex;
                        child.distanceTravelled = b.distanceTravelled - (i * 15); // staggering children slightly
                        newBalloons.add(child);
                    }
                }
                bit.remove();
            }
        }
        balloons.addAll(newBalloons);
    }

    public void reset(long now) {
        state.reset();
        logicalTime = 0;
        balloons.clear();
        towers.clear();
        projectiles.clear();
        selectedTower = null;
        waveMgr.reset();
    }
}
