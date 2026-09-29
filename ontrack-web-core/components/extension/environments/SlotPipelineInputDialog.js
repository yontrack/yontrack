import FormDialog, {useFormDialog} from "@components/form/FormDialog";
import LoadingContainer from "@components/common/LoadingContainer";
import {callGraphQL} from "@components/services/GraphQL";
import {useState} from "react";
import {gql} from "graphql-request";
import {Space} from "antd";
import SlotPipelineInput from "@components/extension/environments/SlotPipelineInput";

export const useSlotPipelineInputDialog = (ruleConfigId) => {

    const [loading, setLoading] = useState(true)
    const [inputs, setInputs] = useState([])

    return useFormDialog({
        loading,
        inputs,
        init: (form, {pipeline}) => {
            setLoading(true)
            callGraphQL({
                query: gql`
                    query PipelineInput($id: String!) {
                        slotPipelineById(id: $id) {
                            requiredInputs {
                                config {
                                    id
                                    name
                                    description
                                    ruleId
                                    ruleConfig
                                }
                            }
                        }
                    }
                `,
                variables: {id: pipeline.id},
            }).then(data => {
                let inputs = data.slotPipelineById.requiredInputs;
                if (ruleConfigId) {
                    inputs = inputs.filter(it => it.config.id === ruleConfigId)
                }
                setInputs(inputs)
            }).finally(() => {
                setLoading(false)
            })
        },
        prepareValues: (values, {pipeline}) => {
            const inputs = []
            Object.keys(values).forEach(configId => {
                const data = values[configId]
                inputs.push({
                    configId,
                    data,
                })
            })
            return {
                pipelineId: pipeline.id,
                values: inputs,
            }
        },
        query: gql`
            mutation PipelineInput(
                $pipelineId: String!,
                $values: [SlotPipelineDataInputValue!]!,
            ) {
                updatePipelineData(input: {
                    pipelineId: $pipelineId,
                    values: $values,
                }) {
                    errors {
                        message
                    }
                }
            }
        `,
        userNode: 'updatePipelineData',
        onSuccess: (result, {onChange}) => {
            if (onChange) onChange()
        }
    })
}

export default function SlotPipelineInputDialog({dialog}) {
    return (
        <>
            <FormDialog dialog={dialog}>
                <LoadingContainer loading={dialog.loading}>
                    <Space orientation="vertical" className="ot-line">
                        {
                            dialog.inputs.map((input, index) => (
                                <SlotPipelineInput
                                    key={index}
                                    input={input}
                                />
                            ))
                        }
                    </Space>
                </LoadingContainer>
            </FormDialog>
        </>
    )
}