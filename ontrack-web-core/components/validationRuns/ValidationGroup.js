import {Popover, Space} from "antd";
import ValidationRunStatus from "@components/validationRuns/ValidationRunStatus";
import ValidationGroupDialog, {useValidationGroupDialog} from "@components/validationRuns/ValidationGroupDialog";
import ValidationRunHistoryDialog, {
    useValidationRunHistoryDialog
} from "@components/validationRuns/ValidationRunHistoryDialog";
import ValidationChip from "@components/primitives/ValidationChip";

/**
 * One status bucket in the builds table's grouped validation column.
 *
 * A bucket holding a single run is drawn as the `ValidationChip` that run would
 * be anywhere else - stamp icon, stamp name, run state - rather than as a
 * hand-assembled copy of one, which is what this used to do.
 *
 * A bucket holding several keeps `ValidationRunStatus`: there is no stamp
 * identity to show, only a status and a count, and the status mark's shape is
 * worth more there than a chip's outline would be.
 */
export default function ValidationGroup({group}) {

    const dialog = useValidationGroupDialog()
    const validationRunHistoryDialog = useValidationRunHistoryDialog()

    const onClick = () => {
        dialog.start(group)
    }

    const showRunHistory = (run) => {
        validationRunHistoryDialog.start(run)
    }

    const single = group.count === 1 ? group.validations[0] : undefined

    return (
        <>
            <Space className="ot-validation-group">
                {
                    group.count > 1 &&
                    <ValidationRunStatus
                        status={group}
                        text={`${group.count} ${group.statusID.name}`}
                        tooltipContent={`${group.description}. Click to get more details.`}
                        onClick={onClick}
                    />
                }
                {
                    single &&
                    <Popover
                        title={group.statusID.name}
                        content={group.description}
                        placement="bottom"
                    >
                        <span>
                            <ValidationChip
                                id={`validation-group-${group.statusID.id}`}
                                validationStamp={single.validationStamp}
                                statusID={group.statusID}
                                onClick={() => showRunHistory(single.validationRuns[0])}
                            />
                        </span>
                    </Popover>
                }
            </Space>
            <ValidationGroupDialog dialog={dialog}/>
            <ValidationRunHistoryDialog dialog={validationRunHistoryDialog}/>
        </>
    )
}
