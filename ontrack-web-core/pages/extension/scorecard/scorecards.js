import StandardPage from "@components/layouts/StandardPage";
import ScorecardsView from "@components/extension/scorecard/estates/ScorecardsView";

/**
 * The scorecards of the estates, reached from the information group of the user menu: the estates,
 * each linking to its scorecard. Licensed, like the estates.
 */
export default function ScorecardsPage() {
    return (
        <StandardPage pageTitle="Scorecards">
            <ScorecardsView/>
        </StandardPage>
    )
}
