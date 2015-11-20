package telegram

data class Audio(
        var file_id : String,
        var duration : Int,
        var performer : String?,
        var title : String?,
        var mime_type : String?,
        var file_size : Int?
) {
    constructor() : this("", 0, null, null, null, null)
}
