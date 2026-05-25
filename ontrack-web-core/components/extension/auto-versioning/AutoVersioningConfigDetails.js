import {Descriptions, Typography} from "antd";
import AutoVersioningPostProcessing from "@components/extension/auto-versioning/AutoVersioningPostProcessing";
import AutoVersioningConfigNotifications from "@components/extension/auto-versioning/AutoVersioningConfigNotifications";
import AutoVersioningAdditionalPaths from "@components/extension/auto-versioning/AutoVersioningAdditionalPaths";
import AutoVersioningSchedule from "@components/extension/auto-versioning/AutoVersioningSchedule";
import AutoVersioningPushMode from "@components/extension/auto-versioning/AutoVersioningPushMode";

export default function AutoVersioningConfigDetails({source, additionalItems = [], size}) {

    const items = additionalItems
    if (source.versionSource) {
        items.push(
            {
                key: 'versionSource',
                label: 'Version source',
                children: <Typography.Text code>{source.versionSource}</Typography.Text>,
                span: 4,
            }
        )
    }
    items.push(
        {
            key: 'targetPath',
            label: 'Target path',
            children: <Typography.Text code>{source.targetPath}</Typography.Text>,
            span: 12,
        },
        {
            key: 'targetPropertyType',
            label: 'Target property type',
            children: <Typography.Text code>{source.targetPropertyType}</Typography.Text>,
            span: 4,
        },
        {
            key: 'targetProperty',
            label: 'Target property',
            children: <Typography.Text code>{source.targetProperty}</Typography.Text>,
            span: 4,
        },
        {
            key: 'targetPropertyRegex',
            label: 'Target property regex',
            children: <Typography.Text code>{source.targetPropertyRegex}</Typography.Text>,
            span: 4,
        },
        {
            key: 'targetRegex',
            label: 'Target regex',
            children: <Typography.Text code>{source.targetRegex}</Typography.Text>,
            span: 4,
        },
        {
            key: 'qualifier',
            label: 'Qualifier',
            children: <Typography.Text code>{source.qualifier}</Typography.Text>,
            span: 4,
        },
        {
            key: 'upgradeBranchPattern',
            label: 'Upgrade branch pattern',
            children: <Typography.Text code>{source.upgradeBranchPattern}</Typography.Text>,
            span: 4,
        },
        {
            key: 'validationStamp',
            label: 'Validation stamp',
            children: <Typography.Text code>{source.validationStamp}</Typography.Text>,
            span: 4,
        },
        {
            key: 'backValidation',
            label: 'Back validation',
            children: <Typography.Text code>{source.backValidation}</Typography.Text>,
            span: 4,
        },
        {
            key: 'prTitleTemplate',
            label: 'PR Title template',
            children: <Typography.Text code>{source.prTitleTemplate}</Typography.Text>,
            span: 12,
        },
        {
            key: 'prBodyTemplateFormat',
            label: 'PR Body template format',
            children: <Typography.Text code>{source.prBodyTemplateFormat}</Typography.Text>,
            span: 4,
        },
        {
            key: 'prBodyTemplate',
            label: 'PR Body template',
            children: <Typography.Text code>{source.prBodyTemplate}</Typography.Text>,
            span: 8,
        },
        {
            key: 'buildLinkCreation',
            label: 'Build link creation',
            children: <Typography.Text code>{source.buildLinkCreation}</Typography.Text>,
            span: 4,
        },
        {
            key: 'reviewers',
            label: 'Reviewers',
            children: <>
                {source.reviewers && source.reviewers.length > 0 &&
                    <ul>
                        {
                            source.reviewers.map(reviewer => (<>
                                <li key={reviewer}>{reviewer}</li>
                            </>))
                        }
                    </ul>
                }
            </>,
            span: 8,
        },
        {
            key: 'pushMode',
            label: 'Push mode',
            children: source.pushMode && <AutoVersioningPushMode pushMode={source.pushMode}/>,
            span: 4,
        },
        {
            key: 'postProcessing',
            label: 'Post processing',
            children: <AutoVersioningPostProcessing
                type={source.postProcessing}
                config={source.postProcessingConfig}
            />,
            span: 12,
        },
        {
            key: 'notifications',
            label: 'Notifications',
            children: <AutoVersioningConfigNotifications
                notifications={source.notifications}
            />,
            span: 12,
        },
        {
            key: 'additionalPaths',
            label: "Additional paths",
            children: <AutoVersioningAdditionalPaths additionalPaths={source.additionalPaths}/>,
        },
        {
            key: 'cronSchedule',
            label: "Schedule",
            children: <AutoVersioningSchedule schedule={source.cronSchedule}/>,
        },
        {
            key: 'disabled',
            label: "Disabled",
            children: source.disabled,
        },
    )

    return (
        <>
            <Descriptions
                items={items}
                column={12}
                bordered={true}
                size={size}
            />
        </>
    )
}