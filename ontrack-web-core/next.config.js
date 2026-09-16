const {securityHeaders} = require('./components/security/securityHeaders')

/** @type {import('next').NextConfig} */
const nextConfig = {
    // reactStrictMode: true,
    // basePath: '/ui',
    output: 'standalone',
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
