import {Command} from "@components/common/Commands";
import {FaListUl} from "react-icons/fa";
import PromotionLevelFieldsEditor, {usePromotionLevelFieldsEditor} from "@components/promotionLevels/PromotionLevelFieldsEditor";
import {useContext} from "react";
import {EventsContext} from "@components/common/EventsContext";

export default function PromotionLevelFieldsCommand({promotionLevel}) {

    const eventsContext = useContext(EventsContext)

    const fieldsEditor = usePromotionLevelFieldsEditor({
        onSuccess: () => {
            eventsContext.fireEvent("promotionLevel.updated", {id: promotionLevel.id})
        }
    })

    const onAction = () => {
        fieldsEditor.start(promotionLevel)
    }

    return (
        <>
            <Command
                icon={<FaListUl/>}
                text="Manage fields"
                action={onAction}
            />
            <PromotionLevelFieldsEditor fieldsEditor={fieldsEditor}/>
        </>
    )
}
