import {FaTrash} from "react-icons/fa";
import {Typography} from "antd";
import {gql} from "graphql-request";
import {useValidationStampById} from "@components/services/fragments";
import {useRouter} from "next/router";
import {branchUri} from "@components/common/Links";
import ConfirmCommand from "@components/common/ConfirmCommand";

export default function ValidationStampDeleteCommand({id}) {

    const {validationStamp} = useValidationStampById({id})

    const router = useRouter()

    const onSuccess = () => {
        router.push(branchUri(validationStamp.branch))
    }

    return (
        <>
            <ConfirmCommand
                icon={<FaTrash/>}
                text="Delete validation stamp"
                confirmTitle={`Do you really want to delete the "${validationStamp?.name}" validation stamp?`}
                confirmText={
                    <Typography.Text>All data associated with the <b>{validationStamp?.name}</b> validation stamp will be
                        gone. This cannot be cancelled.</Typography.Text>
                }
                confirmOkText="Delete"
                confirmOkType="danger"
                gqlQuery={
                    gql`
                        mutation DeleteValidationStamp($id: Int!) {
                            deleteValidationStampById(input: {id: $id}) {
                                errors {
                                    message
                                }
                            }
                        }
                    `
                }
                gqlVariables={{id: Number(id)}}
                gqlUserNode="deleteValidationStampById"
                onSuccess={onSuccess}
            />
        </>
    )
}