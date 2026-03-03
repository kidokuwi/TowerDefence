import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.net.URL;

/**
 * Utility class to load and process game assets.
 */
public class AssetLoader {

    public static BufferedImage loadSprite(String path) {
        // Try multiple locations to handle different launch contexts
        String[] locations = {
                "src/assets/images/" + path, // Relative to project root
                "assets/images/" + path, // Relative to src/
                "../assets/images/" + path // Just in case
        };

        BufferedImage rawValue = null;
        for (String loc : locations) {
            File f = new File(loc);
            if (f.exists()) {
                try {
                    rawValue = ImageIO.read(f);
                    break;
                } catch (IOException ignored) {
                }
            }
        }

        if (rawValue == null) {
            // Final attempt: Java Resource (Classpath)
            try {
                URL url = AssetLoader.class.getResource("/assets/images/" + path);
                if (url != null) {
                    rawValue = ImageIO.read(url);
                }
            } catch (IOException ignored) {
            }
        }

        if (rawValue == null) {
            System.err.println("AssetLoader: Could not load image: " + path);
            return null;
        }

        return makeTransparent(rawValue);
    }

    private static BufferedImage makeTransparent(BufferedImage img) {
        if (img == null)
            return null;
        int w = img.getWidth();
        int h = img.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = img.getRGB(x, y);
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = (argb) & 0xFF;

                // Thresholding for "white" background removal
                if (r > 240 && g > 240 && b > 240) {
                    out.setRGB(x, y, 0); // Transparent
                } else {
                    out.setRGB(x, y, argb);
                }
            }
        }
        return out;
    }
}
