/**
 * The route map: which desktop routes have a mobile equivalent, and what to
 * call the ones that do not.
 *
 * This module is the single source of truth for the mobile/desktop split, read
 * by the middleware (to decide between a redirect and the interstitial) and by
 * the interstitial itself (to name the destination).
 *
 * Adding a desktop route means deciding what a phone user following a link to it
 * should see, and that decision is recorded here. Leaving it out is not neutral:
 * the route silently gets the interstitial, which may well be right, but should
 * be a choice rather than an oversight.
 *
 * See `doc/dev-guide/ui/mobile-ui.md`.
 */

/** Everything the mobile UI serves lives under this prefix. */
export const MOBILE_PREFIX = '/mobile'

/** Where a phone lands when its destination has a mobile equivalent of its own. */
export const MOBILE_HOME = MOBILE_PREFIX

/** The project list, which is also where the home screen's empty state points. */
export const MOBILE_PROJECTS = `${MOBILE_PREFIX}/projects`

/**
 * Who is signed in, and the way to stop being signed in.
 *
 * Reached by tapping the name in the header, and from nowhere else. It
 * deliberately appears in neither `EQUIVALENTS` nor `MOBILE_NAV_ITEMS`: it
 * stands in for no desktop route - `/core/admin/userProfile` is API tokens and
 * groups, which is a different page that happens to share a name - and two
 * thumb-level destinations are what the bottom bar carries.
 */
export const MOBILE_ACCOUNT = `${MOBILE_PREFIX}/account`

/**
 * One project's screen: its branches.
 *
 * @param {string|number} id
 * @returns {string}
 */
export const mobileProjectUri = (id) => `${MOBILE_PREFIX}/project/${id}`

/**
 * One branch's screen: its latest builds.
 *
 * @param {string|number} id
 * @returns {string}
 */
export const mobileBranchUri = (id) => `${MOBILE_PREFIX}/branch/${id}`

/**
 * One build's screen: the decision surface.
 *
 * @param {string|number} id
 * @returns {string}
 */
export const mobileBuildUri = (id) => `${MOBILE_PREFIX}/build/${id}`

/**
 * One deployment's screen: its admission rules, and what can be done about them.
 *
 * A deployment id is a UUID rather than a number - the mobile UI's only
 * non-numeric entity route.
 *
 * @param {string} id
 * @returns {string}
 */
export const mobileDeploymentUri = (id) => `${MOBILE_PREFIX}/deployment/${id}`

/**
 * One workflow run's screen: its status and its nodes, as a list.
 *
 * Keyed on the plain `WorkflowInstance` id, so one screen serves both kinds of
 * run a phone can reach - the one a promotion set off and the one a slot
 * workflow ran for a deployment. They are the same type behind the same
 * `workflowInstance(id:)` root query; what is slot-specific (the trigger, the
 * override, the check's reason) belongs to the deployment and stays on the
 * deployment screen.
 *
 * @param {string} id
 * @returns {string}
 */
export const mobileWorkflowInstanceUri = (id) => `${MOBILE_PREFIX}/workflow-instance/${id}`

/** Where a phone lands when its destination has no mobile equivalent. */
export const MOBILE_INTERSTITIAL = `${MOBILE_PREFIX}/desktop-only`

/** The query parameter carrying the destination through to the interstitial. */
export const INTERSTITIAL_TARGET_PARAM = 'target'

/** The desktop UI's home page. */
export const DESKTOP_HOME = '/'

/**
 * Desktop routes with a mobile equivalent.
 *
 * Deliberately short. A route earns an entry only once the mobile screen behind
 * it actually exists and does the job: sending a phone to a placeholder is worse
 * than the interstitial, which at least offers the desktop page that works.
 * The screen issues in the mobile UI initiative add their rows here as they land.
 */
const EQUIVALENTS = {
    [DESKTOP_HOME]: MOBILE_HOME,
}

/**
 * A `:` as a browser leaves it in a path, or as one may percent-encode it.
 *
 * A workflow instance id is the only entity id in the product carrying a
 * character a browser is free to encode, and a link pasted into a chat can
 * arrive spelled either way.
 */
