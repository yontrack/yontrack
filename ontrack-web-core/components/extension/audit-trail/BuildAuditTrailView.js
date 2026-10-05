import {useState} from "react";
import Head from "next/head";
import {gql} from "graphql-request";
import {Alert, Button, Space, Tag, Typography} from "antd";
import {FaCheckCircle, FaDownload, FaExclamationTriangle, FaInfoCircle, FaTimesCircle} from "react-icons/fa";
import {callGraphQL, useQuery} from "@components/services/GraphQL";
import {CloseCommand} from "@components/common/Commands";
import {buildAuditTrailExportUri, buildUri} from "@components/common/Links";
import {subBuildTitle} from "@components/common/Titles";
import {downToBuildBreadcrumbs} from "@components/common/Breadcrumbs";
import MainPage from "@components/layouts/MainPage";
import LoadingContainer from "@components/common/LoadingContainer";
import PageSection from "@components/common/PageSection";
import Table from "@components/common/table/Table";
import TimestampText from "@components/common/TimestampText";
import {actorText, entrySummary, evidenceBadge, trailBadge} from "@components/extension/audit-trail/auditTrailModel";
import {evidenceVerificationFlags} from "@components/extension/audit-trail/evidenceModel";
import BuildEvidence from "@components/extension/audit-trail/BuildEvidence";

const gqlVerificationFields = `
    chainIntact
    firstBrokenSeq
    endorsementsValid
    firstInvalidEndorsementSeq
    partial
    unendorsedFromSeq
    problems {
        seq
        type
        message
    }
    missingEvidence
    alteredEvidence
`

export const gqlBuildAuditTrail = gql`
    query BuildAuditTrail($id: Int!) {
        auditTrailStorageState
        build(id: $id) {
            id
            name
            releaseProperty {
                value
            }
            branch {
                id
                name
                project {
                    id
                    name
                }
            }
            auditTrail {
                entries {
                    id
                    seq
                    type
                    time
                    actor
                    payload
                    prevHash
                    hash
                }
                endorsements {
                    seq
                    keyId
                    time
                }
                evidence {
                    id
                    fileName
                    mediaType
                    size
                    sha256
                    collectedAt
                    collectedBy
                    source {
                        tool
                        version
                        url
                    }
                    externalDigest
                    deletedAt
                    downloadUrl
                    validationRun {
                        id
                        runOrder
                        validationStamp {
                            name
                        }
                    }
                }
                verification {
                    ${gqlVerificationFields}
                }
            }
        }
    }
`

export const gqlBuildAuditTrailEvidenceVerification = gql`
    query BuildAuditTrailEvidenceVerification($id: Int!) {
        build(id: $id) {
            auditTrail {
                verification(includeEvidence: true) {
                    ${gqlVerificationFields}
                }
            }
        }
    }
`

const NO_ENTRIES = []

const NO_EVIDENCE = []

/**
 * Icon of each state of the badge, next to its text, so that the state never relies on the
 * colour alone.
 */
const BADGE_ICONS = {
    intact: <FaCheckCircle aria-hidden="true"/>,
    partial: <FaInfoCircle aria-hidden="true"/>,
    unendorsed: <FaExclamationTriangle aria-hidden="true"/>,
    broken: <FaTimesCircle aria-hidden="true"/>,
    failed: <FaTimesCircle aria-hidden="true"/>,
}

/**
 * What each state of the badge means, in a sentence.
 */
const BADGE_EXPLANATIONS = {
    intact: () => "Every entry is chained to the one before it and endorsed by this instance.",
    partial: () => "The trail was opened after the build was created: it starts with its trail.opened entry. " +
        "Every entry since is chained to the one before it and endorsed by this instance.",
    unendorsed: (verification) => `The chain is intact, but the entries from seq ${verification.unendorsedFromSeq} ` +
        "were written while the instance key was not available: no endorsement covers them.",
    broken: () => "The trail was altered: the checks which failed are listed below. " +
        "The entries before the first of them are verified.",
}

const Badge = ({badge, testId}) =>
    <Tag
        color={badge.color}
        icon={BADGE_ICONS[badge.state]}
        data-testid={testId}
        data-state={badge.state}
    >
        <span style={{marginLeft: 4}}>{badge.text}</span>
    </Tag>

