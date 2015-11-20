package telegram

data class Sticker(
        var file_id : String,
        var width : Int,
        var height : Int,
        var thumb : PhotoSize?,
        var file_size : Int?
) {
    constructor() : this("", 0, 0, null, null)
}
