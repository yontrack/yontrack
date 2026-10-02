package net.nemerosa.ontrack.extension.audittrail.endorsement

/**
 * Whether the instance key can endorse the entries.
 */
enum class InstanceKeyStatus {

    /**
     * The key is there, or was generated: every entry is endorsed.
     */
    OK,

    /**
     * There is no usable key and none could be generated — a read-only store with no key, a store
     * which cannot be read, or a stored payload which is not a key. Entries are still written, but
     * unendorsed, until a key is provisioned.
     */
    NOT_PROVISIONED,
}
