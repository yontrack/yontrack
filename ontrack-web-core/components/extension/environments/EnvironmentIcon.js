import GeneratedIcon from "@components/common/icons/GeneratedIcon";
import {useContext, useEffect, useState} from "react";
import {EventsContext} from "@components/common/EventsContext";
import ProxyImage from "@components/common/ProxyImage";
import {useRefresh} from "@components/common/RefreshUtils";
import {restEnvironmentImageUri} from "@components/extension/environments/EnvironmentsLinksUtils";
import {useGraphQLClient} from "@components/providers/ConnectionContextProvider";
import {gql} from "graphql-request";
import LoadingInline from "@components/common/LoadingInline";

export default function EnvironmentIcon({environmentId, onClick, showTooltip = true, tooltipText, size = 16}) {

    const client = useGraphQLClient()
    const [refreshState, refresh] = useRefresh()
    const eventsContext = useContext(EventsContext)

    const [loading, setLoading] = useState(true)
    const [environment, setEnvironment] = useState()
    const [tooltip, setTooltip] = useState('')
    useEffect(() => {
        if (client) {
            setLoading(true)
            client.request(
                gql`
                    query EnvironmentImage($id: String!) {
                        environmentById(id: $id) {
                            id
                            name
                            order
                            image
                        }
                    }
                `,
                {id: environmentId}
            ).then(data => {
                const env = data.environmentById;
                setEnvironment(env)
                if (showTooltip) {
                    setTooltip(tooltipText ?? env.name)
                }
            }).finally(() => {
                setLoading(false)
            })
        }
    }, [client, environmentId, refreshState])

    eventsContext.subscribeToEvent("environment.image", ({id}) => {
        if (environment && id === environment.id) {
            refresh()
        }
    })

    return (
        <LoadingInline loading={loading}>
            {
                environment &&
                <>
                    {
                        environment.image ?
                            <ProxyImage restUri={`${restEnvironmentImageUri(environment)}?key=${refreshState}`}
                                        alt={environment.name}
                                        width={size}
                                        height={size}
                                        onClick={onClick}
                                        tooltipText={tooltip}
                            /> :
                            <GeneratedIcon
                                name={environment.name}
                                colorIndex={environment.order}
                                onClick={onClick}
                                tooltipText={tooltip}
                            />
                    }
                </>
            }
        </LoadingInline>
    )
}