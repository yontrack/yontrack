/**
 * The Next.js Image Optimization API stays off (#1789).
 *
 * GHSA-2xp9-vwfh-vxw4 is an unauthenticated RCE in `/_next/image`, and the
 * `next` version in use has no fix. With `images.unoptimized`, Next answers
 * that endpoint with a 404 instead of running the optimizer. This test keeps
 * the mitigation from being dropped by accident; the Next.js upgrade (#1787)
 * is where it gets reconsidered.
 */
import nextConfig from "../../../next.config"

describe('next.config.js images', () => {

    it('turns the image optimizer off (GHSA-2xp9-vwfh-vxw4)', () => {
        expect(nextConfig.images?.unoptimized).toBe(true)
    })

})
