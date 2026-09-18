import Head from "next/head";
import {pageTitle} from "@components/common/Titles";
import {homeBreadcrumbs} from "@components/common/Breadcrumbs";
import {CloseCommand} from "@components/common/Commands";
import {homeUri} from "@components/common/Links";
import MainPage from "@components/layouts/MainPage";
import EnvironmentsSetupCommand from "@components/extension/environments/EnvironmentsSetupCommand";
import EnvironmentMatrix from "@components/extension/environments/matrix/EnvironmentMatrix";
import {useMatrixFilter} from "@components/extension/environments/matrix/useMatrixFilter";

/**
 * The Environments home: the project x environment matrix.
 *
 * Same route as the list it replaces, so bookmarks, the user menu entry and the dashboard command
 * keep working. Two things left the page with the list: the "still under experiment" banner, which
 * now appears once in Setup rather than on every operational screen, and the "New environment" /
 * "New slot" commands, which moved behind Setup - see `EnvironmentsSetupCommand`.
 */
export default function EnvironmentsView() {

    const {filter, apply, ready} = useMatrixFilter()

    return (
        <>
            <Head>
                {pageTitle("Environments")}
            </Head>
            <MainPage
                title="Environments"
                breadcrumbs={homeBreadcrumbs()}
                commands={[
                    <EnvironmentsSetupCommand key="setup"/>,
                    <CloseCommand key="close" href={homeUri()}/>,
                ]}
            >
                {
                    // The filter comes from the URL, which Next fills on a second render: asking the
                    // server before it is known would show the defaults for a tick and then jump.
                    ready &&
                    <EnvironmentMatrix
                        filter={filter}
                        onFilter={apply}
                    />
                }
            </MainPage>
        </>
    )
}
