import java.util.ArrayList;
import java.util.List;

public class GameCore {
    private List<Player> players = new ArrayList<>();
    private GameState gameState = GameState.WAITING;

    public void addPlayer(Player player) {
        players.add(player);
    }

    public void startGame() {
        if (players.size() < 4) {
            throw new IllegalStateException("Need at least 4 players");
        }
        assignRoles();
        this.gameState = GameState.NIGHT;
    }

    private void assignRoles() {
        players.get(0).setRole(Role.MAFIA);
        players.get(1).setRole(Role.DOCTOR);
    }

    public void processNightAction(Player actor, Player target) {
        if (gameState != GameState.NIGHT) return;
        actor.performNightAction(target);
    }

    public enum GameState {
        WAITING, NIGHT, DAY, ENDED
    }
}