import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
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
    public double vx, vy;
    private int pierce = 1;
    private final List<Integer> hitBalloonIds = new ArrayList<>();
    private final double damage;
    private final Color color;
    private final boolean isAoe;
    private final double blastRadius;
    private double projectileRadius = 4; // Default visual and collision radius

    public boolean done;
    /** Extra AoE projectiles to add (cluster bombs). */
    public List<Projectile> spawnedShots = new ArrayList<>();

    protected BufferedImage image;

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
        this.projectileRadius = isAoe ? 6 : 4;

        // Initial aim
        updateVelocityTowardTarget();
    }

    public void setSprite(String spriteName) {
        this.image = AssetLoader.loadSprite(spriteName);
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

    public int getPierce() {
        return pierce;
    }

    public void setProjectileRadius(double r) {
        this.projectileRadius = r;
    }

    /**
     * Moves toward target (if any) and checks for collisions.
     */
    public void update(List<Balloon> balloons, GameState state, boolean isMultiplayer) {
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

            if (distSq <= (r + projectileRadius) * (r + projectileRadius)) {
                hitBalloonIds.add(id);
                // If we hit our tracked target, stop tracking it
                if (b == target) {
                    target = null;
                }
                hitAt(b, balloons, state, isMultiplayer);
                if (done)
                    break;
            }
        }
    }

    private void hitAt(Balloon b, List<Balloon> balloons, GameState state, boolean isMultiplayer) {
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
                        state.addCash(other.getReward(isMultiplayer));
                    }
                }
            }
        } else {
            // Impact logic
            if (b.takeDamage(damage)) {
                state.addCash(b.getReward(isMultiplayer));
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

        double angle = Math.atan2(vy, vx);
        AffineTransform old = g.getTransform();
        g.translate(x, y);
        g.rotate(angle);

        if (image != null) {
            int drawSize = (int) (projectileRadius * 4);
            g.drawImage(image, -drawSize / 2, -drawSize / 2, drawSize, drawSize, null);
        } else {
            if (isAoe) {
                drawProceduralBomb(g);
            } else {
                drawProceduralDart(g);
            }
        }

        g.setTransform(old);
    }

    private void drawProceduralDart(Graphics2D g) {
        int r = (int) projectileRadius;
        // Wood shaft
        g.setColor(new Color(139, 69, 19));
        g.fillRect(-r * 2, -1, r * 3, 2);
        // Tip
        g.setColor(Color.LIGHT_GRAY);
        int[] tx = { r, r + 4, r };
        int[] ty = { -2, 0, 2 };
        g.fillPolygon(tx, ty, 3);
        // Fletching
        g.setColor(Color.RED);
        int[] fx = { -r * 2, -r * 2 - 3, -r * 2 };
        int[] fy1 = { -1, -3, -1 };
        int[] fy2 = { 1, 3, 1 };
        g.fillPolygon(fx, fy1, 3);
        g.fillPolygon(fx, fy2, 3);
    }

    private void drawProceduralBomb(Graphics2D g) {
        int r = (int) projectileRadius;
        // Bomb body
        g.setColor(Color.DARK_GRAY);
        g.fillOval(-r, -r, r * 2, r * 2);
        // Highlight
        g.setColor(Color.GRAY);
        g.fillOval(-r / 2, -r / 2, r / 2, r / 2);
        // Fuse spark
        g.setColor(Color.ORANGE);
        g.fillOval(-r - 2, -2, 3, 3);
    }
}