const COLON = '(?::|%3[Aa])'

/** `ISO_LOCAL_DATE_TIME-UUID`, which is what `createInstanceId` builds. */
const WORKFLOW_INSTANCE_PATH = new RegExp(
    '^/extension/workflows/instances/(' +
    `\\d{4}-\\d{2}-\\d{2}T\\d{2}${COLON}\\d{2}(?:${COLON}\\d{2}(?:\\.\\d+)?)?` +
    '-[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})$'
)

/**
 * Desktop routes carrying an entity id, and the mobile screen standing in for
 * each.
 *
 * Separate from `EQUIVALENTS` because these are not paths but shapes: the id in
 * `/project/12` has to come through to `/mobile/project/12`, or the redirect
 * would land the user on a screen for some other project - or none.
 *
 * The patterns are exact by design. `/project/[id]` is the whole desktop route;
 * anything deeper is a different page, and an id that is not a number is not a
 * project, so both fall through to the interstitial rather than to a mobile
 * screen that would ask the server a question with no answer.
 */
const ENTITY_EQUIVALENTS = [
    [/^\/project\/(\d+)$/, mobileProjectUri],
    [/^\/branch\/(\d+)$/, mobileBranchUri],
    [/^\/build\/(\d+)$/, mobileBuildUri],
    /*
     * A deployment's id is a UUID and not a number, so this pattern is shaped
     * rather than numeric - but exact all the same, for the same reason as the
     * others: anything else under `/extension/environments/pipeline/` is not a
     * pipeline id, and sending it to a screen that would ask the server an
     * unanswerable question is worse than the interstitial.
     *
     * The mobile screen covers what a phone user does with a deployment -
     * reading its admission rules, answering them, overriding one, running it,
     * completing it, cancelling it - and reading its slot's workflows, which
     * link on to the instance screen below. It does not carry the desktop
     * pipeline page's history or its graph. That is the bar the route map sets:
     * the screen exists and does the job, rather than being a placeholder
     * standing in for a page that works.
     */
    [/^\/extension\/environments\/pipeline\/([0-9a-fA-F-]{36})$/, mobileDeploymentUri],
    /*
     * A workflow instance, whose id is neither a number nor a UUID but an
     * `ISO_LOCAL_DATE_TIME-UUID` pair - so it needs a pattern of its own, exact
     * like the others.
     *
     * The fractional seconds are what make this route awkward rather than
     * merely different: `Time.now()` is not truncated, so the id carries one
     * and the id therefore carries a **dot**. Both of the redirect's
     * "this is a file, leave it alone" heuristics - `LOOKS_LIKE_A_FILE` below
     * and the matcher in `middleware.js` - used to read any dot as a file
     * extension, and would have exempted this path before ever reaching the
     * table. Both now ask for a real extension instead.
     *
     * Only `/instances/[id]` maps. The audit page and the workflow definition
     * pages still have no mobile equivalent and must still reach the
     * interstitial, which is what the `/extension/workflows/` description below
     * is for.
     */
    [WORKFLOW_INSTANCE_PATH, mobileWorkflowInstanceUri],
]

/**
 * Paths the redirect must never touch.
 *
 * Not merely "has no mobile equivalent" - these must not reach the interstitial
 * either:
 *
 * - `/mobile/...` is already the mobile UI, interstitial included.
 * - `/auth/...` is the sign-in flow. The interstitial lives behind the login, so
 *   redirecting the sign-in page would strand a phone user in a loop.
 * - `/api/...` is data, not a page.
 * - `/display/...` answers with a redirect to the real page, which the
 *   middleware then sees on its own terms.
 *
 * The matcher in `middleware.js` already keeps `/api` and `/_next` out; they are
 * repeated here so the decision is complete on its own and testable without a
 * request.
 */
const EXEMPT_PREFIXES = [
    MOBILE_PREFIX,
    '/auth',
    '/api',
    '/display',
    '/_next',
]

