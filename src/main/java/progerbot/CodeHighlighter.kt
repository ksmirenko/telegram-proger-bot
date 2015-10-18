package progerbot

import java.io.*
import java.util.ArrayList
import java.util.Properties

import gui.ava.html.image.generator.HtmlImageGenerator
import org.apache.http.HttpResponse
import org.apache.http.NameValuePair
import org.apache.http.client.entity.UrlEncodedFormEntity
import org.apache.http.client.methods.HttpPost
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.message.BasicNameValuePair
import org.apache.http.HttpEntity
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.entity.mime.MultipartEntityBuilder
import org.apache.http.client.ClientProtocolException
import org.apache.http.impl.client.CloseableHttpClient

import java.io.File
import java.io.IOException


object CodeHighlighter {
    private val PYGMENTS_URL = "http://pygments.simplabs.com/"

    // adds styles (colors) to pygmentized code
    private fun addStyles(highlightedCode : String) : String {
        try {
            // loading token from locally stored file
            val prop = Properties()
            val inputStream = MainServlet::class.java.
                classLoader.getResourceAsStream("code-highlight-styles.properties")
            prop.load(inputStream)
            // TODO: load properties only once
            val prefix = prop.getProperty("json.highlightPrefix")
            val suffix = prop.getProperty("json.highlightSuffix")
            return prefix + highlightedCode + suffix

        } catch (e : IOException) {
            e.printStackTrace()
            return ""
        }

    }

    fun manageCodeHighlightRequest(language : String, content : String, chatId : String, apiUrl : String) : Boolean {
        // creating HTTP request fot pygmentization
        val post = HttpPost(PYGMENTS_URL)
        val urlParameters = ArrayList<NameValuePair>()
        urlParameters.add(BasicNameValuePair("lang", language))
        urlParameters.add(BasicNameValuePair("code", content))
        post.entity = UrlEncodedFormEntity(urlParameters)
        // sending HTTP request to Pygments
        val pygmentsClient = HttpClientBuilder.create().build()
        val response = pygmentsClient.execute(post)

        // debug section: printing pygments request info
        /* println("\nSending 'POST' request to URL : " + PYGMENTS_URL)
        println("Post parameters : " + post.entity)
        println("Response Code : " + response.statusLine.statusCode) */

        // parsing Pygments response
        val rd = InputStreamReader(response.entity.content)
        val result = StringBuffer()
        rd.forEachLine { result.append(it); result.append("\n") }
        result.deleteCharAt(result.length() - 1)

        // generating and obtaining image out of colored code
        val imageGenerator = HtmlImageGenerator()
        val coloredCode = addStyles(result.toString())
        // debug section: printing colored pygmentized code
        /* println("{{${result.toString()}}}")
        println("{{$coloredCode}}")*/
        imageGenerator.loadHtml(coloredCode)
        // TODO: fix hardcoded file name
        imageGenerator.saveAsImage("hello-world.png")
        val photoFile = File("hello-world.png")

        // sending the image back to user
        val post1 = HttpPost(apiUrl + "/sendPhoto")
        val entity = (MultipartEntityBuilder.create().
            addBinaryBody("photo", photoFile)).addTextBody("chat_id", chatId).build()
        post1.entity = entity
        val telegramClient = HttpClientBuilder.create().build()
        try {
            val telegramResponse = telegramClient.execute(post1)
            return telegramResponse.statusLine.statusCode == 200
        } catch (e : ClientProtocolException) {
            e.printStackTrace()
            return false
        } catch (e : IOException) {
            e.printStackTrace()
            return false
        }
        finally {
            pygmentsClient.close()
            telegramClient.close()
        }
    }
}