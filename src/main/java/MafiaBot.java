import io.github.cdimascio.dotenv.Dotenv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.*;

public class MafiaBot extends TelegramLongPollingBot {
    private static final Logger log = LoggerFactory.getLogger(Main.class);
    private final GameCore gameCore = new GameCore();
    private final Map<Long, Long> gameCreators = new HashMap<>();
    private final Map<Long, Timer> gameTimers = new HashMap<>();

    @Override
    public String getBotUsername() {
        return "MafiaGameBot";
    }

    @Override
    public String getBotToken() {
        String token = System.getenv("BOT_TOKEN"); // take token from env vars for Railway
        if (token == null || token.isEmpty()) { //only for local dev
            Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
            token = dotenv.get("BOT_TOKEN");
        }
        return token;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) return;

        long chatId = update.getMessage().getChatId();
        String text = update.getMessage().getText();
        User user = update.getMessage().getFrom();

        try {
            if (text.startsWith("/new")) handleCreateGame(chatId);
            else if (text.startsWith("/join")) handleJoin(chatId, user);
            else if (text.startsWith("/start")) handleStartGame(chatId, user.getId());
            else handleGameAction(chatId, user, text);
        } catch (Exception e) {
            sendSafeMessage(chatId, "⛔ Ошибка: " + e.getMessage());
        }
    }

    private void handleCreateGame(long chatId) throws TelegramApiException {
        sendMessage(chatId,
                "🎮 Мафия\n\n" +
                        "▫️ /join - Войти в игру\n" +
                        "▫️ /start - Начать игру");
        log.info("Display 'starting' message");
    }

    private void handleJoin(long chatId, User user) throws TelegramApiException {
        log.info("Try joining a new player");
        if (gameCore.getGameState() != GameCore.GameState.WAITING) {
            throw new IllegalStateException("Игра уже началась!");
        }

        gameCore.addPlayer(new Civilian(user.getId(), user.getUserName()));

        if (gameCore.getPlayers().size() == 1) {
            gameCreators.put(chatId, user.getId());
        }

        sendMessage(chatId, "✅ " + user.getFirstName() + " присоединился!");
    }

    private void handleStartGame(long chatId, Long userId) throws TelegramApiException {
        try {
            if (!userId.equals(gameCreators.get(chatId))) {
                throw new SecurityException("Только создатель лобби может начать игру!");
            }

            if (gameCore.getGameState() != GameCore.GameState.WAITING) {
                sendMessage(chatId, "⛔ Игра уже началась или завершена!");
                return;
            }
            gameCore.startGame();
            notifyRoles();
            startNightPhase(chatId);
        } catch (Exception e) {
            sendSafeMessage(chatId, "⛔ Ошибка: " + e.getMessage());
        }
    }

    private void notifyRoles() throws TelegramApiException {
        for (Player p : gameCore.getPlayers()) {
            sendMessage(p.getUserId(),
                    "🎭 Ваша роль: " + p.getRole().getDisplayName() +
                            "\n\n" + p.getRoleDescription());
        }
    }

    private void startNightPhase(long chatId) throws TelegramApiException {
        gameCore.setGameState(GameCore.GameState.NIGHT);
        sendToAll("🌙 Ночь началась! У вас 60 секунд:");
        sendRoleSpecificInstructions();
        startTimer(chatId, 60, () -> startDayPhase(chatId));
    }

    private void startDayPhase(long chatId) {
        try {
            gameCore.resolveNightActions();
            sendNightResults(chatId);
            gameCore.setGameState(GameCore.GameState.DAY);
            sendToAll("☀️ День начался! Обсуждение (90 сек):\n" + gameCore.getAlivePlayersList());
            startTimer(chatId, 90, () -> endDayPhase(chatId));
        } catch (Exception e) {
            sendSafeMessage(chatId, e.getMessage());
        }
    }

    private void endDayPhase(long chatId) {
        try {
            gameCore.resolveDayVoting();
            checkGameEnd(chatId);
            startNightPhase(chatId);
            sendDayResults(chatId);
        } catch (Exception e) {
            sendSafeMessage(chatId, e.getMessage());
        }
    }

    private void sendNightResults(long chatId) throws TelegramApiException {
        StringBuilder sb = new StringBuilder("🌃 Ночью:\n");
        if (gameCore.getKilledPlayer() != null) {
            Player killed = gameCore.getKilledPlayer();
            sb.append("☠️ Убит: ").append(killed.getUsername());
            sendSafeMessage(killed.getUserId(), "☠️ Вас убили ночью. Вы выбываете из игры.");
        }
        sendMessage(chatId, sb.toString());
    }
    private void sendDayResults(long chatId) throws TelegramApiException {
        Optional<Map.Entry<String, Integer>> maxVote = gameCore.getVotes().entrySet()
                .stream()
                .max(Map.Entry.comparingByValue());

        if (maxVote.isPresent()) {
            Player lynched = gameCore.findPlayerByName(maxVote.get().getKey());
            if (lynched != null) {
                String message = "☠️ Днем линчеван: " + lynched.getUsername();
                sendMessage(chatId, message);
                sendSafeMessage(lynched.getUserId(), "☠️ Вас линчевали днем. Вы выбываете из игры.");
            }
        }
    }

    private void checkGameEnd(long chatId) throws TelegramApiException {
        if (gameCore.getGameState() == GameCore.GameState.ENDED) {
            sendToAll("🏁 Игра окончена! " + gameCore.getGameResult());
            gameCore.reset();
            gameTimers.get(chatId).cancel();
        }
    }

    private void handleGameAction(long chatId, User user, String text)
            throws TelegramApiException {
        Player player = gameCore.getPlayerById(user.getId());
        if (player == null || !player.isAlive()) {
            sendSafeMessage(chatId, "⚠️ Мертвые игроки не могут выполнять действия");
            return;
        }

        switch (gameCore.getGameState()) {
            case NIGHT -> handleNightAction(player, text);
            case DAY -> handleDayAction(chatId, player, text);
        }
    }
    private void handleNightAction(Player player, String text) {
        String[] parts = text.split(" ", 2);
        if (parts.length < 2) {
            sendSafeMessage(player.getUserId(), "❌ Неверный формат! Используйте: /команда [ник]");
            return;
        }

        String command = parts[0].toLowerCase();
        String targetUsername = parts[1];
        Player target = gameCore.findPlayerByName(targetUsername);
        if (target == null) {
            sendSafeMessage(player.getUserId(), "❌ Игрок '" + targetUsername + "' не найден!");
            return;
        }
        if (!target.isAlive()) {
            sendSafeMessage(player.getUserId(), "❌ Игрок '" + targetUsername + "' уже мертв!");
            return;
        }

        switch (command) {
            case "/kill":
                if (player.getRole() != Role.MAFIA) {
                    sendSafeMessage(player.getUserId(), "⛔ Только мафия может убивать!");
                    return;
                }
                gameCore.processNightAction(player, target);
                sendSafeMessage(player.getUserId(), "✅ Вы выбрали жертву: " + targetUsername);
                break;

            case "/save":
                if (player.getRole() != Role.DOCTOR) {
                    sendSafeMessage(player.getUserId(), "⛔ Только доктор может лечить!");
                    return;
                }
                gameCore.processNightAction(player, target);
                sendSafeMessage(player.getUserId(), "✅ Вы будете лечить: " + targetUsername);
                break;

            case "/check":
                if (player.getRole() != Role.COMMISSAR) {
                    sendSafeMessage(player.getUserId(), "⛔ Только комиссар может проверять!");
                    return;
                }
                gameCore.processNightAction(player, target);
                String result = ((Commissar) player).checkPlayer(target);
                sendSafeMessage(player.getUserId(), result);
                break;
        }
    }


    private void handleDayAction(long chatId, Player player, String text) {
        if (text.startsWith("/vote ")) {
            String target = text.split(" ")[1];
            gameCore.addVote(player.getUserId(), target);
        }
    }

    private void startTimer(long chatId, int seconds, Runnable callback) {
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                callback.run();
            }
        }, seconds * 1000L);
        gameTimers.put(chatId, timer);
    }

    private void sendToAll(String message) {
        gameCore.getPlayers().stream()
                .filter(Player::isAlive)
                .forEach(p -> {
                    try {
                        sendMessage(p.getUserId(), message);
                    } catch (TelegramApiException ignored) {
                    }
                });
    }

    private void sendRoleSpecificInstructions() {
        gameCore.getPlayers().stream()
                .filter(Player::isAlive)
                .forEach(p -> {
                    try {
                        sendMessage(p.getUserId(), "Alive players:\n" + gameCore.getAlivePlayersList());
                        if (p.getRole() == Role.MAFIA) {
                            sendMessage(p.getUserId(), "🔪 Выберите жертву: /kill [имя]");
                        }
                        if (p.getRole() == Role.DOCTOR) {
                            sendMessage(p.getUserId(), "💉 Кого спасти: /save [имя]");
                        }
                        if (p.getRole() == Role.COMMISSAR) {
                            sendMessage(p.getUserId(), "🕵️ Кого проверить: /check [имя]");
                        }
                    } catch (TelegramApiException ignored) {
                    }
                });
    }

    public void sendMessage(long chatId, String text) throws TelegramApiException {
        execute(SendMessage.builder()
                .chatId(String.valueOf(chatId))
                .text(text)
                .build());
    }

    private void sendSafeMessage(long chatId, String text) {
        try {
            sendMessage(chatId, text);
        } catch (TelegramApiException ignored) {
        }
    }
}