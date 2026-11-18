/**
 * URL of an HTTP call, for display: the path appended to the base URL of its HTTP configuration, as
 * `resolveHttpCallUrl` does in the backend. The template expressions of the path are left as they are.
 *
 * @param baseUrl Base URL of the HTTP configuration, if known
 * @param path Path of the call, with an optional query
 * @return The absolute URL, or the path alone when the base URL is not known
 */
export const httpCallUrl = (baseUrl, path) => {
    const trimmed = (path ?? '').trim()
    if (!baseUrl) {
        return trimmed || '/'
    }
    const base = baseUrl.replace(/\/+$/, '')
    if (!trimmed) {
        return baseUrl
    }
    if (trimmed.startsWith('?')) {
        return `${base}${trimmed}`
    }
    return `${base}/${trimmed.replace(/^\/+/, '')}`
}
