import {backend, getAccessToken} from "@/app/api/protected/backend";
import {NextResponse} from "next/server";

/**
 * Headers of the backend's answer passed on to the browser, when the backend sends them: what the
 * content is, how to show it, and what keeps it from being shown in any other way — an evidence is
 * served with `nosniff` and a `Content-Security-Policy` of its own.
 */
const FORWARDED_HEADERS = [
    'Content-Type',
    'Content-Disposition',
    'X-Content-Type-Options',
    'Content-Security-Policy',
]

/**
 * The `Content-Security-Policy` of the backend, with only the UI allowed to frame the content —
 * the policy replaces the one of the UI, which says it, and its preview of a PDF frames it.
 */
const contentSecurityPolicy = (policy) =>
    policy.includes('frame-ancestors') ? policy : `${policy}; frame-ancestors 'self'`

export const download = async ({uri}) => {
    const accessToken = await getAccessToken()
    if (!accessToken) {
        return NextResponse.json({error: "Unauthorized"}, {status: 401})
    }

    const backendUrl = `${backend.url}/${uri}`

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
