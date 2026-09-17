import StandardPage from "@components/layouts/StandardPage";
import {CloseToHomeCommand} from "@components/common/Commands";
import {useRefresh} from "@components/common/RefreshUtils";
import LabelsTable from "@components/core/config/LabelsTable";
import LabelCreateCommand from "@components/core/config/LabelCreateCommand";

export default function LabelsPage() {

    const [refreshState, refresh] = useRefresh()

    return (
        <>
            <StandardPage
                pageTitle="Labels"
                commands={[
                    <LabelCreateCommand key="create" onChange={refresh}/>,
                    <CloseToHomeCommand key="home"/>,
                ]}
            >
                <LabelsTable refreshState={refreshState} refresh={refresh}/>
            </StandardPage>
        </>
    )
}
