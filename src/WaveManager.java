import java.util.*;

/**
 * Manages wave generation and balloon spawning.
 */
public class WaveManager {

    private static final int SPAWN_INTERVAL_MS = 600; // ms between individual balloon spawns
    private static final int WAVE_COOLDOWN_MS = 15_000; // ms between waves

    private Queue<Integer> spawnQueue = new LinkedList<>();
    private long lastSpawnTime = 0;
    private long waveEndTime = 0;
    private boolean wavePending = false;
    private boolean firstWave = true;
    private final Random rng = new Random();
    private double difficultyMultiplier = 1.0;

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
        int count = (int) ((8 + wave * 3) * difficultyMultiplier);
        spawnQueue.clear();
        for (int i = 0; i < count; i++) {
            spawnQueue.add(randomLevel(wave));
        }
    }

    private int randomLevel(int wave) {
        // Weight array: index 0 = level 1 … index 5 = level 6
        int[] weights = new int[6];
        weights[0] = Math.max(0, 10 - wave * 2);
        weights[1] = Math.max(0, 8 - (int) (wave * 1.2));
        weights[2] = Math.min(8, wave * 2);
        weights[3] = Math.min(6, Math.max(0, wave - 2) * 2);
        weights[4] = Math.min(5, Math.max(0, wave - 4) * 2);
        weights[5] = Math.min(4, Math.max(0, wave - 6) * 2);

        int total = 0;
        for (int w : weights)
            total += w;
        if (total == 0)
            return 1;
        int r = rng.nextInt(total);
        int cum = 0;
        for (int i = 0; i < 6; i++) {
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
            if (nowMs - lastSpawnTime >= (SPAWN_INTERVAL_MS / difficultyMultiplier)) {
                int level = spawnQueue.poll();
                balloons.add(new Balloon(level, Path.getWaypoints()));
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
