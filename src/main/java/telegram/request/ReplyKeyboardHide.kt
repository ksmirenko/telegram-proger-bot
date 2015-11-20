package telegram.request

data class ReplyKeyboardHide(
        var selective : Boolean = false
) {
    var hide_keyboard = true
}