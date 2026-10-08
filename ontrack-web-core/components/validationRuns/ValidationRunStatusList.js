import {Space, Typography} from "antd";
import ItemList from "@components/common/ItemList";
import TimestampText from "@components/common/TimestampText";
import ValidationRunStatus from "@components/validationRuns/ValidationRunStatus";
import AnnotatedDescription from "@components/common/AnnotatedDescription";
import {isAuthorized} from "@components/common/authorizations";
import {gql} from "graphql-request";
import {callGraphQL} from "@components/services/GraphQL";
import ActorBadge from "@components/common/actors/ActorBadge";

export default function ValidationRunStatusList({run, onRunChanged}) {

    const replaceStatusCommentInRun = (vrsId, description, annotatedDescription) => {
        const newRun = {
            ...run,
            validationRunStatuses: run.validationRunStatuses.map(vrs => {
                if (vrs.id === vrsId) {
                    return {
                        ...vrs,
                        description,
                        annotatedDescription,
                    }
                } else {
                    return vrs
                }
            }),
        }
        if (onRunChanged) onRunChanged(newRun)
    }

    const editStatusComment = async (vrs, text) => {
        const data = await callGraphQL({
            query: gql`
                mutation ChangeStatusComment(
                    $validationRunStatusId: Int!,
                    $comment: String!,
                ) {
                    changeValidationRunStatusComment(input: {
                        validationRunStatusId: $validationRunStatusId,
                        comment: $comment,
                    }) {
                        validationRun {
                            validationRunStatuses {
                                id
                                description
                                annotatedDescription
                            }
                        }
                        errors {
                            message
                        }
                    }
                }
            `,
            variables: {
                validationRunStatusId: Number(vrs.id),
                comment: text,
            },
        })
        // Gets the text of the changed status
        const newVrs = data.changeValidationRunStatusComment.validationRun
            .validationRunStatuses
            .find(it => it.id === vrs.id)
        // Refreshes only the status
        if (newVrs) {
            const {description, annotatedDescription} = newVrs
            replaceStatusCommentInRun(vrs.id, description, annotatedDescription)
        }
    }

    return (
        <>
            <ItemList>
                {
                    run.validationRunStatuses.map(vrs => (
                        <ItemList.Item
                            key={vrs.id}
                            style={{padding: 8, paddingLeft: 24}}
                            actions={[
                                <Typography.Text
                                    key={vrs.id}
                                    type="secondary"
                                    italic
                                    style={{fontSize: '75%'}}
                                    data-testid={`validation-run-status-signature-${vrs.id}`}
                                >
                                    <ActorBadge signature={vrs.creation} testId={`validation-run-status-actor-${vrs.id}`}/>
                                    {' @ '}
                                    <TimestampText value={vrs.creation.time}/>
                                </Typography.Text>
                            ]}
                            title={
                                <Space>
                                    <ValidationRunStatus
                                        status={vrs}
                                        displayText={false}
                                        tooltip={false}
                                    />
                                    <Typography.Text
                                        strong>{vrs.statusID.name}</Typography.Text>
                                </Space>
                            }
                            description={
                                <AnnotatedDescription
                                    entity={vrs}
                                    disabled={false}
                                    editable={isAuthorized(vrs, 'validation_run_status', 'comment_change')}
                                    onChange={(text) => editStatusComment(vrs, text)}
                                />
                            }
                        />
                    ))
                }
            </ItemList>
        </>
    )
}