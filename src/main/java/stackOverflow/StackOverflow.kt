package stackOverflow

import com.google.appengine.api.urlfetch.HTTPMethod
import com.google.appengine.api.urlfetch.HTTPRequest
import com.google.appengine.api.urlfetch.URLFetchServiceFactory
import org.json.simple.JSONArray
import org.json.simple.JSONObject
import org.json.simple.parser.JSONParser
import progerbot.HttpRequests
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.URL
import java.util.*

public object StackOverflow {
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
                    classLoader.getResourceAsStream("auth.properties")
            prop.load(inputStream)
            clientID = prop.getProperty("json.stackOverflowClientID")
            clientSecret = prop.getProperty("json.stackOverflowClientSecret")
            key = prop.getProperty("json.stackOverflowKey")
            redirectURI = prop.getProperty("json.stackOverflowRedirectURI")
            authUrlWithoutState = "https://stackexchange.com/oauth?client_id=$clientID%26redirect_uri=$redirectURI"
        } catch (e: IOException) {
            progerbot.Logger.println("Cannot load auth.properties")
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

    public fun logOut(chatId : String) : Boolean {
        val containsKey = authTokens.containsKey(chatId)
        if (containsKey)
            authTokens.remove(chatId)
        return containsKey
    }

    public fun search(title : String) : String {
        val url = "https://api.stackexchange.com/2.2/search?" +
                "order=desc&sort=activity&intitle=$title&site=stackoverflow.com&key=$key"
        val res = HttpRequests.simpleRequest(url, HTTPMethod.GET, "")
        if (res.responseCode == 200) {
            val jsonObj = JSONParser().parse(res.content.toString(charset)) as JSONObject
            if (jsonObj.get("has_more").toString() == "false")
                return "No matches"
            var jsonArr = JSONParser().parse((jsonObj).get("items").toString()) as JSONArray
            var searchRes = StringBuilder()
            for (i in 0..jsonArr.size - 1)
                searchRes.append((jsonArr[i] as JSONObject).get("title").toString() + "\n" +
                        (jsonArr[i] as JSONObject).get("link").toString() + "\n\n")
            return searchRes.toString()
        }
        else
            return "Cannot perform search"
    }
}
