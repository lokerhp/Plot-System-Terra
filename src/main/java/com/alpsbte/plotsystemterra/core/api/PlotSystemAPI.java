package com.alpsbte.plotsystemterra.core.api;

import com.alpsbte.plotsystemterra.PlotSystemTerra;
import com.alpsbte.plotsystemterra.core.config.ConfigPaths;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Level;

public class PlotSystemAPI {

    private String GET_BUILDERS_URL = "http://%DOMAIN%:8080/api/builders";
    private String GET_TEAM_URL = "http://%DOMAIN%:8080/api/teams/%API_KEY%";
    private String BASE_URL = "http://%DOMAIN%:8080/api/";
    private final String host;
    private final String apiKey;

    private static PlotSystemAPI instance;
    public static PlotSystemAPI getInstance() {
        if (instance == null) {
            instance = new PlotSystemAPI(PlotSystemTerra.getPlugin().getConfig().getString(ConfigPaths.API_KEY), PlotSystemTerra.getPlugin().getConfig().getString(ConfigPaths.API_URL));
        }
        return instance;
    }

    public PlotSystemAPI(String apiKey, String host) {
        this.apiKey = apiKey;
        this.host = host;
    }
    public JsonObject getDataForPSUrl(String suffix) throws Exception {
        return getDataForURL("/plotsystem/" + suffix);
    }

    public JsonObject getDataForURL(String suffix) throws Exception {
            JsonParser parser = new JsonParser();
            String url = BASE_URL + suffix;
            url = url.replace("%DOMAIN%", host);
            url = url.replace("%API_KEY%", apiKey);

            String json = get(new URL(url));
        return parser.parse(json).getAsJsonObject();
    }

    public HttpResponse makeRequest(String suffix, RequestMethod method, Map<String, Object> requestBodyMap) throws Exception {
        try {
            JsonParser parser = new JsonParser();
            String strUrl = BASE_URL + suffix;
            strUrl = strUrl.replace("%DOMAIN%", host);
            strUrl = strUrl.replace("%API_KEY%", apiKey);
            URL url = new URL(strUrl);

            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod(method.name());

            // Add headers to the request
            con.setRequestProperty("User-Agent", "Mozilla/5.0");
            con.setRequestProperty("Accept-Language", "en-US,en;q=0.5");
            con.setRequestProperty("Content-Type", "application/json");
            con.setRequestProperty("Accept", "application/json");

            // If it's a POST or PUT request, convert the map to JSON and add it as the request body
            if (method == RequestMethod.POST || method == RequestMethod.PUT || method == RequestMethod.DELETE) {
                con.setDoOutput(true);
                try (OutputStream os = con.getOutputStream()) {
                    JsonObject requestBody = new JsonObject();
                    for (Map.Entry<String, Object> entry : requestBodyMap.entrySet()) {
                        requestBody.addProperty(entry.getKey(), entry.getValue().toString());
                    }
                    byte[] input = requestBody.toString().getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }
            }

            int responseCode = con.getResponseCode();
            JsonElement response;
            try (BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()))) {
                String inputLine;
                StringBuilder content = new StringBuilder();
                while ((inputLine = in.readLine()) != null) {
                    content.append(inputLine);
                }

                if (content.length() == 0) {
                    // Handle empty response as needed
                    response = new JsonObject(); // Treat empty as a JsonObject
                } else {
                    // Parse the response as a JsonObject
                    response = parser.parse(content.toString()).getAsJsonObject();
                }
            }

            return new HttpResponse(response, responseCode);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }
    public String getTeamID(){
        try {
            return get(new URL(GET_TEAM_URL.replace("%DOMAIN%", host).replace("%API_KEY%", apiKey)));
        } catch (Exception e) {
            Bukkit.getLogger().log(Level.SEVERE, "An error occurred while getting the list of builders from the PlotSystem API!");
            e.printStackTrace();
        }
        return null;
    }

    // A function that returns the content of a GET Request from a given URL
    private String get(URL url) {
        try {
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("GET");

            // Add headers to the request
            con.setRequestProperty("User-Agent", "Mozilla/5.0");
            con.setRequestProperty("Accept-Language", "en-US,en;q=0.5");
            con.setRequestProperty("Content-Type", "application/json");
            con.setRequestProperty("Accept", "application/json");

            BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
            String inputLine;
            StringBuffer content = new StringBuffer();
            while ((inputLine = in.readLine()) != null) {
                content.append(inputLine);
            }
            in.close();

            return content.toString();
        } catch (Exception e) {
            Bukkit.getLogger().log(Level.SEVERE, "An error occurred while performin a GET request to the PlotSystem API!");
            e.printStackTrace();
        }

        return null;
    }

}
