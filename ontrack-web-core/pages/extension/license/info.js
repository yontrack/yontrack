import StandardPage from "@components/layouts/StandardPage";
import {useQuery} from "@components/services/GraphQL";
import LoadingContainer from "@components/common/LoadingContainer";
import {gql} from "graphql-request";
import {Alert, Card, Col, Descriptions, Row, Space, Tag, Typography} from "antd";
import PageSection from "@components/common/PageSection";
import LicenseActive from "@components/extension/license/LicenseActive";
import LicenseValidUntil from "@components/extension/license/LicenseValidUntil";
import LicenseMaxProjects from "@components/extension/license/LicenseMaxProjects";
import LicenseFeatureData from "@components/extension/license/LicenseFeatureData";

const NO_LICENSE_INFO = {}

export default function LicenseInfoPage() {

    const {data, loading, finished} = useQuery(
        gql`
            query LicenseInfo {
                licenseInfo {
                    license {
                        type
                        name
                        assignee
                        active
                        validUntil
                        maxProjects
                        message
                        licensedFeatures {
                            id
                            name
                            enabled
                            data {
                                name
                                value
                            }
                        }
                    }
                    licenseControl {
                        active
                    }
                }
            }
        `,
        {
            dataFn: data => data.licenseInfo,
        }
    )
    const licenseInfo = data ?? NO_LICENSE_INFO

    let licenseItems = []
    const info = data
    if (info) {
        if (info.license) {
            licenseItems = [
                {
                    key: 'type',
                    label: "Source",
                    children: info.license.type,
                },
                {
                    key: 'name',
                    label: "Name",
                    children: info.license.name,
                },
                {
                    key: 'assignee',
                    label: "Assignee",
                    children: info.license.assignee,
                },
                {
                    key: 'active',
                    label: "Activation",
                    children: <LicenseActive active={info.license.active}/>
                },
                {
                    key: 'validUntil',
                    label: "Valid until",
                    children: <LicenseValidUntil validUntil={info.license.validUntil}/>,
                },
                {
                    key: 'maxProjects',
                    label: "Max. projects",
                    children: <LicenseMaxProjects maxProjects={info.license.maxProjects}/>,
                },
                {
                    key: 'message',
                    label: "Message",
                    children: info.license.message ?
                        <Typography.Text>{info.license.message}</Typography.Text> :
                        <Typography.Text type="secondary">No message</Typography.Text>,
                },
            ]
        } else {
            licenseItems = [
                {
                    key: 'none',
                    children: <Typography.Text strong>No license.</Typography.Text>
                }
            ]
        }
    }

    return (
        <StandardPage
            pageTitle="License info">
            <LoadingContainer loading={loading || !finished}>
                <Space orientation="vertical" className="ot-line">
                    {
                        licenseInfo?.license &&
                        licenseInfo?.licenseControl && (
                            licenseInfo.licenseControl.active ?
                                <Alert
                                    type="success"
                                    title="License is active."
                                    showIcon
                                /> :
                                <Alert
                                    type="error"
                                    title="License is disabled."
                                    showIcon
                                />
                        )
                    }
                    <PageSection
                        title=""
                        padding={true}
                    >
                        <Space orientation="vertical" className="ot-line">
                            <Descriptions
                                items={licenseItems}
                                bordered={true}
                                layout="vertical"
                            />
                            <Row gutter={[8, 8]}>
                                {
                                    licenseInfo?.license?.licensedFeatures.map(feature => (
                                        <Col key={feature.id} span={6}>
                                            <Card
                                                title={feature.name}
                                            >
                                                <Space orientation="vertical" className="ot-line">
                                                    {
                                                        feature.enabled ?
                                                            <Tag color="success">Enabled</Tag> :
                                                            <Tag color="error">Disabled</Tag>
                                                    }
                                                    <LicenseFeatureData
                                                        featureId={feature.id}
                                                        featureData={feature.data}
                                                    />
                                                </Space>
                                            </Card>
                                        </Col>
                                    ))
                                }
                            </Row>
                        </Space>
                    </PageSection>
                </Space>
            </LoadingContainer>
        </StandardPage>
    )
}