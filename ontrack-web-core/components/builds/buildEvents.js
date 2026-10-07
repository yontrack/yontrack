/**
 * Names of the page events (see `EventsContext`) fired when a build changes from the UI, so that
 * whatever on the page depends on its state - like an open "What's missing" popover (#2023) - can
 * refresh itself.
 *
 * Values: `{buildId}`.
 */

/** The build got a new validation run, or one of its runs changed status. */
export const BUILD_VALIDATED = "build.validated"

/** The build got a new promotion run. */
export const BUILD_PROMOTED = "build.promoted"
