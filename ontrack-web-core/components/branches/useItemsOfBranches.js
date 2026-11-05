import {gql} from "graphql-request";
import {useQuery} from "@components/services/GraphQL";

const NO_ITEMS = []

/**
 * Items of one branch, or the union by name of the items of several branches, in one query.
 *
 * @param name Name of the query
 * @param field Field of a branch holding the items
 * @param selection Selection of the fields of an item
 * @param branch One branch
 * @param branches Several branches, in place of `branch`
 * @return `{items, error}`
 */
export default function useItemsOfBranches({name, field, selection, branch, branches}) {
    const targets = branches ?? (branch ? [branch] : [])
    const variable = (index) => index === 0 ? 'branchId' : `branchId${index}`
    const alias = (index) => index === 0 ? '' : `branch${index}: `
    const variables = targets.map((_, index) => `$${variable(index)}: Int!`).join(', ')
    const queries = targets.map((_, index) =>
        `${alias(index)}branches(id: $${variable(index)}) { ${field} { ${selection} } }`
    ).join('\n')
    const {data: items, error} = useQuery(
        gql`
            query ${name}(${variables}) {
                ${queries}
            }
        `,
        {
            variables: Object.fromEntries(targets.map((target, index) => [variable(index), Number(target.id)])),
            deps: [targets.map(target => target.id).join(',')],
            condition: targets.length > 0,
            dataFn: data => {
                const union = new Map()
                Object.values(data).flat().flatMap(it => it[field]).forEach(item => {
                    if (!union.has(item.name)) union.set(item.name, item)
                })
                return [...union.values()]
            },
        }
    )
    return {items: items ?? NO_ITEMS, error}
}
