package net.nemerosa.ontrack.extension.queue

/**
 * Queue prefix for the processor, used for its exchange, its queues and its routing keys.
 *
 * The queues of a processor with a [single active consumer][QueueProcessor.singleActiveConsumer]
 * are declared with an argument its former queues did not have, so they have other names.
 */
val QueueProcessor<*>.queueNamePrefix: String
    get() =
        if (singleActiveConsumer) {
            "ontrack.queue.$id.v2"
        } else {
            "ontrack.queue.$id"
        }


/**
 * Queue routing prefix for the processor
 */
val QueueProcessor<*>.queueRoutingPrefix: String
    get() =
        "${queueNamePrefix}.routing"
