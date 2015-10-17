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

    // HTTP POST request
    private void sendPost() throws Exception {

        //String url = "https://selfsolve.apple.com/wcResults.do";
        String url = "http://pygments.simplabs.com/";

        org.apache.http.client.HttpClient client = new DefaultHttpClient();
        HttpPost post = new HttpPost(url);

        // add header
        post.setHeader("User-Agent", USER_AGENT);

        List<NameValuePair> urlParameters = new ArrayList<NameValuePair>();

        urlParameters.add(new BasicNameValuePair("lang", "java"));
        urlParameters.add(new BasicNameValuePair("code", "if (foo) then\n\tbar();\nelse\n\treturn;"));

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
        //imageGenerator.loadHtml("<b>Hello World!</b> Please goto <a title=\"Goto Google\" href=\"http://www.google.com\">Google</a>.");
        imageGenerator.loadHtml(addStyles(result.toString()));
        imageGenerator.saveAsImage("hello-world.png");

        //imageGenerator.saveAsHtmlWithMap("hello-world.html", "hello-world.png");

        //BufferedImage img = imageGenerator.getBufferedImage();
        //File f = new File("TEST_PNG_PICTURE.png");
        //ImageIO.write(img, "PNG", f);

/*

        BufferedImage img = null;
        try {
            img = ImageIO.read(new File("pic.png"));
        } catch (IOException e) {
            e.printStackTrace();
        }

        HttpPost post1 = new HttpPost("server_address");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            ImageIO.write(img, "png", baos);
        } catch (IOException e) {
            e.printStackTrace();
        }

        HttpEntity entity = MultipartEntityBuilder.create()
                .addBinaryBody("file_attach", baos.toByteArray()).build();

        post1.setEntity(entity);
        HttpClient client1 = HttpClientBuilder.create().build();
        HttpResponse response1 = null;
        try {
            response1 = client1.execute(post1);
        } catch (ClientProtocolException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }*/
    }

    public static void main(String[] args) throws Exception {

        CodeHighlighter http = new CodeHighlighter();

        //System.out.println("Testing 1 - Send Http GET request");
        //http.sendGet();

        System.out.println("\nTesting 2 - Send Http POST request");
        http.sendPost();

    }
}