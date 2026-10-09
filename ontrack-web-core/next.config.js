const {securityHeaderRules} = require('./components/security/securityHeaders')

/** @type {import('next').NextConfig} */
const nextConfig = {
    // reactStrictMode: true,
    // basePath: '/ui',
    output: 'standalone',
    /*
     * `next dev` would otherwise write an `AGENTS.md` and a `CLAUDE.md` into
     * this module on every start (#1787). Agent guidance for the repository is
     * the root `CLAUDE.md`, kept by hand.
     */
    agentRules: false,
    experimental: {
        /*
         * No Turbopack file-system cache for `next build` (#2048). Next.js
         * 16.3 turns it on by default: Turbopack then tracks dependencies and
         * persists its cache while compiling, all in the main process and held
         * until the build ends. That took the peak memory of `yarn build` in
         * the UI image from 9.4 GB to 11.7 GB, over what a 12 GB Docker
         * Desktop VM can give, and the build was SIGKILLed. The image build
         * never reuses `.next/cache`, so the cache was pure cost. `next dev`
         * keeps its own cache: this option is for builds only.
         */
        turbopackFileSystemCacheForBuild: false,
    },
    /*
     * The Image Optimization API (`/_next/image`) stays off, deliberately. It
     * was turned off against GHSA-2xp9-vwfh-vxw4 (#1789), an unauthenticated
     * RCE, and kept off after the upgrade that fixes it (#1787): the UI has no
     * image that needs resizing, so the optimizer is attack surface for no
     * benefit. With it off, `next/image` renders a plain `<img>` pointing at the
     * source and Next answers `/_next/image` with a 404.
     */
    images: {
        unoptimized: true,
    },
    /*
     * Security headers on every route - pages, `/mobile`, `/api/*` and static
     * assets alike (#1770). Evaluated at build time: the one value a running
     * container can change, who may frame the pages, is applied by
     * `proxy.js`. The evidence downloads keep the Content-Security-Policy
     * of the backend instead of the UI's one. See
     * `components/security/securityHeaders.js`.
     */
    async headers() {
        return securityHeaderRules({development: process.env.NODE_ENV === 'development'})
    },
}

module.exports = nextConfig