const JsonBlock = ({value, label}) =>
    <pre aria-label={label} style={{margin: 0, whiteSpace: 'pre-wrap', wordBreak: 'break-all'}}>
        {JSON.stringify(value, null, 2)}
    </pre>

/**
 * The trail of a build: its verification badge, with the verification of its evidence on demand,
 * the evidence of its validations, and its entries.
 *
 * @param buildId ID of the build
 * @param auditTrail `BuildAuditTrail` of the build
 * @param storageState `AuditTrailStorageState`
 */
export function BuildAuditTrailContent({buildId, auditTrail, storageState}) {

    // Verification including the evidence, run on demand - it reads every blob back
    const [evidenceVerification, setEvidenceVerification] = useState(null)
    const [evidenceVerifying, setEvidenceVerifying] = useState(false)
    const [evidenceError, setEvidenceError] = useState(null)

    const verifyEvidence = async () => {
        setEvidenceVerifying(true)
        setEvidenceError(null)
        try {
            const data = await callGraphQL({
                query: gqlBuildAuditTrailEvidenceVerification,
                variables: {id: Number(buildId)},
            })
            setEvidenceVerification(data?.build?.auditTrail?.verification ?? null)
        } catch (ex) {
            setEvidenceError(ex.message)
        } finally {
            setEvidenceVerifying(false)
        }
    }

    const verification = evidenceVerification ?? auditTrail.verification
    const badge = trailBadge(verification)
    const evidence = evidenceBadge(verification)
    const problems = verification.problems ?? []
    const entries = auditTrail.entries ?? NO_ENTRIES
    const endorsedSeqs = new Map((auditTrail.endorsements ?? []).map(endorsement => [endorsement.seq, endorsement]))
    const problemSeqs = new Set(problems.map(problem => problem.seq))
    const evidenceFlags = evidenceVerificationFlags(entries, verification)

    return (
        <Space orientation="vertical" className="ot-line">
            <PageSection
                id="audit-trail-verification"
                title="Verification"
                padding={true}
                extra={
                    <Button
                        onClick={verifyEvidence}
                        loading={evidenceVerifying}
                        data-testid="audit-trail-verify-evidence"
                        title="Verifies the trail again, reading every evidence back from the storage to check its digest"
                    >
                        Verify including evidence
                    </Button>
                }
            >
                <Space orientation="vertical">
                    <Space wrap>
                        <Badge badge={badge} testId="audit-trail-badge"/>
                        {evidence && <Badge badge={evidence} testId="audit-trail-evidence-badge"/>}
                    </Space>
                    <Typography.Text data-testid="audit-trail-explanation">
                        {BADGE_EXPLANATIONS[badge.state](verification)}
                    </Typography.Text>
                    {
                        problems.length > 0 &&
                        <ul data-testid="audit-trail-problems" style={{margin: 0}}>
                            {
                                problems.map((problem, index) =>
                                    <li key={index}>
                                        <Typography.Text strong>Seq {problem.seq}</Typography.Text>
                                        {' '}
                                        <Typography.Text code>{problem.type}</Typography.Text>
                                        {' '}
                                        {problem.message}
                                    </li>
                                )
                            }
                        </ul>
                    }
                    {
                        evidenceError &&
                        <Alert
                            type="error"
                            showIcon
                            title="The evidence could not be verified"
                            description={evidenceError}
                            data-testid="audit-trail-evidence-error"
                        />
                    }
                </Space>
            </PageSection>
            <BuildEvidence
                evidence={auditTrail.evidence ?? NO_EVIDENCE}
                flags={evidenceFlags}
                storageState={storageState}
            />
            <PageSection id="audit-trail-entries" title={`Entries (${entries.length})`}>
                <Table
                    dataSource={entries}
                    rowKey="seq"
                    size="small"
                    pagination={{pageSize: 50, hideOnSinglePage: true}}
                    expandable={{
                        expandedRowRender: (entry) => {
                            const endorsement = endorsedSeqs.get(entry.seq)
                            return (
                                <Space orientation="vertical" className="ot-line">
                                    <Typography.Text strong>Payload</Typography.Text>
                                    <JsonBlock value={entry.payload} label={`Payload of seq ${entry.seq}`}/>
                                    <Typography.Text strong>Actor</Typography.Text>
                                    <JsonBlock value={entry.actor} label={`Actor of seq ${entry.seq}`}/>
                                    <Typography.Text>
                                        Hash <Typography.Text code copyable>{entry.hash}</Typography.Text>
                                    </Typography.Text>
                                    <Typography.Text>
                                        Previous hash {
                                        entry.prevHash ?
                                            <Typography.Text code>{entry.prevHash}</Typography.Text> :
                                            <Typography.Text type="secondary">none, first entry</Typography.Text>
                                    }
                                    </Typography.Text>
                                    <Typography.Text>
                                        Endorsement {
                                        endorsement ?
                                            <>by key <Typography.Text code>{endorsement.keyId}</Typography.Text></> :
                                            <Typography.Text type="secondary">none</Typography.Text>
                                    }
                                    </Typography.Text>
                                </Space>
                            )
                        },
                    }}
                >
                    <Table.Column
                        key="seq"
                        title="Seq"
                        dataIndex="seq"
                        render={(seq) =>
                            <Space size={4}>
                                {seq}
                                {
                                    problemSeqs.has(seq) &&
                                    <Typography.Text type="danger" title="This entry fails the verification">
                                        <FaTimesCircle aria-label="Fails the verification" role="img"/>
                                    </Typography.Text>
                                }
                            </Space>
                        }
                    />
                    <Table.Column
                        key="time"
                        title="Time"
                        dataIndex="time"
                        render={(time) => <TimestampText value={time} format="YYYY MMM DD, HH:mm:ss"/>}
                    />
                    <Table.Column
                        key="type"
                        title="Type"
                        dataIndex="type"
                        render={(type) => <Typography.Text code>{type}</Typography.Text>}
                    />
                    <Table.Column
                        key="actor"
                        title="Actor"
                        dataIndex="actor"
                        render={(actor) => actorText(actor)}
                    />
                    <Table.Column
                        key="summary"
                        title="Summary"
                        render={(_, entry) => entrySummary(entry)}
                    />
                </Table>
            </PageSection>
        </Space>
    )
}

