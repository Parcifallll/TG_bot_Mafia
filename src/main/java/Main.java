import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {
    static {
        System.setProperty("org.slf4j.simpleLogger.showDateTime", "true"); //log with time
        System.setProperty("org.slf4j.simpleLogger.dateTimeFormat", "HH:mm:ss"); // time format
    }

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(new MafiaBot());
            log.info("The bot is running."); //logger
        } catch (TelegramApiException e) {
            log.error("Cannot run", e);
        }
    }
}