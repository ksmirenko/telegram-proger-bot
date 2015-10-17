package progerbot

import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.io.InputStreamReader
import java.net.URL
import java.nio.charset.Charset
import java.util.Properties

object MainServlet {
    private var updateId = 0

    private val isLogging = true
    private val isTokenHardcoded = false
    private val apiUrl = "https://api.telegram.org/bot"
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

    fun readJsonFromUrl(url : String) : JSONObject {
        val inputStream = URL(url).openStream()
        try {
            val rd = InputStreamReader(inputStream, Charset.forName("UTF-8"))
            val sb = StringBuilder()
            rd.forEachLine { sb.append(it) }
            val json = JSONObject(sb.toString())
            return json
        } finally {
            inputStream.close()
        }
    }

    //gets a message object and handles it
    fun messageHandler(json : JSONObject) {
        val textMessage : String = json.getJSONObject("message").getString("text")
        val chatId = Integer.toString(json.getJSONObject("message").getJSONObject("chat").getInt("id"))
        val response : JSONObject
        when {
            textMessage.startsWith("/help") -> {
                // TODO: make a pretty help message
                response = readJsonFromUrl("$url/sendmessage?chat_id=$chatId&text=Supported commands:  /help  /start")
            }
            textMessage.startsWith("/start") -> {
                response = readJsonFromUrl("$url/sendmessage?chat_id=$chatId&text=I`m Intelligent Proger Bot. Let`s start!")
            }
            textMessage.startsWith("/highlight ") -> {
                val splitMessage = textMessage.split(" ".toRegex(), 3)
                CodeHighlighter.sendPost(splitMessage[1], splitMessage[2], chatId, url)
            }
            else -> {
                response = readJsonFromUrl("$url/sendmessage?chat_id=$chatId&text=$textMessage")
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
            val json = readJsonFromUrl(url + "/getupdates?offset=" + Integer.toString(updateId) + "&timeout=60")
            logprintln(json.toString())
            if (json.getBoolean("ok")) {
                var counter = 0
                val messageArray = json.getJSONArray("result")
                val messageArrayLength = messageArray.length()
                while (counter < messageArrayLength) {
                    try {
                        messageHandler(messageArray.getJSONObject(counter))
                    }
                    catch (e : Exception) {
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