/**
 * The audit trail page of a build: its trail, and the JSON export of it.
 *
 * @param id ID of the build
 */
export default function BuildAuditTrailView({id}) {

    const {data, loading, finished, error} = useQuery(gqlBuildAuditTrail, {
        variables: {id: Number(id)},
        deps: [id],
        condition: !!id,
    })
    const loadedBuild = data?.build
    const auditTrail = loadedBuild?.auditTrail

    const commands = []
    if (loadedBuild) {
        if (auditTrail) {
            commands.push(
                <Button
                    key="export"
                    type="text"
                    icon={<FaDownload aria-hidden="true"/>}
                    href={buildAuditTrailExportUri(loadedBuild)}
                    download
                    title="Downloads the trail as a JSON file, to be verified offline"
                    data-testid="audit-trail-export"
                >
                    Export JSON
                </Button>
            )
        }
        commands.push(<CloseCommand key="close" href={buildUri(loadedBuild)}/>)
    }

    return (
        <>
            <Head>
                {subBuildTitle(loadedBuild, "Audit trail")}
            </Head>
            <MainPage
                title="Audit trail"
                breadcrumbs={downToBuildBreadcrumbs({build: loadedBuild})}
                commands={commands}
            >
                <LoadingContainer loading={loading || !finished} error={error}>
                    {
                        loadedBuild && !auditTrail &&
                        <Alert
                            type="info"
                            showIcon
                            title="This build has no audit trail: the audit trail is not licensed, and no entry was ever written for this build."
                            data-testid="audit-trail-none"
                        />
                    }
                    {
                        auditTrail && <BuildAuditTrailContent
                            buildId={id}
                            auditTrail={auditTrail}
                            storageState={data.auditTrailStorageState}
                        />
                    }
                </LoadingContainer>
            </MainPage>
        </>
    )
}
