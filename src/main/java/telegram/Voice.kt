package telegram

data class Voice(
        var file_id : String,
        var duration : Int,
        var mime_type : String?,
        var file_size : Int?
) {
    constructor() : this("", 0, null, null)
}