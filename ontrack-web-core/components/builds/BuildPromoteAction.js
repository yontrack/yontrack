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
 */
export default function BuildPromoteAction({build, promotionLevel, tooltip, onPromotion, presentation = 'icon'}) {
    const actualTooltip = tooltip ? tooltip : `Promotes the build to ${promotionLevel.name}`

    const dialog = useBuildPromoteDialog({
        onSuccess: onPromotion,
    })

    const onPromote = () => {
        dialog.start({
            build,
            promotionLevel,
        })
    }

    const testId = `build-promote-${build.id}-${promotionLevel.id}`

    return (
        <>
            {
                presentation === 'button' ?
                    <Button data-testid={testId} onClick={onPromote} title={actualTooltip}>
                        <Space size={8}>
                            <FaRegThumbsUp/>
                            <Typography.Text>Promote to {promotionLevel.name}</Typography.Text>
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
