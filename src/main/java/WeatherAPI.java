import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.lang.String;


public class WeatherAPI {

    private final String apiKey;

    public WeatherAPI(String apiKey) {
        this.apiKey = apiKey;
    }

    private static final String API_BASE_URL = "https://weather.visualcrossing.com/VisualCrossingWebServices/rest/services/timeline/";

    public String getWeatherInfo(String cityName, String apiKey) { // This method builds the URL for the API call
        try {
            String apiUrl = API_BASE_URL + cityName + "?unitGroup=metric&key=" + apiKey;
            URL url = new URL(apiUrl);
            // Opens connection via HTTP
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            int responseCode = connection.getResponseCode();

            // If a connection is established, read output from server
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String line;
                StringBuilder response = new StringBuilder();
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                return response.toString();
            } else {
                System.out.println("Request error for" + cityName + ". Response error: " + responseCode);
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("Request error for" + cityName + ". Exception: " + e.getMessage());
        }
        return null;
    }
}

