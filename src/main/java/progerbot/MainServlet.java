package progerbot;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Properties;

public class MainServlet {
    private static int updateId = 0;

    private static final Boolean isTokenHardcoded = false;
    private static final String apiUrl = "https://api.telegram.org/bot";
    private static String url = "";

    static {
        if (!isTokenHardcoded) {
            // loading token from locally stored file
            Properties prop = new Properties();
            String token = "";
            try {

                InputStream inputStream =
                        MainServlet.class.getClassLoader().getResourceAsStream("auth.properties");

                prop.load(inputStream);
                token = prop.getProperty("json.token");

            } catch (IOException e) {
                e.printStackTrace();
            }
            url = apiUrl + token;
        }
    }

    private static String readAll(Reader rd) throws IOException {
        StringBuilder sb = new StringBuilder();
        int cp;
        while ((cp = rd.read()) != -1) {
            sb.append((char) cp);
        }
        return sb.toString();
    }

    public static JSONObject readJsonFromUrl(String url) throws IOException, JSONException {
        InputStream is = new URL(url).openStream();
        try {
            BufferedReader rd = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")));
            String jsonText = readAll(rd);
            JSONObject json = new JSONObject(jsonText);
            return json;
        } finally {
            is.close();
        }
    }

    //gets a message object and handles it
    public static void messageHandler(JSONObject json) throws IOException, JSONException, Exception {
        String textMessage = json.getJSONObject("message").getString("text");
        String chatId = Integer.toString(json.getJSONObject("message").getJSONObject("chat").getInt("id"));
        JSONObject response;
        if (textMessage.startsWith("/help")) {
            response = readJsonFromUrl(url + "/sendmessage?" + "chat_id=" + chatId + "&" +
                    "text=" + "Supported commands:  /help  /start");
        }
        else if (textMessage.startsWith("/start")) {
            response = readJsonFromUrl(url + "/sendmessage?" + "chat_id=" + chatId + "&" +
                    "text=" + "I`m Intelligent Proger Bot. Let`s start!");
        }
        else if (textMessage.startsWith("/highlight ")) {
            String[] splitMessage = textMessage.split(" ", 3);
            CodeHighlighter.sendPost(splitMessage[1], splitMessage[2], chatId, url);
        }
        else {
            response = readJsonFromUrl(url + "/sendmessage?" + "chat_id=" + chatId + "&" +
                    "text=" + textMessage);
        }
    }

    private static void setNewUpdateId(JSONObject json) throws JSONException {
        updateId = json.getInt("update_id") + 1;
    }

    public static void main(String[] args) throws IOException, JSONException, Exception {
        while (true) {
            JSONObject json = readJsonFromUrl(url + "/getupdates" + "?offset=" + Integer.toString(updateId)
                    + "&timeout=60");
            System.out.println(json.toString());

            if (json.getBoolean("ok")) {
                int counter = 0;
                JSONArray messageArray = json.getJSONArray("result");
                int messageArrayLength = messageArray.length();
                while (counter < messageArrayLength) {
                    messageHandler(messageArray.getJSONObject(counter));
                    counter++;
                }
                if (counter > 0) setNewUpdateId(messageArray.getJSONObject(counter - 1));
            }
        }
    }
}