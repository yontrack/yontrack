import {useEffect} from "react";
import {gql} from "graphql-request";
import {useMutation} from "@components/services/GraphQL";

/**
 * Deleting a promotion run, as a primitive rather than as a component.
 *
 * Extracted so that a second host can delete a run without importing the whole
 * `PromotionRunDeleteAction` composition - the icon, the popover and the confirmation - which is the
 * ADR 0003 rule: hosts share the primitive, not the arrangement. It also retires one usage of the
 * deprecated `useGraphQLClient`, which is what the action was built on.
 *
 * `onDeletion` runs only when the mutation reports no user error, where the previous implementation
 * ran its callback on any completed request and reloaded a list from which nothing had been removed.
 * That precision is only an improvement if the failure is then SAID: a deletion which is refused -
 * the permission withdrawn, the run already gone - must not simply leave the row sitting there. So
 * `onError` is part of the contract, and it covers both kinds of failure, which arrive by different
 * routes: a `UserError` in the payload lands in `useMutation`'s error state, while a transport
 * failure is thrown out of it and would otherwise be an unhandled rejection.
 */
export const gqlDeletePromotionRun = gql`
    mutation DeletePromotionRun($promotionRunId: Int!) {
        deletePromotionRun(input: {promotionRunId: $promotionRunId}) {
            errors {
                message
            }
        }
    }
`

export function usePromotionRunDeletion({onDeletion, onError} = {}) {

    const {mutate, loading, error} = useMutation(gqlDeletePromotionRun, {
        userNodeName: 'deletePromotionRun',
        onSuccess: () => onDeletion?.(),
    })

    // A refusal by the server arrives as state rather than as a throw
    useEffect(() => {
        if (error) onError?.(error)
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [error])

    const deletePromotionRun = async (promotionRun) => {
        try {
            await mutate({promotionRunId: Number(promotionRun.id)})
        } catch (ex) {
            onError?.(ex?.message ?? String(ex))
        }
    }

    return {
        deletePromotionRun,
        deleting: loading,
        error,
    }
}
