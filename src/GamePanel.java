import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.net.*;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class GamePanel extends JPanel implements ActionListener, MouseListener, KeyListener {

    public enum GameMode {
        AUTH_LOGIN, AUTH_REGISTER, MENU,
        SOLO, VS, HOST, JOIN,
        LOBBY_HOST, LOBBY_JOIN, LOBBY_MATCHMAKING,
        PROFILE, LEADERBOARD
    }

    public static final int GAME_W = 800;
    public static final int GAME_H = 600;
    public static final int CELL = 40;
    public static final String BROKER_IP = "localhost";

    // ── Auth / Player ─────────────────────────────────────────────────────────
    private UserDatabase.UserRecord currentUser = null;
    private String opponentUsername = "Opponent";
    private int opponentRankPoints = RankSystem.STARTING_POINTS;

    // Text-input state for auth screens
    private final StringBuilder inputUser = new StringBuilder();
    private final StringBuilder inputPass = new StringBuilder();
    private int activeField = 0; // 0 = username, 1 = password
    private String authMessage = "";
    private boolean authIsError = false;

    // ── Game State ────────────────────────────────────────────────────────────
    private final List<GameSession> sessions = new ArrayList<>();
    private final List<Sidebar> sidebars = new ArrayList<>();
    private GameMode mode = GameMode.AUTH_LOGIN;
    private double renderScale = 1.0;
    private NetworkManager network = null;
    private boolean isMultiplayer = false;
    private int mySessionIndex = 0;
    private String gameCode = "";
    private boolean otherPlayerConnected = false;
    private boolean rankUpdated = false;

    private BufferedImage buffer;
    private final Timer timer;

    // ─────────────────────────────────────────────────────────────────────────
    public GamePanel() {
        setBackground(Color.BLACK);
        addMouseListener(this);
        addKeyListener(this);
        setFocusable(true);
        setPreferredSize(new Dimension(GAME_W, GAME_H));
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                calculateScale();
                repaint();
            }
        });
        timer = new Timer(16, this);
        timer.start();
    }

    // ── Mode helpers ──────────────────────────────────────────────────────────
    private boolean isPlayingMode() {
        return mode == GameMode.SOLO || mode == GameMode.VS || mode == GameMode.HOST || mode == GameMode.JOIN;
    }

    private boolean isLobbyMode() {
        return mode == GameMode.LOBBY_HOST || mode == GameMode.LOBBY_JOIN || mode == GameMode.LOBBY_MATCHMAKING;
    }

    private boolean isMenuMode() {
        return !isPlayingMode() && !isLobbyMode();
    }

    private void calculateScale() {
        if (!isPlayingMode()) {
            renderScale = 1.0;
            return;
        }
        int tW = (mode == GameMode.SOLO) ? GAME_W + Sidebar.WIDTH : (GAME_W + Sidebar.WIDTH) * 2;
        renderScale = Math.min((double) getWidth() / tW, (double) getHeight() / GAME_H);
    }

    // ── Init ──────────────────────────────────────────────────────────────────
    public void initGame(GameMode m) {
        initGame(m, null);
    }

    public void initGame(GameMode m, String autoJoinIp) {
        this.mode = m;
        rankUpdated = false;
        if (network != null && !isLobbyMode()) {
            network.close();
            network = null;
        }

        isMultiplayer = m == GameMode.HOST || m == GameMode.JOIN || m == GameMode.LOBBY_HOST || m == GameMode.LOBBY_JOIN
                || m == GameMode.LOBBY_MATCHMAKING;
        sessions.clear();
        sidebars.clear();
        otherPlayerConnected = false;
        opponentUsername = "Opponent";
        opponentRankPoints = RankSystem.STARTING_POINTS;

        long seed = new Random().nextLong();

        if (m == GameMode.SOLO) {
            renderScale = 1.0;
            GameSession s = new GameSession(GAME_W, GAME_H, CELL, false);
            s.waveMgr.setSeed(seed);
            sessions.add(s);
            Sidebar sb = new Sidebar();
            sb.setOffsetX(GAME_W);
            sidebars.add(sb);
        } else if (m == GameMode.VS) {
            renderScale = 0.65;
            for (int i = 0; i < 2; i++) {
                GameSession s = new GameSession(GAME_W, GAME_H, CELL, true);
                s.waveMgr.setDifficulty(1.5);
                s.waveMgr.setSeed(seed);
                sessions.add(s);
                Sidebar sb = new Sidebar();
                sb.setOffsetX(GAME_W);
                sidebars.add(sb);
            }
        } else if (m == GameMode.LOBBY_MATCHMAKING) {
            this.mode = GameMode.LOBBY_MATCHMAKING;
            gameCode = "";
            renderScale = 1.0;
        } else if (m == GameMode.HOST || m == GameMode.LOBBY_HOST) {
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
            renderScale = 1.0;
        } else if (m == GameMode.JOIN || m == GameMode.LOBBY_JOIN) {
            this.mode = GameMode.LOBBY_JOIN;
            mySessionIndex = 1;
            String ip = autoJoinIp;
            if (ip == null && network == null) {
                network = new NetworkManager();
                String input = JOptionPane.showInputDialog(this, "Enter Game Code or Host IP:", "ABCDEFG");
                if (input == null || input.isEmpty()) {
                    returnToMenu();
                    return;
                }
                ip = NetworkManager.decodeIp(input);
                gameCode = (input.length() == 7) ? input.toUpperCase() : NetworkManager.encodeIp(ip);
            }
            if (network == null)
                network = new NetworkManager();
            final String fip = ip;
            if (fip != null) {
                try {
                    network.connect(fip, 12345, this::handleNetworkMessage);
                    String myName = currentUser != null ? currentUser.username : "Player";
                    int myPts = currentUser != null ? currentUser.rankPoints : RankSystem.STARTING_POINTS;
                    network.send("JOIN");
                    network.send("NAME:" + myName + ":" + myPts);
                } catch (Exception ex) {
                    ex.printStackTrace();
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(this, "Failed to connect: " + ex.getMessage());
                        returnToMenu();
                    });
                    return;
                }
            }
            renderScale = 1.0;
        }
        calculateScale();
        repaint();
        ensureBuffer(Math.max(1, getWidth()), Math.max(1, getHeight()));
    }

    // ── Game Loop ─────────────────────────────────────────────────────────────
    @Override
    public void actionPerformed(ActionEvent e) {
        if (isPlayingMode() && !sessions.isEmpty()) {
            for (GameSession s : sessions) {
                if (isMultiplayer) {
                    s.logicalTime += 24;
                    s.tick(s.logicalTime);
                } else {
                    int it = s.state.turboMode ? 3 : 1;
                    for (int i = 0; i < it; i++) {
                        s.logicalTime += 16;
                        s.tick(s.logicalTime);
                    }
                }
            }
            if ((mode == GameMode.VS || isMultiplayer) && sessions.size() == 2) {
                GameSession s1 = sessions.get(0), s2 = sessions.get(1);
                if (s1.state.gameOver && !s2.state.gameOver) {
                    s2.state.victory = true;
                    if (isMultiplayer && network != null && mySessionIndex == 0)
                        network.send("LOST");
                }
                if (s2.state.gameOver && !s1.state.gameOver) {
                    s1.state.victory = true;
                    if (isMultiplayer && network != null && mySessionIndex == 1)
                        network.send("LOST");
                }
            }
            checkMatchEnd();
        }
        render();
        repaint();
    }

    private void checkMatchEnd() {
        if (rankUpdated || sessions.isEmpty() || currentUser == null)
            return;
        // Local VS is unranked — no rank changes
        if (mode == GameMode.VS)
            return;
        boolean ended = false, myWin = false;
        if (isMultiplayer && sessions.size() == 2) {
            GameSession mine = sessions.get(mySessionIndex), opp = sessions.get(1 - mySessionIndex);
            if (mine.state.gameOver || mine.state.victory || opp.state.gameOver || opp.state.victory) {
                ended = true;
                myWin = mine.state.victory;
            }
        } else if (mode == GameMode.SOLO) {
            GameSession s = sessions.get(0);
            if (s.state.gameOver || s.state.victory) {
                ended = true;
                myWin = s.state.victory;
            }
        }
        if (!ended)
            return;
        rankUpdated = true;
        if (myWin)
            UserDatabase.getInstance().recordWin(currentUser.username, opponentRankPoints);
        else
            UserDatabase.getInstance().recordLoss(currentUser.username, opponentRankPoints);
        currentUser = UserDatabase.getInstance().getUser(currentUser.username);
    }

    // ── Render Dispatch ───────────────────────────────────────────────────────
    private void render() {
        if (isMenuMode()) {
            ensureBuffer(GAME_W, GAME_H);
            Graphics2D g = buffer.createGraphics();
            aa(g);
            switch (mode) {
                case AUTH_LOGIN -> renderAuthLogin(g);
                case AUTH_REGISTER -> renderAuthRegister(g);
                case MENU -> renderMenu(g);
                case PROFILE -> renderProfile(g);
                case LEADERBOARD -> renderLeaderboard(g);
                default -> {
                }
            }
            g.dispose();
            return;
        }
        if (isLobbyMode()) {
            ensureBuffer(GAME_W, GAME_H);
            Graphics2D g = buffer.createGraphics();
            aa(g);
            renderLobby(g);
            g.dispose();
            return;
        }
        // Playing
        int tW = (mode == GameMode.SOLO) ? GAME_W + Sidebar.WIDTH : (GAME_W + Sidebar.WIDTH) * 2;
        int vW = (int) (tW * renderScale), vH = (int) (GAME_H * renderScale);
        ensureBuffer(Math.max(1, vW), Math.max(1, vH));
        Graphics2D bg = buffer.createGraphics();
        aa(bg);
        bg.setColor(new Color(40, 45, 35));
        bg.fillRect(0, 0, vW, vH);
        for (int i = 0; i < sessions.size(); i++) {
            Graphics2D g2 = (Graphics2D) bg.create();
            g2.scale(renderScale, renderScale);
            g2.translate(i * (GAME_W + Sidebar.WIDTH), 0);
            int si = (isMultiplayer && mySessionIndex == 1) ? 1 - i : i;
            GameSession s = sessions.get(si);
            Sidebar sb = sidebars.get(si);
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
            sb.draw(g2, s.state, s.selectedTower, s.waveMgr, s.logicalTime, isMultiplayer && si != mySessionIndex);
            if (s.state.gameOver)
                drawOverlay(g2, "GAME OVER", "Final Wave: " + s.state.waveNumber, new Color(150, 0, 0, 180));
            else if (s.state.victory)
                drawOverlay(g2, "VICTORY!", "All Waves Clear", new Color(0, 150, 0, 180));
            boolean isMe = !isMultiplayer || si == mySessionIndex;
            String nm = isMe ? (currentUser != null ? currentUser.username : "YOU") : opponentUsername;
            int rp = isMe ? (currentUser != null ? currentUser.rankPoints : 0) : opponentRankPoints;
            drawBadgeInGame(g2, nm, RankSystem.getRankDisplay(rp), rp, 8, 8);
            g2.dispose();
        }
        bg.dispose();
    }

    private void aa(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    }

    private void ensureBuffer(int w, int h) {
        if (buffer == null || buffer.getWidth() != w || buffer.getHeight() != h)
            buffer = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
    }

    // ── Auth Login ────────────────────────────────────────────────────────────
    private void renderAuthLogin(Graphics2D g) {
        bgDark(g, GAME_W, GAME_H);
        drawTitle(g, "BALLOON TOWER DEFENSE", GAME_W / 2, 90, 38, new Color(100, 180, 255));
        sub(g, "Welcome back! Please log in.", GAME_W / 2, 135);
        int fy = GAME_H / 2 - 80, bx = GAME_W / 2 - 170, bw = 340;
        field(g, "Username", inputUser.toString(), activeField == 0, bx, fy, bw, 46);
        field(g, "Password", stars(inputPass), activeField == 1, bx, fy + 66, bw, 46);
        bigBtn(g, "LOGIN", new Color(50, 120, 220), bx, fy + 136, bw, 52);
        link(g, "Don't have an account?  Register", GAME_W / 2, fy + 220);
        authMsg(g, GAME_W / 2, fy + 250);
    }

    private void renderAuthRegister(Graphics2D g) {
        bgDark(g, GAME_W, GAME_H);
        drawTitle(g, "BALLOON TOWER DEFENSE", GAME_W / 2, 90, 38, new Color(100, 180, 255));
        sub(g, "Create a new account", GAME_W / 2, 135);
        int fy = GAME_H / 2 - 80, bx = GAME_W / 2 - 170, bw = 340;
        field(g, "Username (3-20 chars)", inputUser.toString(), activeField == 0, bx, fy, bw, 46);
        field(g, "Password (min 4 chars)", stars(inputPass), activeField == 1, bx, fy + 66, bw, 46);
        bigBtn(g, "REGISTER", new Color(40, 160, 80), bx, fy + 136, bw, 52);
        link(g, "Already have an account?  Login", GAME_W / 2, fy + 220);
        authMsg(g, GAME_W / 2, fy + 250);
    }

    // ── Main Menu ─────────────────────────────────────────────────────────────
    private void renderMenu(Graphics2D g) {
        bgDark(g, GAME_W, GAME_H);
        drawTitle(g, "TOWER DEFENSE", GAME_W / 2, 72, 46, new Color(255, 200, 60));
        if (currentUser != null)
            drawBadge(g, currentUser.username, currentUser.getRankDisplay(), currentUser.rankPoints, GAME_W - 185, 10);
        int bw = 260, bh = 52, bx = (GAME_W - bw) / 2, sp = 64, sy = GAME_H / 2 - 155;
        menuBtn(g, "SOLO MODE", new Color(50, 120, 220), bx, sy, bw, bh);
        menuBtn(g, "LOCAL VS", new Color(100, 60, 200), bx, sy + sp, bw, bh);
        menuBtn(g, "HOST GAME", new Color(30, 140, 90), bx, sy + 2 * sp, bw, bh);
        menuBtn(g, "JOIN GAME", new Color(180, 80, 20), bx, sy + 3 * sp, bw, bh);
        menuBtn(g, "FIND MATCH", new Color(140, 30, 140), bx, sy + 4 * sp, bw, bh);
        menuBtn(g, "LEADERBOARD", new Color(40, 100, 160), bx, sy + 5 * sp, bw, bh);
        menuBtn(g, "MY PROFILE", new Color(55, 60, 75), bx, sy + 6 * sp, bw, bh);
    }

    // ── Profile ───────────────────────────────────────────────────────────────
    private void renderProfile(Graphics2D g) {
        bgDark(g, GAME_W, GAME_H);
        if (currentUser == null) {
            mode = GameMode.MENU;
            return;
        }
        drawTitle(g, "PLAYER PROFILE", GAME_W / 2, 58, 32, new Color(100, 200, 255));
        int cx = GAME_W / 2;
        rankEmblem(g, currentUser.rankPoints, cx, 150, 46);
        bold(g, currentUser.username, cx, 220, 26, Color.WHITE);
        bold(g, currentUser.getRankDisplay(), cx, 248, 18, RankSystem.getRankColor(currentUser.rankPoints));
        if (!RankSystem.getTier(currentUser.rankPoints).equals("Legend")) {
            float p = RankSystem.tierProgress(currentUser.rankPoints);
            int bw = 240, bh = 10, bx = cx - bw / 2, by = 262;
            g.setColor(new Color(50, 55, 70));
            g.fillRoundRect(bx, by, bw, bh, 5, 5);
            g.setColor(RankSystem.getRankColor(currentUser.rankPoints));
            g.fillRoundRect(bx, by, (int) (bw * p), bh, 5, 5);
            small(g, currentUser.rankPoints + " pts", cx, 282);
        }
        int cy = 300;
        statCard(g, "WINS", String.valueOf(currentUser.wins), new Color(40, 160, 80), cx - 175, cy, 150, 80);
        statCard(g, "LOSSES", String.valueOf(currentUser.losses), new Color(160, 40, 40), cx + 25, cy, 150, 80);
        int wl = currentUser.wins + currentUser.losses;
        String wr = wl == 0 ? "N/A" : String.format("%.0f%%", 100.0 * currentUser.wins / wl);
        statCard(g, "WIN RATE", wr, new Color(50, 100, 200), cx - 65, cy + 100, 130, 80);
        menuBtn(g, "BACK", new Color(55, 60, 75), cx - 100, GAME_H - 75, 200, 48);
    }

    // ── Leaderboard ───────────────────────────────────────────────────────────
    private void renderLeaderboard(Graphics2D g) {
        bgDark(g, GAME_W, GAME_H);
        drawTitle(g, "LEADERBOARD", GAME_W / 2, 52, 34, new Color(255, 200, 60));
        List<UserDatabase.UserRecord> top = UserDatabase.getInstance().getLeaderboard(14);
        int sy = 88, rh = 32, c1 = 44, c2 = 90, c3 = 310, c4 = 540, c5 = 640;
        g.setFont(new Font("Segoe UI", Font.BOLD, 12));
        g.setColor(new Color(130, 140, 170));
        g.drawString("#", c1, sy);
        g.drawString("Player", c2, sy);
        g.drawString("Rank", c3, sy);
        g.drawString("W", c4, sy);
        g.drawString("L", c5, sy);
        g.setColor(new Color(60, 65, 85));
        g.drawLine(36, sy + 5, GAME_W - 36, sy + 5);
        for (int i = 0; i < top.size(); i++) {
            UserDatabase.UserRecord r = top.get(i);
            int ry = sy + 25 + i * rh;
            if (currentUser != null && r.username.equalsIgnoreCase(currentUser.username)) {
                g.setColor(new Color(50, 80, 140, 70));
                g.fillRoundRect(32, ry - 18, GAME_W - 64, rh - 2, 6, 6);
            }
            Color pc = i == 0 ? new Color(255, 215, 0)
                    : i == 1 ? new Color(192, 192, 192) : i == 2 ? new Color(205, 127, 50) : new Color(160, 170, 190);
            g.setFont(new Font("Segoe UI", Font.BOLD, 13));
            g.setColor(pc);
            g.drawString((i + 1) + ".", c1, ry);
            g.setColor(Color.WHITE);
            g.drawString(trunc(r.username, 16), c2, ry);
            g.setFont(new Font("Segoe UI", Font.BOLD, 12));
            g.setColor(RankSystem.getRankColor(r.rankPoints));
            g.drawString(r.getRankDisplay(), c3, ry);
            g.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            g.setColor(new Color(80, 200, 100));
            g.drawString("" + r.wins, c4, ry);
            g.setColor(new Color(200, 80, 80));
            g.drawString("" + r.losses, c5, ry);
        }
        if (top.isEmpty()) {
            g.setFont(new Font("Segoe UI", Font.ITALIC, 16));
            g.setColor(new Color(120, 130, 155));
            centered(g, "No players yet.", GAME_W / 2, GAME_H / 2);
        }
        menuBtn(g, "BACK", new Color(55, 60, 75), GAME_W / 2 - 100, GAME_H - 68, 200, 46);
    }

    // ── Lobby ─────────────────────────────────────────────────────────────────
    private void renderLobby(Graphics2D g) {
        bgDark(g, GAME_W, GAME_H);
        String title = mode == GameMode.LOBBY_HOST ? "HOSTING GAME"
                : mode == GameMode.LOBBY_JOIN ? "JOINING GAME" : "FINDING MATCH";
        drawTitle(g, title, GAME_W / 2, 80, 28, Color.WHITE);
        if (!gameCode.isEmpty()) {
            g.setFont(new Font("Segoe UI", Font.PLAIN, 20));
            g.setColor(new Color(200, 210, 230));
            centered(g, "Code: " + gameCode, GAME_W / 2, 135);
        }
        // Your card
        if (currentUser != null)
            drawBadge(g, currentUser.username, currentUser.getRankDisplay(), currentUser.rankPoints, GAME_W / 2 - 185,
                    175);
        // VS divider
        if (otherPlayerConnected) {
            g.setFont(new Font("Segoe UI", Font.BOLD, 22));
            g.setColor(new Color(255, 200, 60));
            centered(g, "VS", GAME_W / 2, 224);
        }
        // Opponent card
        if (otherPlayerConnected)
            drawBadge(g, opponentUsername, RankSystem.getRankDisplay(opponentRankPoints), opponentRankPoints,
                    GAME_W / 2 + 18, 175);
        String status = mode == GameMode.LOBBY_MATCHMAKING ? "SEARCHING FOR OPPONENT..."
                : otherPlayerConnected ? "OPPONENT FOUND!" : "WAITING FOR PLAYER...";
        Color sc = mode == GameMode.LOBBY_MATCHMAKING ? new Color(100, 200, 255)
                : otherPlayerConnected ? new Color(100, 255, 100) : new Color(255, 200, 100);
        g.setFont(new Font("Segoe UI", Font.BOLD, 17));
        g.setColor(sc);
        centered(g, status, GAME_W / 2, 305);
        if (mode == GameMode.LOBBY_HOST && otherPlayerConnected)
            bigBtn(g, "START GAME", new Color(40, 160, 80), GAME_W / 2 - 120, 350, 240, 52);
        bigBtn(g, "BACK TO MENU", new Color(80, 40, 40), GAME_W / 2 - 100, 430, 200, 46);
    }

    // ── paintComponent ────────────────────────────────────────────────────────
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (buffer == null)
            return;
        int pw = getWidth(), ph = getHeight();
        if (isMenuMode() || isLobbyMode()) {
            double s = Math.min((double) pw / GAME_W, (double) ph / GAME_H);
            int dw = (int) (GAME_W * s), dh = (int) (GAME_H * s);
            g.drawImage(buffer, (pw - dw) / 2, (ph - dh) / 2, dw, dh, null);
        } else {
            int bw = buffer.getWidth(), bh = buffer.getHeight();
            g.drawImage(buffer, (pw - bw) / 2, (ph - bh) / 2, null);
        }
    }

    // ── Mouse ─────────────────────────────────────────────────────────────────
    @Override
    public void mouseClicked(MouseEvent e) {
        requestFocusInWindow();
        int rx = e.getX(), ry = e.getY(), mx, my;
        if (isMenuMode() || isLobbyMode()) {
            double s = Math.min((double) getWidth() / GAME_W, (double) getHeight() / GAME_H);
            int ox = (int) ((getWidth() - GAME_W * s) / 2), oy = (int) ((getHeight() - GAME_H * s) / 2);
            mx = (int) ((rx - ox) / s);
            my = (int) ((ry - oy) / s);
        } else {
            int tW = (mode == GameMode.SOLO) ? GAME_W + Sidebar.WIDTH : (GAME_W + Sidebar.WIDTH) * 2;
            int vW = (int) (tW * renderScale), vH = (int) (GAME_H * renderScale);
            mx = (int) ((rx - (getWidth() - vW) / 2) / renderScale);
            my = (int) ((ry - (getHeight() - vH) / 2) / renderScale);
        }
        switch (mode) {
            case AUTH_LOGIN -> clickAuthLogin(mx, my);
            case AUTH_REGISTER -> clickAuthRegister(mx, my);
            case MENU -> clickMenu(mx, my);
            case PROFILE -> {
                if (inBtn(mx, my, GAME_W / 2 - 100, GAME_H - 75, 200, 48))
                    mode = GameMode.MENU;
            }
            case LEADERBOARD -> {
                if (inBtn(mx, my, GAME_W / 2 - 100, GAME_H - 68, 200, 46))
                    mode = GameMode.MENU;
            }
            case LOBBY_HOST, LOBBY_JOIN, LOBBY_MATCHMAKING -> clickLobby(mx, my);
            default -> clickGame(mx, my);
        }
    }

    private boolean inBtn(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private int formY() {
        return GAME_H / 2 - 80;
    }

    private void clickAuthLogin(int mx, int my) {
        int fy = formY(), bx = GAME_W / 2 - 170, bw = 340;
        if (inBtn(mx, my, bx, fy, bw, 46)) {
            activeField = 0;
            return;
        }
        if (inBtn(mx, my, bx, fy + 66, bw, 46)) {
            activeField = 1;
            return;
        }
        if (inBtn(mx, my, bx, fy + 136, bw, 52)) {
            doLogin();
            return;
        }
        if (my >= fy + 205 && my <= fy + 235) {
            switchToRegister();
        }
    }

    private void clickAuthRegister(int mx, int my) {
        int fy = formY(), bx = GAME_W / 2 - 170, bw = 340;
        if (inBtn(mx, my, bx, fy, bw, 46)) {
            activeField = 0;
            return;
        }
        if (inBtn(mx, my, bx, fy + 66, bw, 46)) {
            activeField = 1;
            return;
        }
        if (inBtn(mx, my, bx, fy + 136, bw, 52)) {
            doRegister();
            return;
        }
        if (my >= fy + 205 && my <= fy + 235) {
            switchToLogin();
        }
    }

    private void clickMenu(int mx, int my) {
        int bw = 260, bh = 52, bx = (GAME_W - bw) / 2, sp = 64, sy = GAME_H / 2 - 155;
        if (!inBtn(mx, my, bx, sy, bw, (bh + sp) * 7))
            return;
        if (inBtn(mx, my, bx, sy, bw, bh)) {
            initGame(GameMode.SOLO);
            return;
        }
        if (inBtn(mx, my, bx, sy + sp, bw, bh)) {
            initGame(GameMode.VS);
            return;
        }
        if (inBtn(mx, my, bx, sy + 2 * sp, bw, bh)) {
            initGame(GameMode.HOST);
            return;
        }
        if (inBtn(mx, my, bx, sy + 3 * sp, bw, bh)) {
            initGame(GameMode.JOIN);
            return;
        }
        if (inBtn(mx, my, bx, sy + 4 * sp, bw, bh)) {
            startMatchmaking();
            return;
        }
        if (inBtn(mx, my, bx, sy + 5 * sp, bw, bh)) {
            mode = GameMode.LEADERBOARD;
            return;
        }
        if (inBtn(mx, my, bx, sy + 6 * sp, bw, bh)) {
            mode = GameMode.PROFILE;
        }
    }

    private void clickLobby(int mx, int my) {
        if (mode == GameMode.LOBBY_HOST && otherPlayerConnected && inBtn(mx, my, GAME_W / 2 - 120, 350, 240, 52)) {
            startMatch();
            return;
        }
        if (inBtn(mx, my, GAME_W / 2 - 100, 430, 200, 46))
            returnToMenu();
    }

    private void clickGame(int mx, int my) {
        boolean anyOver = sessions.stream().anyMatch(s -> s.state.gameOver || s.state.victory);
        if (anyOver) {
            int bx = GAME_W / 2 - 80, by = GAME_H / 2 + 40, bw = 160, bh = 42;
            int fW = GAME_W + Sidebar.WIDTH, lx = mx % fW;
            if (inBtn(lx, my, bx, by, bw, bh))
                returnToMenu();
            return;
        }
        int fW = GAME_W + Sidebar.WIDTH, vi = mx / fW;
        if (vi >= sessions.size())
            return;
        int si = (isMultiplayer && mySessionIndex == 1) ? 1 - vi : vi;
        if (isMultiplayer && si != mySessionIndex)
            return;
        GameSession s = sessions.get(si);
        Sidebar sb = sidebars.get(si);
        int lx = mx % fW;
        if (lx >= GAME_W) {
            String a = sb.handleClick(lx, my, s.state, s.selectedTower, s.waveMgr, true);
            if (a != null)
                doSidebar(s, a);
            return;
        }
        int gx = lx / CELL, gy = my / CELL;
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

    // ── Auth Logic ────────────────────────────────────────────────────────────
    private void doLogin() {
        String[] err = new String[1];
        UserDatabase.UserRecord r = UserDatabase.getInstance().login(inputUser.toString(), inputPass.toString(), err);
        if (r == null) {
            setAuthMsg(err[0], true);
            return;
        }
        currentUser = r;
        inputUser.setLength(0);
        inputPass.setLength(0);
        authMessage = "";
        mode = GameMode.MENU;
    }

    private void doRegister() {
        String err = UserDatabase.getInstance().register(inputUser.toString(), inputPass.toString());
        if (err != null) {
            setAuthMsg(err, true);
            return;
        }
        setAuthMsg("Account created! You can now log in.", false);
        inputUser.setLength(0);
        inputPass.setLength(0);
        mode = GameMode.AUTH_LOGIN;
        activeField = 0;
    }

    private void switchToRegister() {
        inputUser.setLength(0);
        inputPass.setLength(0);
        authMessage = "";
        mode = GameMode.AUTH_REGISTER;
        activeField = 0;
    }

    private void switchToLogin() {
        inputUser.setLength(0);
        inputPass.setLength(0);
        authMessage = "";
        mode = GameMode.AUTH_LOGIN;
        activeField = 0;
    }

    private void setAuthMsg(String m, boolean err) {
        authMessage = m;
        authIsError = err;
    }

    // ── KeyListener ───────────────────────────────────────────────────────────
    @Override
    public void keyTyped(KeyEvent e) {
        if (!isMenuMode())
            return;
        if (mode != GameMode.AUTH_LOGIN && mode != GameMode.AUTH_REGISTER)
            return;
        char c = e.getKeyChar();
        if (c == KeyEvent.VK_BACK_SPACE) {
            StringBuilder sb = activeField == 0 ? inputUser : inputPass;
            if (!sb.isEmpty())
                sb.deleteCharAt(sb.length() - 1);
        } else if (c == '\t') {
            activeField = 1 - activeField;
        } else if (c == '\n') {
            if (mode == GameMode.AUTH_LOGIN)
                doLogin();
            else
                doRegister();
        } else if (c >= 32 && c < 127) {
            if (activeField == 0 && inputUser.length() < 20)
                inputUser.append(c);
            else if (activeField == 1 && inputPass.length() < 64)
                inputPass.append(c);
        }
    }

    @Override
    public void keyPressed(KeyEvent e) {
    }

    @Override
    public void keyReleased(KeyEvent e) {
    }

    // ── Sidebar Actions ───────────────────────────────────────────────────────
    private void doSidebar(GameSession s, String action) {
        switch (action) {
            case "dart" -> {
                s.state.selectedTowerType = "dart";
                s.selectedTower = null;
            }
            case "sniper" -> {
                s.state.selectedTowerType = "sniper";
                s.selectedTower = null;
            }
            case "home" -> returnToMenu();
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
            case "sell" -> {
                if (s.selectedTower != null) {
                    int idx = s.towers.indexOf(s.selectedTower);
                    s.state.addCash(s.selectedTower.getSellPrice());
                    s.towers.remove(s.selectedTower);
                    s.selectedTower = null;
                    if (network != null)
                        network.send("SELL:" + idx);
                }
            }
            case "upgA" -> {
                if (s.selectedTower != null) {
                    int idx = s.towers.indexOf(s.selectedTower);
                    if (s.selectedTower.upgrade(0, s.state) && network != null)
                        network.send("UPG:" + idx + ":0");
                }
            }
            case "upgB" -> {
                if (s.selectedTower != null) {
                    int idx = s.towers.indexOf(s.selectedTower);
                    if (s.selectedTower.upgrade(1, s.state) && network != null)
                        network.send("UPG:" + idx + ":1");
                }
            }
            default -> {
                if (action.startsWith("send_")) {
                    int lvl = Integer.parseInt(action.substring(5));
                    int cost = Balloon.getSendCost(lvl);
                    int eco = Balloon.getSendEcoChange(lvl);
                    if (s.state.canAfford(cost)) {
                        s.state.spend(cost);
                        s.state.income += eco;
                        if (network != null) {
                            network.send("SEND:" + lvl);
                        }
                        // Local sync: Spawn in the other session so the sender sees it too
                        if (sessions.size() == 2) {
                            int opponentIndex = 1 - sessions.indexOf(s);
                            sessions.get(opponentIndex).spawnSentBalloon(lvl);
                        }
                    }
                }
            }
        }
    }

    // ── Tower Placement ───────────────────────────────────────────────────────
    private void placeTower(GameSession s, int gx, int gy) {
        int sz = "farm".equals(s.state.selectedTowerType) ? 2 : 1;
        if (gx < 0 || gy < 0 || gx + sz > GAME_W / CELL || gy + sz > GAME_H / CELL)
            return;
        for (int dx = 0; dx < sz; dx++)
            for (int dy = 0; dy < sz; dy++) {
                int nx = gx + dx, ny = gy + dy;
                if (s.onPath[nx][ny])
                    return;
                for (Tower t : s.towers)
                    if (nx >= t.gridX && nx < t.gridX + t.getSize() && ny >= t.gridY && ny < t.gridY + t.getSize())
                        return;
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
            t.totalMoneySpent = t.getCost();
            s.towers.add(t);
            if (network != null && s == sessions.get(mySessionIndex))
                network.send("PLACE:" + s.state.selectedTowerType + ":" + gx + ":" + gy);
            s.state.selectedTowerType = null;
        }
    }

    // ── Network ───────────────────────────────────────────────────────────────
    private void handleNetworkMessage(String msg) {
        SwingUtilities.invokeLater(() -> {
            String[] p = msg.split(":");
            switch (p[0]) {
                case "JOIN" -> {
                    otherPlayerConnected = true;
                    if (network != null) {
                        String mn = currentUser != null ? currentUser.username : "Player";
                        int mp = currentUser != null ? currentUser.rankPoints : RankSystem.STARTING_POINTS;
                        network.send("JOIN_ACK");
                        network.send("NAME:" + mn + ":" + mp);
                    }
                }
                case "JOIN_ACK" -> otherPlayerConnected = true;
                case "NAME" -> {
                    if (p.length >= 3) {
                        opponentUsername = p[1];
                        try {
                            opponentRankPoints = Integer.parseInt(p[2]);
                        } catch (Exception ignored) {
                        }
                    }
                }
                case "START" -> {
                    initGameSession(Long.parseLong(p[1]));
                    this.mode = GameMode.JOIN;
                }
                case "JOINED" -> otherPlayerConnected = true;
                case "LOST" -> {
                    if (isMultiplayer && !sessions.isEmpty()) {
                        sessions.get(mySessionIndex).state.victory = true;
                    }
                }
                case "QUIT", "DISCONNECT" -> {
                    if (isMultiplayer && !sessions.isEmpty() && !sessions.get(mySessionIndex).state.gameOver
                            && !sessions.get(mySessionIndex).state.victory) {
                        sessions.get(mySessionIndex).state.victory = true;
                        sessions.get(mySessionIndex).state.gameOver = false;
                    } else if (isLobbyMode()) {
                        otherPlayerConnected = false;
                        if (p[0].equals("QUIT") && mode == GameMode.LOBBY_JOIN)
                            returnToMenu();
                    }
                }
                default -> {
                    int oi = 1 - mySessionIndex;
                    if (sessions.size() <= oi)
                        return;
                    GameSession other = sessions.get(oi);
                    try {
                        switch (p[0]) {
                            case "SEED" -> {
                                long s = Long.parseLong(p[1]);
                                sessions.forEach(gs -> gs.waveMgr.setSeed(s));
                            }
                            case "PLACE" -> {
                                other.state.selectedTowerType = p[1];
                                placeTower(other, Integer.parseInt(p[2]), Integer.parseInt(p[3]));
                            }
                            case "UPG" -> {
                                int ti = Integer.parseInt(p[1]);
                                if (ti >= 0 && ti < other.towers.size())
                                    other.towers.get(ti).upgrade(Integer.parseInt(p[2]), other.state);
                            }
                            case "EARLY" -> other.waveMgr.startNextWave(other.state);
                            case "SELL" -> {
                                int ti = Integer.parseInt(p[1]);
                                if (ti >= 0 && ti < other.towers.size()) {
                                    Tower t = other.towers.get(ti);
                                    other.state.addCash(t.getSellPrice());
                                    other.towers.remove(ti);
                                }
                            }
                            case "SEND" -> {
                                int lvl = Integer.parseInt(p[1]);
                                sessions.get(mySessionIndex).spawnSentBalloon(lvl);
                            }
                        }
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
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
                String resp = in.readLine();
                if (resp == null)
                    return;
                SwingUtilities.invokeLater(() -> {
                    if (resp.equals("ROLE:HOST"))
                        initGame(GameMode.HOST);
                    else if (resp.startsWith("ROLE:JOIN:"))
                        initGame(GameMode.JOIN, resp.substring(10));
                });
            } catch (Exception ex) {
                ex.printStackTrace();
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this, "Matchmaking broker not found at " + BROKER_IP + ":12346");
                    returnToMenu();
                });
            }
        }).start();
    }

    private void initGameSession(long seed) {
        sessions.clear();
        sidebars.clear();
        renderScale = 0.65;
        for (int i = 0; i < 2; i++) {
            GameSession s = new GameSession(GAME_W, GAME_H, CELL, true);
            s.waveMgr.setDifficulty(1.5);
            s.waveMgr.setSeed(seed);
            sessions.add(s);
            Sidebar sb = new Sidebar();
            sb.setOffsetX(GAME_W);
            sidebars.add(sb);
        }
        calculateScale();
        SwingUtilities.invokeLater(() -> {
            ensureBuffer(Math.max(1, getWidth()), Math.max(1, getHeight()));
            revalidate();
            repaint();
        });
    }

    private void returnToMenu() {
        if (network != null) {
            if (isMultiplayer)
                network.send("QUIT");
            network.close();
            network = null;
        }
        sessions.clear();
        sidebars.clear();
        mode = GameMode.MENU;
        otherPlayerConnected = false;
        repaint();
    }

    // ── Drawing helpers ───────────────────────────────────────────────────────
    private void bgDark(Graphics2D g, int w, int h) {
        g.setPaint(new GradientPaint(0, 0, new Color(12, 14, 26), 0, h, new Color(20, 26, 50)));
        g.fillRect(0, 0, w, h);
        g.setColor(new Color(50, 100, 220, 20));
        g.fillOval(-80, -80, 320, 320);
        g.fillOval(w - 220, h - 220, 380, 380);
    }

    private void drawTitle(Graphics2D g, String t, int cx, int cy, int sz, Color c) {
        g.setFont(new Font("Segoe UI", Font.BOLD, sz));
        FontMetrics fm = g.getFontMetrics();
        g.setColor(new Color(0, 0, 0, 100));
        g.drawString(t, cx - fm.stringWidth(t) / 2 + 3, cy + 3);
        g.setColor(c);
        g.drawString(t, cx - fm.stringWidth(t) / 2, cy);
    }

    private void sub(Graphics2D g, String t, int cx, int cy) {
        g.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        g.setColor(new Color(150, 165, 200));
        centered(g, t, cx, cy);
    }

    private void link(Graphics2D g, String t, int cx, int cy) {
        g.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        g.setColor(new Color(100, 160, 240));
        centered(g, t, cx, cy);
    }

    private void centered(Graphics2D g, String t, int cx, int cy) {
        FontMetrics fm = g.getFontMetrics();
        g.drawString(t, cx - fm.stringWidth(t) / 2, cy);
    }

    private void bold(Graphics2D g, String t, int cx, int cy, int sz, Color c) {
        g.setFont(new Font("Segoe UI", Font.BOLD, sz));
        g.setColor(c);
        centered(g, t, cx, cy);
    }

    private void small(Graphics2D g, String t, int cx, int cy) {
        g.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        g.setColor(new Color(150, 160, 185));
        centered(g, t, cx, cy);
    }

    private void authMsg(Graphics2D g, int cx, int cy) {
        if (authMessage.isEmpty())
            return;
        g.setFont(new Font("Segoe UI", Font.BOLD, 13));
        g.setColor(authIsError ? new Color(255, 100, 100) : new Color(100, 220, 100));
        centered(g, authMessage, cx, cy);
    }

    private void field(Graphics2D g, String lbl, String val, boolean focused, int x, int y, int w, int h) {
        g.setFont(new Font("Segoe UI", Font.BOLD, 11));
        g.setColor(new Color(130, 150, 200));
        g.drawString(lbl, x, y - 5);
        g.setColor(focused ? new Color(40, 60, 100) : new Color(30, 34, 55));
        g.fillRoundRect(x, y, w, h, 10, 10);
        g.setColor(focused ? new Color(80, 140, 255) : new Color(60, 70, 100));
        g.setStroke(new BasicStroke(focused ? 2f : 1f));
        g.drawRoundRect(x, y, w, h, 10, 10);
        g.setStroke(new BasicStroke(1f));
        g.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        g.setColor(Color.WHITE);
        String dv = val;
        FontMetrics fm = g.getFontMetrics();
        while (fm.stringWidth(dv) > w - 20 && !dv.isEmpty())
            dv = dv.substring(1);
        g.drawString(dv, x + 10, y + h / 2 + 6);
        if (focused && (System.currentTimeMillis() / 500) % 2 == 0) {
            int cx2 = x + 10 + fm.stringWidth(dv);
            g.setColor(Color.WHITE);
            g.drawLine(cx2, y + 7, cx2, y + h - 7);
        }
    }

    private String stars(StringBuilder sb) {
        return "●".repeat(sb.length());
    }

    private void bigBtn(Graphics2D g, String t, Color c, int x, int y, int w, int h) {
        g.setPaint(new GradientPaint(x, y, c.brighter(), x, y + h, c.darker()));
        g.fillRoundRect(x, y, w, h, 12, 12);
        g.setColor(Color.WHITE);
        g.setFont(new Font("Segoe UI", Font.BOLD, 17));
        FontMetrics fm = g.getFontMetrics();
        g.drawString(t, x + (w - fm.stringWidth(t)) / 2, y + h / 2 + 7);
    }

    private void menuBtn(Graphics2D g, String t, Color c, int x, int y, int w, int h) {
        g.setPaint(new GradientPaint(x, y, c.brighter(), x, y + h, c.darker()));
        g.fillRoundRect(x, y, w, h, 12, 12);
        g.setColor(new Color(255, 255, 255, 30));
        g.drawRoundRect(x, y, w, h, 12, 12);
        g.setColor(Color.WHITE);
        g.setFont(new Font("Segoe UI", Font.BOLD, 17));
        FontMetrics fm = g.getFontMetrics();
        g.drawString(t, x + (w - fm.stringWidth(t)) / 2, y + h / 2 + 7);
    }

    private void drawBadge(Graphics2D g, String name, String rank, int rp, int x, int y) {
        int bw = 160, bh = 56;
        g.setColor(new Color(22, 28, 50, 220));
        g.fillRoundRect(x, y, bw, bh, 10, 10);
        g.setColor(RankSystem.getRankColor(rp));
        g.setStroke(new BasicStroke(1.5f));
        g.drawRoundRect(x, y, bw, bh, 10, 10);
        g.setStroke(new BasicStroke(1f));
        rankEmblem(g, rp, x + 20, y + 28, 12);
        g.setFont(new Font("Segoe UI", Font.BOLD, 13));
        g.setColor(Color.WHITE);
        g.drawString(trunc(name, 12), x + 38, y + 22);
        g.setFont(new Font("Segoe UI", Font.BOLD, 11));
        g.setColor(RankSystem.getRankColor(rp));
        g.drawString(rank, x + 38, y + 42);
    }

    private void drawBadgeInGame(Graphics2D g, String name, String rank, int rp, int x, int y) {
        int bw = 155, bh = 50;
        g.setColor(new Color(15, 18, 35, 200));
        g.fillRoundRect(x, y, bw, bh, 8, 8);
        g.setColor(RankSystem.getRankColor(rp));
        g.setStroke(new BasicStroke(1.5f));
        g.drawRoundRect(x, y, bw, bh, 8, 8);
        g.setStroke(new BasicStroke(1f));
        rankEmblem(g, rp, x + 18, y + 25, 11);
        g.setFont(new Font("Segoe UI", Font.BOLD, 12));
        g.setColor(Color.WHITE);
        g.drawString(trunc(name, 12), x + 36, y + 20);
        g.setFont(new Font("Segoe UI", Font.BOLD, 10));
        g.setColor(RankSystem.getRankColor(rp));
        g.drawString(rank, x + 36, y + 38);
    }

    private void rankEmblem(Graphics2D g, int rp, int cx, int cy, int r) {
        Color c = RankSystem.getRankColor(rp);
        g.setColor(c.darker().darker());
        g.fillOval(cx - r, cy - r, r * 2, r * 2);
        g.setColor(c);
        g.setStroke(new BasicStroke(1.5f));
        g.drawOval(cx - r, cy - r, r * 2, r * 2);
        g.setStroke(new BasicStroke(1f));
        String ltr = RankSystem.getTier(rp).substring(0, 1);
        g.setFont(new Font("Segoe UI", Font.BOLD, Math.max(7, r)));
        g.setColor(c.brighter());
        FontMetrics fm = g.getFontMetrics();
        g.drawString(ltr, cx - fm.stringWidth(ltr) / 2, cy + fm.getAscent() / 2);
    }

    private void statCard(Graphics2D g, String lbl, String val, Color c, int x, int y, int w, int h) {
        g.setColor(new Color(28, 33, 52));
        g.fillRoundRect(x, y, w, h, 10, 10);
        g.setColor(c.darker());
        g.setStroke(new BasicStroke(1.5f));
        g.drawRoundRect(x, y, w, h, 10, 10);
        g.setStroke(new BasicStroke(1f));
        g.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        g.setColor(new Color(140, 155, 185));
        FontMetrics fm = g.getFontMetrics();
        g.drawString(lbl, x + (w - fm.stringWidth(lbl)) / 2, y + 22);
        g.setFont(new Font("Segoe UI", Font.BOLD, 24));
        g.setColor(Color.WHITE);
        fm = g.getFontMetrics();
        g.drawString(val, x + (w - fm.stringWidth(val)) / 2, y + h - 16);
    }

    private void drawGrid(Graphics2D g) {
        g.setColor(new Color(42, 50, 34));
        for (int x = 0; x < GAME_W; x += CELL)
            g.drawLine(x, 0, x, GAME_H);
        for (int y = 0; y < GAME_H; y += CELL)
            g.drawLine(0, y, GAME_W, y);
    }

    private void drawPath(Graphics2D g) {
        List<Point> wp = Path.getWaypoints();
        g.setStroke(new BasicStroke(36, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(165, 130, 80));
        for (int i = 1; i < wp.size(); i++) {
            Point a = wp.get(i - 1), b = wp.get(i);
            g.drawLine(a.x, a.y, b.x, b.y);
        }
        g.setStroke(new BasicStroke(1f));
    }

    private void drawOverlay(Graphics2D g, String t, String sub, Color bg2) {
        g.setColor(bg2);
        g.fillRoundRect(GAME_W / 2 - 160, GAME_H / 2 - 120, 320, 230, 20, 20);
        g.setFont(new Font("Segoe UI", Font.BOLD, 36));
        g.setColor(Color.WHITE);
        FontMetrics fm = g.getFontMetrics();
        g.drawString(t, GAME_W / 2 - fm.stringWidth(t) / 2, GAME_H / 2 - 60);
        g.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        fm = g.getFontMetrics();
        g.setColor(new Color(220, 220, 220));
        g.drawString(sub, GAME_W / 2 - fm.stringWidth(sub) / 2, GAME_H / 2 - 30);
        if (currentUser != null && rankUpdated) {
            String rs = currentUser.getRankDisplay() + " (" + currentUser.wins + "W/" + currentUser.losses + "L)";
            g.setFont(new Font("Segoe UI", Font.BOLD, 14));
            g.setColor(RankSystem.getRankColor(currentUser.rankPoints));
            fm = g.getFontMetrics();
            g.drawString(rs, GAME_W / 2 - fm.stringWidth(rs) / 2, GAME_H / 2 + 5);
        }
        menuBtn(g, "MAIN MENU", new Color(50, 55, 72), GAME_W / 2 - 80, GAME_H / 2 + 35, 160, 42);
    }

    private String trunc(String s, int n) {
        return s.length() > n ? s.substring(0, n) + "…" : s;
    }

    // ── Unused MouseListener stubs ────────────────────────────────────────────
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
