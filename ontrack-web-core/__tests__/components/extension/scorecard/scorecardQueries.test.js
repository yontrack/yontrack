import {parse} from "graphql";
import {gqlProjectScorecard} from "@components/extension/scorecard/project/ProjectScorecardView";
import {gqlProjectScorecardSummary} from "@components/extension/scorecard/project/ProjectScorecardSection";

/**
 * A fragment declared twice in a document fails the whole query on the server
 * (`DuplicateFragmentName`) - easy to do by adding `${gqlLabelFragment}` next to a fragment which
 * already carries it.
 */
const fragmentNames = (query) =>
    parse(query).definitions
        .filter(it => it.kind === 'FragmentDefinition')
        .map(it => it.name.value)

describe('Queries of the scorecard', () => {
    it.each([
        ['scorecard page', gqlProjectScorecard],
        ['Scorecard section', gqlProjectScorecardSummary],
    ])('the %s query declares each fragment once', (_, query) => {
        const names = fragmentNames(query)
        expect(names).toEqual([...new Set(names)])
    })
})
