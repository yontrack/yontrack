import GridCell from "@components/grid/GridCell";
import {Popover, Space, Table, Typography} from "antd";
import Link from "next/link";
import TimestampText from "@components/common/TimestampText";
import {buildUri} from "@components/common/Links";
import {buildKnownName, buildLinkName} from "@components/common/Titles";
import PromotionRun from "@components/promotionRuns/PromotionRun";
import {FaLink} from "react-icons/fa";
import SafeHTMLComponent from "@components/common/SafeHTMLComponent";

const {Column} = Table

export default function GitChangeLogCommits({id, loading, commits, diffLink}) {
    return (
        <>
            <GridCell
                id={id}
                loading={loading}
                title={
                    <>
                        Commits (<Link href={diffLink}>diff</Link>)
                    </>
                }
            >
                <Table
                    dataSource={commits}
                    pagination={false}
                    size="small"
                    rowKey={(commit) => `commit-${commit.commit.id}`}
                >

                    <Column
                        key="sha"
                        title="SHA"
                        render={(_, commit) =>
                            <Link href={commit.commit.link} title="Link to the commit in the SCM">
                                <Typography.Text code>{commit.commit.shortId}</Typography.Text>
                            </Link>
                        }
                        width="8em"
                    />

                    <Column
                        key="message"
                        title="Message"
                        className="ot-git-change-log-commit-message"
                        render={(_, commit) =>
                            <Typography.Paragraph>
                                <SafeHTMLComponent htmlContent={commit.annotatedMessage}/>
                            </Typography.Paragraph>
                        }
                    />

                    <Column
                        key="author"
                        title="Author"
                        render={(_, commit) =>
                            <Typography.Text>{commit.commit.author}</Typography.Text>
                        }
                        width="16em"
                    />

                    <Column
                        key="timestamp"
                        title="Timestamp"
                        render={(_, commit) =>
                            <TimestampText value={commit.commit.timestamp}/>
                        }
                        width="12em"
                    />

                    <Column
                        key="build"
                        title="Build"
                        className="ot-git-change-log-commit-build"
                        render={(_, commit) =>
                            commit.build && <Space size={8}>
                                <Link href={buildUri(commit.build)}>
                                    {buildKnownName(commit.build)}
                                </Link>
                                {
                                    commit.build.promotionRuns &&
                                    <>
                                        {
                                            commit.build.promotionRuns.map(promotionRun =>
                                                <PromotionRun
                                                    key={promotionRun.id}
                                                    promotionRun={promotionRun}
                                                    size={16}
                                                />
                                            )
                                        }
                                    </>
                                }
                            </Space>
                        }
                        width="15em"
                    />

                    <Column
                        key="links"
                        title="Links"
                        render={(_, commit) =>
                            <>
                                {
                                    commit.build && commit.build.usingQualified.pageItems.length > 0 &&
                                    <Popover
                                        title="Links from this build/commit"
                                        content={
                                            <Space direction="vertical">
                                                {
                                                    commit.build.usingQualified.pageItems.map(link =>
                                                        <Link key={`${link.build.id}-${link.qualifier}`}
                                                              href={buildUri(link.build)}>
                                                                    <span className="ot-git-plot-line-build">
                                                                        {buildLinkName(link)}
                                                                    </span>
                                                        </Link>
                                                    )
                                                }
                                            </Space>
                                        }
                                    >
                                        <FaLink className="ot-action"/>
                                    </Popover>
                                }
                            </>
                        }
                    />

                </Table>
            </GridCell>
        </>
    )
}
