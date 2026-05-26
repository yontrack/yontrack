import {Descriptions, Typography} from "antd";
import AutoVersioningPostProcessing from "@components/extension/auto-versioning/AutoVersioningPostProcessing";
import AutoVersioningConfigNotifications from "@components/extension/auto-versioning/AutoVersioningConfigNotifications";
import AutoVersioningAdditionalPaths from "@components/extension/auto-versioning/AutoVersioningAdditionalPaths";
import AutoVersioningSchedule from "@components/extension/auto-versioning/AutoVersioningSchedule";
import AutoVersioningPushMode from "@components/extension/auto-versioning/AutoVersioningPushMode";
import FieldLabel from "@components/extension/auto-versioning/AutoVersioningFieldLabel";

export default function AutoVersioningConfigDetails({source, additionalItems = [], size}) {

    const items = additionalItems
    if (source.versionSource) {
        items.push(
            {
                key: 'versionSource',
                label: <FieldLabel fieldName="versionSource"
                                   description="Source of the version for the build. By default, uses the build label if the source project is configured so, or the build name itself."/>,
                children: <Typography.Text code>{source.versionSource}</Typography.Text>,
                span: 1,
            }
        )
    }
    items.push(
        {
            key: 'targetPath',
            label: <FieldLabel fieldName="targetPath"
                               description="Comma-separated list of files to update with the new version"/>,
            children: <Typography.Text code>{source.targetPath}</Typography.Text>,
            span: 3,
        },
        {
            key: 'targetPropertyType',
            label: <FieldLabel fieldName="targetPropertyType"
                               description="When targetProperty is defined, defines the type of property (defaults to Java properties file, but could be NPM, etc.)"/>,
            children: <Typography.Text code>{source.targetPropertyType}</Typography.Text>,
            span: 1,
        },
        {
            key: 'targetProperty',
            label: <FieldLabel fieldName="targetProperty"
                               description="Optional replacement for the regex, using only a property name"/>,
            children: <Typography.Text code>{source.targetProperty}</Typography.Text>,
            span: 1,
        },
        {
            key: 'targetPropertyRegex',
            label: <FieldLabel fieldName="targetPropertyRegex"
                               description="Optional regex to use on the targetProperty value"/>,
            children: <Typography.Text code>{source.targetPropertyRegex}</Typography.Text>,
            span: 1,
        },
        {
            key: 'targetRegex',
            label: <FieldLabel fieldName="targetRegex"
                               description="Regex to use in the target file to identify the line to replace with the new version. The first matching group must be the version."/>,
            children: <Typography.Text code>{source.targetRegex}</Typography.Text>,
            span: 1,
        },
        {
            key: 'qualifier',
            label: <FieldLabel fieldName="qualifier"
                               description="Qualifier for the build link to create (when links are created)"/>,
            children: <Typography.Text code>{source.qualifier}</Typography.Text>,
            span: 1,
        },
        {
            key: 'upgradeBranchPattern',
            label: <FieldLabel fieldName="upgradeBranchPattern"
                               description="Prefix to use for the upgrade branch in Git, defaults to feature/auto-upgrade-<project>-<version>-<branch>"/>,
            children: <Typography.Text code>{source.upgradeBranchPattern}</Typography.Text>,
            span: 1,
        },
        {
            key: 'validationStamp',
            label: <FieldLabel fieldName="validationStamp"
                               description="Validation stamp to create on auto versioning (optional)"/>,
            children: <Typography.Text code>{source.validationStamp}</Typography.Text>,
            span: 1,
        },
        {
            key: 'backValidation',
            label: <FieldLabel fieldName="backValidation"
                               description="Validation stamp to create on the source build (optional)"/>,
            children: <Typography.Text code>{source.backValidation}</Typography.Text>,
            span: 1,
        },
        {
            key: 'prTitleTemplate',
            label: <FieldLabel fieldName="prTitleTemplate"
                               description="Template for the title of the pull request (optional)"/>,
            children: <Typography.Text code>{source.prTitleTemplate}</Typography.Text>,
            span: 3,
        },
        {
            key: 'prBodyTemplateFormat',
            label: <FieldLabel fieldName="prBodyTemplateFormat"
                               description="Template format for the body of the pull request (plain by default, html, markdown as possible values)"/>,
            children: <Typography.Text code>{source.prBodyTemplateFormat}</Typography.Text>,
            span: 1,
        },
        {
            key: 'prBodyTemplate',
            label: <FieldLabel fieldName="prBodyTemplate"
                               description="Template for the body of the pull request (optional)"/>,
            children: <Typography.Text code>{source.prBodyTemplate}</Typography.Text>,
            span: 3,
        },
        {
            key: 'buildLinkCreation',
            label: <FieldLabel fieldName="buildLinkCreation"
                               description="Build link creation. True by default."/>,
            children: <Typography.Text code>{source.buildLinkCreation}</Typography.Text>,
            span: 1,
        },
        {
            key: 'reviewers',
            label: <FieldLabel fieldName="reviewers"
                               description="List of reviewers to always set on the pull request created by the auto versioning"/>,
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
            span: 2,
        },
        {
            key: 'pushMode',
            label: <FieldLabel fieldName="pushMode" description="Push mode"/>,
            children: source.pushMode && <AutoVersioningPushMode pushMode={source.pushMode}/>,
            span: 1,
        },
        {
            key: 'postProcessing',
            label: <FieldLabel fieldName="postProcessing"
                               description="Type of post processing to launch after the version has been updated"/>,
            children: <AutoVersioningPostProcessing
                type={source.postProcessing}
                config={source.postProcessingConfig}
            />,
            span: 3,
        },
        {
            key: 'notifications',
            label: <FieldLabel fieldName="notifications"
                               description="List of notification subscriptions to setup for this auto versioning"/>,
            children: <AutoVersioningConfigNotifications
                notifications={source.notifications}
            />,
            span: 3,
        },
        {
            key: 'additionalPaths',
            label: <FieldLabel fieldName="additionalPaths" description="Additional paths to change"/>,
            children: <AutoVersioningAdditionalPaths additionalPaths={source.additionalPaths}/>,
            span: 3,
        },
        {
            key: 'cronSchedule',
            label: <FieldLabel fieldName="cronSchedule"
                               description="Cron schedule (when to start applying queued requests)"/>,
            children: <AutoVersioningSchedule schedule={source.cronSchedule}/>,
            span: 1,
        },
        {
            key: 'disabled',
            label: <FieldLabel fieldName="disabled" description="Set if this configuration is disabled"/>,
            children: source.disabled,
            span: 1,
        },
    )

    return (
        <>
            <Descriptions
                items={items}
                column={3}
                bordered={true}
                layout="vertical"
                size={size}
            />
        </>
    )
}