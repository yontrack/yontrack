import {useContext} from "react";
import StandardPage from "@components/layouts/StandardPage";
import {CloseToHomeCommand} from "@components/common/Commands";
import {useRefresh} from "@components/common/RefreshUtils";
import {UserContext} from "@components/providers/UserProvider";
import EstatesView from "@components/extension/scorecard/estates/EstatesView";
import {EstateCreateCommand} from "@components/extension/scorecard/estates/EstateCommands";
import EstateDialog, {useEstateDialog} from "@components/extension/scorecard/estates/EstateDialog";

/**
 * The estates admin page, reached from the configurations group of the user menu. Every user who
 * can see the estates can see the page; managing them needs the global `estate/edit`
 * authorization — the estate management, with the licence.
 */
export default function EstatesPage() {

    const user = useContext(UserContext)
    const canEdit = !!user?.authorizations?.estate?.edit

    const [refreshState, refresh] = useRefresh()

    // One dialog for the creation and the edition of every estate of the page
    const dialog = useEstateDialog({onSuccess: refresh})

    return (
        <StandardPage
            pageTitle="Estates"
            commands={[
                ...(canEdit ? [<EstateCreateCommand key="create" dialog={dialog}/>] : []),
                <CloseToHomeCommand key="home"/>,
            ]}
        >
            <EstatesView refreshState={refreshState} refresh={refresh} canEdit={canEdit} dialog={dialog}/>
            {
                canEdit &&
                <EstateDialog dialog={dialog}/>
            }
        </StandardPage>
    )
}
