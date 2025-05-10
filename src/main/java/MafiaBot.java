import io.github.cdimascio.dotenv.Dotenv;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import java.util.*;

public class MafiaBot extends TelegramLongPollingBot {
    private final GameCore gameCore = new GameCore();
    private final Map<Long, Long> gameCreators = new HashMap<>();
    private final Map<Long, Timer> gameTimers = new HashMap<>();

    @Override
    public String getBotUsername() { return "MafiaGameBot"; }

    @Override
    public String getBotToken() {
        return Dotenv.load().get("BOT_TOKEN");
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) return;

        long chatId = update.getMessage().getChatId();
        String text = update.getMessage().getText();
        User user = update.getMessage().getFrom();

        try {
            if (text.startsWith("/start")) handleStart(chatId);
            else if (text.startsWith("/join")) handleJoin(chatId, user);
            else if (text.startsWith("/startgame")) handleStartGame(chatId, user.getId());
            else handleGameAction(chatId, user, text);
        } catch (Exception e) {
            sendSafeMessage(chatId, "⛔ Ошибка: " + e.getMessage());
        }
    }

    private void handleStart(long chatId) throws TelegramApiException {
        sendMessage(chatId,
                "🎮 Мафия\n\n" +
                        "▫️ /join - Войти в игру\n" +
                        "▫️ /startgame - Начать игру");
    }

    private void handleJoin(long chatId, User user) throws TelegramApiException {
        if (gameCore.getGameState() != GameCore.GameState.WAITING) {
            throw new IllegalStateException("Игра уже началась!");
        }

        gameCore.addPlayer(new Civilian(user.getId(), user.getUserName()));

        if (gameCore.getPlayers().size() == 1) {
            gameCreators.put(chatId, user.getId());
        }

        sendMessage(chatId, "✅ " + user.getUserName() + " присоединился!");
    }

    private void handleStartGame(long chatId, Long userId) throws TelegramApiException {
        if (!userId.equals(gameCreators.get(chatId))) {
            throw new SecurityException("Только создатель лобби может начать игру!");
        }

        gameCore.startGame();
        notifyRoles();
        startNightPhase(chatId);
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
        } catch (Exception e) {
            sendSafeMessage(chatId, e.getMessage());
        }
    }

    private void sendNightResults(long chatId) throws TelegramApiException {
        StringBuilder sb = new StringBuilder("🌃 Ночью:\n");
        if (gameCore.getKilledPlayer() != null) {
            sb.append("☠️ Убит: ").append(gameCore.getKilledPlayer().getUsername());
        }
        sb.append("\n").append(gameCore.getCommissarCheckResult());
        sendMessage(chatId, sb.toString());
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
        if (player == null || !player.isAlive()) return;

        switch (gameCore.getGameState()) {
            case NIGHT -> handleNightAction(player, text);
            case DAY -> handleDayAction(chatId, player, text);
        }
    }

    private void handleNightAction(Player player, String text) {
        String[] parts = text.split(" ", 2);
        if (parts.length < 2) return;

        Player target = gameCore.findPlayerByName(parts[1]);
        if (target == null || !target.isAlive()) return;

        switch (parts[0].toLowerCase()) {
            case "/kill" -> gameCore.processNightAction(player, target);
            case "/save" -> gameCore.processNightAction(player, target);
            case "/check" -> gameCore.processNightAction(player, target);
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
        gameCore.getPlayers().forEach(p -> {
            try { sendMessage(p.getUserId(), message); }
            catch (TelegramApiException ignored) {}
        });
    }

    private void sendRoleSpecificInstructions() {
        gameCore.getPlayers().forEach(p -> {
            try {
                if (p.getRole() == Role.MAFIA) {
                    sendMessage(p.getUserId(), "🔪 Выберите жертву: /kill [имя]");
                }
                if (p.getRole() == Role.DOCTOR) {
                    sendMessage(p.getUserId(), "💉 Кого спасти: /save [имя]");
                }
                if (p.getRole() == Role.COMMISSAR) {
                    sendMessage(p.getUserId(), "🕵️ Кого проверить: /check [имя]");
                }
            } catch (TelegramApiException ignored) {}
        });
    }

    private void sendMessage(long chatId, String text) throws TelegramApiException {
        execute(SendMessage.builder()
                .chatId(String.valueOf(chatId))
                .text(text)
                .build());
    }

    private void sendSafeMessage(long chatId, String text) {
        try { sendMessage(chatId, text); }
        catch (TelegramApiException ignored) {}
    }
}