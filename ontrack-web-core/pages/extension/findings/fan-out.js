import {useRouter} from "next/router";
import StandardPage from "@components/layouts/StandardPage";
import HomeLink from "@components/common/HomeLink";
import {CloseCommand} from "@components/common/Commands";
import {homeUri} from "@components/common/Links";
import FindingAcrossProjectsView from "@components/extension/findings/FindingAcrossProjectsView";
import {fanOutPageQuery, fanOutPageState} from "@components/extension/findings/findingFanOutModel";

/**
 * One finding across all the projects: which ones report it, on which branches, since when.
 */
export default function FindingAcrossProjectsPage() {
    const router = useRouter()
    const {finding, search} = fanOutPageState(router.query)

    // The finding and the search are in the URL, so that they can be shared by its link - replaced,
    // not pushed, without a full navigation: changing them is not a navigation to go back from
    const onChange = (state) => {
        router.replace(
            {pathname: router.pathname, query: fanOutPageQuery(state)},
            undefined,
            {shallow: true},
        )
    }

    return (
        <StandardPage
            pageTitle="Finding across projects"
            breadcrumbs={[<HomeLink key="home"/>]}
            commands={[<CloseCommand key="close" href={homeUri()}/>]}
        >
            <FindingAcrossProjectsView finding={finding} search={search} onChange={onChange}/>
        </StandardPage>
    )
}
