import {FaRegThumbsUp} from "react-icons/fa";
import {Button, Popover, Space, Typography} from "antd";
import BuildPromoteDialog, {useBuildPromoteDialog} from "@components/builds/BuildPromoteDialog";

/**
 * Promotes a build, through the same dialog and the same permissions wherever it is offered.
 *
 * `presentation` decides how the trigger looks, and nothing else:
 *
 *   'icon'   a bare thumbs-up, for the dense hosts - a table cell, a timeline row, a popover.
 *   'button' a labelled button, for the hosts with room for one - the pipeline view's inspector,
 *            where the next promotion is the panel's call to action rather than a hover target.
 *
 * The caller decides its own layout; the dialog, the mutation and the authorization check stay here.
 *
 * `promotionLevel` is the level this affordance IS FOR - it names the tooltip, the label and the
 * test id, and it pre-fills the dialog. It is optional, because an affordance can be for promoting
 * the build without being for promoting it to one particular level. In that case pass
 * `defaultPromotionLevel` instead: the dialog opens pre-filled with it, but nothing claims the
 * promotion is restricted to it. The dialog's level field has always been editable, so a label
 * naming a level was describing a restriction the dialog never had.
 */
export default function BuildPromoteAction({
                                               build,
                                               promotionLevel,
                                               defaultPromotionLevel,
                                               tooltip,
                                               onPromotion,
                                               presentation = 'icon',
                                           }) {
    const actualTooltip = tooltip
        ? tooltip
        : (promotionLevel ? `Promotes the build to ${promotionLevel.name}` : "Promotes the build")

    const dialog = useBuildPromoteDialog({
        onSuccess: onPromotion,
    })

    const onPromote = () => {
        dialog.start({
            build,
            // The level the dialog STARTS on, which is the one this affordance is for when it is
            // for one at all, and otherwise merely a sensible default the user may change.
            promotionLevel: promotionLevel ?? defaultPromotionLevel,
        })
    }

    const testId = promotionLevel
        ? `build-promote-${build.id}-${promotionLevel.id}`
        : `build-promote-${build.id}`

    return (
        <>
            {
                presentation === 'button' ?
                    <Button data-testid={testId} onClick={onPromote} title={actualTooltip}>
                        <Space size={8}>
                            <FaRegThumbsUp/>
                            <Typography.Text>
                                {promotionLevel ? `Promote to ${promotionLevel.name}` : "Promote..."}
                            </Typography.Text>
                        </Space>
                    </Button> :
                    <Popover content={actualTooltip}>
                        <FaRegThumbsUp data-testid={testId} className="ot-command" onClick={onPromote}/>
                    </Popover>
            }
            <BuildPromoteDialog buildPromoteDialog={dialog}/>
        </>
    )
}
