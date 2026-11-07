package top.madkarma.conduit.game

/** An expected failure, such as invalid input or a missing entity, that callers see as is. */
sealed class ConduitError(val code: String, message: String) : RuntimeException(message) {
    class InvalidInput(message: String) : ConduitError("INVALID_ARGUMENT", message)

    class NotFound(message: String) : ConduitError("NOT_FOUND", message)

    class FailedPrecondition(message: String) : ConduitError("FAILED_PRECONDITION", message)

    class Aborted(message: String) : ConduitError("ABORTED", message)

    class Unavailable(message: String) : ConduitError("UNAVAILABLE", message)

    class TimedOut(message: String) : ConduitError("DEADLINE_EXCEEDED", message)
}

fun invalidInput(message: String): Nothing = throw ConduitError.InvalidInput(message)

fun notFound(message: String): Nothing = throw ConduitError.NotFound(message)

fun failedPrecondition(message: String): Nothing = throw ConduitError.FailedPrecondition(message)

fun aborted(message: String): Nothing = throw ConduitError.Aborted(message)

fun unavailable(message: String): Nothing = throw ConduitError.Unavailable(message)
