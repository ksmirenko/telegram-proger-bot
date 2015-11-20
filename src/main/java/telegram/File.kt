package telegram

data class File(
        var file_id : String,
        var file_size : Int?,
        var file_path : String?
) {
    constructor() : this("", null, null)
}
