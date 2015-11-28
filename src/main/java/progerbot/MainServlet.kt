package progerbot

import com.google.appengine.api.urlfetch.HTTPResponse
import com.google.gson.Gson
import com.google.gson.JsonParser
import org.json.JSONObject
import telegram.Message
import telegram.Update
import java.util.*
import java.util.concurrent.Executors
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

/**
 * Main servlet that handles requests to the bot.
 */
public class MainServlet : HttpServlet() {
    private val CHARSET = "UTF-8"
    /**
     * Bot's help message.
     */
    private val helpMessage : String
    /**
     * JSON parser.
     */
    private val gson = Gson()
    private var updatesOffset = 0
    /**
     * main thread pool for handling user messages in active mode
     */
    private val pool = Executors.newFixedThreadPool(8);


    init {
        // loading properties here
        // no try-catch here, cuz if this fails, the bot can't work anyway
        val prop = Properties()
        val inputStream = MainServlet::class.java.
                classLoader.getResourceAsStream("res.properties")
        prop.load(inputStream)
        helpMessage = prop.getProperty("json.helpMessage")
    }

    /**
     * Handles GET requests to this server.
     * Presumably requests are sent by user when he is redirected from StackOverflow.
     */
    public override fun doGet(req : HttpServletRequest, resp : HttpServletResponse) {
        try {
            val chatId = req.getParameter("state")
            val code = req.getParameter("code")
            val text = if (stackOverflow.StackOverflow.tryConfirmCode(chatId, code))
                "Your StackOverflow account was connected successfully!"
            else "Connection to StackOverflow failed"
            TelegramApi.sendText(chatId, text)
        }
        catch (e : Exception) {
            Logger.println(e)
        }
    }

    /**
     * Handles POST requests to this servlet.
     * Presumably requests are sent by Telegram and contain messages from users.
     */
    public override fun doPost(req : HttpServletRequest, resp : HttpServletResponse) {
        // if there is a p2i_token, the request is a callback from Page2Image
        val p2iToken = req.getParameter("p2i_token")
        if (p2iToken != null) {
            resp.contentType = "text/html"
            resp.setStatus(200)
            val result = req.getParameter("result")
            Logger.println(result)
            val jsonObject = JsonParser().parse(result).asJsonObject
            Highlighter.handlePreparedImage(p2iToken, jsonObject.get("image_url").asString)
        }
        // else, the request is an authorization response from StackOverflow
        val update = parseClass(req, telegram.Update::class.java) as Update
        val msg = update.message
        if (msg != null)
            handleMessage(msg)
    }

    /**
     * Main loop which obtains new user messages in active mode
     */
    public fun main(args : Array<String>) {
        while (true) {
            // requesting new messges
            val response : HTTPResponse = TelegramApi.getUpdates(updatesOffset)
            Logger.println(response.toString())
            val json = JSONObject(response)
            if (response.responseCode == 200) {
                // successfully obtained messages; handling them one by one
                val messageArray = json.getJSONArray("result")
                val messageArrayLength = messageArray.length()
                var counter = 0
                while (counter < messageArrayLength) {
                    val messageJson = messageArray.getJSONObject(counter)
                    pool.execute {
                        try {
                            handleMessage(gson.fromJson(messageJson.toString(), telegram.Message::class.java))
                        }
                        catch (e : Exception) {
                            e.printStackTrace(System.err)
                        }
                    }
                    counter++
                }
                if (counter > 0) {
                    updatesOffset = messageArray.getJSONObject(counter - 1).getInt("update_id") + 1
                }
            }
        }
    }

    /**
     * Handles a message from Telegram user.
     * Main bot functionality is here.
     */
    private fun handleMessage(message : Message) {
        val chatId : String = message.chat.id.toString()
        var success = false
        val text = message.text
        if (text != null) {
            when {
                text.startsWith("/help") -> {
                    success = TelegramApi.sendText(chatId, helpMessage)
                }
                text.startsWith("/start") -> {
                    success = TelegramApi.sendText(chatId, "I'm Intelligent Proger Bot. Let's start!")
                }
                text.startsWith("/highlight") -> {
                    success = Highlighter.handleRequest(chatId, text)
                }
                text.startsWith("/stackoverflowconnect") -> {
                    success = TelegramApi.sendText(chatId, "Open this link to authorize the bot: " +
                            stackOverflow.StackOverflow.getAuthUrl(chatId))
                }
                text.startsWith("/stackoverflowlogout") -> {
                    val answer =
                            if (stackOverflow.StackOverflow.logOut(chatId)) "Log out performed!"
                            else "Yor are not authorized!"
                    success = TelegramApi.sendText(chatId, answer)
                }
                text.startsWith("/stackoverflowsearch ") -> {
                    val splitMessage = text.split(" ".toRegex(), 2)
                    success = TelegramApi.sendText(chatId, stackOverflow.StackOverflow.search(splitMessage[1]))
                }
                text.startsWith("/stackoverflowsgetnotifications") -> {
                    success = TelegramApi.sendText(chatId, stackOverflow.StackOverflow.getUnreadNotifications(chatId))
                }
                else -> {
                    success = TelegramApi.sendText(chatId, "NO U $text")
                }
            }
        }
        val document = message.document
        if (document != null) {
            Highlighter.handleDocument(chatId, document.file_id)
        }
        Logger.println(success.toString())
    }

    /**
     * Parses POST data of [req] into an object of type [clazz].
     */
    private fun parseClass(req : HttpServletRequest, clazz : Class<out Any>) : Any {
        val rd = req.reader
        val sb = StringBuilder()
        rd.forEachLine { sb.append(it); sb.append("\n") }
        return gson.fromJson(sb.toString(), clazz)
    }
}
