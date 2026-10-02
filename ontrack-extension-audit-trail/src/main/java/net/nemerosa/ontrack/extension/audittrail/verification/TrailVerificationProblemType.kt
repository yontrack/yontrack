package net.nemerosa.ontrack.extension.audittrail.verification

/**
 * Checks of the verification of a trail. All but [ENDORSEMENT] and [UNKNOWN_KEY] are checks of the
 * chain.
 */
enum class TrailVerificationProblemType(
    /**
     * Whether a failure of this check breaks the chain
     */
    val chain: Boolean,
) {

    /**
     * The entry is not at the position its seq gives: an entry before it is missing, or the
     * entries were reordered.
     */
    SEQ(chain = true),

    /**
     * The schema version of the entry is not one this verifier supports.
     */
    SCHEMA_VERSION(chain = true),

    /**
     * The stored hash of the entry is not the hash of its content: the entry, or its hash, was
     * changed.
     */
    HASH(chain = true),

    /**
     * The previous hash of the entry is not the hash of the entry before it: an entry was removed,
     * inserted or replaced.
     */
    PREVIOUS_HASH(chain = true),

    /**
     * The first entry is neither `build.created` nor `trail.opened`.
     */
    FIRST_ENTRY(chain = true),

    /**
     * The first entry names another build: the trail is not the trail of this build.
     */
    BUILD(chain = true),

    /**
     * An endorsement of the entry is not the signature of its hash by the key it names.
     */
    ENDORSEMENT(chain = false),

    /**
     * An endorsement of the entry names a key which is not a public key of the instance.
     */
    UNKNOWN_KEY(chain = false),
}
