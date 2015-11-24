package progerbot

import com.google.appengine.api.urlfetch.HTTPMethod
import com.google.appengine.api.urlfetch.HTTPResponse
import com.google.appengine.api.urlfetch.URLFetchServiceFactory
import com.google.gson.Gson
import org.json.JSONObject
import telegram.Message
import telegram.Update
import java.net.URL
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
    private val isTokenHardcoded = false
    /**
     * URL of API with token.
     */
    private val telegramApiUrl : String
    private val telegramToken : String
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

    // TODO: remove old requests
    /**
     * Pairs (ID, language) containing information about chats that have asked to highlight a file.
     */
    private val requestsToHighlightFile = LinkedList<Pair<String, String>>()

    init {
        // loading properties here
        // no try-catch here, cuz if this fails, the bot can't work anyway
        val prop = Properties()
        val inputStream = MainServlet::class.java.
                classLoader.getResourceAsStream("res.properties")
        prop.load(inputStream)
        helpMessage = prop.getProperty("json.helpMessage")
        if (!isTokenHardcoded) {
            // loading token from locally stored file
            val inputStreamToken = MainServlet::class.java.classLoader.getResourceAsStream("auth.properties")
            prop.load(inputStreamToken)
            telegramToken = prop.getProperty("json.telegramBotToken")
        }
        else {
            telegramToken = "" // hardcoded token would be here
        }
        telegramApiUrl = "https://api.telegram.org/bot$telegramToken"
    }

    /**
     * Handles GET requests to this server.
     * Presumably requests are sent by user when he is redirected from StackOverflow.
     */
    public override fun doGet(req : HttpServletRequest, resp : HttpServletResponse) {
        /*resp.contentType = "text/plain"
        resp.writer.println("I am alive!")*/
        try {
            val chatId = req.getParameter("state")
            val code = req.getParameter("code")
            val text = if (stackOverflow.StackOverflow.tryConfirmCode(chatId, code))
                "Your StackOverflow account was connected successfully!"
            else "Connection to StackOverflow failed"
            sendTextMessage(chatId, text)
        }
        catch (e : Throwable) {
            Logger.println(e.message ?: "Cannot handle doGet : Unknown exception")
        }
    }

    /**
     * Handles POST requests to this servlet.
     * Presumably requests are sent by Telegram and contain messages from users.
     */
    public override fun doPost(req : HttpServletRequest, resp : HttpServletResponse) {
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
            val response : HTTPResponse = HttpRequests.simpleRequest("$telegramApiUrl/getupdates",
                    HTTPMethod.GET,
                    "offset=${Integer.toString(updatesOffset)}&timeout=60"
            )
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
                            Logger.println("Request handling failed because of ${e.toString()}")
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
                    success = sendTextMessage(chatId, helpMessage)
                }
                text.startsWith("/start") -> {
                    success = sendTextMessage(chatId, "I'm Intelligent Proger Bot. Let's start!")
                }
                text.startsWith("/highlight ") -> {
                    val splitMessage = text.split(" ".toRegex(), 3)
                    try {
                        success = CodeHighlighter.manageCodeHighlightRequest(
                                splitMessage[1], splitMessage[2], chatId, telegramApiUrl)
                    }
                    catch (e : Exception) {
                        sendTextMessage(
                                chatId,
                                "I couldn't proceed your request because of:\n${e.toString()}\nSorry for that."
                        )
                        success = false
                    }
                }
                text.startsWith("/highlightfile ") -> {
                    val splitMessage = text.split(" ".toRegex(), 2)
                    val requestsIterator = requestsToHighlightFile.iterator()
                    // TODO: optimize it using Set
                    while (requestsIterator.hasNext())
                        if (requestsIterator.next().first == chatId) {
                            requestsIterator.remove()
                            break
                        }
                    requestsToHighlightFile.addFirst(Pair(chatId, splitMessage[1]))
                }
                text.startsWith("/stackoverflowconnect") -> {
                    success = sendTextMessage(chatId, "Open this link to authorize the bot: " +
                            stackOverflow.StackOverflow.getAuthUrl(chatId))
                }
                text.startsWith("/stackoverflowsearch ") -> {
                    val splitMessage = text.split(" ".toRegex(), 2)
                    success = sendTextMessage(chatId, stackOverflow.StackOverflow.search(splitMessage[1]))
                }
                else -> {
                    success = sendTextMessage(chatId, "NO U $text")
                }
            }
        }
        val document = message.document
        if (document != null) {
            var language = ""
            var fileRequestedToHighlight = false
            val requestsIterator = requestsToHighlightFile.iterator()
            // TODO: optimize it using a Set
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
                val codeRequestResponse = URLFetchServiceFactory.getURLFetchService().fetch(
                        URL("$telegramApiUrl/getFile?file_id=${document.file_id}"))
                if (codeRequestResponse.responseCode == 200) {
                    val responseContent = codeRequestResponse.content.toString(CHARSET)
                    val file = gson.fromJson(responseContent, telegram.File::class.java)
                    val sourceCode = URLFetchServiceFactory.getURLFetchService().fetch(
                            URL("https://api.telegram.org/file/bot$telegramToken/${file.file_path}")
                    ).content.toString(CHARSET)
                    success = CodeHighlighter.manageCodeHighlightRequest(language, sourceCode, chatId, telegramApiUrl)
                }
            }
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

    // TODO: send special symbols, such as '&'
    /**
     * Sends [text] to a Telegram chat with id=[chatId].
     */
    private fun sendTextMessage(chatId : String, text : String) : Boolean {
        try {
            Logger.println("Sending message: {$text}")
            val resp = HttpRequests.simpleRequest("$telegramApiUrl/sendMessage", HTTPMethod.POST, "chat_id=$chatId&text=$text")
            return resp.responseCode == 200
        }
        catch (e : Exception) {
            if (e.message != null)
                Logger.println(e.message!!)
            return false
        }
    }
}