import {Command} from "@components/common/Commands";
import {FaPlus} from "react-icons/fa";
import LabelDialog, {useLabelDialog} from "@components/core/config/LabelDialog";

export default function LabelCreateCommand({onChange}) {

    const dialog = useLabelDialog({onSuccess: onChange})

    const onCreate = () => {
        dialog.start({})
    }

    return (
        <>
            <Command
                icon={<FaPlus/>}
                text="New label"
                action={onCreate}
            />
            <LabelDialog dialog={dialog}/>
        </>
    )
}
