import java.util.*;
import java.util.stream.Collectors;

public class GameCore {
    private List<Player> players = new ArrayList<>();
    private GameState gameState = GameState.WAITING;
    private final Map<Long, String> nightActions = new HashMap<>();
    private final Map<String, Integer> votes = new HashMap<>();
    private Player killedPlayer;
    private Player savedPlayer;
    private Commissar commissar;

    public enum GameState {WAITING, NIGHT, DAY, ENDED}

    public void addPlayer(Player player) {
        validateGameState(GameState.WAITING);
        if (players.stream().anyMatch(p -> p.getUserId() == player.getUserId())) {
            throw new IllegalArgumentException(player.getUsername() + ", ты уже в игре!");
        }
        players.add(player);
    }

    public void startGame() {
        validatePlayersCount();
        assignRoles();
        gameState = GameState.NIGHT;
    }


    public void resolveNightActions() {

        killedPlayer = null;
        savedPlayer = null;

        handleMafiaAction();
        handleDoctorAction();
        handleCommissarAction();
        applyNightResults();
        nightActions.clear();

        checkWinConditions();

        if (gameState != GameState.ENDED) {
            gameState = GameState.DAY;
        }
    }

    void applyNightResults() {
        if (killedPlayer != null && !killedPlayer.equals(savedPlayer)) {
            killedPlayer.setAlive(false);
        }
    }

    public void addVote(Long voterId, String targetUsername) {
        if (gameState != GameState.DAY) return;

        Player voter = findPlayerById(voterId);
        if (voter == null || !voter.isAlive()) return;

        votes.merge(targetUsername, 1, Integer::sum);
    }

    public void resolveDayVoting() {
        Optional<Map.Entry<String, Integer>> maxVote = votes.entrySet().stream().max(Map.Entry.comparingByValue());

        maxVote.ifPresent(entry -> {
            Player target = findPlayerByName(entry.getKey());
            if (target != null) {
                target.setAlive(false);
                killedPlayer = target;
            }
        });

        votes.clear();
        checkWinConditions();
        gameState = GameState.NIGHT;
    }


    private void validateGameState(GameState requiredState) {
        if (gameState != requiredState) {
            throw new IllegalStateException("Некорректное состояние игры!");
        }
    }

    private void validatePlayersCount() {
        if (players.size() < 4) {
            throw new IllegalStateException("Необходимо минимум 4 игрока!");
        }
    }

    private void assignRoles() {
        Collections.shuffle(players);
        List<Player> newPlayers = new ArrayList<>();

        newPlayers.add(new Mafia(players.get(0).getUserId(), players.get(0).getUsername()));
        newPlayers.add(new Doctor(players.get(1).getUserId(), players.get(1).getUsername()));
        commissar = new Commissar(players.get(2).getUserId(), players.get(2).getUsername());
        newPlayers.add(commissar);

        for (int i = 3; i < players.size(); i++) {
            newPlayers.add(new Civilian(players.get(i).getUserId(), players.get(i).getUsername()));
        }

        players = newPlayers;
    }

    private void handleMafiaAction() {
        nightActions.entrySet().stream().filter(e -> e.getValue().startsWith("MAFIA ")).findFirst().ifPresent(action -> {
            long targetId = Long.parseLong(action.getValue().split(" ")[1]);
            killedPlayer = findPlayerById(targetId);
        });
    }

    private void handleDoctorAction() {
        nightActions.entrySet().stream().filter(e -> e.getValue().startsWith("DOCTOR ")).findFirst().ifPresent(action -> {
            long targetId = Long.parseLong(action.getValue().split(" ")[1]);
            savedPlayer = findPlayerById(targetId);
        });
    }

    private void handleCommissarAction() {
        nightActions.entrySet().stream().filter(e -> e.getValue().startsWith("COMMISSAR ")).findFirst().ifPresent(action -> {
            long targetId = Long.parseLong(action.getValue().split(" ")[1]);
            Player target = findPlayerById(targetId);
            commissar.performNightAction(target);
        });
    }

    private void checkWinConditions() {
        long totalAlive = players.stream().filter(Player::isAlive).count();
        long mafiaAlive = countAliveByRole(Role.MAFIA);
        long civiliansAlive = totalAlive - mafiaAlive;

        if (totalAlive == 0) {
            endGame("🤝 Ничья! Все игроки погибли");
        } else if (mafiaAlive == 0) {
            endGame("🟢 Мирные победили!");
        } else if (mafiaAlive >= civiliansAlive) {
            endGame("🔴 Мафия победила!");
        }
    }

    public String getDetailedGameResult() {
        if (gameState != GameState.ENDED) return "";

        StringBuilder sb = new StringBuilder("\n\n🏁 ИГРА ОКОНЧЕНА!\n");
        sb.append(countAliveByRole(Role.MAFIA) == 0 ? "🟢 ПОБЕДА МИРНЫХ!\n" : "🔴 ПОБЕДА МАФИИ!\n");

        sb.append("\nУчастники:\n");
        for (Player p : players) {
            sb.append(p.getUsername()).append(" - ").append(p.getRole().getDisplayName()).append(p.isAlive() ? " (выжил)" : " (погиб)").append("\n");
        }

        return sb.toString();
    }

    public Player findPlayerById(Long userId) {
        return players.stream().filter(p -> p.getUserId() == userId).findFirst().orElse(null);
    }

    public Player findPlayerByName(String name) {
        return players.stream().filter(p -> p.getUsername().equalsIgnoreCase(name)).findFirst().orElse(null);
    }

    public Map<String, Integer> getVotes() {
        return Collections.unmodifiableMap(votes);
    }

    public String getAlivePlayersList() {
        return players.stream().filter(Player::isAlive).map(Player::getUsername).collect(Collectors.joining("\n"));
    }

    public String getGameResult() {
        return gameState == GameState.ENDED ? (countAliveByRole(Role.MAFIA) == 0 ? "Мирные победили!" : "Мафия победила!") : "";
    }

    private long countAliveByRole(Role role) {
        return players.stream().filter(p -> p.isAlive() && p.getRole() == role).count();
    }

    public void reset() {
        players.clear();
        nightActions.clear();
        votes.clear();
        gameState = GameState.WAITING;
        killedPlayer = null;
        savedPlayer = null;
    }

    public void endGame(String resultMessage) {
        gameState = GameState.ENDED;
    }

    public Player getPlayerById(long userId) {
        return players.stream().filter(p -> p.getUserId() == userId).findFirst().orElse(null);
    }

    public void processNightAction(Player actor, Player target) {
        if (gameState != GameState.NIGHT) return;

        String action = actor.getRole().name() + " " + target.getUserId();
        nightActions.put(actor.getUserId(), action);
    }

    public GameState getGameState() {
        return gameState;
    }

    public List<Player> getPlayers() {
        return Collections.unmodifiableList(players);
    }

    public Player getKilledPlayer() {
        return killedPlayer;
    }

    public void setGameState(GameState state) {
        this.gameState = state;
    }
}