import {useQuery} from "@components/services/useQuery";
import {gql} from "graphql-request";
import {Space} from "antd";
import LoadingInline from "@components/common/LoadingInline";
import ValidationRunLink from "@components/validationRuns/ValidationRunLink";
import ValidationStampImage from "@components/validationStamps/ValidationStampImage";

export default function OntrackValidationNotificationChannelOutput({runId}) {

    const {data: run, loading} = useQuery(
        gql`
            query ValidationRun($runId: Int!) {
                validationRuns(id: $runId) {
                    id
                    runOrder
                    validationStamp {
                        id
                        name
                        image
                    }
                }
            }
        `,
        {
            variables: {runId},
            dataFn: data => data.validationRuns[0],
        }
    )

    return (
        <>
            <Space orientation="vertical">
                Validation created.
                <LoadingInline loading={loading}>
                    {
                        run &&
                        <ValidationRunLink
                            run={run}
                            text={
                                <Space>
                                    <ValidationStampImage validationStamp={run.validationStamp}/>
                                    <span>#{run.runOrder}</span>
                                </Space>
                            }
                        />
                    }
                </LoadingInline>
            </Space>
        </>
    )
}
