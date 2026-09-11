"use client"

/**
 * One build, as a card.
 *
 * This is the mobile UI's one real divergence from the desktop layout rather
 * than a smaller copy of it. `BranchBuilds` renders builds as a matrix with a
 * column per validation stamp: it is wide by construction, and no amount of
 * narrowing turns a matrix into something readable at 375px. A phone gets a card
 * per build instead - what it is called, when it happened, how far it has been
 * promoted, and where it is deployed.
 *
 * Validation *status* is deliberately absent: per-stamp status is the build
 * screen's job, and a strip of validation chips here would rebuild the matrix
 * one card at a time.
 *
 * The whole head of the card is the tap target for the build screen - the card
 * carries no other control, so there is no button to nest inside a link here,
 * unlike `MobileEntityRow`. The badges below are not links: they say something
 * about this build, not about somewhere else to go.
 */

import Link from "next/link"
import {FaServer} from "react-icons/fa"
import TimestampText from "@components/common/TimestampText"
import {PromotionLevelImage} from "@components/promotionLevels/PromotionLevelImage"
import {mobileBuildUri} from "@components/mobile/mobileRoutes"
import {deploymentName} from "@components/mobile/builds/useMobileDeployments"

/**
 * The size a promotion medal is drawn at on a phone.
 *
 * Bigger than the desktop's 16px: on a phone that is a coloured dot. It is
 * paired with the level's name for the same reason - the acceptance criterion is
 * that promotions read without zooming, and only text does that reliably.
 *
 * Exported because the branch screen's promotion picker draws the same medals
 * and must draw them the same size - a level shown at one size in the list and
 * another in the control that filters by it reads as two different things.
 */
export const MEDAL_SIZE = 20

/**
 * @param {Object} build A build with `displayName`, `creation` and
 *   `promotionRuns`.
 * @param {Array} [deployments] Where the build is currently deployed. Passed in
 *   rather than read off the build, because it comes from a query of its own -
 *   see `useMobileDeployments` for why. Absent means either "nowhere" or "this
 *   instance has no environments feature", and a card draws both the same way:
 *   no badges. The build screen is where the difference is worth spelling out.
 */
export default function MobileBuildCard({build, deployments = []}) {

    const promotions = build.promotionRuns ?? []

    return (
        <li className="ot-mobile-card" data-testid={`mobile-build-${build.id}`}>
            <Link href={mobileBuildUri(build.id)} className="ot-mobile-card-head">
                <span className="ot-mobile-card-title">{build.displayName || build.name}</span>
                <span className="ot-mobile-card-time" data-testid={`mobile-build-${build.id}-time`}>
                    {/*
                      Relative, with the absolute time one tap away: an age is
                      what a scanning reader wants ("did this run today?"), and
                      it is also much shorter, which matters on a row that has to
                      share its line with the build's name.
                    */}
                    <TimestampText value={build.creation?.time} relative/>
                </span>
            </Link>
            {
                promotions.length > 0 &&
                <div className="ot-mobile-badges" data-testid={`mobile-build-${build.id}-promotions`}>
                    {
                        promotions.map(run =>
                            <span
                                key={run.id}
                                className="ot-mobile-badge"
                                data-testid={`mobile-promotion-${run.id}`}
                            >
                                <PromotionLevelImage
                                    promotionLevel={run.promotionLevel}
                                    size={MEDAL_SIZE}
                                />
                                <span className="ot-mobile-badge-text">{run.promotionLevel?.name}</span>
                            </span>
                        )
                    }
                </div>
            }
            {
                deployments.length > 0 &&
                <div className="ot-mobile-badges" data-testid={`mobile-build-${build.id}-deployments`}>
                    {
                        deployments.map(pipeline =>
                            <span
                                key={pipeline.id}
                                className="ot-mobile-badge"
                                data-testid={`mobile-deployment-${pipeline.id}`}
                            >
                                {/*
                                  The environments icon the desktop UI uses. It
                                  is what tells this strip from the promotions
                                  one above it - by shape rather than by colour,
                                  which the light theme's identical page and
                                  elevated surfaces rule out.
                                */}
                                <span className="ot-mobile-badge-icon" aria-hidden="true">
                                    <FaServer/>
                                </span>
                                {/*
                                  The qualifier goes beside the environment, and
                                  has to: a project can have two slots in the
                                  same environment and nothing but the qualifier
                                  tells them apart, so a build deployed into both
                                  would draw the same badge twice. See
                                  `deploymentName`.
                                */}
                                <span className="ot-mobile-badge-text">
                                    {deploymentName(pipeline)}
                                </span>
                            </span>
                        )
                    }
                </div>
            }
        </li>
    )
}
