package telegram.request

import retrofit.mime.TypedByteArray

class InputFileBytes(mimeType : String, bytes : ByteArray, private val fileName : String) : TypedByteArray(mimeType, bytes) {
    override fun fileName() : String {
        return fileName
    }

    companion object {
        // everything becomes jpeg or mpeg or ogg
        val PHOTO_MIME_TYPE = "image/jpeg"
        val AUDIO_MIME_TYPE = "audio/mpeg"
        val VIDEO_MIME_TYPE = "video/mp4"
        val VOICE_MIME_TYPE = "audio/ogg"

        // necessary for telegram
        val PHOTO_FILE_NAME = "file.jpg"
        val AUDIO_FILE_NAME = "file.mp3"
        val VIDEO_FILE_NAME = "file.mp4"
        val VOICE_FILE_NAME = "file.ogg"

        fun photo(bytes : ByteArray) : InputFileBytes {
            return InputFileBytes(PHOTO_MIME_TYPE, bytes, PHOTO_FILE_NAME)
        }

        fun audio(bytes : ByteArray) : InputFileBytes {
            return InputFileBytes(AUDIO_MIME_TYPE, bytes, AUDIO_FILE_NAME)
        }

        fun video(bytes : ByteArray) : InputFileBytes {
            return InputFileBytes(VIDEO_MIME_TYPE, bytes, VIDEO_FILE_NAME)
        }

        fun voice(bytes : ByteArray) : InputFileBytes {
            return InputFileBytes(VOICE_MIME_TYPE, bytes, VOICE_FILE_NAME)
        }
    }
}
