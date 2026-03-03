/**
 * Rank system: Wood(5) -> Bronze(5) -> Silver(5) -> Gold(5)
 * -> Platinum(5) -> Diamond(5) -> Ruby(5) -> Legend (top 10 only)
 *
 * Each tier has 5 sub-ranks: I, II, III, IV, V (V is the lowest, I the highest
 * in tier).
 * Points per tier = 100, so each sub-rank = 20 pts within its tier.
 * Total tiers before Legend: 7 × 100 = 700 pts.
 * Legend threshold = 700 pts.
 */
public class RankSystem {

    // Tier thresholds (inclusive lower bound)
    public static final int WOOD_MIN = 0;
    public static final int BRONZE_MIN = 100;
    public static final int SILVER_MIN = 200;
    public static final int GOLD_MIN = 300;
    public static final int PLATINUM_MIN = 400;
    public static final int DIAMOND_MIN = 500;
    public static final int RUBY_MIN = 600;
    public static final int LEGEND_THRESHOLD = 700;

    public static final int STARTING_POINTS = 50; // Start mid Wood

    private static final String[] TIERS = {
            "Wood", "Bronze", "Silver", "Gold", "Platinum", "Diamond", "Ruby", "Legend"
    };
    private static final int[] THRESHOLDS = {
            WOOD_MIN, BRONZE_MIN, SILVER_MIN, GOLD_MIN,
            PLATINUM_MIN, DIAMOND_MIN, RUBY_MIN, LEGEND_THRESHOLD
    };
    private static final String[] SUB_RANKS = { "V", "IV", "III", "II", "I" };

    /** Get the tier name for a given point total. */
    public static String getTier(int points) {
        if (points >= LEGEND_THRESHOLD)
            return "Legend";
        for (int i = THRESHOLDS.length - 2; i >= 0; i--) {
            if (points >= THRESHOLDS[i])
                return TIERS[i];
        }
        return "Wood";
    }

    /**
     * Get the sub-rank number (1-5) within the current tier.
     * 1 = highest sub-rank (close to promotion), 5 = lowest.
     */
    public static int getSubrank(int points) {
        if (points >= LEGEND_THRESHOLD)
            return 0; // Legend has no sub-ranks
        String tier = getTier(points);
        int tierMin = getTierMin(tier);
        int posInTier = points - tierMin; // 0..99
        // 0-19 = V, 20-39 = IV, 40-59 = III, 60-79 = II, 80-99 = I
        int idx = posInTier / 20; // 0..4
        return 5 - idx; // V=5 at 0, I=1 at 80-99
    }

    /** Get sub-rank roman numeral string. */
    public static String getSubrankLabel(int points) {
        if (points >= LEGEND_THRESHOLD)
            return "";
        return SUB_RANKS[4 - (getSubrank(points) - 1)];
    }

    public static String getRankDisplay(int points) {
        String tier = getTier(points);
        if (tier.equals("Legend"))
            return "Legend";
        return tier + " " + getSubrankLabel(points);
    }

    private static int getTierMin(String tier) {
        for (int i = 0; i < TIERS.length; i++) {
            if (TIERS[i].equals(tier))
                return THRESHOLDS[i];
        }
        return 0;
    }

    /**
     * Returns positive points gained for a win.
     * Base = 20. If opponent is higher rank, bonus up to +10.
     * If opponent is lower rank, slightly less, min 10.
     */
    public static int pointsForWin(int myPoints, int opponentPoints) {
        int diff = opponentPoints - myPoints;
        int base = 20;
        int bonus = (int) Math.max(-10, Math.min(15, diff / 20.0));
        return Math.max(10, base + bonus);
    }

    /**
     * Returns negative points for a loss.
     * Base = -20. If opponent is lower rank, larger penalty (up to -30).
     * If opponent is higher rank, smaller penalty (min -10).
     */
    public static int pointsForLoss(int myPoints, int opponentPoints) {
        int diff = myPoints - opponentPoints; // positive when opponent is weaker
        int base = -20;
        int extra = (int) Math.max(-10, Math.min(10, diff / 20.0));
        return Math.min(-10, base - extra);
    }

    /** Rank color for display. */
    public static java.awt.Color getRankColor(int points) {
        String tier = getTier(points);
        return switch (tier) {
            case "Wood" -> new java.awt.Color(139, 90, 43);
            case "Bronze" -> new java.awt.Color(205, 127, 50);
            case "Silver" -> new java.awt.Color(180, 180, 190);
            case "Gold" -> new java.awt.Color(255, 215, 0);
            case "Platinum" -> new java.awt.Color(100, 220, 230);
            case "Diamond" -> new java.awt.Color(100, 180, 255);
            case "Ruby" -> new java.awt.Color(220, 50, 80);
            case "Legend" -> new java.awt.Color(255, 140, 0);
            default -> java.awt.Color.WHITE;
        };
    }

    /** Progress within current tier as a 0.0-1.0 fraction for a progress bar. */
    public static float tierProgress(int points) {
        if (points >= LEGEND_THRESHOLD)
            return 1.0f;
        String tier = getTier(points);
        int tierMin = getTierMin(tier);
        return Math.min(1.0f, (points - tierMin) / 100.0f);
    }
}
