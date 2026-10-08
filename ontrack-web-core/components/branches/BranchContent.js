import {Space} from "antd";
import ValidationStampFilterContextProvider
    from "@components/branches/filters/validationStamps/ValidationStampFilterContext";
import DisabledBranchBanner from "@components/branches/DisabledBranchBanner";
import {getBranchContentView} from "@components/branches/views/branchContentViews";
import {AutoRefreshContextProvider} from "@components/common/AutoRefresh";

/**
 * Content region of the branch view: picks the selected content view, renders it, and hands it only
 * the branch. Everything a content view needs beyond the branch, it fetches itself.
 *
 * What stays above the switch is what belongs to the branch rather than to any one content view: the
 * disabled banner, the validation stamp filter, and the auto refresh - which content views share so
 * that a user's filter, and a refresh they turned on, follow them when they switch view. Neither is
 * kept across a page reload: the auto refresh always starts off.
 *
 * @param branch Branch being displayed
 * @param viewKey Key of the selected content view
 */
export default function BranchContent({branch, viewKey}) {

    const ContentView = getBranchContentView(viewKey).component

    return (
        <>
            <Space orientation="vertical" className="ot-line">
                <DisabledBranchBanner branch={branch}/>
                <ValidationStampFilterContextProvider branch={branch}>
                    <AutoRefreshContextProvider>
                        <ContentView branch={branch}/>
                    </AutoRefreshContextProvider>
                </ValidationStampFilterContextProvider>
            </Space>
        </>
    )
}
