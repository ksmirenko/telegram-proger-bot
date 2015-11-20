package telegram

data class Document(
        var file_id : String,
        var thumb : PhotoSize?,
        var file_name : String?,
        var mime_type : String?,
        var file_size : Int?
) {
    constructor() : this("", null, null, null, null)
}
