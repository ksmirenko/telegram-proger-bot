package telegram

data class PhotoSize(
        var file_id : String,
        var width : Int,
        var height : Int,
        var file_size : Int?
) {
    constructor() : this("", 0, 0, null)
}
