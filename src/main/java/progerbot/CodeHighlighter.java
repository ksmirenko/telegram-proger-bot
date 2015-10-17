package progerbot;

import java.io.*;
import java.util.List;
import java.util.ArrayList;
import java.util.Properties;

import gui.ava.html.image.generator.HtmlImageGenerator;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.HttpEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.impl.client.CloseableHttpClient;

import java.io.File;
import java.io.IOException;


public class CodeHighlighter {
    private final String USER_AGENT = "Mozilla/5.0";

    private static String addStyles(String highlightedCode) {
        try {
            // loading token from locally stored file
            Properties prop = new Properties();
            InputStream inputStream =
                    MainServlet.class.getClassLoader().getResourceAsStream("code-highlight-styles.properties");
            prop.load(inputStream);
            String prefix = prop.getProperty("json.prefix");
            String suffix = prop.getProperty("json.suffix");
            return prefix + highlightedCode + suffix;

        } catch (IOException e) {
            e.printStackTrace();
            return "";
        }
    }

    public static void sendPost(String language, String content, String chatId, String apiUrl) throws Exception {

        String url = "http://pygments.simplabs.com/";

        org.apache.http.client.HttpClient client = new DefaultHttpClient();
        HttpPost post = new HttpPost(url);

        List<NameValuePair> urlParameters = new ArrayList<NameValuePair>();
        urlParameters.add(new BasicNameValuePair("lang", language));
        urlParameters.add(new BasicNameValuePair("code", content));
        post.setEntity(new UrlEncodedFormEntity(urlParameters));

        HttpResponse response = client.execute(post);
        System.out.println("\nSending 'POST' request to URL : " + url);
        System.out.println("Post parameters : " + post.getEntity());
        System.out.println("Response Code : " +
                response.getStatusLine().getStatusCode());

        BufferedReader rd = new BufferedReader(
                new InputStreamReader(response.getEntity().getContent()));

        StringBuffer result = new StringBuffer();
        String line = "";
        while ((line = rd.readLine()) != null) {
            result.append(line);
            result.append("\n");
        }
        result.deleteCharAt(result.length() - 1);

        System.out.println(result.toString());

        HtmlImageGenerator imageGenerator = new HtmlImageGenerator();
        imageGenerator.loadHtml(addStyles(result.toString()));
        imageGenerator.saveAsImage("hello-world.png");

        HttpPost post1 = new HttpPost(apiUrl + "/sendPhoto");

        File photoFile = new File("hello-world.png");

        HttpEntity entity = (MultipartEntityBuilder.create()
                .addBinaryBody("photo", photoFile)).addTextBody("chat_id", chatId).build();

        post1.setEntity(entity);
        CloseableHttpClient client1 = HttpClientBuilder.create().build();
        HttpResponse response1 = null;
        try {
            response1 = client1.execute(post1);
        } catch (ClientProtocolException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}