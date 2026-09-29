import StandardPage from "@components/layouts/StandardPage";
import {CloseToHomeCommand} from "@components/common/Commands";
import {useRefresh} from "@components/common/RefreshUtils";
import PredefinedValidationStampsTable from "@components/core/config/PredefinedValidationStampsTable";
import PredefinedValidationStampCreateCommand from "@components/core/config/PredefinedValidationStampCreateCommand";

export default function PredefinedValidationStampsPage() {

    const [reloadState, reload] = useRefresh()

    return (
        <>
            <StandardPage
                pageTitle="Predefined validation stamps"
                commands={[
                    <PredefinedValidationStampCreateCommand key="create" onChange={reload}/>,
                    <CloseToHomeCommand key="home"/>,
                ]}
            >
                <PredefinedValidationStampsTable reloadState={reloadState}/>
            </StandardPage>
        </>
    )
}