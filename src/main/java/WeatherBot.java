import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import java.io.IOException;

import java.util.concurrent.*;

import org.apache.commons.lang3.StringUtils;

// Vedere se fare un cambio di lingua


public class WeatherBot extends TelegramLongPollingBot {

    private final String token;
    private final String apiKey;
    private final MongoDBConnection mongoDBConnection;

    public WeatherBot(String token, String apiKey, MongoDBConnection mongoConnection){
        this.token = token;
        this.apiKey = apiKey;
        this.mongoDBConnection = mongoConnection;
    }

    public void onUpdateReceived(Update update) {

        if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText();
            String chatId = update.getMessage().getChatId().toString();

            if (messageText.startsWith("/")) {
                // Command received
                String command = messageText.toLowerCase();
                try {
                    handleCommand(command, chatId);
                } catch (IOException e) {
                    e.printStackTrace();
                    sendMessage(chatId, "Error during command elaboration.");
                }
            } else {
                // Command not available
                sendMessage(chatId, "Please insert a valid command: " + "\n"
                            + "/forecast *cityname* to get the current day forecast." + "\n"
                            + "/nextdayforecast *cityname* to get the next day forecast." + "\n"
                            + "/setpreferredcity *cityname* to set a preferred city, receiving messages" +
                            " for good weather and weather alerts." + "\n"
                            + "/setalert *cityname* to set alerts for a specific city." + "\n");
            }
        }
    }

    public void handleCommand(String command, String chatId) throws IOException {
            if (command.startsWith("/start")){

                sendMessage(chatId, "Welcome to the weather bot! Here's the list of available commands: " + "\n"
                        + "/forecast *cityname* to get the current day forecast." + "\n"
                        + "/nextdayforecast *cityname* to get the next day forecast." + "\n"
                        + "/setpreferredcity *cityname* to set a preferred city, receiving messages" +
                        " for good weather and weather alerts." + "\n"
                        + "/setalert *cityname* to set alerts for a specific city." + "\n");


            }
            if (command.startsWith("/setpreferredcity")){

                // Stops the running threads if running, based on chatId
                stopThread(chatId);

                // Saves the preferred city in mongoDB for each user
                String preferredCityName = command.substring(18);
                savePreferredCity(chatId, preferredCityName);

                sendMessage(chatId, StringUtils.capitalize(preferredCityName) + " is set as your preferred city");

                String cityMongo = getPreferredCityFromMongo(chatId);
                // Extracts city from mongoDB
                WeatherAPI weatherAutoAlert = new WeatherAPI(apiKey);
                String weatherAutoAlertInfo = weatherAutoAlert.getWeatherInfo(cityMongo, apiKey);


                if (weatherAutoAlertInfo != null) {
                    startAlertsThread(cityMongo, chatId);
                } else {
                    sendMessage(chatId, "Error in retrieving data for this city.");
                }

                WeatherAPI weatherAutoSunny = new WeatherAPI(apiKey);
                String weatherAutoSunnyInfo = weatherAutoSunny.getWeatherInfo(cityMongo, apiKey);

                if (weatherAutoSunnyInfo != null) {
                    startSunnyThread(cityMongo, chatId);
                } else {
                    sendMessage(chatId, "Error in retrieving data for this city");
                }
            }

            if (command.startsWith("/forecast")) {
                String cityName = command.substring(10); // Extracts city name from the command

                WeatherAPI weatherAPI = new WeatherAPI(apiKey);
                String weatherInfo = weatherAPI.getWeatherInfo(cityName, apiKey);
                System.out.println(chatId);
                if (weatherInfo != null) {
                    String response = processWeatherData(weatherInfo, cityName);
                    sendMessage(chatId, response);
                } else {
                    sendMessage(chatId, "Error in retrieving data for this city.");
                }
            }

            if (command.startsWith("/nextdayforecast")) {
                String cityNameNextDay = command.substring(17); // Extracts city name from the command

                WeatherAPI weatherAPINextDay = new WeatherAPI(apiKey);
                String weatherInfoNextDay = weatherAPINextDay.getWeatherInfo(cityNameNextDay, apiKey);

                if (weatherInfoNextDay != null) {
                    String response = getNextDayForecast(weatherInfoNextDay, cityNameNextDay);
                    sendMessage(chatId, response);
                } else {
                    sendMessage(chatId, "Error in retrieving data for this city.");
                }
            }

            if (command.startsWith("/setalert")){
                String cityAutoAlert = command.substring(10);

                WeatherAPI weatherAutoAlert = new WeatherAPI(apiKey);
                String weatherAutoAlertInfo = weatherAutoAlert.getWeatherInfo(cityAutoAlert, apiKey);

                if (weatherAutoAlertInfo != null) {
                    startAlertsThread(cityAutoAlert, chatId);
                } else {
                    sendMessage(chatId, "Error in retrieving data for this city.");
                }
            }
    }


    private void savePreferredCity(String chatId, String cityName) {
        try {
            MongoDatabase database = mongoDBConnection.getDatabase();
            MongoCollection<Document> collection = database.getCollection("Subscriptions");
            // Saves the preferred city
            Bson filter = Filters.eq("chatId", chatId);

            Document document = new Document("chatId", chatId).append("preferredCity", cityName);
            collection.replaceOne(filter, document, new ReplaceOptions().upsert(true));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String getPreferredCityFromMongo(String chatId){
        MongoDatabase database = mongoDBConnection.getDatabase();
        // Extracts the preferred city from mongo based on the chatId
        MongoCollection<Document> collection = database.getCollection("Subscriptions");
        Document query = new Document("chatId", chatId);
        FindIterable<Document> result = collection.find(query);

        if (result.first() != null){
            String preferredCity = result.first().getString("preferredCity");
            return preferredCity;
        }
        return "Messina";
    }

    private String processWeatherData(String weatherData, String cityName) {
        try {
            // Gets weather data
            JSONObject jsonData = new JSONObject(weatherData);


            // Extracts weather data
            JSONArray daysArray = jsonData.getJSONArray("days");

                JSONObject dayValue = daysArray.getJSONObject(0);

                double maxtemp=dayValue.getDouble("tempmax");
                double mintemp=dayValue.getDouble("tempmin");
                String conditions = dayValue.getString("conditions");
                String icon = dayValue.getString("icon");
                String emoji = getWeatherEmoji(icon);
                double pop = dayValue.getDouble("precip");
                String day = dayValue.getString("datetime");
                String sunrise = dayValue.getString("sunrise");
                String sunset = dayValue.getString("sunset");
                System.out.println(apiKey);
                cityName = StringUtils.capitalize(cityName);



                String message = " " + cityName + "\n" + emoji + " " + conditions + "\n" +
                        "\uD83C\uDF21\uFE0F " + " Max temperature: " + maxtemp + " C°" + "\n" + "Min temperature: " + mintemp + " C°" + "\n" +
                        "\uD83C\uDF05 " + "Sunrise is at: " + sunrise + "\n" +
                        "\uD83C\uDF04 " + "Sunset is at: " + sunset + "\n" +
                        " Rain probabilities are: " + pop + "%" + "\n" +
                        " (" + day + ")";

                return message;

        } catch (Exception e) {
            e.printStackTrace();
        }

        return "Retrieving forecast for " + cityName + "is not possible.";
    }

    private String getNextDayForecast(String forecastData, String cityName) {
        try {
            JSONObject forecastJson = new JSONObject(forecastData);
            JSONArray daysArray = forecastJson.getJSONArray("days");

            if (daysArray.length() > 1) {
                // Gives the weather data for the next day
                JSONObject nextDay = daysArray.getJSONObject(1);
                double maxtemp=nextDay.getDouble("tempmax");
                double mintemp=nextDay.getDouble("tempmin");
                String conditions = nextDay.getString("conditions");
                String icon = nextDay.getString("icon");
                String emoji = getWeatherEmoji(icon);
                double pop = nextDay.getDouble("precip");
                String day = nextDay.getString("datetime");
                String sunrise = nextDay.getString("sunrise");
                String sunset = nextDay.getString("sunset");
                cityName = StringUtils.capitalize(cityName);




                String message = " " + cityName + "\n" + emoji + " " + conditions + "\n" +
                        "\uD83C\uDF21\uFE0F " + " Max temperature: " + maxtemp + " C°" + "\n" + "Min temperature: " + mintemp + " C°" + "\n" +
                        "\uD83C\uDF05 " + "Sunrise is at: " + sunrise + "\n" +
                        "\uD83C\uDF04 " + "Sunset is at: " + sunset + "\n" +
                        " Rain probabilities are: " + pop + "%" + "\n" +
                        " (" + day + ")";
                return message;
            } else {
                return "There's no available forecast for the next day.";
            }
        } catch (JSONException e) {
            e.printStackTrace();
            return "Error.";
        }
    }

    private ConcurrentHashMap<String, ScheduledExecutorService> activeThreads = new ConcurrentHashMap<>(); // This hash map keeps track of active threads relative to single users

    public void startSunnyThread(String cityName, String chatId) {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

        Runnable taskSunny = () -> {
            // Gets weather info and sends a message if the weather is sunny and there are no alerts
            WeatherAPI weatherAPI = new WeatherAPI(apiKey);
            String weatherInfo = weatherAPI.getWeatherInfo(cityName, apiKey);

            if (isSunny(weatherInfo)) {
                String message = "🌞 The weather in " + StringUtils.capitalize(cityName) + " is wonderful! Go have a walk outside.";
                sendMessage(chatId, message);
            }

        };

        executor.scheduleAtFixedRate(taskSunny, 0, 5, TimeUnit.SECONDS);
        activeThreads.put(chatId, executor);
    }

    public void startAlertsThread(String cityName, String chatId) {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

        Runnable taskAlerts = () -> {
            // Gets weather info and sends an alert message if there's any
            WeatherAPI weatherAPIThread = new WeatherAPI(apiKey);
            String weatherInfoThread = weatherAPIThread.getWeatherInfo(cityName, apiKey);

            if (weatherInfoThread != null) {
                String alerts = alertForecast(weatherInfoThread, cityName);
                if (!alerts.isEmpty()) {
                    String message = "🔔 *Weather Alerts* 🔔\n\n" + alerts;
                    sendMessage(chatId, message);
                }
            }

        };

        executor.scheduleAtFixedRate(taskAlerts, 0, 10, TimeUnit.SECONDS);
        activeThreads.put(chatId, executor);
    }

    public void stopThread(String chatId) {
        ExecutorService executor = activeThreads.get(chatId);
        if (executor != null) {
            // Interrupts running threads when a new city is set
            executor.shutdown();
            activeThreads.remove(chatId);
        }
    }


    private boolean isSunny(String weatherInfo){
        try {
            // Checks if the weather is sunny and there are no alerts
            JSONObject jsonData = new JSONObject(weatherInfo);
            JSONArray daysArray = jsonData.getJSONArray("days");
            JSONArray alertsArray = jsonData.getJSONArray("alerts");
            // Checks if the weather condition is sunny and if there are any alerts

                JSONObject dayValue = daysArray.getJSONObject(0);

                String condition = dayValue.getString("conditions");

                if (condition.equalsIgnoreCase("clear") && alertsArray.isEmpty()) {
                    return true;
                }

        } catch (JSONException e) {
            e.printStackTrace();
        }

        return false;
    }
    private String alertForecast(String AlertData, String CityName){
        try {
            JSONObject jsonData = new JSONObject(AlertData);
            JSONArray alertsArray = jsonData.getJSONArray("alerts");
            StringBuilder messageBuilder = new StringBuilder();

            if (alertsArray.length() > 0) {
                messageBuilder.append("\n\n⚠️ *Alert* ⚠️\n");
                // Creates a message giving the alert if present.
                for (int i = 0; i < alertsArray.length(); i++) {
                    JSONObject alert = alertsArray.getJSONObject(i);
                    String event = alert.getString("event");
                    String headline = alert.getString("headline");
                    String description = alert.getString("description");

                    messageBuilder.append("\nAlerts for: ").append(CityName).append("\nEvent: ").append(event).append("\n")
                            .append("Title: ").append(headline).append("\n")
                            .append("Description: ").append(description).append("\n");
                }

            }
            return messageBuilder.toString();

        } catch (JSONException e) {
            e.printStackTrace();
            return "There are no alerts for this city";
        }
    }

    private String getWeatherEmoji(String condition){
        switch(condition){    // This method gives an emoji based on the weather condition
            case "clear-day":
                return "☀\uFE0F";
            case "clear-night":
                return "\uD83C\uDF19";
            case "Cloudy":
                return "☁\uFE0F";
            case "fog":
                return "\uD83C\uDF2B\uFE0F";
            case "hail":
                return "\uD83E\uDDCA";
            case "partly-cloudy-day":
                return "⛅";
            case "partly-cloudy-night":
                return "☁\uFE0F";
            case "rain-snow-showers-day":
                return "\uD83C\uDF28";
            case "rain-snow-showers-night":
                return "\uD83C\uDF28";
            case "rain-snow":
                return "\uD83C\uDF28";
            case "rain":
                return "\uD83C\uDF27";
            case "showers-day":
                return "\uD83C\uDF26";
            case "showers-night":
                return "\uD83C\uDF27";
            case "sleet":
                return "❄\uFE0F";
            case "snow-showers-day":
                return "\uD83C\uDF28\uFE0F";
            case "snow-showers-night":
                return "\uD83C\uDF28\uFE0F";
            case "snow":
                return "❄\uFE0F";
            case "thunder-rain":
                return "⛈";
            case "thunder-showers-day":
                return "⛈";
            case "thunder-showers-night":
                return "⛈";
            case "thunder":
                return "\uD83C\uDF29\uFE0F";
            case "wind":
                return "\uD83D\uDCA8";

            default:
                return "";
        }
    }

    private void sendMessage(String chatId, String text) {
        SendMessage message = new SendMessage(chatId, text);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    @Override
    public String getBotUsername() {return "meteorologo_bot";}

    @Override
    public String getBotToken() {
        return token;
    }


}