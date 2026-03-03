import javax.swing.*;
import java.awt.*;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Balloon Tower Defense");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setResizable(true);

            GamePanel gamePanel = new GamePanel();
            frame.add(gamePanel);

            // Size to 90% of screen so it fits without a title bar overflow
            Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
            int w = (int) (screen.width * 0.90);
            int h = (int) (screen.height * 0.88);
            frame.setSize(w, h);
            frame.setLocationRelativeTo(null); // centre once at startup only
            frame.setVisible(true);
            gamePanel.requestFocusInWindow();
        });
    }
}
