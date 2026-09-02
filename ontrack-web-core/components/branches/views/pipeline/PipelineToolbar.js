import {Button, Popover, Space, Spin, Typography} from "antd";
import {useRouter} from "next/router";
import BuildFilterDropdown from "@components/branches/filters/builds/BuildFilterDropdown";
import ValidationStampFilterDropdown from "@components/branches/filters/validationStamps/ValidationStampFilterDropdown";
import {scmChangeLogUri} from "@components/common/Links";

/**
 * The controls which act on the whole view: which builds it shows, which validations it shows, and
 * the change log between two of the builds it shows.
 *
 * THE CHANGE LOG BUTTON IS HERE ON PURPOSE. Range selection is one of the branch page's most
 * valuable features and the pipeline view is meant to become the default way to read a branch; a
 * default which silently dropped a shipped feature would be a regression dressed up as a redesign.
 * The boundaries are marked on the timeline cards, and this is where the pair is spent.
 *
 * There is no Timeline / Table / Compare tab strip: which view you are reading is one axis of
 * choice, and it is owned by the page's own view menu. "Compare" would be a second answer to the
 * question the change log already answers.
 *
 * @param branch Branch being displayed
 * @param selectedBuildFilter The active build filter, if any
 * @param onSelectedBuildFilter Called when the user picks a build filter
 * @param onPermalinkBuildFilter Called when the user asks for a permalink to the build filter
 * @param scmChangeLogEnabled Whether the branch's SCM can produce a change log
 * @param rangeSelection Range selection state, fed by the timeline cards
 * @param loading Whether a page of builds is in flight
 */
export default function PipelineToolbar({
                                            branch,
                                            selectedBuildFilter,
                                            onSelectedBuildFilter,
                                            onPermalinkBuildFilter,
                                            scmChangeLogEnabled,
                                            rangeSelection,
                                            loading,
                                        }) {

    const router = useRouter()

    const onChangeLog = () => {
        if (scmChangeLogEnabled && rangeSelection.isComplete()) {
            const [from, to] = rangeSelection.selection
            router.push(scmChangeLogUri(from, to))
        }
    }

    return (
        <Space wrap data-testid="pipeline-toolbar">
            <BuildFilterDropdown
                branch={branch}
                selectedBuildFilter={selectedBuildFilter}
                onSelectedBuildFilter={onSelectedBuildFilter}
                onPermalink={onPermalinkBuildFilter}
            />
            {
                scmChangeLogEnabled &&
                <Popover
                    title="Change log between two builds"
                    content={
                        (!rangeSelection || !rangeSelection.isComplete()) &&
                        <Typography.Text disabled>
                            Select two builds in order to get their change log
                        </Typography.Text>
                    }
                >
                    <Button
                        disabled={!rangeSelection || !rangeSelection.isComplete()}
                        onClick={onChangeLog}
                    >
                        <Typography.Text>Change log</Typography.Text>
                    </Button>
                </Popover>
            }
            <ValidationStampFilterDropdown branch={branch}/>
            {
                loading &&
                <Popover data-testid="loading-builds" content="Loading builds...">
                    <Spin/>
                </Popover>
            }
        </Space>
    )
}
