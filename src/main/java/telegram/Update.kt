package telegram

data class Update(
        var update_id : Int,
        var message : Message?
) {
    constructor() : this(0, null)
}
