import HealthIndicator from "@components/core/admin/health/HealthIndicator";
import ItemList from "@components/common/ItemList";

export default function HealthComponents({health}) {
    return (
        <>
            <ItemList>
                <ItemList.Item
                    avatar={<HealthIndicator status={health.status}/>}
                    title="Global health"
                />
                {
                    Object.keys(health.components).map(name => {
                        const component = health.components[name]
                        return (
                            <ItemList.Item
                                key={name}
                                avatar={<HealthIndicator status={component.status}/>}
                                title={name}
                                description={JSON.stringify(component.details, null, 2)}
                            />
                        )
                    })
                }
            </ItemList>
        </>
    )
}
