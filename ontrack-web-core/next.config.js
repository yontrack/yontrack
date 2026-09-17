const {securityHeaders} = require('./components/security/securityHeaders')

/** @type {import('next').NextConfig} */
const nextConfig = {
    // reactStrictMode: true,
    // basePath: '/ui',
    output: 'standalone',
    /*
     * Mitigation for GHSA-2xp9-vwfh-vxw4 (#1789): an unauthenticated RCE in the
     * Image Optimization API (`/_next/image`), unfixed in the `next` version in
     * use. With the optimizer off, `next/image` renders a plain `<img>` pointing
     * at the source and Next answers `/_next/image` with a 404. Reconsider when
     * Next.js is upgraded (#1787).
     */
    images: {
        unoptimized: true,
    },
    /*
     * Security headers on every route - pages, `/mobile`, `/api/*` and static
     * assets alike (#1770). Evaluated at build time: the one value a running
     * container can change, who may frame the pages, is applied by
     * `middleware.js`. See `components/security/securityHeaders.js`.
     */
    async headers() {
        return [
            {
                source: '/:path*',
                headers: securityHeaders({development: process.env.NODE_ENV === 'development'}),
            },
        ]
    },
}

module.exports = nextConfig
