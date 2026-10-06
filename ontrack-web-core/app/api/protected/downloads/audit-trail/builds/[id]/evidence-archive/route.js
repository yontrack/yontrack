import {NextResponse} from "next/server";
import {download} from "@/app/api/protected/downloads/downloads";

/**
 * Evidence archive of a build, as a ZIP attachment, streamed from the backend.
 */
export async function GET(request, props) {
    const params = await props.params
    // Only a build ID goes into the URI of the backend
    if (!/^\d+$/.test(params.id)) {
        return NextResponse.json({error: "Not a build ID"}, {status: 400})
    }
    return await download({
        uri: `rest/extension/audit-trail/builds/${params.id}/evidence-archive`,
    })
}
