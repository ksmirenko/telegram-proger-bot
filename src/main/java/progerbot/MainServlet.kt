package progerbot

import org.json.JSONObject
import java.io.IOException
import java.io.InputStreamReader
import java.net.URL
import java.nio.charset.Charset
import java.util.*

object MainServlet {
    private val isTokenHardcoded = false
    private val apiUrl = "https://api.telegram.org/bot"
    // TODO: make this less ugly
    const private val helpMessage = "Hi!%20I'm%20Intelligent%20Proger%20Bot.%20Here's%20the%20" +
        "what%20I%20can%20do:%0A%2Fstart%20-%20start%20working%20with%20me.%20In%20fact,%20you" +
        "%20already%20have.%0A%2Fhelp%20-%20show%20this%20help.%0A%2Fhighlight%20<language>" +
        "<code>%20-%20highlight%20some%20code%20(if%20I%20know%20the%20language,%20of%20course)." +
        "%0A%0AAlso,%20don't%20be%20mad%20if%20I'm%20offline.%20I'm%20currently%20under%20development."

    private var updateId = 0
    private val isLogging = true
    private var url = ""

    init {
        if (!isTokenHardcoded) {
            // loading token from locally stored file
            val prop = Properties()
            var token = ""
            try {
                val inputStream = MainServlet::class.java.classLoader.getResourceAsStream("auth.properties")
                prop.load(inputStream)
                token = prop.getProperty("json.token")

            } catch (e : IOException) {
                e.printStackTrace()
            }

            url = apiUrl + token
        }
    }

    fun fetchUrlResponse(url : String) : String {
        val inputStream = URL(url).openStream()
        try {
            val rd = InputStreamReader(inputStream, Charset.forName("UTF-8"))
            val sb = StringBuilder()
            rd.forEachLine { sb.append(it) }
            return sb.toString()
        } finally {
            inputStream.close()
        }
    }

    // handles a message from Telegram user
    fun handleMessage(json : JSONObject) {
        val textMessage : String = json.getJSONObject("message").getString("text")
        val chatId = Integer.toString(json.getJSONObject("message").getJSONObject("chat").getInt("id"))
        // TODO: make them all HttpResponses
        val responseCode : Boolean
        when {
            textMessage.startsWith("/help") -> {
                val response = fetchUrlResponse("$url/sendmessage?chat_id=$chatId&text=$helpMessage")
                val json = JSONObject(response)
                responseCode = json.getBoolean("ok")
            }
            textMessage.startsWith("/start") -> {
                val response = fetchUrlResponse("$url/sendmessage?chat_id=$chatId&text=I'm Intelligent Proger Bot. Let's start!")
                val json = JSONObject(response)
                responseCode = json.getBoolean("ok")
            }
            textMessage.startsWith("/highlight ") -> {
                val splitMessage = textMessage.split(" ".toRegex(), 3)
                responseCode = CodeHighlighter.manageCodeHighlightRequest(splitMessage[1], splitMessage[2], chatId, url)
            }
            else -> {
                val response = fetchUrlResponse("$url/sendmessage?chat_id=$chatId&text=NO%20U%20$textMessage")
                val json = JSONObject(response)
                responseCode = json.getBoolean("ok")
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
            val telegramResponse = fetchUrlResponse(url + "/getupdates?offset=" + Integer.toString(updateId) + "&timeout=60")
            logprintln(telegramResponse)
            val json = JSONObject(telegramResponse)
            if (json.getBoolean("ok")) {
                var counter = 0
                val messageArray = json.getJSONArray("result")
                val messageArrayLength = messageArray.length()
                while (counter < messageArrayLength) {
                    try {
                        handleMessage(messageArray.getJSONObject(counter))
                    } catch (e : Exception) {
                        logprintln("Request handling failed because of ${e.toString()}")
                    }
                    counter++
                }
                if (counter > 0) {
                    updateId = messageArray.getJSONObject(counter - 1).getInt("update_id") + 1
                }
            }
        }
    }
}