import {useQuery} from "@components/services/GraphQL";
import {gql} from "graphql-request";
import GlobalMessage from "@components/layouts/GlobalMessage";

export default function MainGlobalMessages() {

    const {data: messages} = useQuery(
        gql`
            query GlobalMessages {
                globalMessages {
                    type
                    content
                }
            }
        `,
        {
            initialData: [],
            dataFn: data => data.globalMessages,
        }
    )

    return (
        <>
            {
                messages && messages.length > 0 &&
                messages.map(({type, content}, index) => (
                        <GlobalMessage key={index} type={type} content={content}/>
                    )
                )
            }
        </>
    )
}