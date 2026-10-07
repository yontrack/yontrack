import {download} from "@/app/api/protected/downloads/downloads";

/**
 * Export of the events as a CSV or JSON attachment, the format and the filter being passed on to
 * the backend as the query string of the request.
 */
export async function GET(request) {
    return await download({
        uri: 'rest/admin/events/export',
        searchParams: new URL(request.url).searchParams,
    })
}