/**
 * A path ending in a file extension is a file, not a page.
 *
 * "A dot anywhere in the last segment" is the obvious reading and is the wrong
 * one: a workflow instance id carries the fractional seconds of its timestamp,
 * so `/extension/workflows/instances/2026-09-12T14:27:57.595125-<uuid>` would
 * be exempted as a file and never reach its mobile screen. An extension is
 * short and alphanumeric and runs to the end of the path, which a UUID trailing
 * a dot does not.
 */
const LOOKS_LIKE_A_FILE = /\/[^/]+\.[A-Za-z0-9]{1,8}$/

/**
 * Is this path outside the mobile/desktop decision altogether?
 *
 * @param {string} pathname
 * @returns {boolean}
 */
export function isRedirectExempt(pathname) {
    if (typeof pathname !== 'string' || !pathname.startsWith('/')) return true
    const exempt = EXEMPT_PREFIXES.some(prefix =>
        // `=== prefix` or `prefix/...` - never `/mobiles`.
        pathname === prefix || pathname.startsWith(`${prefix}/`)
    )
    return exempt || LOOKS_LIKE_A_FILE.test(pathname)
}

/**
 * The mobile screen standing in for a desktop route.
 *
 * @param {string} pathname A desktop path.
 * @returns {string|null} The mobile path, or `null` when there is no equivalent
 *   - which means the interstitial.
 */
export function mobileEquivalent(pathname) {
    if (typeof pathname !== 'string') return null
    // The exact paths first: they are the cheaper lookup, and no entity pattern
    // can match one of them anyway.
    if (Object.prototype.hasOwnProperty.call(EQUIVALENTS, pathname)) {
        return EQUIVALENTS[pathname]
    }
    for (const [pattern, uri] of ENTITY_EQUIVALENTS) {
        const match = pattern.exec(pathname)
        if (match) return uri(match[1])
    }
    return null
}

/**
 * Human names for the desktop routes, so the interstitial can say what the user
 * was heading for rather than only showing them a path.
 *
 * Most specific first: the list is scanned in order.
 */
const DESCRIPTIONS = [
    // A project, a branch or a build only reaches the interstitial through a
    // path the patterns above do not match - `/project/abc`, say.
    [/^\/extension\/scm\/(.+\/)?changelog$/, 'a change log'],
    [/^\/extension\/scm\/[^/]+\/commit-info\//, 'a commit'],
    [/^\/extension\/scm\/[^/]+\/issue-info\//, 'an issue'],
    [/^\/extension\/environments\//, 'an environment'],
    [/^\/extension\/auto-versioning\//, 'auto-versioning'],
    [/^\/extension\/notifications\//, 'notifications'],
    [/^\/extension\/workflows\//, 'a workflow'],
    [/^\/extension\//, 'an extension page'],
    [/^\/core\/admin\//, 'an administration page'],
    [/^\/core\/config\//, 'a configuration page'],
    [/^\/core\/ref\//, 'a reference page'],
    [/^\/project\//, 'a project'],
    [/^\/branch\//, 'a branch'],
    [/^\/build\//, 'a build'],
    [/^\/promotionLevel\//, 'a promotion level'],
    [/^\/promotionRun\//, 'a promotion'],
    [/^\/validationStamp\//, 'a validation stamp'],
    [/^\/validationRun\//, 'a validation run'],
    // Reachable only through the interstitial's own fallback, when a `target`
    // parameter cannot be trusted and is replaced by the desktop home.
    [/^\/$/, 'the Yontrack home page'],
    [/^\/search$/, 'the search page'],
    [/^\/graphiql$/, 'GraphiQL'],
]

/**
 * What to call a desktop route in the interstitial.
 *
 * @param {string} pathname
 * @returns {string|null} A human description, or `null` when the route is not
 *   one we have a name for - the interstitial then names the raw path, which is
 *   still more use than a made-up label.
 */
export function describeDesktopRoute(pathname) {
    if (typeof pathname !== 'string') return null
    const entry = DESCRIPTIONS.find(([pattern]) => pattern.test(pathname))
    return entry ? entry[1] : null
}
