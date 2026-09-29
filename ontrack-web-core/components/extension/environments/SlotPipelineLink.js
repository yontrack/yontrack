import Link from "next/link";
import {slotPipelineUri} from "@components/extension/environments/EnvironmentsLinksUtils";
import {useQuery} from "@components/services/GraphQL";
import LoadingInline from "@components/common/LoadingInline";
import {gql} from "graphql-request";
import {Space} from "antd";
import SlotPipelineStatusLabel from "@components/extension/environments/SlotPipelineStatusLabel";

export default function SlotPipelineLink({pipelineId, status, numberOnly = false}) {

    const {data: pipeline, loading, finished} = useQuery(
        gql`
            query PipelineLink($id: String!) {
                slotPipelineById(id: $id) {
                    id
                    status
                    number
                    slot {
                        project {
                            name
                        }
                        qualifier
                        environment {
                            name
                        }
                    }
                }
            }
        `,
        {
            variables: {id: pipelineId},
            deps: [pipelineId],
            condition: !!pipelineId,
            dataFn: data => data.slotPipelineById,
        }
    )

    return (
        <>
            <LoadingInline loading={loading || !finished}>
                {
                    pipeline &&
                    <Space>
                        <Link href={slotPipelineUri(pipelineId)}>
                            {
                                numberOnly && `#${pipeline.number}`
                            }
                            {
                                !numberOnly && <>
                                    Pipeline {pipeline.slot.environment.name}/{pipeline.slot.project.name}{pipeline.slot.qualifier && `/${pipeline.slot.qualifier}`}#{pipeline.number}
                                </>
                            }
                        </Link>
                        {status && <SlotPipelineStatusLabel status={pipeline.status}/>}
                    </Space>
                }
            </LoadingInline>
        </>
    )
}