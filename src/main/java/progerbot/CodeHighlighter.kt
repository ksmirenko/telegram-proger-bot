package progerbot

import com.google.appengine.api.urlfetch.*
import com.google.gson.JsonParser
import gui.ava.html.image.generator.HtmlImageGenerator
import org.apache.http.entity.ContentType
import org.apache.http.entity.mime.MultipartEntityBuilder
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.StringReader
import java.net.URL
import java.util.*
import javax.imageio.ImageIO


public object CodeHighlighter {
    private val PYGMENTS_URL = "http://pygments.simplabs.com/"
    private val CHARSET = "UTF-8"
    /**
     * HTML prefix containing styles for highlighted text.
     */
    private val prefix : String
    /**
     * HTML suffix of a highlighted text.
     */
    private val suffix : String
    /**
     * A strategy for converting HTML to images.
     */
    private val imageObtainer : ImageObtainingStrategy = LibraryImageObtainingStrategy()

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
        pygmentsPost.payload = "lang=$language&code=$content".toByteArray(CHARSET)
        val pygmentsResponse = URLFetchServiceFactory.getURLFetchService().fetch(pygmentsPost)
        if (pygmentsResponse.responseCode != 200) {
            Logger.println("[ERROR] Could not pygmentize code for chat $chatId")
            return false
        }
        // obtaining image with colored code
        val imageByteArray = imageObtainer.getImageFromHtml(
                addStyles(pygmentsResponse.content.toString()),
                apiUrl,
                chatId
        )
        // FIXME: epic workaround
        if (imageByteArray.size == 1) {
            return true
        }
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

    private abstract class ImageObtainingStrategy() {
        public abstract fun getImageFromHtml(htmlContent : String, telegramApiUrl : String, chatId : String) : ByteArray
    }

    private class LibraryImageObtainingStrategy() : ImageObtainingStrategy() {
        override fun getImageFromHtml(htmlContent : String, telegramApiUrl : String, chatId : String) : ByteArray {
            val imageGenerator = HtmlImageGenerator()
            imageGenerator.loadHtml(htmlContent)
            val baos = ByteArrayOutputStream()
            ImageIO.write(imageGenerator.bufferedImage, "png", baos)
            return baos.toByteArray()
        }
    }

    private class ApiImageObtainingStrategy() : ImageObtainingStrategy() {
        private val API_URL = "http://api.page2images.com/html2image"
        private val apiKey : String

        init {
            // loading properties
            val prop = Properties()
            val inputStream = progerbot.MainServlet::class.java.
                    classLoader.getResourceAsStream("auth.properties")
            prop.load(inputStream)
            apiKey = prop.getProperty("json.pagetoimagesApiKey")
        }

        override fun getImageFromHtml(htmlContent : String, telegramApiUrl : String, chatId : String) : ByteArray {
            val response : HTTPResponse = HttpRequests.simpleRequest(
                    API_URL,
                    HTTPMethod.POST,
                    "p2i_html=$htmlContent&p2i_key=$apiKey&p2i_imageformat=jpg&p2i_fullpage=0"
            )
            val parser = JsonParser()
            val jsonObject = parser.parse(response.content.toString()).asJsonObject
            when (jsonObject.get("status").asString) {
                "processing" -> {
                    HttpRequests.simpleRequest(
                            "$telegramApiUrl/sendMessage",
                            HTTPMethod.POST,
                            "chat_id=$chatId&text=Your photo is being prepared now. But I won't give it to you, haha."
                    )
                }
                "finished" -> {
                    HttpRequests.simpleRequest(
                            "$telegramApiUrl/sendMessage",
                            HTTPMethod.POST,
                            "chat_id=$chatId&text=Here you are:\n${jsonObject.get("image_url").asString}"
                    )
                }

            }
            return ByteArray(1)
        }
    }
}