import Head from "next/head";
import Link from "next/link";
import {gql} from "graphql-request";
import {Alert, Col, Row, Space, Typography} from "antd";
import MainPage from "@components/layouts/MainPage";
import PageSection from "@components/common/PageSection";
import LoadingContainer from "@components/common/LoadingContainer";
import {CloseCommand} from "@components/common/Commands";
import {homeUri, projectFindingsUri} from "@components/common/Links";
import {projectTitle, title as pageTitle} from "@components/common/Titles";
import {downToProjectBreadcrumbs} from "@components/common/Breadcrumbs";
import {useQuery} from "@components/services/GraphQL";
import FindingSeverityTag from "@components/extension/findings/FindingSeverityTag";
import FindingSummary from "@components/extension/findings/finding/FindingSummary";
import FindingAcceptance from "@components/extension/findings/finding/FindingAcceptance";
import FindingExposureTable from "@components/extension/findings/finding/FindingExposureTable";
import FindingObservationsTimeline from "@components/extension/findings/finding/FindingObservationsTimeline";

const gqlFinding = gql`
    query Finding($id: Int!) {
        finding(id: $id) {
            id
            externalId
            title
            scanner
            kind
            location
            url
            maxSeverity
            state
            firstSeen
            lastSeen
            resolvedAt
            project {
                id
                name
            }
            acceptance {
                effective
                statement
                expiresAt
                source
            }
            exposures {
                branch {
                    id
                    name
                }
                validationStamp {
                    id
                    name
                }
                state
                since
                accepted
                acceptanceExpiresAt
                resolvedAt
                resolutionReason
            }
        }
    }
`

/**
 * The page of one security finding, `/extension/findings/finding/{id}`: what it is, its acceptance
 * with its statement and expiry, its exposure per branch with its start, and the timeline of its
 * observations.
 *
 * A finding the user is not granted the view of is not told apart from one which does not exist:
 * the server answers null for both.
 */
export default function FindingView({id}) {

    const {data: finding, loading, finished, error} = useQuery(
        gqlFinding,
        {
            variables: {id},
            deps: [id],
            condition: !!id,
            dataFn: data => data.finding,
        }
    )

    const project = finding?.project

    return (
        <>
            <Head>
                {
                    project ?
                        projectTitle(project, finding.externalId) :
                        pageTitle("Security finding")
                }
            </Head>
            <MainPage
                title={
                    finding ?
                        <Space>
                            <Typography.Text>{finding.externalId}</Typography.Text>
                            <FindingSeverityTag severity={finding.maxSeverity}/>
                        </Space> :
                        "Security finding"
                }
                breadcrumbs={project ? [
                    ...downToProjectBreadcrumbs({project}),
                    <Link key="findings" href={projectFindingsUri(project)}>Security findings</Link>,
                ] : []}
                commands={[
                    <CloseCommand key="close" href={project ? projectFindingsUri(project) : homeUri()}/>,
                ]}
            >
                <LoadingContainer loading={loading || !finished} error={error}>
                    {
                        !finding &&
                        <Alert
                            type="warning"
                            showIcon
                            title="This finding does not exist, or you are not allowed to see the security findings of its project."
                        />
                    }
                    {
                        finding &&
                        <Row gutter={[16, 16]} className="ot-line">
                            <Col xs={24} lg={14}>
                                <PageSection id="finding-details" title="Finding" padding={true} height="auto">
                                    <FindingSummary finding={finding}/>
                                </PageSection>
                            </Col>
                            <Col xs={24} lg={10}>
                                <PageSection id="finding-acceptance-section" title="Acceptance" padding={true}
                                             height="auto">
                                    <FindingAcceptance acceptance={finding.acceptance}/>
                                </PageSection>
                            </Col>
                            <Col span={24}>
                                <PageSection id="finding-exposure-section" title="Exposure per branch" height="auto">
                                    <FindingExposureTable exposures={finding.exposures}/>
                                </PageSection>
                            </Col>
                            <Col span={24}>
                                <PageSection id="finding-observations-section" title="Observations" padding={true}
                                             height="auto">
                                    <FindingObservationsTimeline id={finding.id}/>
                                </PageSection>
                            </Col>
                        </Row>
                    }
                </LoadingContainer>
            </MainPage>
        </>
    )
}
