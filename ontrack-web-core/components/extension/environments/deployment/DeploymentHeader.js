import {Alert, Button, Space, Steps, Typography} from "antd"
import {FaBan, FaCheck, FaPlay} from "react-icons/fa"
import BuildLink from "@components/builds/BuildLink"
import {PromotionLevelImage} from "@components/promotionLevels/PromotionLevelImage"
import TimestampText from "@components/common/TimestampText"
import Freshness from "@components/extension/environments/shared/Freshness"
import {isAuthorized} from "@components/common/authorizations"
import {topPromotionRun} from "@components/extension/environments/shared/slotCellModel"
import {deploymentSteps, isSettled, statusChange} from "@components/extension/environments/deployment/deploymentModel"

/**
 * The top of the deployment page: what is being deployed, where it got to, and the one thing to do
 * about it.
 *
 * The old page spread this over a `Descriptions` block and a `List` whose shape changed with the
 * status, with a Run button, a Finish button and a Cancel button appearing and disappearing among
 * the rules. **One primary action** is the decision here: at any moment a deployment has exactly
 * one way forward - Start it, or Finish it - and the bar names it. Cancel is a secondary action
 * beside it because cancelling is not what anybody came to do.
 *
 * A finished deployment offers no action at all, which is why the buttons are not merely disabled.
 *
 * @param {Object} deployment The deployment.
 * @param {boolean} acting Whether an action is in flight, so the buttons can say so.
 * @param {function} onStart Move a candidate into running.
 * @param {function} onFinish Complete a running deployment.
 * @param {function} onCancel Cancel whatever is in flight.
 * @param {number} refreshedAt When the page last asked the server.
 * @param {function} refresh Ask it again now.
 */
export default function DeploymentHeader({
                                             deployment,
                                             acting,
                                             onStart,
                                             onFinish,
                                             onCancel,
                                             refreshedAt,
                                             refresh,
                                         }) {

    const steps = deploymentSteps(deployment)
    const settled = isSettled(deployment)

    const started = statusChange(deployment, 'CANDIDATE')
    const build = deployment.build
    const promotion = topPromotionRun(build)

    const canAct = isAuthorized(deployment.slot ?? {}, 'pipeline', 'create')

    return (
        <Space orientation="vertical" size={8} className="ot-line" data-testid="deployment-header">

            <Space size={12} wrap>
                {/*
                  * `BuildLink` names the build through `buildKnownName`, which is the display name.
                  * There was a second `Typography.Text` here rendering
                  * `build.releaseProperty.value` beside it - the property's JSON object, which React
                  * refuses as a child, so the whole page was an error screen for any build carrying
                  * a release (#1824). The link already says the name, so nothing replaces it.
                  */}
                <BuildLink build={build}/>
                {
                    build?.branch &&
                    <Typography.Text type="secondary">({build.branch.name})</Typography.Text>
                }
                {
                    promotion &&
                    <PromotionLevelImage promotionLevel={promotion.promotionLevel} size={16}/>
                }
                <Typography.Text type="secondary" data-testid="deployment-started">
                    {'Started '}
                    <TimestampText value={deployment.start} relative={true}/>
                    {started?.user ? ` by ${started.user}` : ''}
                </Typography.Text>
                {
                    deployment.end &&
                    <Typography.Text type="secondary" data-testid="deployment-ended">
                        {'Ended '}
                        <TimestampText value={deployment.end} relative={true}/>
                    </Typography.Text>
                }
                <Freshness refreshedAt={refreshedAt} refresh={refresh} testId="deployment-freshness"/>
            </Space>

            {
                deployment.errorMessage &&
                <Alert
                    type="error"
                    showIcon
                    title={deployment.errorMessage}
                    data-testid="deployment-error"
                />
            }

            <Space align="center" size={24} style={{width: '100%'}} wrap>
                <div style={{minWidth: 360, flexGrow: 1}}>
                    <Steps
                        size="small"
                        current={steps.current}
                        status={steps.status}
                        data-testid="deployment-steps"
                        items={steps.items.map(item => ({
                            title: <span data-testid={`deployment-step-${item.key}`}>{item.title}</span>,
                        }))}
                    />
                </div>
                {
                    /*
                     * Hidden rather than disabled when the user may not act, like everywhere else in
                     * the redesign. Disabled when the deployment itself is not ready: that button
                     * *will* become available, which is exactly what a disabled control promises.
                     */
                    !settled && canAct &&
                    <Space data-testid="deployment-actions">
                        {
                            deployment.status === 'CANDIDATE' &&
                            <Button
                                type="primary"
                                icon={<FaPlay/>}
                                loading={acting}
                                disabled={!deployment.runAction?.ok}
                                data-testid="deployment-start"
                                onClick={onStart}
                            >
                                Start the deployment
                            </Button>
                        }
                        {
                            deployment.status === 'RUNNING' &&
                            <Button
                                type="primary"
                                icon={<FaCheck/>}
                                loading={acting}
                                disabled={!deployment.finishAction?.ok}
                                data-testid="deployment-finish"
                                onClick={onFinish}
                            >
                                Finish the deployment
                            </Button>
                        }
                        <Button
                            danger
                            type="text"
                            icon={<FaBan/>}
                            loading={acting}
                            data-testid="deployment-cancel"
                            onClick={onCancel}
                        >
                            Cancel
                        </Button>
                    </Space>
                }
            </Space>
        </Space>
    )
}
