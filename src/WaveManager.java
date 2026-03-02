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
        // More aggressive exponential scaling for balloon count
        int count = (int) (8 * Math.pow(1.22, wave) * difficultyMultiplier);
        spawnQueue.clear();
        for (int i = 0; i < count; i++) {
            spawnQueue.add(randomLevel(wave));
        }
    }

    private int randomLevel(int wave) {
        // Weight array: index 0 = level 1 … index 9 = level 10
        int[] weights = new int[10];
        weights[0] = Math.max(0, 10 - wave * 2);
        weights[1] = Math.max(0, 8 - (int) (wave * 1.2));
        weights[2] = Math.min(8, wave * 2);
        weights[3] = Math.min(6, Math.max(0, wave - 2) * 2);
        weights[4] = Math.min(5, Math.max(0, wave - 3) * 2);
        weights[5] = Math.min(4, Math.max(0, wave - 5) * 2);
        weights[6] = Math.min(4, Math.max(0, wave - 8) * 3); // Level 7 at Wave 8
        weights[7] = Math.min(3, Math.max(0, wave - 10) * 4); // Level 8 at Wave 11
        weights[8] = Math.min(3, Math.max(0, wave - 12) * 5); // Level 9 at Wave 14
        weights[9] = Math.min(2, Math.max(0, wave - 16) * 6); // Level 10 (MOAB) at Wave 18

        int total = 0;
        for (int w : weights)
            total += w;
        if (total == 0)
            return 1;
        int r = rng.nextInt(total);
        int cum = 0;
        for (int i = 0; i < 10; i++) {
            cum += weights[i];
            if (r < cum)
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
            // Spawn interval shrinks by 1.5% per wave
            double spawnDelayMult = Math.pow(0.985, state.waveNumber);
            if (nowMs - lastSpawnTime >= (SPAWN_INTERVAL_MS * spawnDelayMult / difficultyMultiplier)) {
                int level = spawnQueue.poll();

                // Balloon speed increases by 1.5% per wave
                double speedMult = Math.pow(1.015, state.waveNumber);
                Balloon b = new Balloon(level, Path.getWaypoints(), speedMult);

                // MOAB HP scaling: 400 base + 12% exponential per wave past 18
                if (level == 10) {
                    double scaledHP = 400 * Math.pow(1.12, Math.max(0, state.waveNumber - 18));
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
