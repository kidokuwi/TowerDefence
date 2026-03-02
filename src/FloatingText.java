import java.awt.*;

/**
 * Simple animated text that floats upward and fades out.
 */
public class FloatingText {
    public double x, y;
    public String text;
    public Color color;
    public int life = 60; // frames
    private final double speedY = -0.5;

    public FloatingText(String text, double x, double y, Color color) {
        this.text = text;
        this.x = x;
        this.y = y;
        this.color = color;
    }

    public void update() {
        y += speedY;
        life--;
    }

    public void draw(Graphics2D g) {
        int alpha = Math.min(255, life * 4 + 15);
        g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha));
        g.setFont(new Font("Segoe UI", Font.BOLD, 14));
        g.drawString(text, (int) x, (int) y);
    }
}
