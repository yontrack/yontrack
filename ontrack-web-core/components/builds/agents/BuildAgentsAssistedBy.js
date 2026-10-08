import {Space, Tag, Typography} from "antd";
import {FaRobot} from "react-icons/fa";
import Link from "next/link";
import {scmChangeLogUri} from "@components/common/Links";
import {assistedBasisText, assistedCommitsText} from "@components/builds/agents/buildAgents";

/**
 * *Assisted by*, from git (#2033): the assistants of the commits of the build, the share of assisted
 * commits since the previous build - linking to the change log between the two builds -, the
 * sessions behind the commits, and how this was obtained.
 *
 * The assistants are agent *kinds* read from the commits, never registered agents: they are plain
 * tags, never actor badges, and never link to the actions by agents.
 *
 * @param build Build of the page (`id`)
 * @param assistedChange `Build.assistedChange`, with its `previousBuild` - null when not computed yet
 */
export default function BuildAgentsAssistedBy({build, assistedChange}) {

    const commitsText = assistedCommitsText(assistedChange)
    const previousBuild = assistedChange?.previousBuild
    const sessionLinks = assistedChange?.sessionLinks ?? []
    const assistants = assistedChange?.assistants ?? []

    return (
        <section aria-labelledby="build-agents-assisted-title" data-testid="build-agents-assisted">
            <Typography.Title level={5} id="build-agents-assisted-title">Assisted by</Typography.Title>
            <Space orientation="vertical" size={4}>
                {
                    assistants.length > 0 ?
                        <Space wrap size={4} data-testid="build-agents-assistants">
                            {
                                assistants.map(assistant => (
                                    <Tag
                                        key={assistant}
                                        color="purple"
                                        variant="outlined"
                                        icon={<FaRobot aria-hidden="true"/>}
                                        style={{marginInlineEnd: 0}}
                                    >
                                        {assistant}
                                    </Tag>
                                ))
                            }
                        </Space> :
                        <Typography.Text type="secondary" data-testid="build-agents-assistants">
                            {assistedChange?.basis === 'UNKNOWN' || !assistedChange ? "No assistant known" : "No assisted commit"}
                        </Typography.Text>
                }
                {
                    commitsText &&
                    <span data-testid="build-agents-commits">
                        {
                            previousBuild ?
                                <Link
                                    href={scmChangeLogUri(previousBuild.id, build.id)}
                                    title="Change log between the two builds"
                                >
                                    {commitsText}
                                </Link> :
                                commitsText
                        }
                    </span>
                }
                {
                    sessionLinks.length > 0 &&
                    <Space wrap size={8} data-testid="build-agents-sessions">
                        {
                            sessionLinks.map((link, index) => (
                                <a key={link} href={link} target="_blank" rel="noopener noreferrer">
                                    {sessionLinks.length > 1 ? `Agent session ${index + 1}` : 'Agent session'}
                                </a>
                            ))
                        }
                    </Space>
                }
                <Typography.Text type="secondary" data-testid="build-agents-basis">
                    {assistedBasisText(assistedChange)}
                </Typography.Text>
            </Space>
        </section>
    )
}
