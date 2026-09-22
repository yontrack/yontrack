import {useQuery} from "@components/services/useQuery";
import {gql} from "graphql-request";
import LoadingContainer from "@components/common/LoadingContainer";
import ItemList from "@components/common/ItemList";

export default function CascLocations() {

    const {data, loading} = useQuery(
        gql`
            query CasC {
                casc {
                    locations
                }
            }
        `
    )

    return (
        <>
            <LoadingContainer loading={loading}>
                {
                    data &&
                    <ItemList>
                        {
                            data.casc.locations.map(item =>
                                <ItemList.Item key={item} title={item}/>
                            )
                        }
                    </ItemList>
                }
            </LoadingContainer>
        </>
    )
}