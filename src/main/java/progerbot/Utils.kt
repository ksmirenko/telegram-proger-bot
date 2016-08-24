package progerbot

import com.google.appengine.api.urlfetch.HTTPMethod
import com.google.appengine.api.urlfetch.HTTPRequest
import com.google.appengine.api.urlfetch.HTTPResponse
import com.google.appengine.api.urlfetch.URLFetchServiceFactory
import java.net.URL
import java.util.concurrent.locks.ReentrantLock

/**
 * Thread-safe log service.
 */
object Logger {
    private val lock = ReentrantLock()
    private val isLogging = true

    /**
     * Prints [str] to the log (standard error stream).
     * Messages written to err are shown as warnings in GAE logs.
     */
    fun println(str : String) {
        lock.withLock {
            if (isLogging)
                System.err.println(str)
        }
    }

    /**
     * Prints the stack trace of [exc] to the log (standard error stream).
     */
    fun println(exc : Exception) {
        lock.withLock {
            if (isLogging)
                exc.printStackTrace(System.err)
        }
    }
}

object HttpRequests {
    fun simpleRequest(
            url : String, method : HTTPMethod, content : String, charset : String = "UTF-8"
    ) : HTTPResponse {
        val post = HTTPRequest(URL(url), method)
        post.payload = content.toByteArray(charset)
        return URLFetchServiceFactory.getURLFetchService().fetch(post)
    }
}
