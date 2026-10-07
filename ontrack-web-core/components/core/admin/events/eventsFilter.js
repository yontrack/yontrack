import {
    branchUri,
    buildUri,
    projectUri,
    promotionLevelUri,
    promotionRunUri,
    validationRunUri,
    validationStampUri,
} from "@components/common/Links";

/**
 * Turns the values of the filter form of the events page into the variables of the `events`
 * query. A blank or empty value gives no variable: an empty form lists every event.
 *
 * @param range Date-time range, as given by antd's `RangePicker` (two `dayjs` values, either
 * of which may be missing)
 * @param user Prefix of the user name
 * @param eventTypes IDs of event types
 * @param project Name of a project
 */
export const eventsFilterVariables = ({range, user, eventTypes, project} = {}) => {
    const variables = {}
    const [from, to] = range ?? []
    if (from) variables.from = from.toISOString()
    if (to) variables.to = to.toISOString()
    const trimmedUser = user?.trim()
    if (trimmedUser) variables.user = trimmedUser
    if (eventTypes?.length > 0) variables.eventTypes = eventTypes
    if (project) variables.project = project
    return variables
}

const entityUris = {
    PROJECT: projectUri,
    BRANCH: branchUri,
    PROMOTION_LEVEL: promotionLevelUri,
    VALIDATION_STAMP: validationStampUri,
    BUILD: buildUri,
    PROMOTION_RUN: promotionRunUri,
    VALIDATION_RUN: validationRunUri,
}

/**
 * Link to the page of an entity an event is about. The `events` query gives the type, the ID
 * and the display name of the entity only, the display name naming the type (`Branch P/X`).
 *
 * @return `href` (null for an unknown type) and `text` of the link
 */
export const eventEntityLink = ({type, id, displayName}) => {
    const uri = entityUris[type]
    return {
        href: uri ? uri({id}) : null,
        text: displayName,
    }
}
