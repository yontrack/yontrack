import GeneratedIcon from "@components/common/icons/GeneratedIcon";
import {useContext} from "react";
import {EventsContext} from "@components/common/EventsContext";
import ProxyImage from "@components/common/ProxyImage";
import {useRefresh} from "@components/common/RefreshUtils";
import {restEnvironmentImageUri} from "@components/extension/environments/EnvironmentsLinksUtils";
import {useQuery} from "@components/services/GraphQL";
import {gql} from "graphql-request";
import LoadingInline from "@components/common/LoadingInline";

export default function EnvironmentIcon({environmentId, onClick, showTooltip = true, tooltipText, size = 16}) {

    const [refreshState, refresh] = useRefresh()
    const eventsContext = useContext(EventsContext)

    const {data: environment, loading, finished} = useQuery(
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
        {
            variables: {id: environmentId},
            deps: [environmentId, refreshState],
            dataFn: data => data.environmentById,
        }
    )
    const tooltip = environment && showTooltip ? (tooltipText ?? environment.name) : ''

    eventsContext.subscribeToEvent("environment.image", ({id}) => {
        if (environment && id === environment.id) {
            refresh()
        }
    })

    return (
        <LoadingInline loading={loading || !finished}>
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