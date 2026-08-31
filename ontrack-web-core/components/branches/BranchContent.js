import {Space} from "antd";
import ValidationStampFilterContextProvider
    from "@components/branches/filters/validationStamps/ValidationStampFilterContext";
import DisabledBranchBanner from "@components/branches/DisabledBranchBanner";
import {getBranchContentView} from "@components/branches/views/branchContentViews";

/**
 * Content region of the branch view: picks the selected content view, renders it, and hands it only
 * the branch. Everything a content view needs beyond the branch, it fetches itself.
 *
 * What stays above the switch is what belongs to the branch rather than to any one content view: the
 * disabled banner, and the validation stamp filter, which content views share so that a user's filter
 * follows them when they switch view.
 *
 * @param branch Branch being displayed
 * @param viewKey Key of the selected content view
 */
export default function BranchContent({branch, viewKey}) {

    const ContentView = getBranchContentView(viewKey).component

    return (
        <>
            <Space direction="vertical" className="ot-line">
                <DisabledBranchBanner branch={branch}/>
                <ValidationStampFilterContextProvider branch={branch}>
                    <ContentView branch={branch}/>
                </ValidationStampFilterContextProvider>
            </Space>
        </>
    )
}
