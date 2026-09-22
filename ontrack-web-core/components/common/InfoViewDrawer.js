import {Badge, Drawer, Space, theme} from "antd";
import {startTransition, useState} from "react";
import {FaInfoCircle} from "react-icons/fa";
import {Command} from "@components/common/Commands";
import PropertiesSection from "@components/framework/properties/PropertiesSection";
import InformationSection from "@components/framework/information/InformationSection";

/**
 * Does the entity carry anything worth opening its details for?
 */
export const hasEntityDetails = ({properties, information}) =>
    (properties ?? []).some(it => it.value) ||
    (information ?? []).some(it => it.data)

/**
 * "Details" command of an entity page header, opening a drawer with the
 * properties and the additional information of the entity.
 *
 * @param id Test ID of the command
 * @param entityType Type of the entity, like `BUILD`
 * @param entityName Human name of the entity type, like `build`
 * @param entity Entity, with its `properties { value }` and `information`
 * @param width Width of the drawer
 */
export default function InfoViewDrawer({id, entityType, entityName, entity, width = "33%"}) {

    const {token} = theme.useToken()

    const [expanded, setExpanded] = useState(false)

    // Properties as reloaded by the drawer after a change, only for the entity they were loaded for
    const [reloaded, setReloaded] = useState({entityId: null, properties: null})
    const properties = reloaded.entityId === entity.id ? reloaded.properties : entity.properties

    const dot = hasEntityDetails({properties, information: entity.information})

    const toggleExpanded = () => {
        startTransition(() => {
            setExpanded(!expanded)
        })
    }

    const onPropertiesLoaded = (propertyList) => {
        setReloaded({entityId: entity.id, properties: propertyList})
    }

    const icon = <FaInfoCircle/>

    return (
        <>
            <Command
                testId={id}
                icon={
                    dot ?
                        <Badge dot color={token.colorPrimary} data-testid={`${id}-dot`}>{icon}</Badge> :
                        icon
                }
                text="Details"
                title={`Properties and additional information about this ${entityName}`}
                action={toggleExpanded}
            />
            <Drawer
                title={`${entityName.charAt(0).toUpperCase()}${entityName.slice(1)} details`}
                placement="right"
                open={expanded}
                onClose={toggleExpanded}
                size={width}
            >
                <Space orientation="vertical" size={16} className="ot-line">
                    <PropertiesSection
                        entityType={entityType}
                        entityId={entity.id}
                        onPropertiesLoaded={onPropertiesLoaded}
                    />
                    <InformationSection
                        entity={entity}
                        loading={false}
                    />
                </Space>
            </Drawer>
        </>
    )
}
