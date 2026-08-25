import ListSection from "@components/common/ListSection";
import {FaWrench} from "react-icons/fa";
import PropertyAddButton from "@components/core/model/properties/PropertyAddButton";
import PropertyIcon from "@components/framework/properties/PropertyIcon";
import PropertyComponent from "@components/framework/properties/PropertyComponent";
import {useEffect, useState} from "react";
import {useGraphQLClient} from "@components/providers/ConnectionContextProvider";
import {gql} from "graphql-request";
import {useEventForRefresh} from "@components/common/EventsContext";
import PropertyTitle from "@components/framework/properties/PropertyTitle";
import {callDynamicFunction} from "@components/common/DynamicFunction";
import {getExtensionShortName} from "@components/common/ExtensionUtils";

export default function PropertiesSection({entityType, entityId}) {

    const client = useGraphQLClient()
    const [propertyList, setPropertyList] = useState([])

    const refreshCount = useEventForRefresh("entity.properties.changed")

    useEffect(() => {

        const fetchPropertyList = async () => {
            const data = await client.request(
                gql`
                    query EntityProperties(
                        $type: ProjectEntityType!,
                        $id: Int!,
                    ) {
                        entity(type: $type, id: $id) {
                            properties {
                                editable
                                type {
                                    typeName
                                    name
                                    description
                                }
                                value
                            }
                        }
                    }
                `,
                {
                    type: entityType,
                    id: Number(entityId),
                }
            )

            const initialProperties = data.entity.properties
                .sort((a, b) => a.type.name.localeCompare(b.type.name))

            const transformedProperties = await Promise.all(
                initialProperties.map(async (property) => {
                    if (property.value) {
                        const shortName = getExtensionShortName(property.type.typeName)
                        const newValue = await callDynamicFunction(
                            `framework/properties/${shortName}/Prepare`,
                            property.value
                        ) ?? property.value
                        return {
                            ...property,
                            clientValue: newValue,
                        }
                    } else {
                        return property
                    }
                })
            )

            setPropertyList(transformedProperties)
        }

        if (client) {
            // noinspection JSIgnoredPromiseFromCall
            fetchPropertyList()
        }
    }, [client, entityType, entityId, refreshCount])

    return (
        <ListSection
            icon={<FaWrench/>}
            title="Properties"
            extraTitle={
                <>
                    <PropertyAddButton entityType={entityType} entityId={entityId} propertyList={propertyList}/>
                </>
            }
            items={
                propertyList
                    .filter(it => it.value)
                    .map(property => {
                        return {
                            id: `property-${getExtensionShortName(property.type.typeName)}`,
                            title: <PropertyTitle entityType={entityType} entityId={entityId} property={property}/>,
                            icon: <PropertyIcon property={property}/>,
                            content: <PropertyComponent
                                property={property}
                                entityType={entityType}
                                entityId={entityId}
                            />,
                        }
                    })
            }
        />
    )
}