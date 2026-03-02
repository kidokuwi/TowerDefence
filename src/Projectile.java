import java.awt.*;
import java.util.List;
import java.util.ArrayList;

/**
 * A projectile that moves toward a target balloon.
 * Supports AoE for BombTower and cluster sub-projectiles.
 */
public class Projectile {

    public double x, y;
    private final Balloon target;
    private final double damage;
    private final double speed;
    private final Color color;
    private final boolean isAoe;
    private final double blastRadius;

    public boolean done;
    /** Extra AoE projectiles to add (cluster bombs). */
    public List<Projectile> spawnedShots = new ArrayList<>();

    public Projectile(double sx, double sy, Balloon target,
            double damage, double speed,
            Color color, boolean isAoe, double blastRadius) {
        this.x = sx;
        this.y = sy;
        this.target = target;
        this.damage = damage;
        this.speed = speed;
        this.color = color;
        this.isAoe = isAoe;
        this.blastRadius = blastRadius;
        this.done = false;
    }

    /**
     * Moves toward the target balloon.
     * 
     * @param balloons – full balloon list needed for AoE hits.
     * @param state    – GameState for awarding money.
     */
    public void update(List<Balloon> balloons, GameState state) {
        if (done)
            return;
        if (target.dead || target.reachedEnd) {
            done = true;
            return;
        }

        double dx = target.x - x;
        double dy = target.y - y;
        double dist = Math.sqrt(dx * dx + dy * dy);

        if (dist <= speed + 2) {
            // Hit the target
            hitAt(target.x, target.y, balloons, state);
        } else {
            double scale = speed / dist;
            x += dx * scale;
            y += dy * scale;
        }
    }

    private void hitAt(double hx, double hy, List<Balloon> balloons, GameState state) {
        done = true;
        if (isAoe) {
            // Damage all balloons in blast radius
            for (Balloon b : balloons) {
                if (b.dead || b.reachedEnd)
                    continue;
                double dx = b.x - hx;
                double dy = b.y - hy;
                if (dx * dx + dy * dy <= blastRadius * blastRadius) {
                    if (b.takeDamage(damage)) {
                        state.addCash(b.getReward());
                    }
                }
            }
        } else {
            if (target.takeDamage(damage)) {
                state.addCash(target.getReward());
            }
        }
    }

    public void draw(Graphics2D g) {
        if (done)
            return;
        int r = isAoe ? 6 : 4;
        g.setColor(color);
        g.fillOval((int) (x - r), (int) (y - r), r * 2, r * 2);
        if (isAoe) {
            g.setColor(new Color(255, 140, 0, 180));
            g.fillOval((int) (x - r + 1), (int) (y - r + 1), r, r);
        }
    }
}
