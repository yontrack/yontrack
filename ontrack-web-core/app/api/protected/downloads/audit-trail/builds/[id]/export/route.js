import {NextResponse} from "next/server";
import {download} from "@/app/api/protected/downloads/downloads";

/**
 * JSON export of the trail of a build, as an attachment.
 */
export async function GET(request, props) {
    const params = await props.params
    // Only a build ID goes into the URI of the backend
    if (!/^\d+$/.test(params.id)) {
        return NextResponse.json({error: "Not a build ID"}, {status: 400})
    }
    return await download({
        uri: `rest/extension/audit-trail/builds/${params.id}/export`,
    })
}
