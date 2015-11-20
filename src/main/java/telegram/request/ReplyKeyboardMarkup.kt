package telegram.request

data class ReplyKeyboardMarkup(
        var keyboard : Array<Array<String>>,
        var resize_keyboard : Boolean,
        var one_time_keyboard : Boolean,
        var selective : Boolean
) {
}
