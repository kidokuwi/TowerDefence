/**
 * Simple data class holding all mutable game state.
 */
public class GameState {
    public int cash;
    public int lives;
    public int waveNumber;
    public int score;
    public boolean gameOver;
    public boolean victory;
    public boolean turboMode;
    public int income;
    public long incomeTimer;
    /** Type name of tower currently selected for placement, or null. */
    public String selectedTowerType; // "dart", "sniper", "bomb"

    public GameState() {
        cash = 450;
        lives = 20;
        waveNumber = 0;
        score = 0;
        gameOver = false;
        victory = false;
        turboMode = false;
        income = 0;
        incomeTimer = 0;
        selectedTowerType = null;
    }

    public void reset() {
        cash = 450;
        lives = 20;
        waveNumber = 0;
        score = 0;
        gameOver = false;
        victory = false;
        turboMode = false;
        income = 0;
        incomeTimer = 0;
        selectedTowerType = null;
    }

    public void addCash(int amount) {
        cash += amount;
        score += amount;
    }

    public boolean canAfford(int cost) {
        return cash >= cost;
    }

    public void spend(int cost) {
        cash -= cost;
    }

    public void loseLife() {
        lives--;
        if (lives <= 0) {
            lives = 0;
            gameOver = true;
        }
    }
}
