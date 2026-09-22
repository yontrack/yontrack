import EventDisplay from "@components/core/model/EventDisplay";
import {Space} from "antd";
import {extractProjectEntityInfo} from "@components/entities/ProjectEntityPageInfo";

export default function EventDetails({event}) {
    return (
        <>
            <Space orientation="vertical">
                <EventDisplay event={event.eventType.id}/>
                {
                    Object.keys(event.entities).map((entityType) => {
                        const entity = event.entities[entityType]
                        const info = extractProjectEntityInfo(entityType, entity)
                        if (info) {
                            return (
                                <>
                                    <Space>
                                        {info.type}
                                        {info.component}
                                    </Space>
                                </>
                            )
                        }
                    })
                }
            </Space>
        </>
    )
}