import java.util.Random;

/**
 * An AI opponent for BOT MATCH mode.
 */
public class AIBot {
    private final GameSession session;
    private final Random random;
    private long lastActionTime = 0;

    // A queue for balloons the bot decides to send to the human opponent
    public final java.util.Queue<Integer> pendingSends = new java.util.LinkedList<>();

    // Configurable delays (in logical time units)
    private static final long ACTION_COOLDOWN = 1500; // ms between actions
    private static final long EARLY_GAME_WAVE_MIN = 3;

    public AIBot(GameSession session) {
        this.session = session;
        this.random = new Random();
    }

    public void update(long now) {
        if (session.state.gameOver || session.state.victory)
            return;

        // Rate limiting bot actions so it doesn't instantly do everything perfectly
        if (now - lastActionTime < ACTION_COOLDOWN)
            return;

        boolean tookAction = false;

        // Strategy Priority:
        // 1. If we have lots of loose money, spend on Farms/Economy
        // 2. If we are struggling (balloons getting far), buy defenders
        // 3. Upgrade existing towers
        // 4. Send balloons to the human player

        if (session.state.cash > 1500) {
            tookAction = tryPlaceTower("farm");
        }

        if (!tookAction && needsMoreDefense()) {
            tookAction = tryPlaceTower(getRandomAttacker());
        }

        if (!tookAction && session.state.cash > 400 && random.nextDouble() < 0.4) {
            tookAction = tryUpgradeRandomTower();
        }

        if (!tookAction && session.state.waveNumber > EARLY_GAME_WAVE_MIN && session.state.cash > 200
                && random.nextDouble() < 0.3) {
            tookAction = trySendBalloon();
        }

        // Default: If we have cash but did nothing else, try putting down a cheap tower
        if (!tookAction && session.state.cash >= 200 && session.towers.size() < 10) {
            tookAction = tryPlaceTower("dart");
        }

        if (tookAction) {
            lastActionTime = now;
        }
    }

    private boolean needsMoreDefense() {
        if (session.towers.isEmpty() && session.state.cash >= 210)
            return true;

        // Simple heuristic: If there are many balloons on screen, we need more defense
        return session.balloons.size() > 5
                || (session.state.cash > 500 && session.towers.size() < (session.state.waveNumber * 2));
    }

    private String getRandomAttacker() {
        double r = random.nextDouble();
        if (r < 0.4)
            return "dart";
        if (r < 0.7)
            return "sniper";
        return "bomb";
    }

    private boolean tryPlaceTower(String type) {
        int cost = switch (type) {
            case "dart" -> 210;
            case "sniper" -> 350;
            case "bomb" -> 400;
            case "farm" -> 1200;
            default -> 99999;
        };

        if (!session.state.canAfford(cost))
            return false;

        // Find a valid spot
        int sz = "farm".equals(type) ? 2 : 1;
        int gridW = 800 / 40;
        int gridH = 600 / 40;

        // Try random spots (avoid checking whole grid every time for performance)
        for (int i = 0; i < 20; i++) {
            int gx = random.nextInt(gridW - sz);
            int gy = random.nextInt(gridH - sz);

            if (isValidPlacement(gx, gy, sz)) {
                Tower t = switch (type) {
                    case "dart" -> new DartTower(gx, gy, 40);
                    case "sniper" -> new SniperTower(gx, gy, 40);
                    case "bomb" -> new BombTower(gx, gy, 40);
                    case "farm" -> new BananaFarm(gx, gy, 40);
                    default -> null;
                };

                if (t != null) {
                    session.state.spend(cost);
                    t.totalMoneySpent = cost;
                    session.towers.add(t);
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isValidPlacement(int gx, int gy, int sz) {
        for (int dx = 0; dx < sz; dx++) {
            for (int dy = 0; dy < sz; dy++) {
                int nx = gx + dx, ny = gy + dy;
                if (nx < 0 || ny < 0 || nx >= session.onPath.length || ny >= session.onPath[0].length)
                    return false;
                if (session.onPath[nx][ny])
                    return false;

                for (Tower t : session.towers) {
                    if (nx >= t.gridX && nx < t.gridX + t.getSize() && ny >= t.gridY && ny < t.gridY + t.getSize()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private boolean tryUpgradeRandomTower() {
        if (session.towers.isEmpty())
            return false;

        Tower t = session.towers.get(random.nextInt(session.towers.size()));

        // Randomly choose branch 0 or 1
        int branch = random.nextInt(2);
        return t.upgrade(branch, session.state);
    }

    private boolean trySendBalloon() {
        // Levels 1 through 6
        int lvl = 1 + random.nextInt(Math.min(6, session.state.waveNumber));
        int cost = Balloon.getSendCost(lvl);

        if (session.state.canAfford((int) (cost * 1.5))) { // Leave some buffer cash
            int eco = Balloon.getSendEcoChange(lvl);
            session.state.spend(cost);
            session.state.income += eco;
            pendingSends.offer(lvl); // Queue the send for the GamePanel to process
            return true;
        }
        return false;
    }
}
