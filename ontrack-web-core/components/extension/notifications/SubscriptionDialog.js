import FormDialog, {useFormDialog} from "@components/form/FormDialog";
import {Form, Input} from "antd";
import SelectMultipleEvents from "@components/core/model/SelectMultipleEvents";
import SelectNotificationChannel from "@components/extension/notifications/SelectNotificationChannel";
import {useState} from "react";
import Well from "@components/common/Well";
import NotificationChannelConfigForm from "@components/extension/notifications/NotificationChannelConfigForm";
import {gql} from "graphql-request";
import {callDynamicFunction} from "@components/common/DynamicFunction";
import SubscriptionKeywordsHelp from "@components/extension/notifications/SubscriptionKeywordsHelp";

export const useSubscriptionDialog = ({onSuccess, projectEntity}) => {

    const [channelType, setChannelType] = useState('')

    const customPreparation = async (channelType, channelConfig) => {
        const newConfig = await callDynamicFunction(
            `framework/notification-channel/${channelType}/FormPrepare`,
            channelConfig,
        )
        return newConfig ?? channelConfig
    }

    return useFormDialog({
        onSuccess,
        channelType, setChannelType,
        prepareValues: async (values) => {
            // Custom preparation of values for the configuration
            const channelConfig = await customPreparation(channelType, values.channelConfig)
            // OK
            return {
                ...values,
                channel: channelType,
                channelConfig: channelConfig,
                projectEntity,
            }
        },
        query: gql`
            mutation CreateSubscription(
                $name: String!,
                $projectEntity: ProjectEntityIDInput,
                $events: [String!]!,
                $keywords: String,
                $channel: String!,
                $channelConfig: JSON!,
                $contentTemplate: String,
            ) {
                subscribeToEvents(input: {
                    name: $name,
                    projectEntity: $projectEntity,
                    events: $events,
                    keywords: $keywords,
                    channel: $channel,
                    channelConfig: $channelConfig,
                    contentTemplate: $contentTemplate,
                }) {
                    errors {
                        message
                    }
                }
            }
        `,
        userNode: 'subscribeToEvents'
    })
}

export default function SubscriptionDialog({subscriptionDialog}) {

    const onSelectedNotificationChannel = (channelType) => {
        subscriptionDialog.setChannelType(channelType)
    }

    return (
        <>
            <FormDialog dialog={subscriptionDialog}>
                {/*  Name  */}
                <Form.Item
                    name="name"
                    label="Name"
                    extra="Unique name for this subscription"
                    rules={[{required: true, message: 'Subscription name is required'}]}
                >
                    <Input/>
                </Form.Item>
                {/*  Event  */}
                <Form.Item
                    name="events"
                    label="Events"
                    extra="Events to subscribe to"
                    rules={[{required: true, message: 'At least one event is required.'}]}
                >
                    <SelectMultipleEvents/>
                </Form.Item>
                {/*  Keywords  */}
                <Form.Item
                    name="keywords"
                    label="Keywords"
                    extra={<SubscriptionKeywordsHelp/>}
                >
                    <Input/>
                </Form.Item>
                {/*  Channel selection  */}
                <Form.Item
                    name="channel"
                    label="Channel"
                    rules={[{required: true, message: 'Channel is required.'}]}
                >
                    <SelectNotificationChannel onSelectedNotificationChannel={onSelectedNotificationChannel}/>
                </Form.Item>
                {/*  Channel config form  */}
                {
                    subscriptionDialog.channelType &&
                    <Form.Item
                        label="Channel config"
                    >
                        <Well>
                            <NotificationChannelConfigForm
                                prefix="channelConfig"
                                channelType={subscriptionDialog.channelType}
                            />
                        </Well>
                    </Form.Item>
                }
                {/*  Content template  */}
                <Form.Item
                    name="contentTemplate"
                    label="Custom template"
                >
                    <Input.TextArea rows={5}/>
                </Form.Item>
            </FormDialog>
        </>
    )
}