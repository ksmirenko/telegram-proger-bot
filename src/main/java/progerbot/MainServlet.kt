package progerbot

import com.google.appengine.api.urlfetch.HTTPMethod
import com.google.appengine.api.urlfetch.URLFetchServiceFactory
import com.google.gson.Gson
import telegram.Message
import telegram.Update
import java.net.URL
import java.util.*
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

/**
 * Main servlet that handles requests to the bot.
 */
public class MainServlet : HttpServlet() {
    private val telegramUrl = "https://api.telegram.org/bot"
    private val isTokenHardcoded = false
    private val apiUrl : String
    private val helpMessage : String
    private val token : String // sometimes we use it separately from the url
    private val charset = "UTF-8"

    private val gson = Gson()
    //private var updatesOffset = 0

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
            token = prop.getProperty("json.token")
        }
        else {
            token = "" // hardcoded token would be here
        }
        apiUrl = telegramUrl + token
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
            val text = if (stackOverflow.StackOverflowAuthData.tryConfirmCode(chatId, code))
                "Your StackOverflow account was connected successfully!"
            else "Connection to StackOverflow failed"
            sendTextMessage(chatId, text)
        } catch (e : Throwable) {
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
     * Parses POST data of [req] into an object of type [clazz].
     */
    private fun parseClass(req : HttpServletRequest, clazz : Class<out Any>) : Any {
        val rd = req.reader
        val sb = StringBuilder()
        rd.forEachLine { sb.append(it); sb.append("\n") }
        return gson.fromJson(sb.toString(), clazz)
    }

    /**
     * Sends [text] to a Telegram chat with id=[chatId].
     */
    fun sendTextMessage(chatId : String, text : String) : Boolean {
        try {
            val resp = HttpRequests.simpleRequest("$apiUrl/sendMessage", HTTPMethod.POST, "chat_id=$chatId&text=$text")
            return resp.responseCode == 200
        }
        catch (e : Exception) {
            if (e.message != null)
                Logger.println(e.message!!)
            return false
        }
    }

    /**
     * Handles a message from Telegram user.
     * Main bot functionality is here.
     */
    fun handleMessage(message : Message) {
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
                                splitMessage[1], splitMessage[2], chatId, apiUrl)
                    }
                    catch (e : Exception) {
                        sendTextMessage(
                                chatId,
                                "I couldn't proceed your request because of:\n${e.toString()}\nSorry for that."
                        )
                        success = false
                    }
                }
                text.startsWith("/highlightFile ") -> {
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
                text.startsWith("/StackOverflowConnect") -> {
                    success = sendTextMessage(chatId, "Open this link to authorize the bot: " +
                            stackOverflow.StackOverflowAuthData.getAuthUrl(chatId))
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
                        URL("$apiUrl/getFile?file_id=${document.file_id}"))
                if (codeRequestResponse.responseCode == 200) {
                    val responseContent = codeRequestResponse.content.toString(charset)
                    val file = gson.fromJson(responseContent, telegram.File::class.java)
                    val sourceCode = URLFetchServiceFactory.getURLFetchService().fetch(
                            URL("https://api.telegram.org/file/bot$token/${file.file_path}")
                    ).content.toString(charset)
                    success = CodeHighlighter.manageCodeHighlightRequest(language, sourceCode, chatId, apiUrl)
                }
            }
        }
        Logger.println(success.toString())
    }

    /*public fun main(args : Array<String>) {
        // main loop which obtains new user messages
        while (true) {
            // requesting new messges
            val telegramResponse = HttpRequests.simpleRequest("$apiUrl/getupdates",
                    HTTPMethod.GET,
                    "offset=${Integer.toString(updatesOffset)}&timeout=60"
            )
            Logger.println(telegramResponse.toString())
            val json = JSONObject(telegramResponse)
            if (json.getBoolean("ok")) {
                // successfully obtained messages; handling them one by one
                val messageArray = json.getJSONArray("result")
                val messageArrayLength = messageArray.length()
                var counter = 0
                while (counter < messageArrayLength) {
                    val message = messageArray.getJSONObject(counter)
                    pool.execute {
                        try {
                            handleMessage(message)
                        }
                        catch (e : Exception) {
                            logprintln("Request handling failed because of ${e.toString()}")
                        }
                    }
                    counter++
                }
                if (counter > 0) {
                    updatesOffset = messageArray.getJSONObject(counter - 1).getInt("update_id") + 1
                }
            }
        }
    }*/
}