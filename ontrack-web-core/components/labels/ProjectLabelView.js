import Head from "next/head";
import {Empty, Space, Typography} from "antd";
import {gql} from "graphql-request";
import MainLayout from "@components/layouts/MainLayout";
import MainPage from "@components/layouts/MainPage";
import {homeBreadcrumbs} from "@components/common/Breadcrumbs";
import {CloseToHomeCommand} from "@components/common/Commands";
import {title} from "@components/common/Titles";
import {useQuery} from "@components/services/GraphQL";
import LabelChip, {labelDisplay} from "@components/labels/LabelChip";
import {gqlProjectContentFragment} from "@components/projects/ProjectGraphQLFragments";
import {gqlDecorationFragment} from "@components/services/fragments";
import PageSection from "@components/common/PageSection";
import SimpleProjectList from "@components/projects/SimpleProjectList";

/**
 * Page of one label: the chip, the description, and the projects carrying the label.
 *
 * It is open to all users - it is where a chip click and the project count of the admin page
 * lead - and shows only the projects the viewer may see, which `Label.projects` enforces.
 */
export default function ProjectLabelView({id}) {

    const {data: label, loading, finished} = useQuery(
        gql`
            query GetLabel($id: Int!) {
                label(id: $id) {
                    # labelFragment is defined by ProjectContent below - a query carrying both
                    # must not add it again
                    ...labelFragment
                    projects {
                        ...ProjectContent
                        favourite
                        decorations {
                            ...decorationContent
                        }
                    }
                }
            }

            ${gqlProjectContentFragment}
            ${gqlDecorationFragment}
        `,
        {
            variables: {id},
            deps: [id],
            condition: !!id,
            dataFn: data => data.label,
        }
    )

    return (
        <>
            <main>
                <Head>
                    {title(label ? labelDisplay(label) : undefined)}
                </Head>
                <MainLayout>
                    <MainPage
                        pageId="project-label"
                        title={
                            <Space>
                                <LabelChip label={label} link={false}/>
                                {
                                    label?.description &&
                                    <Typography.Text type="secondary">{label.description}</Typography.Text>
                                }
                            </Space>
                        }
                        breadcrumbs={homeBreadcrumbs()}
                        commands={[
                            <CloseToHomeCommand key="home"/>,
                        ]}
                    >
                        {
                            finished && !label &&
                            <Empty
                                image={Empty.PRESENTED_IMAGE_SIMPLE}
                                description="This label does not exist."
                            />
                        }
                        {
                            label &&
                            <PageSection
                                id="label-projects"
                                loading={loading}
                                title="Projects"
                                padding={true}
                            >
                                <SimpleProjectList
                                    projects={label.projects}
                                    emptyText="No project carries this label."
                                />
                            </PageSection>
                        }
                    </MainPage>
                </MainLayout>
            </main>
        </>
    )
}
