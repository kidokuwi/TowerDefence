import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.net.*;
import java.io.*;
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
        SOLO, VS, HOST, JOIN, LOBBY_HOST, LOBBY_JOIN, LOBBY_MATCHMAKING
    }

    public static final int GAME_W = 800;
    public static final int GAME_H = 600;
    public static final int CELL = 40;

    private List<GameSession> sessions = new ArrayList<>();
    private List<Sidebar> sidebars = new ArrayList<>();
    private GameMode mode = GameMode.SOLO;
    private boolean modeSelected = false;
    private double renderScale = 1.0;
    private NetworkManager network;
    private boolean isMultiplayer = false;
    private int mySessionIndex = 0;
    private String gameCode = ""; // For matchmaking
    private boolean otherPlayerConnected = false;
    public static final String BROKER_IP = "localhost"; // Change this to your server IP for global play

    private BufferedImage buffer;
    private Graphics2D bg;
    private Timer timer;

    public GamePanel() {
        setBackground(Color.BLACK);
        addMouseListener(this);
        setFocusable(true);
        setPreferredSize(new Dimension(800, 600));
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                calculateScale();
            }
        });
    }

    private void calculateScale() {
        if (!modeSelected)
            return;
        int targetW;
        if (mode == GameMode.SOLO) {
            targetW = GAME_W + Sidebar.WIDTH;
        } else {
            targetW = (GAME_W + Sidebar.WIDTH) * 2;
        }
        double scaleX = (double) getWidth() / targetW;
        double scaleY = (double) getHeight() / GAME_H;
        renderScale = Math.min(scaleX, scaleY);
    }

    public void initGame(GameMode m) {
        initGame(m, null);
    }

    public void initGame(GameMode m, String autoJoinIp) {
        this.mode = m;
        this.modeSelected = true;

        // Clean up old network state before starting new game
        if (network != null) {
            network.close();
            network = null;
        }

        this.isMultiplayer = (mode == GameMode.HOST || mode == GameMode.JOIN || mode == GameMode.LOBBY_HOST
                || mode == GameMode.LOBBY_JOIN || mode == GameMode.LOBBY_MATCHMAKING);

        // renderScale will be set within the blocks now
        // this.renderScale = (mode == GameMode.VS || mode == GameMode.HOST || mode ==
        // GameMode.JOIN) ? 0.85 : 1.65;

        sessions.clear();
        sidebars.clear();
        otherPlayerConnected = false; // Reset connection status

        long waveSeed = new Random().nextLong();
        int viewW = 800, viewH = 600; // Default values, will be updated

        if (mode == GameMode.SOLO) {
            this.renderScale = 1.65;
            GameSession s1 = new GameSession(GAME_W, GAME_H, CELL);
            s1.waveMgr.setSeed(waveSeed);
            sessions.add(s1);

            Sidebar sb1 = new Sidebar();
            sb1.setOffsetX(GAME_W);
            sidebars.add(sb1);

            viewW = (int) ((GAME_W + Sidebar.WIDTH) * renderScale);
            viewH = (int) (GAME_H * renderScale);
        } else if (mode == GameMode.VS) {
            this.renderScale = 0.65;
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
        } else if (mode == GameMode.LOBBY_MATCHMAKING) {
            this.mode = GameMode.LOBBY_MATCHMAKING;
            gameCode = "";
            viewW = 800;
            viewH = 600;
            renderScale = 1.0;
        } else if (mode == GameMode.HOST || mode == GameMode.LOBBY_HOST) {
            this.mode = GameMode.LOBBY_HOST;
            mySessionIndex = 0;
            if (network == null) {
                network = new NetworkManager();
                gameCode = network.getLocalIpCode();
                try {
                    network.host(12345, this::handleNetworkMessage);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
            viewW = 800;
            viewH = 600;
            renderScale = 1.0;
        } else if (mode == GameMode.JOIN || mode == GameMode.LOBBY_JOIN) {
            this.mode = GameMode.LOBBY_JOIN;
            mySessionIndex = 1;

            String ip = autoJoinIp;
            if (ip == null && network == null) {
                network = new NetworkManager();
                String input = JOptionPane.showInputDialog(this, "Enter Game Code or Host IP:", "ABCDEFG");
                if (input == null || input.isEmpty()) {
                    modeSelected = false;
                    return;
                }
                ip = NetworkManager.decodeIp(input);
                gameCode = (input.length() == 7) ? input.toUpperCase() : NetworkManager.encodeIp(ip);
            }

            if (network == null) {
                network = new NetworkManager();
                try {
                    network.connect(ip, 12345, this::handleNetworkMessage);
                    network.send("JOIN");
                } catch (Exception ex) {
                    ex.printStackTrace();
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(this, "Failed to connect to host: " + ex.getMessage());
                        modeSelected = false;
                        network = null;
                    });
                    return;
                }
            }
            viewW = 800;
            viewH = 600;
            renderScale = 1.0;
        }

        setPreferredSize(new Dimension(viewW, viewH));
        Window win = SwingUtilities.getWindowAncestor(this);
        if (win != null) {
            win.pack();
            win.setLocationRelativeTo(null);
        }

        calculateScale();
        repaint();

        buffer = new BufferedImage(viewW, viewH, BufferedImage.TYPE_INT_ARGB);
        bg = buffer.createGraphics();
        bg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

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
            // In multiplayer, turbo is disabled but base speed is 1.5x (16 * 1.5 = 24)
            if (isMultiplayer) {
                s.logicalTime += 24;
                s.tick(s.logicalTime);
            } else {
                int iterations = s.state.turboMode ? 3 : 1;
                for (int i = 0; i < iterations; i++) {
                    s.logicalTime += 16;
                    s.tick(s.logicalTime);
                }
            }
        }

        if ((mode == GameMode.VS || isMultiplayer) && sessions.size() == 2) {
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
        if (!modeSelected)
            return;

        int targetW = GAME_W + Sidebar.WIDTH; // Solo
        if (mode == GameMode.LOBBY_HOST || mode == GameMode.LOBBY_JOIN || mode == GameMode.LOBBY_MATCHMAKING) {
            targetW = GAME_W;
        } else if (mode == GameMode.VS || mode == GameMode.HOST || mode == GameMode.JOIN) {
            targetW = (GAME_W + Sidebar.WIDTH) * 2;
        }

        int viewW = (int) (targetW * renderScale);
        int viewH = (int) (GAME_H * renderScale);

        if (buffer == null || buffer.getWidth() != viewW || buffer.getHeight() != viewH) {
            buffer = new BufferedImage(Math.max(1, viewW), Math.max(1, viewH), BufferedImage.TYPE_INT_RGB);
        }

        bg = buffer.createGraphics();
        bg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        if (mode == GameMode.LOBBY_HOST || mode == GameMode.LOBBY_JOIN || mode == GameMode.LOBBY_MATCHMAKING) {
            bg.scale(renderScale, renderScale);
            renderLobby();
            bg.dispose();
            return;
        }

        bg.setColor(new Color(40, 45, 35));
        bg.fillRect(0, 0, viewW, viewH);

        for (int i = 0; i < sessions.size(); i++) {
            Graphics2D g2 = (Graphics2D) bg.create();
            g2.scale(renderScale, renderScale);
            g2.translate(i * (GAME_W + Sidebar.WIDTH), 0);

            // Map visual slot i back to session index
            int sessionIdx = (isMultiplayer && mySessionIndex == 1) ? 1 - i : i;
            GameSession s = sessions.get(sessionIdx);
            Sidebar sb = sidebars.get(sessionIdx);

            drawGrid(g2);
            drawPath(g2);

            for (Tower t : s.towers)
                t.draw(g2, t == s.selectedTower);
            for (Balloon b : s.balloons)
                b.draw(g2);
            for (Projectile p : s.projectiles)
                p.draw(g2);
            for (FloatingText ft : s.floatingTexts)
                ft.draw(g2);

            sb.draw(g2, s.state, s.selectedTower, s.waveMgr, s.logicalTime,
                    (isMultiplayer && sessionIdx != mySessionIndex));

            if (s.state.gameOver)
                drawOverlay(g2, "GAME OVER", "Final Wave: " + s.state.waveNumber, new Color(150, 0, 0, 180));
            else if (s.state.victory)
                drawOverlay(g2, "VICTORY!", "All Waves Defeated", new Color(0, 150, 0, 180));

            if (isMultiplayer) {
                g2.setFont(new Font("Segoe UI", Font.BOLD, 24));
                g2.setColor(Color.WHITE);
                String label = (sessionIdx == mySessionIndex) ? "YOU" : "OPPONENT";
                g2.drawString(label, 20, 40);
            }
            g2.dispose();
        }
        bg.dispose();
    }

    private void renderMenu() {
        int w = GAME_W, h = GAME_H; // Use fixed game dimensions for menu rendering

        // Ensure buffer is correctly sized for menu
        if (buffer == null || buffer.getWidth() != w || buffer.getHeight() != h) {
            buffer = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        }
        bg = buffer.createGraphics();
        bg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        bg.setColor(new Color(20, 22, 28));
        bg.fillRect(0, 0, w, h);

        bg.setColor(Color.WHITE);
        bg.setFont(new Font("Segoe UI", Font.BOLD, 52));
        FontMetrics fm = bg.getFontMetrics();
        String titleString = "TOWER DEFENSE";
        bg.drawString(titleString, (w - fm.stringWidth(titleString)) / 2, h / 2 - 220);

        int btnW = 240, btnH = 60;
        int bx = (w - btnW) / 2;
        int spacing = 80;
        int startY = h / 2 - 140;

        drawMenuButton(bg, "SOLO MODE", bx, startY, btnW, btnH);
        drawMenuButton(bg, "LOCAL VS", bx, startY + spacing, btnW, btnH);
        drawMenuButton(bg, "HOST GAME", bx, startY + 2 * spacing, btnW, btnH);
        drawMenuButton(bg, "JOIN GAME", bx, startY + 3 * spacing, btnW, btnH);
        drawMenuButton(bg, "FIND MATCH", bx, startY + 4 * spacing, btnW, btnH);
        bg.dispose();
    }

    private void drawMenuButton(Graphics2D g, String txt, int x, int y, int w, int h) {
        g.setColor(new Color(50, 110, 210));
        g.fillRoundRect(x, y, w, h, 12, 12);
        g.setColor(Color.WHITE);
        g.setFont(new Font("Segoe UI", Font.BOLD, 22));
        FontMetrics fm = g.getFontMetrics();
        g.drawString(txt, x + (w - fm.stringWidth(txt)) / 2, y + h / 2 + 8);
    }

    private void renderLobby() {
        int w = GAME_W, h = GAME_H;
        bg.setColor(new Color(20, 22, 28));
        bg.fillRect(0, 0, w, h);

        bg.setColor(Color.WHITE);
        bg.setFont(new Font("Segoe UI", Font.BOLD, 36));
        String title = (mode == GameMode.LOBBY_HOST) ? "HOSTING SESSION" : "JOINING SESSION";
        drawCenteredString(bg, title, w / 2, 100);

        if (!gameCode.isEmpty()) {
            bg.setFont(new Font("Segoe UI", Font.PLAIN, 24));
            bg.setColor(new Color(200, 210, 230));
            drawCenteredString(bg, "GAME CODE: " + gameCode, w / 2, 200);
        }

        String status = otherPlayerConnected ? "PLAYER JOINED!" : "WAITING FOR PLAYER...";
        if (mode == GameMode.LOBBY_MATCHMAKING) {
            status = "QUEUED: SEARCHING OPPONENT...";
            bg.setColor(new Color(100, 200, 255));
        } else {
            bg.setColor(otherPlayerConnected ? new Color(100, 255, 100) : new Color(255, 200, 100));
        }
        drawCenteredString(bg, status, w / 2, 300);

        if (mode == GameMode.LOBBY_HOST && otherPlayerConnected) {
            drawMenuButton(bg, "START GAME", (w - 200) / 2, 400, 200, 50);
        } else if (mode == GameMode.LOBBY_JOIN || mode == GameMode.LOBBY_MATCHMAKING) {
            bg.setColor(Color.LIGHT_GRAY);
            bg.setFont(new Font("Segoe UI", Font.ITALIC, 18));
            String msg = (mode == GameMode.LOBBY_MATCHMAKING) ? "Please wait for a match..."
                    : "Waiting for host to start...";
            drawCenteredString(bg, msg, w / 2, 400);
        }
    }

    private void drawCenteredString(Graphics2D g, String s, int x, int y) {
        FontMetrics fm = g.getFontMetrics();
        g.drawString(s, x - fm.stringWidth(s) / 2, y);
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
        if (buffer != null) {
            int targetW = GAME_W + Sidebar.WIDTH;
            if (mode == GameMode.LOBBY_HOST || mode == GameMode.LOBBY_JOIN || mode == GameMode.LOBBY_MATCHMAKING) {
                targetW = GAME_W;
            } else if (mode == GameMode.VS || mode == GameMode.HOST || mode == GameMode.JOIN) {
                targetW = (GAME_W + Sidebar.WIDTH) * 2;
            }

            int viewW = (int) (targetW * renderScale);
            int viewH = (int) (GAME_H * renderScale);
            int offsetX = (getWidth() - viewW) / 2;
            int offsetY = (getHeight() - viewH) / 2;
            g.drawImage(buffer, offsetX, offsetY, null);
        } else {
            renderMenu();
        }
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        int realX = e.getX(), realY = e.getY();

        // Calculate targetW/Scale exactly as in rendering
        int targetW = GAME_W + Sidebar.WIDTH;
        if (mode == GameMode.LOBBY_HOST || mode == GameMode.LOBBY_JOIN || mode == GameMode.LOBBY_MATCHMAKING) {
            targetW = GAME_W;
        } else if (mode == GameMode.VS || mode == GameMode.HOST || mode == GameMode.JOIN) {
            targetW = (GAME_W + Sidebar.WIDTH) * 2;
        }

        int viewW = (int) (targetW * renderScale);
        int viewH = (int) (GAME_H * renderScale);
        int offsetX = (getWidth() - viewW) / 2;
        int offsetY = (getHeight() - viewH) / 2;

        int mx = (int) ((realX - offsetX) / renderScale);
        int my = (int) ((realY - offsetY) / renderScale);

        if (!modeSelected) {
            int w = getWidth(), h = getHeight();
            int btnW = 240, btnH = 60;
            int bx = (w - btnW) / 2;
            int spacing = 80;
            int startY = h / 2 - 140;

            if (realX >= bx && realX <= bx + btnW) {
                if (realY >= startY && realY <= startY + btnH)
                    initGame(GameMode.SOLO);
                else if (realY >= startY + spacing && realY <= startY + spacing + btnH)
                    initGame(GameMode.VS);
                else if (realY >= startY + 2 * spacing && realY <= startY + 2 * spacing + btnH)
                    initGame(GameMode.HOST);
                else if (realY >= startY + 3 * spacing && realY <= startY + 3 * spacing + btnH)
                    initGame(GameMode.JOIN);
                else if (realY >= startY + 4 * spacing && realY <= startY + 4 * spacing + btnH)
                    startMatchmaking();
            }
            return;
        }

        if (mode == GameMode.LOBBY_HOST) {
            int btnW = 200, btnH = 50;
            int bx = (GAME_W - btnW) / 2;
            int by = 400;
            if (otherPlayerConnected && mx >= bx && mx <= bx + btnW && my >= by && my <= by + btnH) {
                startMatch();
            }
            return;
        }
        if (mode == GameMode.LOBBY_JOIN)
            return;

        boolean anyGameOver = false;
        for (GameSession s : sessions)
            if (s.state.gameOver || s.state.victory)
                anyGameOver = true;
        if (anyGameOver) {
            initGame(mode);
            return;
        }

        int fullSessionWidth = GAME_W + Sidebar.WIDTH;
        int visualIdx = mx / fullSessionWidth;
        if (visualIdx >= sessions.size())
            return;

        // Map visual index back to session index
        int sessionIdx = (isMultiplayer && mySessionIndex == 1) ? 1 - visualIdx : visualIdx;

        if (isMultiplayer && sessionIdx != mySessionIndex)
            return;

        GameSession s = sessions.get(sessionIdx);
        Sidebar sb = sidebars.get(sessionIdx);
        int localX = mx % fullSessionWidth;

        if (localX >= GAME_W) {
            String action = sb.handleClick(localX, my, s.state, s.selectedTower, s.waveMgr, isMultiplayer);
            if (action != null)
                handleSidebarAction(s, action);
            return;
        }

        int gx = localX / CELL, gy = my / CELL;
        for (Tower t : s.towers) {
            if (gx >= t.gridX && gx < t.gridX + t.getSize() && gy >= t.gridY && gy < t.gridY + t.getSize()) {
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
            case "early" -> {
                s.waveMgr.startNextWave(s.state);
                if (network != null)
                    network.send("EARLY");
            }
            case "turbo" -> s.state.turboMode = !s.state.turboMode;
            case "upgA" -> {
                if (s.selectedTower != null) {
                    int idx = s.towers.indexOf(s.selectedTower);
                    if (s.selectedTower.upgrade(0, s.state)) {
                        if (network != null)
                            network.send("UPG:" + idx + ":0");
                    }
                }
            }
            case "upgB" -> {
                if (s.selectedTower != null) {
                    int idx = s.towers.indexOf(s.selectedTower);
                    if (s.selectedTower.upgrade(1, s.state)) {
                        if (network != null)
                            network.send("UPG:" + idx + ":1");
                    }
                }
            }
        }
    }

    private void handleNetworkMessage(String msg) {
        SwingUtilities.invokeLater(() -> {
            String[] parts = msg.split(":");

            // Connection handshakes
            if (parts[0].equals("JOIN")) {
                otherPlayerConnected = true;
                if (network != null)
                    network.send("JOIN_ACK");
                return;
            }
            if (parts[0].equals("JOIN_ACK")) {
                otherPlayerConnected = true;
                return;
            }
            if (parts[0].equals("START")) {
                initGameSession(Long.parseLong(parts[1]));
                this.mode = GameMode.JOIN;
                return;
            }
            if (parts[0].equals("JOINED")) {
                otherPlayerConnected = true;
                return;
            }

            int otherIdx = 1 - mySessionIndex;
            if (sessions.size() <= otherIdx)
                return;
            GameSession other = sessions.get(otherIdx);
            try {
                if (parts[0].equals("SEED")) {
                    long seed = Long.parseLong(parts[1]);
                    for (GameSession s : sessions)
                        s.waveMgr.setSeed(seed);
                } else if (parts[0].equals("PLACE")) {
                    other.state.selectedTowerType = parts[1];
                    placeTower(other, Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
                } else if (parts[0].equals("UPG")) {
                    int tIdx = Integer.parseInt(parts[1]);
                    if (tIdx >= 0 && tIdx < other.towers.size())
                        other.towers.get(tIdx).upgrade(Integer.parseInt(parts[2]), other.state);
                } else if (parts[0].equals("EARLY")) {
                    other.waveMgr.startNextWave(other.state);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void startMatch() {
        long seed = new Random().nextLong();
        network.send("START:" + seed);
        initGameSession(seed);
        this.mode = GameMode.HOST;
    }

    private void startMatchmaking() {
        initGame(GameMode.LOBBY_MATCHMAKING);

        new Thread(() -> {
            try (Socket broker = new Socket(BROKER_IP, 12346);
                    BufferedReader in = new BufferedReader(new InputStreamReader(broker.getInputStream()))) {
                String response = in.readLine(); // "ROLE:HOST" or "ROLE:JOIN:IP"
                if (response == null)
                    return;

                SwingUtilities.invokeLater(() -> {
                    if (response.equals("ROLE:HOST")) {
                        initGame(GameMode.HOST);
                    } else if (response.startsWith("ROLE:JOIN:")) {
                        String ip = response.substring(10);
                        initGame(GameMode.JOIN, ip);
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this,
                            "Matchmaking Broker not found at " + BROKER_IP + ":" + 12346 + "\n" +
                                    "Please make sure you have run 'java MatchmakingBroker' in a terminal.",
                            "Connection Error", JOptionPane.ERROR_MESSAGE);
                    modeSelected = false;
                });
            }
        }).start();
    }

    private void initGameSession(long waveSeed) {
        sessions.clear();
        sidebars.clear();
        this.renderScale = 0.65;

        for (int i = 0; i < 2; i++) {
            GameSession s = new GameSession(GAME_W, GAME_H, CELL);
            s.waveMgr.setDifficulty(1.5);
            s.waveMgr.setSeed(waveSeed);
            sessions.add(s);
            Sidebar sb = new Sidebar();
            sb.setOffsetX(GAME_W);
            sidebars.add(sb);
        }

        int viewW = (int) ((GAME_W + Sidebar.WIDTH) * 2 * renderScale);
        int viewH = (int) (GAME_H * renderScale);
        setPreferredSize(new Dimension(viewW, viewH));

        SwingUtilities.invokeLater(() -> {
            Window win = SwingUtilities.getWindowAncestor(this);
            if (win != null) {
                if (win instanceof JFrame) {
                    ((JFrame) win).setResizable(true);
                }
                win.pack();
                win.setLocationRelativeTo(null);
                win.revalidate();
                win.repaint();
            }
            buffer = new BufferedImage(viewW, viewH, BufferedImage.TYPE_INT_ARGB);
            bg = buffer.createGraphics();
            bg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            revalidate();
            repaint();
        });
    }

    private void placeTower(GameSession s, int gx, int gy) {
        int size = ("farm".equals(s.state.selectedTowerType)) ? 2 : 1;
        if (gx < 0 || gy < 0 || gx + size > GAME_W / CELL || gy + size > GAME_H / CELL)
            return;
        for (int dx = 0; dx < size; dx++) {
            for (int dy = 0; dy < size; dy++) {
                int nx = gx + dx, ny = gy + dy;
                if (s.onPath[nx][ny])
                    return;
                for (Tower t : s.towers)
                    if (nx >= t.gridX && nx < t.gridX + t.getSize() && ny >= t.gridY && ny < t.gridY + t.getSize())
                        return;
            }
        }
        Tower t = switch (s.state.selectedTowerType) {
            case "dart" -> new DartTower(gx, gy, CELL);
            case "sniper" -> new SniperTower(gx, gy, CELL);
            case "bomb" -> new BombTower(gx, gy, CELL);
            case "farm" -> new BananaFarm(gx, gy, CELL);
            default -> null;
        };
        if (t != null && s.state.canAfford(t.getCost())) {
            s.state.spend(t.getCost());
            s.towers.add(t);
            if (network != null && s == sessions.get(mySessionIndex))
                network.send("PLACE:" + s.state.selectedTowerType + ":" + gx + ":" + gy);
            s.state.selectedTowerType = null;
        }
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
