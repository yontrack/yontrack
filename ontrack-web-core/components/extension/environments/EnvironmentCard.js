import {Card, Flex, Space} from "antd";
import EnvironmentTitle from "@components/extension/environments/EnvironmentTitle";
import SlotTitle from "@components/extension/environments/SlotTitle";
import SlotLink from "@components/extension/environments/SlotLink";
import DeleteEnvironmentButton from "@components/extension/environments/DeleteEnvironmentButton";
import SlotCell from "@components/extension/environments/shared/SlotCell";

/**
 * One environment and its slots.
 *
 * The slot cards carry the shared [SlotCell] and open the slot drawer on a click (#1797), so the
 * quick view is reachable from the environments list before the matrix that replaces this screen
 * arrives in phase 2. The card's own title and slot link stay as they are: this screen is on its
 * way out, and re-laying it out would be work thrown away.
 *
 * @param {function} onSlotClick Called with a slot when its card is activated. Without it the cells
 *   are inert, which is what the dashboard widgets embedding this card want.
 */
export default function EnvironmentCard({environment, showSlots = true, onSlotClick}) {
    return (
        <>
            <Card
                style={{height: '100%'}}
                headStyle={{
                    background: 'linear-gradient(to right, var(--ot-bg-subtle), var(--ot-bg-subtle-alt))'
                }}
                size="small"
                data-testid={`environment-${environment.id}`}
                title={<EnvironmentTitle environment={environment}/>}
                extra={<DeleteEnvironmentButton environment={environment}/>}
            >
                {
                    showSlots && <Space>
                        {
                            environment.slots.map(slot => (
                                <Card
                                    key={slot.id}
                                    style={{height: '100%'}}
                                    size="small"
                                    bodyStyle={{
                                        background: 'linear-gradient(to right, var(--ot-bg-subtle), var(--ot-bg-subtle-alt))'
                                    }}
                                    hoverable={true}
                                >
                                    <Flex vertical={true} gap={4}>
                                        <Flex justify="space-between" align="center" gap={16}>
                                            <SlotTitle slot={slot}/>
                                            <SlotLink slot={slot} text="Slot"/>
                                        </Flex>
                                        <SlotCell slot={slot} onClick={onSlotClick}/>
                                    </Flex>
                                </Card>
                            ))
                        }
                    </Space>
                }
            </Card>
        </>
    )
}
