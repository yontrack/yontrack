import {useQuery} from "@components/services/GraphQL";
import {gql} from "graphql-request";

const noRules = []

export const useSlotAdmissionRules = () => {
    const {data} = useQuery(
        gql`
            query SlotAdmissionRules {
                slotAdmissionRules {
                    id
                    name
                }
            }
        `,
        {
            initialData: noRules,
            dataFn: data => data.slotAdmissionRules,
        }
    )
    return data ?? noRules
}