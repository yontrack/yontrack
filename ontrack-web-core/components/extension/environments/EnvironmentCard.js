import {Card, Flex, Space} from "antd";
import EnvironmentTitle from "@components/extension/environments/EnvironmentTitle";
import SlotTitle from "@components/extension/environments/SlotTitle";
import SlotLink from "@components/extension/environments/SlotLink";
import DeleteEnvironmentButton from "@components/extension/environments/DeleteEnvironmentButton";

export default function EnvironmentCard({environment, showSlots = true}) {
    return (
        <>
            <Card
                style={{
                    height: '100%',
                }}
                headStyle={{
                    background: 'linear-gradient(to right, var(--ot-bg-subtle), var(--ot-bg-subtle-alt))'
                }}
                size="small"
                data-testid={`environment-${environment.id}`}
                title={
                    <EnvironmentTitle environment={environment}/>
                }
                extra={
                    <DeleteEnvironmentButton environment={environment}/>
                }
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
                                    <Flex justify="space-between" align="center" gap={16}>
                                        <SlotTitle
                                            slot={slot}
                                            showLastDeployed={true}
                                        />
                                        <SlotLink
                                            slot={slot}
                                            text="Slot"
                                        />
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