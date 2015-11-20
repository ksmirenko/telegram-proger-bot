package telegram

data class UserProfilePhotos(
        var total_count : Int,
        var photos : Array<Array<PhotoSize>>
) {
    constructor() : this(0, emptyArray())
}
