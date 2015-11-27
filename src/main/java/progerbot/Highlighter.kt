package progerbot

import com.google.appengine.api.urlfetch.HTTPMethod
import com.google.appengine.api.urlfetch.HTTPRequest
import com.google.appengine.api.urlfetch.URLFetchServiceFactory
import gui.ava.html.image.generator.HtmlImageGenerator
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URL
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO


public object Highlighter {
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
    private val imageObtainer : ImageObtainingStrategy = ApiImageObtainingStrategy()
    /**
     * Pairs (chat ID, language) containing information about chats that
     * have asked to highlight a file and not sent the document yet.
     * Warning: old requests are not removed if the user has not sent a file or a cancel message.
     */
    private val pendingDocumentsUsers = ConcurrentHashMap<String, String>()
    /**
     * Chat IDs for which P2I has not prepared images yet.
     */
    private val pendingP2IRequests = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

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
     * Handles text messages concerning code highlighting.
     * @param chatId User chat ID.
     * @param text Message content.
     * @return True, if successful, or false otherwise.
     */
    public fun handleRequest(chatId : String, text : String) : Boolean {
        val splitMessage = text.split(" ".toRegex(), 3)
        when (splitMessage[0]) {
            "/highlight" -> {
                try {
                    return highlightCodeText(splitMessage[1], splitMessage[2], chatId)
                }
                catch (e : Exception) {
                    e.printStackTrace(System.err)
                    TelegramApi.sendText(
                            chatId,
                            "I couldn't proceed your request because of:\n${e.toString()}\nSorry for that."
                    )
                    return false
                }
            }
            "/highlightfile" -> {
                if (splitMessage.size < 2) {
                    TelegramApi.sendText(chatId, "Error: you didn't name the language!")
                    return false
                }
                pendingDocumentsUsers.put(chatId, splitMessage[1])
                TelegramApi.sendText(
                        chatId,
                        "All right, now send me the file."
                )
            }
            "/highlightcancel" -> {
                pendingDocumentsUsers.remove(chatId)
                TelegramApi.sendText(
                        chatId,
                        "OK, we won't highlight any files now."
                )
            }
        }
        return true
    }

    /**
     * Handles the message with a document containing code for highlighting.
     * @param chatId User chat ID.
     * @param fileId File ID for downloading the file from Telegram server.
     * @return True, if successful, or false otherwise.
     */
    public fun handleDocument(chatId : String, fileId : String) : Boolean {
        if (!pendingDocumentsUsers.containsKey(chatId)) {
            TelegramApi.sendText(chatId, "Why are you sending me files? I don't need them.")
            return true
        }
        val file = TelegramApi.getFile(fileId) ?: return false
        val path = file.file_path ?: return false
        val sourceCode = TelegramApi.downloadTextFile(path)
        val language = pendingDocumentsUsers.get(chatId) ?: return false
        return highlightCodeText(language, sourceCode, chatId)
    }

    /**
     * Sends the image prepared by Page2Images to the user.
     */
    public fun handlePreparedImage(chatId : String, imageUrl : String) {
        if (pendingP2IRequests.contains(chatId)) {
            pendingP2IRequests.remove(chatId)
            TelegramApi.sendText(chatId, "Here you are:\n$imageUrl")
        }
    }

    private fun highlightCodeText(language : String, content : String, chatId : String) : Boolean {
        // creating and sending HTTP request to Pygments
        val pygmentsPost = HTTPRequest(URL(PYGMENTS_URL), HTTPMethod.POST)
        pygmentsPost.payload = "lang=$language&code=$content".toByteArray(CHARSET)
        val pygmentsResponse = URLFetchServiceFactory.getURLFetchService().fetch(pygmentsPost)
        if (pygmentsResponse.responseCode != 200) {
            Logger.println("Error: Could not pygmentize code for chat $chatId")
            return false
        }
        // obtaining image with colored code
        imageObtainer.getImageFromHtml(
                addStyles(pygmentsResponse.content.toString()),
                chatId
        )
        return true
    }

    /**
     * Adds styles (colors) to pygmentized code.
     */
    private fun addStyles(highlightedCode : String) = prefix + highlightedCode + suffix

    private abstract class ImageObtainingStrategy() {
        public abstract fun getImageFromHtml(htmlContent : String, chatId : String) : Boolean
    }

    /**
     * Generates images from HTML via external library. Much faster, but prohibited by GAE.
     */
    private class LibraryImageObtainingStrategy() : ImageObtainingStrategy() {
        override fun getImageFromHtml(htmlContent : String, chatId : String) : Boolean {
            val imageGenerator = HtmlImageGenerator()
            imageGenerator.loadHtml(htmlContent)
            val baos = ByteArrayOutputStream()
            ImageIO.write(imageGenerator.bufferedImage, "png", baos)
            return TelegramApi.sendImage(chatId, baos.toByteArray()).responseCode == 200
        }
    }

    /**
     * Obtains images from HTML via Page2Images REST API.
     */
    private class ApiImageObtainingStrategy() : ImageObtainingStrategy() {
        private val P2I_URL = "http://api.page2images.com/html2image"
        private val P2I_CALLBACK_URL = "https://telegram-proger-bot.appspot.com/main"
        private val apiKey : String

        init {
            // loading properties
            val prop = Properties()
            val inputStream = progerbot.MainServlet::class.java.
                    classLoader.getResourceAsStream("auth.properties")
            prop.load(inputStream)
            apiKey = prop.getProperty("json.pagetoimagesApiKey")
        }

        override fun getImageFromHtml(htmlContent : String, chatId : String) : Boolean {
            try {
                pendingP2IRequests.add(chatId)
                HttpRequests.simpleRequest(
                        P2I_URL,
                        HTTPMethod.POST,
                        "p2i_html=$htmlContent&p2i_key=$apiKey&p2i_imageformat=jpg" +
                                "&p2i_fullpage=0&p2i_callback=$P2I_CALLBACK_URL?token=$chatId"
                )
                TelegramApi.sendText(
                        chatId,
                        "Your code image is being prepared now."
                )
                return true
            }
            catch (e : Exception) {
                Logger.println(e)
                return false
            }
        }
    }
}
