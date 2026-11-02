import {Popover} from "antd";
import Link from "next/link";
import {promotionLevelUri} from "@components/common/Links";
import {PromotionLevelImage} from "@components/promotionLevels/PromotionLevelImage";
import {
    NotGrantedPromotionLevelContent,
    NotGrantedPromotionLevelTitle
} from "@components/promotionLevels/NotGrantedPromotionLevel";

/**
 * Medal of a promotion level which a build does not have yet, in the branch builds view: the place to
 * hover, or to focus, to see what the build still needs to reach this level.
 *
 * The dashed outline is what marks the state, and the greying is what makes it visible at a glance.
 *
 * @param build Build
 * @param promotionLevel Promotion level the build does not have
 */
export default function NotGrantedPromotionStep({build, promotionLevel}) {
    return (
        <Popover
            trigger={['hover', 'focus']}
            title={<NotGrantedPromotionLevelTitle promotionLevel={promotionLevel}/>}
            content={
                <NotGrantedPromotionLevelContent
                    buildId={build.id}
                    promotionLevel={promotionLevel}
                    testId={`branch-promotion-level-popover-${build.id}-${promotionLevel.id}`}
                />
            }
        >
            <Link
                href={promotionLevelUri(promotionLevel)}
                className="ot-promotion-not-granted"
                aria-label={`${promotionLevel.name}, not granted`}
                data-testid={`branch-promotion-level-trigger-${build.id}-${promotionLevel.id}`}
            >
                <span className="ot-promotion-not-granted-medal">
                    <PromotionLevelImage promotionLevel={promotionLevel}/>
                </span>
            </Link>
        </Popover>
    )
}
