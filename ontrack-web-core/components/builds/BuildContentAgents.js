import {useState} from "react";
import {Space} from "antd";
import GridCell from "@components/grid/GridCell";
import {useQuery} from "@components/services/GraphQL";
import {AGENT_ACTIONS_PAGE_SIZE, gqlBuildAgents} from "@components/builds/agents/buildAgents";
import BuildAgentsAssistedBy from "@components/builds/agents/BuildAgentsAssistedBy";
import BuildAgentsActions from "@components/builds/agents/BuildAgentsActions";

const FIRST_PAGE = {offset: 0, size: AGENT_ACTIONS_PAGE_SIZE}

/**
 * The *Agents* section of a build page (#2033) - what agents had to do with this build, read by a
 * release manager before promoting it. Shown by `BuildContent` only when there is something to show
 * (`hasAgentsSection`).
 *
 * Two lists, one above the other and never merged nor linked: *Assisted by*, from git, and *Actions by
 * agents*, from the event log.
 *
 * Desktop only: the mobile UI shows the badges, not this section.
 *
 * @param build Build of the page (`id`)
 */
export default function BuildContentAgents({build}) {

    const [pagination, setPagination] = useState(FIRST_PAGE)

    const {data: page, loading} = useQuery(
        gqlBuildAgents,
        {
            variables: {
                id: Number(build.id),
                offset: pagination.offset,
                size: pagination.size,
            },
            deps: [build.id, pagination],
            dataFn: data => ({
                build: data.build,
                offset: pagination.offset,
            }),
        }
    )

    // A page beyond the first one is appended to the actions already loaded. This is state kept
    // from one page to the next, adjusted while rendering.
    const [loaded, setLoaded] = useState({page: null, actions: []})
    if (page && page !== loaded.page) {
        const newItems = page.build?.agentActions?.pageItems ?? []
        setLoaded({
            page,
            actions: page.offset > 0 ? [...loaded.actions, ...newItems] : newItems,
        })
    }
    const pageInfo = loaded.page?.build?.agentActions?.pageInfo
    // The assisted change does not change from one page to the next: read from the first page
    const assistedChange = loaded.page?.build?.assistedChange

    const onLoadMore = () => {
        if (pageInfo?.nextPage) {
            setPagination(pageInfo.nextPage)
        }
    }

    return (
        <GridCell
            id="agents"
            title="Agents"
            loading={loading && pagination.offset === 0}
            padding={true}
        >
            <div data-testid="build-agents">
                <Space orientation="vertical" size={16} style={{width: '100%'}}>
                    <BuildAgentsAssistedBy build={build} assistedChange={assistedChange}/>
                    <BuildAgentsActions
                        actions={loaded.actions}
                        pageInfo={pageInfo}
                        loading={loading && pagination.offset > 0}
                        onLoadMore={onLoadMore}
                    />
                </Space>
            </div>
        </GridCell>
    )
}
