package telegram

data class Location(
        var longitude : Float,
        var latitude : Float
) {
    constructor() : this(0f, 0f)
}
