import Link from "next/link";
import {useRouter} from "next/router";
import StandardPage from "@components/layouts/StandardPage";
import HomeLink from "@components/common/HomeLink";
import {CloseCommand} from "@components/common/Commands";
import {scorecardsUri} from "@components/common/Links";
import EstateScorecardView from "@components/extension/scorecard/estates/EstateScorecardView";

/**
 * The scorecard of an estate: its projects × its readings, against its targets.
 */
export default function EstateScorecardPage() {
    const router = useRouter()
    const {name} = router.query

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
            <EstateScorecardView name={name}/>
        </StandardPage>
    )
}
