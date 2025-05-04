import io.github.cdimascio.dotenv.Dotenv;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

public class MafiaBot extends TelegramLongPollingBot {
    private final GameCore gameCore = new GameCore();

    @Override
    public String getBotUsername() {
        return "MafiaGameBot";
    }

    @Override
    public String getBotToken() {
        Dotenv dotenv = Dotenv.load();
        String botToken = dotenv.get("BOT_TOKEN");
        return botToken;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) return;

        long chatId = update.getMessage().getChatId();
        String text = update.getMessage().getText();

        try {
            if (text.startsWith("/start")) {
                handleStart(chatId);
            } else if (text.startsWith("/join")) {
                handleJoin(chatId, update.getMessage().getFrom());
            }
        } catch (Exception e) {
            try {
                sendMessage(chatId, "⚠️ Error: " + e.getMessage());
            } catch (TelegramApiException ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    private void handleStart(long chatId) throws TelegramApiException {
        sendMessage(chatId, "Welcome to Mafia Game Bot! Use /join to participate.");
    }

    private void handleJoin(long chatId, User user) throws TelegramApiException {
        Player player = new Civilian(user.getId(), user.getFirstName());
        gameCore.addPlayer(player);
        sendMessage(chatId, "You've joined the game!");
    }

    private void sendMessage(long chatId, String text) throws TelegramApiException {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(text);
        execute(message);
    }
}