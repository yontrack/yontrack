/**
 * The kinds of `ReadinessItem`, in the order the server declares them - which is the order a
 * "What's missing" list reads in: what a pipeline can still provide first, what needs a person last.
 */
export const readinessKinds = [
    {kind: 'VALIDATION', title: 'Validations'},
    {kind: 'PROMOTION', title: 'Promotions'},
    {kind: 'CHECK', title: 'Promotion checks'},
    {kind: 'ADMISSION_RULE', title: 'Admission rules'},
    {kind: 'MANUAL', title: 'A person must act'},
    {kind: 'AGENT_POLICY', title: 'Agent policy'},
]

/**
 * Groups the missing items of a readiness by kind.
 *
 * @param {?Array} missing The `missing` items of a `Readiness`
 * @return {Array} The non-empty groups, as `{kind, title, items}`, in the order of
 *   {@link readinessKinds}. A kind this UI does not know yet comes last, titled by the kind itself,
 *   rather than being dropped: a missing condition left out would read as "nothing missing".
 */
export const groupReadinessItems = (missing) => {
    const items = missing ?? []
    const known = readinessKinds.map(({kind, title}) => ({
        kind,
        title,
        items: items.filter(it => it.kind === kind),
    }))
    const unknownKinds = [...new Set(
        items.map(it => it.kind).filter(kind => !readinessKinds.some(it => it.kind === kind))
    )]
    const unknown = unknownKinds.map(kind => ({
        kind,
        title: kind,
        items: items.filter(it => it.kind === kind),
    }))
    return [...known, ...unknown].filter(group => group.items.length > 0)
}
