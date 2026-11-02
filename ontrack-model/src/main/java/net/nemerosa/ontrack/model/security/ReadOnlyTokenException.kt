package net.nemerosa.ontrack.model.security

import org.springframework.security.access.AccessDeniedException

/**
 * Refusal of an action to a read-only token.
 *
 * It is an [AccessDeniedException], so that it is handled as any other refusal, but its message
 * always says why. A refusal creates no audit or event entry: nothing happened.
 */
class ReadOnlyTokenException(message: String) : AccessDeniedException(message) {

    companion object {

        private fun refusal(tokenName: String, action: String) =
            ReadOnlyTokenException("read-only token '$tokenName' may not $action")

        /**
         * The function is not in the allowlist of the [policy][ReadOnlyTokenPolicy].
         */
        fun function(tokenName: String, fn: Class<*>) = refusal(tokenName, "use ${fn.simpleName}")

        /**
         * The GraphQL document names a mutation.
         */
        fun mutation(tokenName: String) = refusal(tokenName, "run a mutation")

        /**
         * The REST call is not a read.
         *
         * @param method HTTP method of the call
         * @param path Path of the call
         */
        fun request(tokenName: String, method: String, path: String) = refusal(tokenName, "call $method $path")

        /**
         * The token is used to generate another token.
         */
        fun tokenGeneration(tokenName: String) = refusal(tokenName, "generate a token")
    }

}
