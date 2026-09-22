import {useState} from "react";
import {Modal, Typography} from "antd";
import ItemList from "@components/common/ItemList";
import Rows from "@components/common/Rows";
import ValidationRunStatus from "@components/validationRuns/ValidationRunStatus";
import Columns from "@components/common/Columns";
import ValidationRunLink from "@components/validationRuns/ValidationRunLink";
import ValidationStamp from "@components/validationStamps/ValidationStamp";
import Timestamp from "@components/common/Timestamp";
import AnnotatedDescription from "@components/common/AnnotatedDescription";
import ValidationRunHistoryDialog, {useValidationRunHistoryDialog} from "@components/validationRuns/ValidationRunHistoryDialog";

export function useValidationGroupDialog() {

    const [open, setOpen] = useState(false)
    /**
     * Group has the following attributes:
     *
     * * statusID - the status ID
     * * count - number of items in the group
     * * validations - validations attached to the group
     */
    const [group, setGroup] = useState({})

    const start = (group) => {
        setGroup(group)
        setOpen(true)
    }

    const close = () => {
        setOpen(false)
    }

    const validationRunHistoryDialog = useValidationRunHistoryDialog()

    const showRunHistory = (run) => {
        return () => {
            // Closes this dialog first
            close()
            // Then opens the run history dialog
            validationRunHistoryDialog.start(run)
        }
    }

    return {
        open,
        start,
        close,
        group,
        showRunHistory,
        validationRunHistoryDialog,
    }
}

export default function ValidationGroupDialog({dialog}) {

    const onOk = () => {
        dialog.close()
    }

    return (
        <>
            <Modal
                open={dialog.open}
                closable={false}
                destroyOnHidden={true}
                cancelButtonProps={{style: {display: 'none'}}}
                onOk={onOk}
                onCancel={onOk}
                width={600}
            >
                <Rows>
                    <Columns>
                        <ValidationRunStatus status={dialog.group} displayText={false} tooltip={false}/>
                        <Typography.Title level={4}>
                            {
                                `${dialog.group?.count} ${dialog.group?.statusID?.name} validations in build `
                            }
                        </Typography.Title>
                    </Columns>
                    <ItemList>
                        {
                            (dialog.group.validations ?? []).map((validation, index) =>
                                <ItemList.Item key={index}>
                                    <Columns>
                                        <ValidationStamp
                                            validationStamp={validation.validationStamp}
                                            displayTooltip={false}
                                            displayLink={false}
                                            onClick={dialog.showRunHistory(validation.validationRuns[0])}
                                        />
                                        <Timestamp
                                            prefix="Validated on"
                                            value={validation.validationRuns[0].lastStatus.creation.time}
                                        />
                                        <AnnotatedDescription
                                            entity={validation.validationRuns[0].lastStatus}
                                            disabled={false}
                                        />
                                    </Columns>
                                </ItemList.Item>
                            )
                        }
                    </ItemList>
                </Rows>
            </Modal>
            <ValidationRunHistoryDialog
                dialog={dialog.validationRunHistoryDialog}
                />
        </>
    )
}