package top.madkarma.conduit.game

/** An expected failure, such as invalid input or a missing entity, that callers see as is. */
sealed class ConduitError(val label: String, message: String) : RuntimeException(message) {
    class InvalidInput(message: String) : ConduitError("Invalid input", message)

    class NotFound(message: String) : ConduitError("Not found", message)

    class FailedPrecondition(message: String) : ConduitError("Failed precondition", message)

    class Aborted(message: String) : ConduitError("Aborted", message)

    class Unavailable(message: String) : ConduitError("Unavailable", message)

    class TimedOut(message: String) : ConduitError("Timed out", message)

    /** The text a caller sees, such as "Not found: Unknown dimension: minecraft:moon". */
    fun describe(): String = "$label: $message"
}

fun invalidInput(message: String): Nothing = throw ConduitError.InvalidInput(message)

fun notFound(message: String): Nothing = throw ConduitError.NotFound(message)

fun failedPrecondition(message: String): Nothing = throw ConduitError.FailedPrecondition(message)

fun aborted(message: String): Nothing = throw ConduitError.Aborted(message)

fun unavailable(message: String): Nothing = throw ConduitError.Unavailable(message)
