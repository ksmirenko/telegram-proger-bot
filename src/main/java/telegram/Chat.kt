package telegram

data class Chat(
        var id : Long,
        var type : Chat.Type,
        var title : String?, // group, channel
        var username : String?, // private, channel
        var first_name : String?, // private
        var last_name : String? // private
) {

    enum class Type {
        private, group, channel
    }

    constructor() : this(0L, Type.private, null, null, null, null)
}
