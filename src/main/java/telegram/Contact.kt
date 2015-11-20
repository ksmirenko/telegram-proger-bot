package telegram

data class Contact(
        var phone_number : String,
        var first_name : String,
        var last_name : String?,
        var user_id : Int?
) {
    constructor() : this("", "", null, null)
}
