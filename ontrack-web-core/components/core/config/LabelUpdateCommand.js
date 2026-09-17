import {Command} from "@components/common/Commands";
import {FaPencilAlt} from "react-icons/fa";
import LabelDialog, {useLabelDialog} from "@components/core/config/LabelDialog";
import {labelDisplay} from "@components/labels/LabelChip";

export default function LabelUpdateCommand({label, onChange}) {

    const dialog = useLabelDialog({onSuccess: onChange})

    const onAction = () => {
        dialog.start({label})
    }

    return (
        <>
            <Command
                icon={<FaPencilAlt/>}
                title={`Edit the ${labelDisplay(label)} label`}
                testId={`label-edit-${labelDisplay(label)}`}
                action={onAction}
            />
            <LabelDialog dialog={dialog}/>
        </>
    )
}
