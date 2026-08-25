/**
 * The stored property carries validation stamps & promotion levels as full objects, whose `id` is a
 * number. The selectors build their options from GraphQL, where those ids are `ID!` and therefore
 * strings. Coercing here is what makes the existing values actually pre-select in the edit form.
 */
export default function prepare(value) {
    return {
        ...value,
        validationStamps: value.validationStamps?.map(vs => String(vs.id)) ?? [],
        promotionLevels: value.promotionLevels?.map(pl => String(pl.id)) ?? [],
    }
}
