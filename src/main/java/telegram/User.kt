package telegram

data class User(
        var id : Int,
        var first_name : String,
        var last_name : String?,
        var username : String?
) {
    constructor() : this(0, "", null, null)
}