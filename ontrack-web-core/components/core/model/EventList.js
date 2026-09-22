import {Space} from "antd";
import EventDisplay from "@components/core/model/EventDisplay";

export default function EventList({events}) {
    return (
        <>
            <Space orientation="vertical">
                {
                    events.map((event, index) => (
                        <EventDisplay key={index} event={event}/>
                    ))
                }
            </Space>
        </>
    )
}