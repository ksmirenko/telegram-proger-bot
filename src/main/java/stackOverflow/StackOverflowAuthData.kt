package stackOverflow

import com.google.appengine.api.urlfetch.HTTPMethod
import com.google.appengine.api.urlfetch.HTTPRequest
import com.google.appengine.api.urlfetch.URLFetchServiceFactory
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.URL
import java.util.*

public object StackOverflowAuthData {
    public val clientID : String
    public val clientSecret : String
    public val key : String
    public val redirectURI : String
    //'State' url parameter is a chatID
    private val authUrlWithoutState: String
    //stores pairs chatID/accessToken
    private val authTokens = Hashtable<String, String>()
    private val charset = "UTF-8"

    init {
        try {
            val prop = Properties()
            val inputStream = progerbot.MainServlet::class.java.
                    classLoader.getResourceAsStream("stackOverflowAuth.properties")
            prop.load(inputStream)
            clientID = prop.getProperty("json.clientID")
            clientSecret = prop.getProperty("json.clientSecret")
            key = prop.getProperty("json.key")
            redirectURI = prop.getProperty("json.redirectURI")
            authUrlWithoutState = "https://stackexchange.com/oauth?client_id=$clientID%26redirect_uri=$redirectURI"
        } catch (e: IOException) {
            progerbot.Logger.println("Cannot load stackOverflowAuth.properties")
            clientID = ""
            clientSecret = ""
            key = ""
            redirectURI = ""
            authUrlWithoutState = ""
        }
    }

    public fun getAuthUrl(chatId : String) : String = "$authUrlWithoutState%26state=$chatId"

    public fun tryConfirmCode(chatId : String, code : String) : Boolean {
        val url = "https://stackexchange.com/oauth/access_token"
        val stackOverflowPost = HTTPRequest(URL(url), HTTPMethod.POST)
        stackOverflowPost.payload = ("client_id=$clientID&client_secret=$clientSecret&" +
                "code=$code&redirect_uri=$redirectURI").toByteArray(charset)
        val stackOverflowResponse = URLFetchServiceFactory.getURLFetchService().fetch(stackOverflowPost)
        if (stackOverflowResponse.responseCode == 200) {
            var postResponseProp = Properties()
            postResponseProp.load(ByteArrayInputStream(stackOverflowResponse.content))
            authTokens.put(chatId, postResponseProp.getProperty("access_token"))
            return true
        }
        else {
            progerbot.Logger.println("[StackOverflowError] Could not confirm code for chat $chatId")
            return false
        }
    }
}