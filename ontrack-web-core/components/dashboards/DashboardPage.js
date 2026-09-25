import MainPage from "@components/layouts/MainPage";
import DashboardContextProvider from "@components/dashboards/DashboardContextProvider";
import DashboardView from "@components/dashboards/DashboardView";
import DashboardCommandMenu from "@components/dashboards/DashboardCommandMenu";
import GridTableContextProvider from "@components/grid/GridTableContext";
import NewProjectCommand from "@components/projects/NewProjectCommand";
import DashboardPageTitle from "@components/dashboards/DashboardPageTitle";
import EnvironmentsCommand from "@components/extension/environments/EnvironmentsCommand";

export default function DashboardPage({title}) {

    const commands = [
        <NewProjectCommand key="create-project"/>,
        <EnvironmentsCommand key="environments"/>,
        <DashboardCommandMenu key="dashboard"/>,
    ]

    return (
        <>
            <GridTableContextProvider>
                <DashboardContextProvider>
                    <MainPage
                        title={
                            <DashboardPageTitle title={title}/>
                        }
                        breadcrumbs={[]}
                        commands={commands}
                    >
                        <DashboardView/>
                    </MainPage>
                </DashboardContextProvider>
            </GridTableContextProvider>
        </>
    )
}