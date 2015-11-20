package telegram.request

data class ForceReply(
        var selective : Boolean = false
) {
    val force_reply = true
}
