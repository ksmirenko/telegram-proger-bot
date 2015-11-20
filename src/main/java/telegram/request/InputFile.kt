package telegram.request

import retrofit.mime.TypedFile
import java.io.File

class InputFile(mimeType : String, file : File) : TypedFile(mimeType, file) {
    companion object {
        fun photo(file : File) : InputFile {
            return InputFile(InputFileBytes.PHOTO_MIME_TYPE, file)
        }

        fun audio(file : File) : InputFile {
            return InputFile(InputFileBytes.AUDIO_MIME_TYPE, file)
        }

        fun video(file : File) : InputFile {
            return InputFile(InputFileBytes.VIDEO_MIME_TYPE, file)
        }

        fun voice(file : File) : InputFile {
            return InputFile(InputFileBytes.VOICE_MIME_TYPE, file)
        }
    }
}
