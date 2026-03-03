import java.io.*;
import java.util.*;

/**
 * Simple file-based user database.
 * Stores one user per line in CSV format:
 * username|passwordHash|rankPoints|wins|losses
 */
public class UserDatabase {

    private static final String DB_FILE = "users.db";
    private static UserDatabase instance;

    // In-memory map: username -> UserRecord
    private final Map<String, UserRecord> users = new LinkedHashMap<>();

    public static class UserRecord {
        public String username;
        public int passwordHash;
        public int rankPoints;
        public int wins;
        public int losses;

        public UserRecord(String username, int passwordHash, int rankPoints, int wins, int losses) {
            this.username = username;
            this.passwordHash = passwordHash;
            this.rankPoints = rankPoints;
            this.wins = wins;
            this.losses = losses;
        }

        /** Display rank string like "Silver III" */
        public String getRankDisplay() {
            return RankSystem.getRankDisplay(rankPoints);
        }

        public String getRankTier() {
            return RankSystem.getTier(rankPoints);
        }

        public int getRankSubrank() {
            return RankSystem.getSubrank(rankPoints);
        }
    }

    private UserDatabase() {
        load();
    }

    public static UserDatabase getInstance() {
        if (instance == null) {
            instance = new UserDatabase();
        }
        return instance;
    }

    // ── Persistence ──────────────────────────────────────────────────────────

    private void load() {
        File f = new File(DB_FILE);
        if (!f.exists())
            return;
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty())
                    continue;
                String[] parts = line.split("\\|");
                if (parts.length < 5)
                    continue;
                try {
                    String u = parts[0];
                    int ph = Integer.parseInt(parts[1]);
                    int rp = Integer.parseInt(parts[2]);
                    int w = Integer.parseInt(parts[3]);
                    int l = Integer.parseInt(parts[4]);
                    users.put(u.toLowerCase(), new UserRecord(u, ph, rp, w, l));
                } catch (NumberFormatException ignored) {
                }
            }
        } catch (IOException e) {
            System.err.println("UserDatabase: could not load db: " + e.getMessage());
        }
    }

    private void save() {
        try (PrintWriter pw = new PrintWriter(new FileWriter(DB_FILE))) {
            for (UserRecord r : users.values()) {
                pw.println(r.username + "|" + r.passwordHash + "|" + r.rankPoints + "|" + r.wins + "|" + r.losses);
            }
        } catch (IOException e) {
            System.err.println("UserDatabase: could not save db: " + e.getMessage());
        }
    }

    // ── Auth ─────────────────────────────────────────────────────────────────

    /** Returns null if registration succeeds, or an error message. */
    public synchronized String register(String username, String password) {
        if (username == null || username.trim().isEmpty())
            return "Username cannot be empty.";
        if (username.length() < 3 || username.length() > 20)
            return "Username must be 3-20 characters.";
        if (!username.matches("[A-Za-z0-9_]+"))
            return "Username may only contain letters, numbers and underscores.";
        if (password == null || password.length() < 4)
            return "Password must be at least 4 characters.";
        if (users.containsKey(username.toLowerCase()))
            return "Username already taken.";

        UserRecord r = new UserRecord(username, password.hashCode(),
                RankSystem.STARTING_POINTS, 0, 0);
        users.put(username.toLowerCase(), r);
        save();
        return null; // success
    }

    /**
     * Returns the UserRecord on success, or null on failure.
     * Sets outError[0] to the error message when returning null.
     */
    public synchronized UserRecord login(String username, String password, String[] outError) {
        if (username == null || username.trim().isEmpty()) {
            outError[0] = "Please enter a username.";
            return null;
        }
        UserRecord r = users.get(username.toLowerCase());
        if (r == null) {
            outError[0] = "User not found.";
            return null;
        }
        if (r.passwordHash != password.hashCode()) {
            outError[0] = "Incorrect password.";
            return null;
        }
        return r;
    }

    // ── Rank Updates ─────────────────────────────────────────────────────────

    public synchronized void recordWin(String username, int opponentPoints) {
        UserRecord r = users.get(username.toLowerCase());
        if (r == null)
            return;
        int delta = RankSystem.pointsForWin(r.rankPoints, opponentPoints);
        r.rankPoints = Math.max(0, r.rankPoints + delta);
        r.wins++;
        enforceLeaderboardCap();
        save();
    }

    public synchronized void recordLoss(String username, int opponentPoints) {
        UserRecord r = users.get(username.toLowerCase());
        if (r == null)
            return;
        int delta = RankSystem.pointsForLoss(r.rankPoints, opponentPoints);
        r.rankPoints = Math.max(0, r.rankPoints + delta);
        r.losses++;
        enforceLeaderboardCap();
        save();
    }

    /** Only allow top 10 users to be in Legend tier. Push excess back to Ruby V. */
    private void enforceLeaderboardCap() {
        List<UserRecord> legends = new ArrayList<>();
        for (UserRecord r : users.values()) {
            if (RankSystem.getTier(r.rankPoints).equals("Legend")) {
                legends.add(r);
            }
        }
        if (legends.size() <= 10)
            return;
        legends.sort(Comparator.comparingInt((UserRecord r) -> r.rankPoints).reversed());
        for (int i = 10; i < legends.size(); i++) {
            legends.get(i).rankPoints = RankSystem.LEGEND_THRESHOLD - 1;
        }
    }

    // ── Queries ──────────────────────────────────────────────────────────────

    public synchronized UserRecord getUser(String username) {
        return users.get(username.toLowerCase());
    }

    /** Returns top N players sorted by rank points descending. */
    public synchronized List<UserRecord> getLeaderboard(int n) {
        List<UserRecord> all = new ArrayList<>(users.values());
        all.sort(Comparator.comparingInt((UserRecord r) -> r.rankPoints).reversed());
        return all.subList(0, Math.min(n, all.size()));
    }

    public synchronized int getUserCount() {
        return users.size();
    }
}
