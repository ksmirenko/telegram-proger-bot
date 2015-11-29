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
import java.net.URLEncoder
import java.util.*

public object StackOverflow {
    public val clientID: String
    public val clientSecret: String
    public val key: String
    public val redirectURI: String
    //'State' url parameter is a chatID
    private val authUrlWithoutState: String
    //stores pairs chatID/accessToken
    private val authTokens = Hashtable<String, String>()
    private val CHARSET = "UTF-8"

    init {
        try {
            // loading authorization data from locally stored file
            val prop = Properties()
            val inputStream = progerbot.MainServlet::class.java.
                    classLoader.getResourceAsStream("auth.properties")
            prop.load(inputStream)
            clientID = prop.getProperty("json.stackOverflowClientID")
            clientSecret = prop.getProperty("json.stackOverflowClientSecret")
            key = prop.getProperty("json.stackOverflowKey")
            redirectURI = prop.getProperty("json.stackOverflowRedirectURI")
            authUrlWithoutState = "https://stackexchange.com/oauth?client_id=$clientID&" +
                    "redirect_uri=$redirectURI&scope=read_inbox"
        } catch (e: IOException) {
            progerbot.Logger.println("Cannot load auth.properties")
            clientID = ""
            clientSecret = ""
            key = ""
            redirectURI = ""
            authUrlWithoutState = ""
        }
    }

    //returns url user should follow to get authorized
    public fun getAuthUrl(chatId: String): String = "$authUrlWithoutState&state=$chatId"

    //try to authorize to StackOverflow by providing code gotten when user was redirected
    //from StackOverflow to our server
    public fun tryConfirmCode(chatId: String, code: String): Boolean {
        val url = "https://stackexchange.com/oauth/access_token"
        val stackOverflowPost = HTTPRequest(URL(url), HTTPMethod.POST)
        stackOverflowPost.payload = ("client_id=$clientID&client_secret=$clientSecret&" +
                "code=$code&redirect_uri=$redirectURI").toByteArray(CHARSET)
        val stackOverflowResponse = URLFetchServiceFactory.getURLFetchService().fetch(stackOverflowPost)
        val success = (stackOverflowResponse.responseCode == 200)
        if (success) {
            var postResponseProp = Properties()
            postResponseProp.load(ByteArrayInputStream(stackOverflowResponse.content))
            authTokens.put(chatId, postResponseProp.getProperty("access_token"))
        } else {
            progerbot.Logger.println("[StackOverflowError] Could not confirm code for chat $chatId")
        }
        return success
    }

    //just removes user`s authorization token if it`s stored
    public fun logOut(chatId: String): Boolean {
        val containsKey = authTokens.containsKey(chatId)
        if (containsKey)
            authTokens.remove(chatId)
        return containsKey
    }

    //returns result of search on StackOverflow where parameter 'title' is a searching question
    public fun search(textToSearch: String): String {
        val url = "https://api.stackexchange.com/2.2/search/advanced?order=desc&sort=activity&" +
                "q=${URLEncoder.encode(textToSearch, CHARSET)}&site=stackoverflow.com&key=$key"
        val res = HttpRequests.simpleRequest(url, HTTPMethod.GET, "")
        if (res.responseCode != 200)
            return "Cannot perform search"
        val jsonObj = JSONParser().parse(res.content.toString(CHARSET)) as JSONObject
        var jsonArr = JSONParser().parse((jsonObj).get("items").toString()) as JSONArray
        if (jsonArr.isEmpty())
            return "No matches"
        var searchRes = StringBuilder()
        for (i in 0..Math.min(jsonArr.size - 1, 5))
            searchRes.append((jsonArr[i] as JSONObject).get("title").toString() + "\n" +
                    (jsonArr[i] as JSONObject).get("link").toString() + "\n\n")
        return searchRes.toString()
    }

    public fun getUnreadInboxItems(chatId: String): String {
        if (!authTokens.containsKey(chatId))
            return "Yor are not authorized!"
        val authToken = authTokens.get(chatId)
        val url = "https://api.stackexchange.com/2.2/inbox/unread?access_token=$authToken&key=$key&filter=withbody"
        val res = HttpRequests.simpleRequest(url, HTTPMethod.GET, "")
        if (res.responseCode != 200)
            return "Cannot get notifications"
        val jsonObj = JSONParser().parse(res.content.toString(CHARSET)) as JSONObject
        var jsonArr = JSONParser().parse((jsonObj).get("items").toString()) as JSONArray
        //contains pairs of inner item types and item types to be shown to user
        val itemTypes = mapOf<String, String>(Pair("comment", "comment"), Pair("new_answer", "answer"),
                Pair("comment", "comment"), Pair("meta_question", "meta question"),
                Pair("chat_message", "chat message"), Pair("careers_invitations", "careers invitation"),
                Pair("careers_message", "careers message"), Pair("post_notice", "post notice"))
        var searchRes = StringBuilder()
        jsonArr.forEach {
            val message = it as JSONObject
            if (message.get("is_unread").toString() == "true")
                searchRes.append("Unread ${itemTypes.get(message.get("item_type"))}:\n" +
                        message.get("body").toString() + "\n" + message.get("link").toString() + "\n\n")
        }
        val unreadMessages = searchRes.toString()
        if (unreadMessages.isEmpty())
            return "No unread notifications"
        return unreadMessages
    }
}
