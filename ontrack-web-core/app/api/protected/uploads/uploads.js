import {backend, getAccessToken} from "@/app/api/protected/backend";
import {NextResponse} from "next/server";

/**
 * Sends a `multipart/form-data` request of the browser on to the backend, with the token of the
 * session. The body is streamed as it comes — never read whole here — so that the limits of the
 * backend apply to it; the answer of the backend, refusals included, goes back as it is.
 *
 * @param uri Path of the backend end point, relative to its root
 * @param request Request of the browser
 */
export const upload = async ({uri, request}) => {
    const accessToken = await getAccessToken()
    if (!accessToken) {
        return NextResponse.json({error: "Unauthorized"}, {status: 401})
    }

    const backendResponse = await fetch(`${backend.url}/${uri}`, {
        method: 'POST',
        headers: {
            Authorization: `Bearer ${accessToken}`,
            'Content-Type': request.headers.get('content-type'),
        },
        body: request.body,
        // Required by fetch to send a stream
        duplex: 'half',
    })

    const headers = {}
    const contentType = backendResponse.headers.get('content-type')
    if (contentType) {
        headers['Content-Type'] = contentType
    }
    return new Response(backendResponse.body, {
        status: backendResponse.status,
        headers,
    })
}
