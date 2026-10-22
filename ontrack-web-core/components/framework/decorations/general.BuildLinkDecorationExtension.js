import {buildLinksUri, buildUri} from "@components/common/Links";
import {FaLink} from "react-icons/fa";
import {Alert, Popover, Space, Spin, Tag, Typography} from "antd";
import Link from "next/link";
import {useEffect, useState} from "react";
import {useQuery} from "@components/services/GraphQL";
import PromotionRun from "@components/promotionRuns/PromotionRun";

const linkFields = `
    pageItems {
        qualifier
        build {
            id
            name
            displayName
            branch {
                id
                project {
                    id
                    name
                }
            }
            promotionRuns(lastPerLevel: true) {
                id
                creation {
                    time
                }
                promotionLevel {
                    id
                    name
                    description
                    image
                }
            }
        }
    }
`

const NO_LINKS = []

/**
 * One row per link: project (and qualifier), build, promotions.
 */
function BuildLinkDecorationSection({id, arrow, title, count, links, maxLinks, buildId}) {
    return (
        <Space orientation="vertical" size={4} data-testid={`build-link-decoration-${id}`}>
            <Typography.Text strong>
                {arrow} {title} <Typography.Text type="secondary">{count}</Typography.Text>
            </Typography.Text>
            <div style={{
                display: "grid",
                gridTemplateColumns: "auto auto auto",
                columnGap: 16,
                rowGap: 4,
                alignItems: "center",
            }}>
                {
                    links.map(link =>
                        <BuildLinkDecorationRow key={`${link.build.id}-${link.qualifier}`} link={link}/>
                    )
                }
            </div>
            {
                count > maxLinks &&
                <Typography.Text type="secondary" data-testid={`build-link-decoration-${id}-more`}>
                    {count - maxLinks} more · <Link href={buildLinksUri({id: buildId})}>All links →</Link>
                </Typography.Text>
            }
        </Space>
    )
}

function BuildLinkDecorationRow({link}) {
    const build = link.build
    return (
        <>
            <Space size={4}>
                <Typography.Text>{build.branch.project.name}</Typography.Text>
                {link.qualifier && <Tag style={{marginInlineEnd: 0}}>{link.qualifier}</Tag>}
            </Space>
            <Link href={buildUri(build)}>{build.displayName || build.name}</Link>
            {
                build.promotionRuns.length > 0 ?
                    <Space size={4}>
                        {
                            build.promotionRuns.map(promotionRun =>
                                <PromotionRun
                                    key={promotionRun.id}
                                    promotionRun={promotionRun}
                                    size={16}
                                />
                            )
                        }
                    </Space> :
                    <Typography.Text type="secondary">—</Typography.Text>
            }
        </>
    )
}

/**
 * Links of the build, loaded when the popover opens for the first time - never with the
 * decoration itself, which is computed for every build of a list.
 */
function BuildLinkDecorationContent({buildId, downstreamCount, upstreamCount, maxLinks}) {
    const {data, loading, error, finished} = useQuery(
        `
            query BuildLinkDecoration($id: Int!, $size: Int!) {
                build(id: $id) {
                    usingQualified(size: $size) {
                        ${linkFields}
                    }
                    usedByQualified(size: $size) {
                        ${linkFields}
                    }
                }
            }
        `,
        {
            variables: {id: Number(buildId), size: maxLinks},
            deps: [buildId, maxLinks],
        }
    )

    const downstream = data?.build?.usingQualified?.pageItems ?? NO_LINKS
    const upstream = data?.build?.usedByQualified?.pageItems ?? NO_LINKS

    return (
        <Spin spinning={loading || !finished} size="small">
            <Space
                orientation="vertical"
                size={12}
                data-testid={`build-link-decoration-popover-${buildId}`}
                data-loaded={finished && !loading}
            >
                {error && <Alert type="error" title={error}/>}
                {
                    downstreamCount > 0 &&
                    <BuildLinkDecorationSection
                        id="downstream"
                        arrow="↓"
                        title="Downstream"
                        count={downstreamCount}
                        links={downstream}
                        maxLinks={maxLinks}
                        buildId={buildId}
                    />
                }
                {
                    upstreamCount > 0 &&
                    <BuildLinkDecorationSection
                        id="upstream"
                        arrow="↑"
                        title="Upstream"
                        count={upstreamCount}
                        links={upstream}
                        maxLinks={maxLinks}
                        buildId={buildId}
                    />
                }
            </Space>
        </Spin>
    )
}

export default function BuildLinkDecorationExtension({decoration}) {
    const {buildId, linksCount: downstreamCount = 0, upstreamCount = 0, maxLinks = 10} = decoration.data
    const [open, setOpen] = useState(false)

    // Esc closes the popover, whether it was opened by the mouse or by the keyboard
    useEffect(() => {
        if (open) {
            const onKeyDown = (e) => {
                if (e.key === "Escape") {
                    setOpen(false)
                }
            }
            document.addEventListener("keydown", onKeyDown)
            return () => document.removeEventListener("keydown", onKeyDown)
        }
    }, [open])

    if (!buildId || downstreamCount + upstreamCount <= 0) {
        return null
    }

    return (
        <Popover
            trigger={["hover", "focus"]}
            open={open}
            onOpenChange={setOpen}
            // Rendered on the first opening only, and kept afterwards: the links are fetched once
            content={
                <BuildLinkDecorationContent
                    buildId={buildId}
                    downstreamCount={downstreamCount}
                    upstreamCount={upstreamCount}
                    maxLinks={maxLinks}
                />
            }
        >
            <Tag data-testid={`build-link-decoration-${buildId}`}>
                <Link href={buildLinksUri({id: buildId})} aria-label="Build links">
                    <FaLink size={8}/>
                </Link>
            </Tag>
        </Popover>
    )
}
