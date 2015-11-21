package progerbot

import com.google.appengine.api.urlfetch.HTTPMethod
import com.google.appengine.api.urlfetch.HTTPRequest
import com.google.appengine.api.urlfetch.HTTPResponse
import com.google.appengine.api.urlfetch.URLFetchServiceFactory
import java.net.URL
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Thread-safe log service.
 */
public object Logger {
    private val lock = ReentrantLock()
    private val isLogging = false

    /**
     * Prints [str] to the log.
     */
    public fun println(str : String) {
        lock.withLock {
            if (isLogging)
                kotlin.io.println(str)
        }
    }
}

public class HttpRequests {
    companion object {
        public fun simpleRequest(
                url : String, method : HTTPMethod, content : String, charset : String = "UTF-8"
        ) : HTTPResponse {
            val post = HTTPRequest(URL(url), method)
            post.payload = content.toByteArray(charset)
            return URLFetchServiceFactory.getURLFetchService().fetch(post)
        }
    }
}