import FormDialog, {useFormDialog} from "@components/form/FormDialog";
import {useContext, useState} from "react";
import {Alert, Empty, Form, Select, Space, Typography} from "antd";
import PropertyIcon from "@components/framework/properties/PropertyIcon";
import {FaAsterisk, FaPencilAlt} from "react-icons/fa";
import PropertyComponent from "@components/framework/properties/PropertyComponent";
import PropertyForm from "@components/framework/properties/PropertyForm";
import {gql} from "graphql-request";
import {EventsContext} from "@components/common/EventsContext";
import {callDynamicFunction} from "@components/common/DynamicFunction";
import {getExtensionShortName} from "@components/common/ExtensionUtils";

export const usePropertyDialog = () => {

    const eventsContext = useContext(EventsContext)

    const [options, setOptions] = useState([])
    const [selectedProperty, setSelectedProperty] = useState()

    const [entity, setEntity] = useState({})

    const customPreparation = async (type, values) => {
        const shortName = getExtensionShortName(type)
        const newValues = await callDynamicFunction(
            `framework/properties/${shortName}/FormPrepare`,
            values,
        )
        return newValues ?? values
    }

    return useFormDialog({
        init: (form, {entityType, entityId, propertyList, initialProperty}) => {
            form.setFieldValue("propertyType", initialProperty?.type?.typeName)
            form.setFieldValue("value", initialProperty?.clientValue)
            setEntity({entityType, entityId})
            setSelectedProperty(initialProperty)
            setOptions(
                propertyList.map(property => (
                    {
                        value: property.type.typeName,
                        label: <>
                            <Space>
                                <PropertyIcon property={property}/>
                                <Typography.Text>{property.type.name}</Typography.Text>
                                {
                                    property.value && <FaAsterisk title="Value set" color="green"/>
                                }
                                {
                                    property.editable && <FaPencilAlt title="Value editable" color="blue"/>
                                }
                            </Space>
                        </>
                    }
                ))
            )
        },
        options,
        selectedProperty,
        setSelectedProperty,
        entity,
        query: gql`
            mutation SaveProperty(
                $entityType: ProjectEntityType!,
                $entityId: Int!,
                $type: String!,
                $value: JSON!,
            ) {
                setGenericProperty(input: {
                    entityType: $entityType,
                    entityId: $entityId,
                    type: $type,
                    value: $value,
                }) {
                    errors {
                        message
                    }
                }
            }
        `,
        userNode: 'setGenericProperty',
        prepareValues: async (values, {entityType, entityId}) => {

            const type = selectedProperty?.type?.typeName

            const preparedValues = await customPreparation(type, values.value)

            return {
                entityType,
                entityId: Number(entityId),
                type: type,
                value: preparedValues,
            }
        },
        onSuccess: () => {
            eventsContext.fireEvent("entity.properties.changed", {entity})
        },
    })
}

export default function PropertyDialog({dialog}) {

    const onChangeProperty = (propertyType) => {
        const property = dialog.context.propertyList.find(it => it.type.typeName === propertyType)
        if (property) {
            dialog.form.setFieldValue("value", property.value)
        } else {
            dialog.form.setFieldValue("value", {})
        }
        dialog.setSelectedProperty(property)
    }

    return (
        <>
            <FormDialog id="property-dialog" dialog={dialog} width={800}>
                <Form.Item
                    name="propertyType"
                    label="Property"
                >
                    <Select
                        options={dialog.options}
                        onChange={onChangeProperty}
                        rules={[{required: true, message: 'Property is required.'}]}
                    />
                </Form.Item>
                {
                    dialog.selectedProperty && <>
                        <Form.Item>
                            <Typography.Text code>{dialog.selectedProperty.type.typeName}</Typography.Text>
                        </Form.Item>
                        <Form.Item>
                            <Typography.Text type="secondary">{dialog.selectedProperty.type.description}</Typography.Text>
                        </Form.Item>
                        {
                            !dialog.selectedProperty.editable && <>
                                <Space direction="vertical" className="ot-line">
                                    <Alert type="info" message="Non editable. Read only."/>
                                    {
                                        !dialog.selectedProperty.value && <Empty description="No value"/>
                                    }
                                    {
                                        dialog.selectedProperty.value && <PropertyComponent property={dialog.selectedProperty}/>
                                    }
                                </Space>
                            </>
                        }
                        {
                            dialog.selectedProperty.editable && <>
                                <PropertyForm property={dialog.selectedProperty} entity={dialog.entity} prefix="value" form={dialog.form}/>
                            </>
                        }
                    </>
                }
            </FormDialog>
        </>
    )
}