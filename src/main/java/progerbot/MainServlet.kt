package progerbot

import com.google.appengine.api.urlfetch.HTTPMethod
import com.google.appengine.api.urlfetch.HTTPRequest
import com.google.appengine.api.urlfetch.URLFetchServiceFactory
import com.google.gson.Gson
import telegram.Message
import telegram.Update
import java.net.URL
import java.util.*
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

public class MainServlet : HttpServlet() {
    private val telegramUrl = "https://api.telegram.org/bot"
    private val isTokenHardcoded = false
    private val apiUrl : String
    private val helpMessage : String
    private val token : String // sometimes we use it separately from the url
    private val charset = "UTF-8"
    private val isLogging = false
    private val gson = Gson()

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
        else {
            token = "" // hardcoded token would be here
        }
        apiUrl = telegramUrl + token
    }

    public override fun doGet(req : HttpServletRequest, resp : HttpServletResponse) {
        resp.contentType = "text/plain"
        resp.writer.println("I am alive!")
    }

    override fun doPost(req : HttpServletRequest, resp : HttpServletResponse) {
        val update = parseClass(req, telegram.Update::class.java) as Update
        val msg = update.message
        if (msg != null)
            handleMessage(msg)
    }

    private fun parseClass(req : HttpServletRequest, clazz : Class<out Any>) : Any {
        val rd = req.reader
        val sb = StringBuilder()
        rd.forEachLine { sb.append(it); sb.append("\n") }
        return gson.fromJson(sb.toString(), clazz)
    }

    fun sendTextMessage(chatId : String, text : String) : Boolean {
        val post = HTTPRequest(URL("$apiUrl/sendMessage"), HTTPMethod.POST)
        val content = "chat_id=$chatId&text=$text"
        post.payload = content.toByteArray(charset)
        try {
            val telegramResponse = URLFetchServiceFactory.getURLFetchService().fetch(post)
            return telegramResponse.responseCode == 200
        }
        catch (e : Exception) {
            e.printStackTrace()
            return false
        }
    }

    // handles a message from Telegram user
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
                    success = CodeHighlighter.manageCodeHighlightRequest(
                            splitMessage[1], splitMessage[2], chatId, apiUrl)
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
        logprintln(success.toString())
    }

    private fun logprintln(str : String) {
        if (isLogging)
            println(str)
    }
}