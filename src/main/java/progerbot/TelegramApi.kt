package progerbot

import com.google.appengine.api.urlfetch.*
import com.google.gson.Gson
import com.google.gson.JsonParser
import org.apache.http.entity.ContentType
import org.apache.http.entity.mime.MultipartEntityBuilder
import telegram.File
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.StringReader
import java.net.URL
import java.net.URLEncoder
import java.util.*

/**
 * Performs all interaction with Telegram Bot API.
 */
object TelegramApi {
    private val CHARSET = "UTF-8"
    private val IS_TOKEN_HARDCODED = false
    /**
     * URL of API with token.
     */
    private val telegramApiUrl : String
    private val telegramToken : String

    init {
        if (!IS_TOKEN_HARDCODED) {
            // loading token from locally stored file
            val prop = Properties()
            //val inputStreamToken = MainServlet::class.java.classLoader.getResourceAsStream("auth.properties")
            val inputStreamToken = javaClass<MainServlet>().getClassLoader.getResourceAsStream("auth.properties")
            prop.load(inputStreamToken)
            telegramToken = prop.getProperty("json.telegramBotToken")
        }
        else {
            telegramToken = "" // hardcoded token would be here
        }
        telegramApiUrl = "https://api.telegram.org/bot$telegramToken"
    }

    fun sendText(chatId : String, text : String) : Boolean {
        try {
            Logger.println("Sending message: {$text}")
            val resp = HttpRequests.simpleRequest(
                    "$telegramApiUrl/sendMessage",
                    HTTPMethod.POST,
                    "chat_id=$chatId&text=${URLEncoder.encode(text, CHARSET)}")
            return resp.responseCode == 200
        }
        catch (e : Exception) {
            if (e.message != null)
                Logger.println(e.message!!)
            return false
        }
    }

    fun sendImage(chatId : String, imageByteArray : ByteArray) : HTTPResponse {
        // creating Telegram POST request
        val telegramPost = HTTPRequest(URL("$telegramApiUrl/sendPhoto"), HTTPMethod.POST)
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
        return URLFetchServiceFactory.getURLFetchService().fetch(telegramPost)
    }

    fun getFile(fileId : String) : File? {
        try {
            val codeRequestResponse : HTTPResponse = URLFetchServiceFactory.getURLFetchService().fetch(
                    URL("$telegramApiUrl/getFile?file_id=$fileId"))
            if (codeRequestResponse.responseCode == 200) {
                val jsonObject = JsonParser().parse(codeRequestResponse.content.toString(CHARSET)).asJsonObject
                val file = Gson().fromJson(jsonObject.get("result"), telegram.File::class.java)
                return file
            }
            else return null
        }
        catch (e : Exception) {
            Logger.println(e)
            return null
        }
    }

    fun getUpdates(offset : Int = 0) = HttpRequests.simpleRequest("$telegramApiUrl/getupdates",
            HTTPMethod.GET,
            "offset=${Integer.toString(offset)}&timeout=60"
    )

    /**
     * Downloads a file from Telegram server.
     */
    fun downloadTextFile(filePath : String) : String =
            URLFetchServiceFactory.getURLFetchService().fetch(
                    URL("https://api.telegram.org/file/bot$telegramToken/$filePath")
            ).content.toString(CHARSET)
}