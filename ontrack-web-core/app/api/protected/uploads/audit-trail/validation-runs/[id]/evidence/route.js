import {NextResponse} from "next/server";
import {upload} from "@/app/api/protected/uploads/uploads";

/**
 * Upload of an evidence to a validation run: the `multipart/form-data` request of the browser, sent
 * on to the backend, which checks who may upload and answers the refusals with their codes.
 */
export async function POST(request, props) {
    const params = await props.params
    // Only a validation run ID goes into the URI of the backend
    if (!/^\d+$/.test(params.id)) {
        return NextResponse.json({error: "Not a validation run ID"}, {status: 400})
    }
    return await upload({
        uri: `rest/extension/audit-trail/validation-runs/${params.id}/evidence`,
        request,
    })
}
