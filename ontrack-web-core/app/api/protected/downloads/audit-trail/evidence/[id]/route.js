import {NextResponse} from "next/server";
import {download} from "@/app/api/protected/downloads/downloads";

/**
 * Content of an evidence, as the backend serves it: inline only for an allow-listed type whose
 * content agrees with it, as an `application/octet-stream` attachment otherwise — always with its
 * `nosniff` and its `Content-Security-Policy`.
 */
export async function GET(request, props) {
    const params = await props.params
    // Only an evidence ID goes into the URI of the backend
    if (!/^\d+$/.test(params.id)) {
        return NextResponse.json({error: "Not an evidence ID"}, {status: 400})
    }
    return await download({
        uri: `rest/extension/audit-trail/evidence/${params.id}/download`,
    })
}
