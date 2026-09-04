import {message, Popconfirm, Popover, Spin} from "antd";
import {FaTrashAlt} from "react-icons/fa";
import {usePromotionRunDeletion} from "@components/promotionRuns/usePromotionRunDeletion";

/**
 * Deletes a promotion run, behind a confirmation.
 *
 * The mutation itself lives in `usePromotionRunDeletion` so that a host which wants the capability
 * without this arrangement can take it - see ADR 0003.
 */
export default function PromotionRunDeleteAction({promotionRun, onDeletion}) {

    const [messageApi, contextHolder] = message.useMessage()

    const {deletePromotionRun, deleting} = usePromotionRunDeletion({
        onDeletion,
        // Without this a refused deletion is completely silent: the confirmation closes, the spinner
        // stops and the row simply stays, which reads as the click having missed.
        onError: (error) => messageApi.error(error ?? "Could not delete the promotion."),
    })

    return (
        <>
            {contextHolder}
            <Popover content="Deletes this promotion.">
                <Popconfirm
                    title="Deleting a promotion"
                    description="Are you sure to delete this promotion? This cannot be undone."
                    okText="Confirm deletion"
                    okType="danger"
                    onConfirm={() => deletePromotionRun(promotionRun)}
                >
                    {
                        deleting ?
                            <Spin size="small"/> :
                            <FaTrashAlt data-testid={`build-promotion-delete-${promotionRun.id}`} className="ot-command"/>
                    }

                </Popconfirm>
            </Popover>
        </>
    )
}
