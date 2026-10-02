import Link from "next/link";
import {useRouter} from "next/router";
import StandardPage from "@components/layouts/StandardPage";
import HomeLink from "@components/common/HomeLink";
import {CloseCommand} from "@components/common/Commands";
import {scorecardsUri} from "@components/common/Links";
import EstateScorecardView from "@components/extension/scorecard/estates/EstateScorecardView";
import {estatePageQuery, estatePageState} from "@components/extension/scorecard/estates/estateViewModel";

/**
 * The scorecard of an estate: its projects × its readings, against its targets.
 */
export default function EstateScorecardPage() {
    const router = useRouter()
    const {name} = router.query
    const {tab, finding} = estatePageState(router.query)

    // The tab and the finding searched are in the URL, so that a fan-out can be shared by its link -
    // replaced, not pushed, without a full navigation: changing them is not a navigation to go back from
    const onChange = (state) => {
        router.replace(
            {pathname: router.pathname, query: {name, ...estatePageQuery(state)}},
            undefined,
            {shallow: true},
        )
    }

    return (
        <StandardPage
            pageTitle={name ? `Scorecard of ${name}` : 'Scorecard'}
            breadcrumbs={[
                <HomeLink key="home"/>,
                <Link key="scorecards" href={scorecardsUri()}>Scorecards</Link>,
            ]}
            commands={[
                <CloseCommand key="close" href={scorecardsUri()}/>,
            ]}
        >
            <EstateScorecardView name={name} tab={tab} finding={finding} onChange={onChange}/>
        </StandardPage>
    )
}
