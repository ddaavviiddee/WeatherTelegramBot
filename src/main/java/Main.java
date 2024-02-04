import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
public class Main {

    public static void main(String [] args){
        String token = // insert Telegram token";
        String apiKey = // insert api token;

        //DB Connection

        String mongoURL = "mongodb://localhost:27017";
        String dbName = "WeatherBotDb";

        MongoDBConnection mongoConnection = new MongoDBConnection(mongoURL, dbName);

        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class); // Inizialize bot
            botsApi.registerBot(new WeatherBot(token, apiKey, mongoConnection));
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }

    }

}


