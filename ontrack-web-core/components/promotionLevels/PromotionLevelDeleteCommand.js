import {FaTrash} from "react-icons/fa";
import {Typography} from "antd";
import {gql} from "graphql-request";
import {usePromotionLevelById} from "@components/services/fragments";
import {useRouter} from "next/router";
import {branchUri} from "@components/common/Links";
import ConfirmCommand from "@components/common/ConfirmCommand";

export default function PromotionLevelDeleteCommand({id}) {

    const {promotionLevel} = usePromotionLevelById({id})

    const router = useRouter()

    const onSuccess = () => {
        router.push(branchUri(promotionLevel.branch))
    }

    return (
        <>
            <ConfirmCommand
                icon={<FaTrash/>}
                text="Delete promotion level"
                confirmTitle={`Do you really want to delete the "${promotionLevel?.name}" promotion level?`}
                confirmText={
                    <Typography.Text>All data associated with the <b>{promotionLevel?.name}</b> promotion level will be
                        gone. This cannot be cancelled.</Typography.Text>
                }
                confirmOkText="Delete"
                confirmOkType="danger"
                gqlQuery={
                    gql`
                        mutation DeletePromotionLevel($id: Int!) {
                            deletePromotionLevelById(input: {id: $id}) {
                                errors {
                                    message
                                }
                            }
                        }
                    `
                }
                gqlVariables={{id: Number(id)}}
                gqlUserNode="deletePromotionLevelById"
                onSuccess={onSuccess}
            />
        </>
    )
}