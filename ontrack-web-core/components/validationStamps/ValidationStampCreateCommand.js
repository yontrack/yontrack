import {Command} from "@components/common/Commands";
import {FaPlus} from "react-icons/fa";
import ValidationStampCreateDialog, {
    useValidationStampCreateDialog
} from "@components/validationStamps/ValidationStampCreateDialog";

export default function ValidationStampCreateCommand({branch}) {

    const dialog = useValidationStampCreateDialog()

    const onClick = () => {
        dialog.start({branch})
    }

    return (
        <>
            <Command
                icon={<FaPlus/>}
                text="Create validation stamp"
                action={onClick}
            />
            <ValidationStampCreateDialog dialog={dialog}/>
        </>
    )
}
