import {download} from "@/app/api/protected/downloads/downloads";

const uri = (params) => `rest/ref/schema/json/${params.key}`

export async function GET(request, props) {
    const params = await props.params
    return await download({
        uri: uri(params),
    })
}