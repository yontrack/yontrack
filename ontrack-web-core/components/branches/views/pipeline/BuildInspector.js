import Link from "next/link";
import {Skeleton, theme, Typography} from "antd";
import {useQuery} from "@components/services/GraphQL";
import {useRefresh} from "@components/common/RefreshUtils";
import {buildUri} from "@components/common/Links";
import {gqlPipelineBuildInspection} from "@components/branches/views/pipeline/pipelineQueries";
import {buildVersion} from "@components/branches/views/pipeline/pipelineFacts";
import BuildInspectorPromotions from "@components/branches/views/pipeline/BuildInspectorPromotions";
import BuildInspectorValidations from "@components/branches/views/pipeline/BuildInspectorValidations";

/**
 * Promotions and validations for the selected build, so a build can be inspected without leaving
 * the branch.
 *
 * THE LAYOUT IS CONTAINER-DRIVEN, not viewport-driven: `auto-fit` over a 360px minimum. The content
 * region's width already varies with the collapsible sidebar, so a viewport media query would be
 * wrong the moment someone collapses the sidebar on a narrow screen - the panels would still be
 * stacked in a region which by then has room for two.
 *
 * The two columns are also equal. Giving validations twice the width would be exactly backwards for
 * a promotion-heavy project, and nothing here knows which kind of project it is looking at.
 *
 * THE HEADER NAMES THE BUILD, and is the way out to it. Without it the panel is two sections of
 * promotions and validations belonging to nothing in particular, and a reader who has scrolled past
 * the timeline has no way to tell which card is selected. The name is a link because this panel is
 * deliberately partial: the build's own page carries the properties, the links, the run info, the
 * commit and the change log, and always will, so an escape hatch is the honest consequence of a
 * partial panel rather than parity-chasing. It goes here rather than on a timeline card - the card
 * exists for the selection click, and a second target on every card would compete with it.
 *
 * @param buildId Id of the selected build, or nothing when there is no build to inspect
 * @param selectedFilter The active validation stamp filter, if any
 * @param showValidations Whether the branch has any validation stamp at all
 * @param onChange Called when the inspected build changes on the server (a promotion is granted)
 */
export default function BuildInspector({buildId, selectedFilter, showValidations, onChange}) {

    const {token} = theme.useToken()

    const [reloadCount, reload] = useRefresh()

    const {data: build, loading, finished} = useQuery(
        gqlPipelineBuildInspection,
        {
            variables: {buildId: Number(buildId)},
            deps: [buildId, reloadCount],
            condition: !!buildId,
            initialData: null,
            dataFn: data => data.build,
        }
    )

    const onPromotion = () => {
        // Both the panel and the regions above it are stale after a promotion: the timeline card
        // gains a medal and the stage card gains a build.
        reload()
        onChange?.()
    }

    if (!buildId) return null

    // The same terms the timeline card names a build on, so the header reads as the card the user
    // clicked: the version when there is one to show, and the name under it.
    const version = buildVersion(build)

    // "Loaded" is not the same as "loaded THIS build". `useQuery` keeps the previous `data` across a
    // selection change, and aborting the request it was already making still flips `finished` true
    // and `loading` back to false - after the new request set `loading` true. Re-selecting while a
    // fetch is in flight therefore opens a window with a fully "loaded" panel describing the build
    // before last. That was survivable while this was two panels of stale promotions; it is not now
    // that the header is a LINK, because the stale window offers the wrong build's page.
    const stale = !build || String(build.id) !== String(buildId)

    return (
        <div data-testid="build-inspector">
            <Skeleton loading={loading || !finished || stale} active>
                {
                    build && <>
                        <div
                            data-testid="inspector-build-title"
                            // Banded like a `PageSection` head, and spanning the full width above
                            // the grid, so it reads as the thing the two panels below belong to
                            // rather than as a caption floating over them. The colour is the
                            // section-head variable, so it follows the theme rather than pinning a
                            // grey which disappears in dark mode.
                            style={{
                                display: 'flex',
                                alignItems: 'baseline',
                                gap: token.marginXS,
                                marginBottom: token.marginSM,
                                padding: `${token.paddingXS}px ${token.padding}px`,
                                borderRadius: token.borderRadiusLG,
                                backgroundColor: 'var(--ot-bg-section-head)',
                            }}
                        >
                            <Typography.Title
                                level={4}
                                style={{margin: 0}}
                                // The version is what gets pasted into a command or a ticket, so
                                // copying it is worth an affordance. It can live here, unlike on the
                                // timeline card, because this header is not inside a button - antd
                                // renders the control as one.
                                copyable={{
                                    text: version ?? build.name,
                                    tooltips: ["Copy", "Copied"],
                                }}
                            >
                                <Link href={buildUri(build)}>{version ?? build.name}</Link>
                            </Typography.Title>
                            {
                                // Only when it says something the link does not. For a project with
                                // no release property the two are the same string.
                                version &&
                                <Typography.Text type="secondary">{build.name}</Typography.Text>
                            }
                        </div>
                        <div
                            style={{
                                display: 'grid',
                                gridTemplateColumns: 'repeat(auto-fit, minmax(360px, 1fr))',
                                gap: token.marginSM,
                                alignItems: 'start',
                            }}
                        >
                            <BuildInspectorPromotions build={build} onChange={onPromotion}/>
                            {
                                // Hidden outright on a branch with no stamps: an empty validations
                                // panel would suggest the build is missing runs it was never going
                                // to have.
                                showValidations &&
                                <BuildInspectorValidations build={build} selectedFilter={selectedFilter}/>
                            }
                        </div>
                    </>
                }
            </Skeleton>
        </div>
    )
}
