import StandardPage from "@components/layouts/StandardPage";
import {CloseToHomeCommand} from "@components/common/Commands";
import PredefinedPromotionLevelsTable from "@components/core/config/PredefinedPromotionLevelsTable";
import PredefinedPromotionLevelCreateCommand from "@components/core/config/PredefinedPromotionLevelCreateCommand";
import {useRefresh} from "@components/common/RefreshUtils";

export default function PredefinedPromotionLevelsPage() {

    const [reloadState, reload] = useRefresh()

    return (
        <>
            <StandardPage
                pageTitle="Predefined promotion levels"
                commands={[
                    <PredefinedPromotionLevelCreateCommand key="create" onChange={reload}/>,
                    <CloseToHomeCommand key="home"/>,
                ]}
            >
                <PredefinedPromotionLevelsTable reloadState={reloadState}/>
            </StandardPage>
        </>
    )
}