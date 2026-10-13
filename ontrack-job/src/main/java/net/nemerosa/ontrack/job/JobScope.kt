package net.nemerosa.ontrack.job

/**
 * Where a job runs, when several replicas share the database (#2078).
 */
enum class JobScope {

    /**
     * Once per period in the cluster, on whichever replica Quartz picks: the jobs page, its
     * history, pause and "run now". The default.
     */
    CLUSTER,

    /**
     * On every replica, on a local scheduler, with no coordination: for what is per pod by nature
     * only - a gauge of this pod's meter registry, the reachability of a store from this pod. Not on
     * the jobs page: no history, no pause, no "run now", but a duration timer and an error counter.
     */
    REPLICA,

}
