import java.awt.*;
import java.util.List;
import java.util.ArrayList;

/**
 * A projectile that moves toward a target balloon.
 * Supports homing (tracking) until the target is hit or dead.
 */
public class Projectile {

    public double x, y;
    private Balloon target;
    private final double speed;
    private double vx, vy;
    private int pierce = 1;
    private final List<Integer> hitBalloonIds = new ArrayList<>();
    private final double damage;
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
        this.speed = speed;
        this.damage = damage;
        this.color = color;
        this.isAoe = isAoe;
        this.blastRadius = blastRadius;
        this.done = false;

        // Initial aim
        updateVelocityTowardTarget();
    }

    private void updateVelocityTowardTarget() {
        if (target == null || target.dead || target.reachedEnd) {
            target = null;
            return;
        }
        double dx = target.x - x;
        double dy = target.y - y;
        double dist = Math.sqrt(dx * dx + dy * dy);
        if (dist > 0) {
            this.vx = (dx / dist) * speed;
            this.vy = (dy / dist) * speed;
        }
    }

    public void setPierce(int p) {
        this.pierce = p;
    }

    /**
     * Moves toward target (if any) and checks for collisions.
     */
    public void update(List<Balloon> balloons, GameState state) {
        if (done)
            return;

        // Update tracking
        if (target != null) {
            updateVelocityTowardTarget();
        }

        // Move projectile
        x += vx;
        y += vy;

        // Out of bounds check
        if (x < -100 || x > 900 || y < -100 || y > 700) {
            done = true;
            return;
        }

        // Collision logic
        for (int i = 0; i < balloons.size(); i++) {
            Balloon b = balloons.get(i);
            if (b.dead || b.reachedEnd)
                continue;

            // Important: don't hit the same balloon twice with the same projectile!
            int id = System.identityHashCode(b);
            if (hitBalloonIds.contains(id))
                continue;

            double dx = b.x - x;
            double dy = b.y - y;
            double distSq = dx * dx + dy * dy;
            int r = (b.level == 10) ? 45
                    : (b.level == 9) ? 24 : (b.level == 8) ? 26 : (b.level == 7) ? 22 : (10 + b.level);

            if (distSq <= (r + 4) * (r + 4)) {
                hitBalloonIds.add(id);
                // If we hit our tracked target, stop tracking it
                if (b == target) {
                    target = null;
                }
                hitAt(b, balloons, state);
                if (done)
                    break;
            }
        }
    }

    private void hitAt(Balloon b, List<Balloon> balloons, GameState state) {
        if (isAoe) {
            done = true;
            // Explosion logic
            double hx = b.x;
            double hy = b.y;
            for (Balloon other : balloons) {
                if (other.dead || other.reachedEnd)
                    continue;
                double dx = other.x - hx;
                double dy = other.y - hy;
                if (dx * dx + dy * dy <= blastRadius * blastRadius) {
                    if (other.takeDamage(damage)) {
                        state.addCash(other.getReward());
                    }
                }
            }
        } else {
            // Impact logic
            if (b.takeDamage(damage)) {
                state.addCash(b.getReward());
            }
            pierce--;
            if (pierce <= 0) {
                done = true;
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
