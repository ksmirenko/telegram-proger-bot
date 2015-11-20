package progerbot

import gui.ava.html.image.generator.HtmlImageGenerator
import org.apache.http.NameValuePair
import org.apache.http.client.ClientProtocolException
import org.apache.http.client.entity.UrlEncodedFormEntity
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.ContentType
import org.apache.http.entity.mime.MultipartEntityBuilder
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.message.BasicNameValuePair
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.util.*
import javax.imageio.ImageIO


public object CodeHighlighter {
    private val PYGMENTS_URL = "http://pygments.simplabs.com/"
    private val prefix : String
    private val suffix : String

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

    // adds styles (colors) to pygmentized code
    private fun addStyles(highlightedCode : String) = prefix + highlightedCode + suffix

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
        // parsing Pygments response
        val rd = InputStreamReader(response.entity.content)
        val result = StringBuffer()
        rd.forEachLine { result.append(it); result.append("\n") }
        result.deleteCharAt(result.length - 1)
        // generating and obtaining image out of colored code
        val imageGenerator = HtmlImageGenerator()
        val coloredCode = addStyles(result.toString())
        imageGenerator.loadHtml(coloredCode)
        // converting buffered image to byte array for sending
        val baos = ByteArrayOutputStream()
        ImageIO.write(imageGenerator.bufferedImage, "png", baos);
        val imageByteArray = baos.toByteArray()
        // sending the image back to user
        val post1 = HttpPost(apiUrl + "/sendPhoto")
        val entity = (MultipartEntityBuilder.create()
                .addTextBody("chat_id", chatId)
                .addBinaryBody("photo", imageByteArray, ContentType.MULTIPART_FORM_DATA, "image.png"))
                .build()
        post1.entity = entity
        val telegramClient = HttpClientBuilder.create().build()
        try {
            val telegramResponse = telegramClient.execute(post1)
            return telegramResponse.statusLine.statusCode == 200
        }
        catch (e : ClientProtocolException) {
            e.printStackTrace()
            return false
        }
        catch (e : IOException) {
            e.printStackTrace()
            return false
        }
        finally {
            pygmentsClient.close()
            telegramClient.close()
        }
    }
}