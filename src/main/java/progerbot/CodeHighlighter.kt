package progerbot

import com.google.appengine.api.urlfetch.HTTPHeader
import gui.ava.html.image.generator.HtmlImageGenerator
/*import org.apache.http.NameValuePair
import org.apache.http.client.ClientProtocolException
import org.apache.http.client.entity.UrlEncodedFormEntity
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.ContentType
import org.apache.http.entity.mime.MultipartEntityBuilder
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.message.BasicNameValuePair*/
import com.google.appengine.api.urlfetch.HTTPMethod
import com.google.appengine.api.urlfetch.HTTPRequest
import com.google.appengine.api.urlfetch.URLFetchServiceFactory
import org.apache.http.client.ClientProtocolException
import org.apache.http.entity.ContentType
import org.apache.http.entity.mime.MultipartEntityBuilder
import org.apache.http.impl.client.HttpClientBuilder
import java.io.*
import java.net.URL
import java.util.*
import javax.imageio.ImageIO


public object CodeHighlighter {
    private val PYGMENTS_URL = "http://pygments.simplabs.com/"
    private val charset = "UTF-8"
    /**
     * HTML prefix containing styles for highlighted text.
     */
    private val prefix : String
    /**
     * HTML suffix of a highlighted text.
     */
    private val suffix : String

    init {
        try {
            // loading token from locally stored file
            val prop = Properties()
            val inputStream = MainServlet::class.java.
                    classLoader.getResourceAsStream("res.properties")
            prop.load(inputStream)
            prefix = prop.getProperty("json.highlightPrefix")
            suffix = prop.getProperty("json.highlightSuffix")

        }
        catch (e : IOException) {
            e.printStackTrace()
            prefix = ""
            suffix = ""
        }
    }

    /**
     * Adds styles (colors) to pygmentized code.
     */
    private fun addStyles(highlightedCode : String) = prefix + highlightedCode + suffix

    public fun manageCodeHighlightRequest(language : String, content : String, chatId : String, apiUrl : String) : Boolean {
        // creating and sending HTTP request to Pygments
        val pygmentsPost = HTTPRequest(URL(PYGMENTS_URL), HTTPMethod.POST)
        pygmentsPost.payload = "lang=$language&code=$content".toByteArray(charset)
        val pygmentsResponse = URLFetchServiceFactory.getURLFetchService().fetch(pygmentsPost)
        if (pygmentsResponse.responseCode != 200) {
            Logger.println("[ERROR] Could not pygmentize code for chat $chatId")
            return false
        }
        // generating and obtaining image out of colored code
        val imageGenerator = HtmlImageGenerator()
        val coloredCode = addStyles(pygmentsResponse.content.toString())
        imageGenerator.loadHtml(coloredCode)
        // converting buffered image to byte array for sending
        val baos = ByteArrayOutputStream()
        ImageIO.write(imageGenerator.bufferedImage, "png", baos)
        val imageByteArray = baos.toByteArray()
        // creating Telegram POST request
        val telegramPost = HTTPRequest(URL(apiUrl + "/sendPhoto"), HTTPMethod.POST)
        val entity = (MultipartEntityBuilder.create()
                .addTextBody("chat_id", chatId)
                .addBinaryBody("photo", imageByteArray, ContentType.MULTIPART_FORM_DATA, "image.png"))
                .build()
        val bos = ByteArrayOutputStream()
        entity.writeTo(bos)
        val body = bos.toByteArray()
        // extract multipart boundary (body starts with --boundary\r\n)
        var boundary = BufferedReader(StringReader(String(body))).readLine()
        boundary = boundary.substring(2, boundary.length)
        // adding multipart header and body
        telegramPost.addHeader(HTTPHeader("Content-type", "multipart/form-data; boundary=" + boundary))
        telegramPost.payload = body
        // sending image back to user
        val telegramResponse = URLFetchServiceFactory.getURLFetchService().fetch(pygmentsPost)
        return telegramResponse.responseCode == 200
    }

    /*private fun addMultipartBodyToRequest(entity : MultipartEntity, req : HTTPRequest) {

        /*
         * turn Entity to byte[] using ByteArrayOutputStream
         */
        val bos = ByteArrayOutputStream()
        entity.writeTo(bos)
        val body = bos.toByteArray()

        /*
         * extract multipart boundary (body starts with --boundary\r\n)
         */
        var boundary = BufferedReader(StringReader(String(body))).readLine()
        boundary = boundary.substring(2, boundary.length)

        /*
         * add multipart header and body
         */
        req.addHeader(HTTPHeader("Content-type", "multipart/form-data; boundary=" + boundary))
        req.setPayload(body)
    }*/
}