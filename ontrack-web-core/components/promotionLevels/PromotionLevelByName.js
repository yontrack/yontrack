import {useQuery} from "@components/services/GraphQL";
import {gql} from "graphql-request";
import PromotionLevel from "@components/promotionLevels/PromotionLevel";
import LoadingInline from "@components/common/LoadingInline";

export default function PromotionLevelByName({projectName, branchName, name}) {
    const {data: pl, loading, finished} = useQuery(
        gql`
            query PromotionLevelByName($projectName: String!, $branchName: String!, $name: String!) {
                promotionLevelByName(
                    project: $projectName,
                    branch: $branchName,
                    name: $name,
                ) {
                    id
                    name
                    image
                }
            }
        `,
        {
            variables: {
                projectName,
                branchName,
                name,
            },
            dataFn: data => data.promotionLevelByName,
        }
    )

    return (
        <>
            <LoadingInline loading={loading || !finished}>
                {
                    pl &&
                    <PromotionLevel promotionLevel={pl} displayText={true}/>
                }
            </LoadingInline>
        </>
    )
}