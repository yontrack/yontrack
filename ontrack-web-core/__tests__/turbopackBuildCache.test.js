/**
 * The Turbopack file-system cache stays off for `next build` (#2048).
 *
 * Next.js 16.3 turns it on by default. It makes Turbopack track dependencies
 * and persist its cache to `.next/cache` while it compiles, which raised the
 * peak memory of `yarn build` in the UI image from 9.4 GB to 11.7 GB: over the
 * 12 GB of a Docker Desktop VM, so the build was SIGKILLed. The image build
 * never reuses `.next/cache`, so the cache only ever cost memory and time. This
 * test keeps it from coming back with a Next.js upgrade or a config clean-up.
 */
import nextConfig from "../next.config"

describe('next.config.js Turbopack build cache', () => {

    it('turns the file-system cache off for builds (#2048)', () => {
        expect(nextConfig.experimental?.turbopackFileSystemCacheForBuild).toBe(false)
    })

})
