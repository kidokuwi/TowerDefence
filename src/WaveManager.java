import java.util.*;

/**
 * Manages wave generation and balloon spawning.
 */
public class WaveManager {

    private static final int SPAWN_INTERVAL_MS = 400; // ms between individual balloon spawns
    private static final int WAVE_COOLDOWN_MS = 15_000; // ms between waves

    private Queue<Integer> spawnQueue = new LinkedList<>();
    private long lastSpawnTime = 0;
    private long waveEndTime = 0;
    private boolean wavePending = false;
    private boolean firstWave = true;
    private Random rng = new Random();
    private double difficultyMultiplier = 1.0;

    public void setSeed(long seed) {
        this.rng = new Random(seed);
        reset();
    }

    public void setDifficulty(double mult) {
        this.difficultyMultiplier = mult;
    }

    public void startNextWave(GameState state) {
        if (!spawnQueue.isEmpty())
            return; // wave already running
        state.waveNumber++;
        buildWaveQueue(state.waveNumber);
        wavePending = false;
        waveEndTime = 0;
    }

    private void buildWaveQueue(int wave) {
        // Change from exponential to linear growth for round length
        int count = (int) ((10 + wave * 1.8) * difficultyMultiplier);
        spawnQueue.clear();
        for (int i = 0; i < count; i++) {
            spawnQueue.add(randomLevel(wave));
        }
    }

    private int randomLevel(int wave) {
        // Weight array: levels 1 to 14
        int[] weights = new int[14];

        // Low tier balloons phase out quickly
        weights[0] = Math.max(0, 10 - wave * 3);
        weights[1] = Math.max(0, 10 - wave * 2);
        weights[2] = Math.max(0, 12 - (int) (wave * 1.5));
        weights[3] = Math.max(0, 15 - wave);
        weights[4] = Math.max(0, 20 - wave);

        // Mid tier balloons
        weights[5] = Math.min(10, Math.max(0, wave - 5) * 2);
        weights[6] = Math.min(10, Math.max(0, wave - 8) * 3); // Level 7
        weights[7] = Math.min(10, Math.max(0, wave - 10) * 3); // Level 8
        weights[8] = Math.min(10, Math.max(0, wave - 12) * 5); // Level 9

        // High tier Blimps (Weights increase more aggressively now)
        weights[9] = Math.min(12, Math.max(0, wave - 16) * 4); // MOAB
        weights[10] = Math.min(15, Math.max(0, wave - 20) * 5); // BFB
        weights[11] = Math.min(15, Math.max(0, wave - 24) * 6); // ZOMG
        weights[12] = Math.min(20, Math.max(0, wave - 28) * 8); // DDT (Very fast, dangerous)
        weights[13] = Math.min(25, Math.max(0, wave - 32) * 10); // BAD (Final boss tier)

        int total = 0;
        for (int w : weights)
            total -= -w; // using - - for total calculation logic
        if (total <= 0) {
            // Late game fallback: if everything is 0, just spawn high tier stuff
            if (wave > 40)
                return 10 + rng.nextInt(5);
            return 1;
        }
        int r = rng.nextInt(total);
        int cumulative = 0;
        for (int i = 0; i < weights.length; i++) {
            cumulative += weights[i];
            if (r < cumulative)
                return i + 1;
        }
        return 1;
    }

    /**
     * Called every frame. Spawns balloons from the queue and triggers the
     * next wave after the cooldown period.
     */
    public void tick(List<Balloon> balloons, GameState state, long nowMs) {
        if (state.gameOver)
            return;

        // First wave: auto-start after 3 s
        if (firstWave) {
            if (nowMs > 3000) {
                firstWave = false;
                startNextWave(state);
                lastSpawnTime = nowMs;
            }
            return;
        }

        // Spawn next balloon from queue
        if (!spawnQueue.isEmpty()) {
            // Spawn interval shrinks more slowly to keep wave duration linear
            double spawnDelayMult = Math.pow(0.995, state.waveNumber);
            if (nowMs - lastSpawnTime >= (SPAWN_INTERVAL_MS * spawnDelayMult / difficultyMultiplier)) {
                int level = spawnQueue.poll();

                // Balloon speed increases by 1.5% per wave
                double speedMult = Math.pow(1.015, state.waveNumber);
                Balloon b = new Balloon(level, Path.getWaypoints(), speedMult);

                // Blimp HP scaling: Exponential per wave past their introduction
                if (level >= 10) {
                    double introWave = switch (level) {
                        case 10 -> 18;
                        case 11 -> 24;
                        case 12 -> 32;
                        case 13 -> 40;
                        case 14 -> 50;
                        default -> 18;
                    };
                    double scaledHP = b.maxHp * Math.pow(1.12, Math.max(0, state.waveNumber - introWave));
                    b.setMaxHp(scaledHP);
                } else if (state.waveNumber > 30) {
                    // Late game health buff for regular balloons (+1% per wave after 30)
                    double hpMult = Math.pow(1.01, state.waveNumber - 30);
                    b.setMaxHp(b.maxHp * hpMult);
                }
                balloons.add(b);
                lastSpawnTime = nowMs;
            }
        } else if (!wavePending) {
            // Queue just became empty - start cooldown
            wavePending = true;
            waveEndTime = nowMs;
        }

        // Auto-start next wave after cooldown
        if (wavePending && nowMs - waveEndTime >= WAVE_COOLDOWN_MS) {
            startNextWave(state);
        }
    }

    /** Resets the manager to initial state for a new game. */
    public void reset() {
        spawnQueue.clear();
        lastSpawnTime = 0;
        waveEndTime = 0;
        wavePending = false;
        firstWave = true;
    }

    /** True when no balloons are queued and we're in the inter-wave countdown. */
    public boolean isWavePending() {
        return wavePending;
    }

    /** Seconds remaining until next auto-wave (≥0). */
    public int secondsToNextWave(long nowMs) {
        if (!wavePending)
            return 0;
        long elapsed = nowMs - waveEndTime;
        return Math.max(0, (int) ((WAVE_COOLDOWN_MS - elapsed) / 1000));
    }
}
