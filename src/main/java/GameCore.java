import java.util.*;
import java.util.stream.Collectors;

public class GameCore {
    private List<Player> players = new ArrayList<>();
    private GameState gameState = GameState.WAITING;
    private long gameChatId;
    private final Map<Long, String> nightActions = new HashMap<>();
    private final Map<Long, String> playerVotes = new HashMap<>(); // voterId → targetUsername
    private final Map<String, Integer> voteCounts = new HashMap<>();
    private Map<String, Integer> voteCountsSnapshot;

    Optional<Map.Entry<String, Integer>> maxEntry = voteCounts.entrySet()
            .stream()
            .max(Map.Entry.comparingByValue());// username → count

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
    public void setGameChatId(long gameChatId) {
        this.gameChatId = gameChatId;
    }

    public long getGameChatId() {
        return gameChatId;
    }

    public void addVote(Long voterId, String targetUsername) {
        if (gameState != GameState.DAY) return;

        Player voter = findPlayerById(voterId);
        if (voter == null || !voter.isAlive()) return;

        String normalizedTarget = targetUsername.trim().toLowerCase();
        if (playerVotes.containsKey(voterId)) {
            String prevVote = playerVotes.get(voterId);
            voteCounts.compute(prevVote, (k, v) -> (v == null || v <= 1) ? null : v - 1);
        }
        playerVotes.put(voterId, normalizedTarget);
        voteCounts.put(normalizedTarget, voteCounts.getOrDefault(normalizedTarget, 0) + 1);
    }

    public void resolveDayVoting() {
        killedPlayer = null;
        voteCounts.clear();
        for (String username : playerVotes.values()) {
            voteCounts.put(username, voteCounts.getOrDefault(username, 0) + 1);
        }
        if (!voteCounts.isEmpty()) {
            int maxVotes = Collections.max(voteCounts.values());
            List<String> candidates = voteCounts.entrySet().stream()
                    .filter(e -> e.getValue() == maxVotes)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());


            if (candidates.size() == 1) {
                String targetUsername = candidates.get(0);
                Player target = findPlayerByName(targetUsername);
                if (target != null && target.isAlive()) {
                    target.setAlive(false);
                    killedPlayer = target;
                }
            }
        }
        voteCountsSnapshot = new HashMap<>(voteCounts);
        playerVotes.clear();

        checkWinConditions();
        if (gameState != GameState.ENDED) {
            gameState = GameState.NIGHT;
        }
    }
    public Map<String, Integer> getVotesSnapshot() {
        return voteCountsSnapshot;
    }
    public Map<String, Integer> getVotes() {
        return Collections.unmodifiableMap(voteCounts);
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

        if (mafiaAlive == 0) {
            endGame("🟢 Мирные победили!");
        } else if (mafiaAlive >= civiliansAlive || civiliansAlive == 0) {
            endGame("🔴 Мафия победила!");
        } else if (totalAlive == 0) {
            endGame("🤝 Ничья! Все игроки погибли");
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
        String normalized = name.trim().toLowerCase();
        return players.stream()
                .filter(p -> p.getUsername().toLowerCase().equals(normalized))
                .findFirst()
                .orElse(null);
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
        playerVotes.clear();
        gameState = GameState.WAITING;
        killedPlayer = null;
        savedPlayer = null;
        gameChatId = 0;
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
    public Player getSavedPlayer() {
        return savedPlayer;
    }
    public void setGameState(GameState state) {
        this.gameState = state;
    }
}