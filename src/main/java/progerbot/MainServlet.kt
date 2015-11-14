package progerbot

import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.mime.MultipartEntityBuilder
import org.apache.http.impl.client.HttpClientBuilder
import org.json.JSONObject
import java.io.InputStreamReader
import java.net.URL
import java.nio.charset.Charset
import java.util.*

object MainServlet {
    private val telegramUrl = "https://api.telegram.org/bot"
    private val isTokenHardcoded = false
    private val isLogging = true
    private val apiUrl : String
    private val helpMessage : String
    private var token = "" // sometimes we need it separately from the url

    private var updatesOffset = 0

    //contains pairs(ID, progrLang) of IDs of chats there bot was requested to highlight file and requested languages
    //TODO: remove old requests
    private val requestsToHighlightFile = LinkedList<Pair<String, String>>()

    init {
        // loading properties here
        // removed try-catch here, cuz if this fails, the bot can't work anyway
        val prop = Properties()
        val inputStream = MainServlet::class.java.
                classLoader.getResourceAsStream("res.properties")
        prop.load(inputStream)
        helpMessage = prop.getProperty("json.helpMessage")
        if (!isTokenHardcoded) {
            // loading token from locally stored file
            val inputStreamToken = MainServlet::class.java.classLoader.getResourceAsStream("auth.properties")
            prop.load(inputStreamToken)
            token = prop.getProperty("json.token")
        }
        apiUrl = telegramUrl + token
    }

    fun fetchUrlResponse(url : String, separateLines : Boolean = false) : String {
        val inputStream = URL(url).openStream()
        try {
            val rd = InputStreamReader(inputStream, Charset.forName("UTF-8"))
            val sb = StringBuilder()
            if (separateLines) rd.forEachLine { sb.append(it); sb.append("\n") }
            else rd.forEachLine { sb.append(it) }
            return sb.toString()
        }
        finally {
            inputStream.close()
        }
    }

    fun sendTextMessage(chatId : String, text : String) : Boolean {
        val post = HttpPost(apiUrl + "/sendMessage")
        post.entity = (MultipartEntityBuilder.create().
                addTextBody("chat_id", chatId).addTextBody("text", text)).build()
        val telegramClient = HttpClientBuilder.create().build()
        try {
            val telegramResponse = telegramClient.execute(post)
            return telegramResponse.statusLine.statusCode == 200
        }
        catch (e : Exception) {
            e.printStackTrace()
            return false
        }
        finally {
            telegramClient.close()
        }
    }

    // handles a message from Telegram user
    fun handleMessage(jsonRequest : JSONObject) {
        val chatId : String = Integer.toString(jsonRequest.getJSONObject("message").getJSONObject("chat").getInt("id"))
        val success : Boolean
        when {
            jsonRequest.getJSONObject("message").has("text") -> {
                val textMessage = jsonRequest.getJSONObject("message").getString("text")
                when {
                    textMessage.startsWith("/help") -> {
                        success = sendTextMessage(chatId, helpMessage)
                    }
                    textMessage.startsWith("/start") -> {
                        success = sendTextMessage(chatId, "I'm Intelligent Proger Bot. Let's start!")
                    }
                    textMessage.startsWith("/highlight ") -> {
                        val splitMessage = textMessage.split(" ".toRegex(), 3)
                        success = CodeHighlighter.manageCodeHighlightRequest(splitMessage[1], splitMessage[2], chatId, apiUrl)
                    }
                    textMessage.startsWith("/highlightFile ") -> {
                        val splitMessage = textMessage.split(" ".toRegex(), 2)
                        val requestsIterator = requestsToHighlightFile.iterator()
                        // TODO: optimize it using Set
                        while (requestsIterator.hasNext())
                            if (requestsIterator.next().first == chatId) {
                                requestsIterator.remove()
                                break
                            }
                        requestsToHighlightFile.addFirst(Pair(chatId, splitMessage[1]))
                    }
                    else -> {
                        success = sendTextMessage(chatId, "NO U $textMessage")
                    }
                }
            }
            jsonRequest.getJSONObject("message").has("document") -> {
                var language = ""
                var fileRequestedToHighlight = false
                val requestsIterator = requestsToHighlightFile.iterator()
                // TODO: optimize it using Set
                while (requestsIterator.hasNext()) {
                    val pair = requestsIterator.next()
                    if (pair.first == chatId) {
                        fileRequestedToHighlight = true
                        language = pair.second
                        requestsIterator.remove()
                        break
                    }
                }
                if (fileRequestedToHighlight) {
                    val response = fetchUrlResponse("$apiUrl/getFile?file_id=" +
                            "${jsonRequest.getJSONObject("message").getJSONObject("document").getString("file_id")}")
                    val json = JSONObject(response)
                    if (json.getBoolean("ok")) {
                        val code = fetchUrlResponse("https://api.telegram.org/file/bot" +
                                "$token/${json.getJSONObject("result").getString("file_path")}", true)
                        success = CodeHighlighter.manageCodeHighlightRequest(language, code, chatId, apiUrl)
                    }
                }
            }
        }
    }

    private fun logprintln(s : String) {
        if (isLogging)
            println(s)
    }

    @JvmStatic fun main(args : Array<String>) {
        // main loop which obtains new user messages
        while (true) {
            val telegramResponse = fetchUrlResponse(apiUrl + "/getupdates?offset=" + Integer.toString(updatesOffset) + "&timeout=60")
            logprintln(telegramResponse)
            val json = JSONObject(telegramResponse)
            if (json.getBoolean("ok")) {
                var counter = 0
                val messageArray = json.getJSONArray("result")
                val messageArrayLength = messageArray.length()
                while (counter < messageArrayLength) {
                    try {
                        handleMessage(messageArray.getJSONObject(counter))
                    }
                    catch (e : Exception) {
                        logprintln("Request handling failed because of ${e.toString()}")
                    }
                    counter++
                }
                if (counter > 0) {
                    updatesOffset = messageArray.getJSONObject(counter - 1).getInt("update_id") + 1
                }
            }
        }
    }
}