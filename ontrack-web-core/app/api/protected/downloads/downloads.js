import {backend, getAccessToken} from "@/app/api/protected/backend";
import {NextResponse} from "next/server";

/**
 * Headers of the backend's answer passed on to the browser, when the backend sends them: what the
 * content is, how to show it, and what keeps it from being shown in any other way — an evidence is
 * served with `nosniff` and a `Content-Security-Policy` of its own — and what an export says of
 * itself — the export of the events gives its format version and whether it is truncated.
 */
const FORWARDED_HEADERS = [
    'Content-Type',
    'Content-Disposition',
    'X-Content-Type-Options',
    'Content-Security-Policy',
    'X-Yontrack-Export-Format-Version',
    'X-Yontrack-Export-Truncated',
]

/**
 * The `Content-Security-Policy` of the backend, with only the UI allowed to frame the content —
 * the policy replaces the one of the UI, which says it, and its preview of a PDF frames it.
 */
const contentSecurityPolicy = (policy) =>
    policy.includes('frame-ancestors') ? policy : `${policy}; frame-ancestors 'self'`

/**
 * Downloads a file from the backend, with the token of the session, and passes it on as it comes.
 *
 * @param uri URI of the backend, relative to its root
 * @param searchParams `URLSearchParams` to forward to the backend as its query string - none by
 * default: a route forwards the query string of its request only when it says so
 */
export const download = async ({uri, searchParams}) => {
    const accessToken = await getAccessToken()
    if (!accessToken) {
        return NextResponse.json({error: "Unauthorized"}, {status: 401})
    }

    const query = searchParams?.toString()
    const backendUrl = query ? `${backend.url}/${uri}?${query}` : `${backend.url}/${uri}`

    const backendResponse = await fetch(backendUrl, {
        method: 'GET',
        headers: {
            Authorization: `Bearer ${accessToken}`
        },
    })

    if (!backendResponse.ok) {
        return NextResponse.json({error: backendResponse.statusText}, {status: backendResponse.status})
    }

    const headers = {}
    FORWARDED_HEADERS.forEach(name => {
        const value = backendResponse.headers.get(name)
        if (value) {
            headers[name] = name === 'Content-Security-Policy' ? contentSecurityPolicy(value) : value
        }
    })

    return new Response(backendResponse.body, {
        status: 200,
        headers,
    })
}
