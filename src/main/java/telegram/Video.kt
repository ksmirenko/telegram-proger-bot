package telegram

data class Video(
        var file_id : String,
        var width : Int,
        var height : Int,
        var duration : Int,
        var thumb : PhotoSize?,
        var mime_type : String?,
        var file_size : Int?
) {
    constructor() : this("", 0, 0, 0, null, null, null)
}
