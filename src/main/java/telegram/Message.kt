package telegram

import java.util.Arrays

data class Message(
        var message_id : Int,
        var from : User?,
        var date : Int,
        var chat : Chat,
        var forward_from : User?,
        var forward_date : Int?,
        var reply_to_message : Message?,
        var text : String?,
        var audio : Audio?,
        var document : Document?,
        var photo : Array<PhotoSize>?,
        var sticker : Sticker?,
        var video : Video?,
        var voice : Voice?,
        var caption : String?,
        var contact : Contact?,
        var location : Location?,
        var new_chat_participant : User?,
        var left_chat_participant : User?,
        var new_chat_title : String?,
        var new_chat_photo : Array<PhotoSize>?,
        var delete_chat_photo : Boolean?,
        var group_chat_created : Boolean?
) {
    constructor() : this(0, null, 0, Chat(), null, null,
            null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null, null, null)
}
