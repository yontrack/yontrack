import GridCell from "@components/grid/GridCell";
import {Dynamic} from "@components/common/Dynamic";
import {useEffect, useState} from "react";
import {useTemplateRenderers} from "@components/extension/issues/SelectTemplateRenderer";
import {Alert, Button, Dropdown, Input, Modal, Space, Spin} from "antd";
import {FaCheck, FaCopy, FaDownload, FaTools} from "react-icons/fa";
import copy from "copy-to-clipboard";
import {gql} from "graphql-request";
import IssueChangeLogExportRequestDialog, {
    useIssueChangeLogExportRequestDialog
} from "@components/extension/issues/IssueChangeLogExportRequestDialog";
import {useMutation, useQuery} from "@components/services/GraphQL";

export default function ChangeLogIssues({id, from, to}) {

    /**
     * The issues are loaded separately from the rest of the change log because,
     * unlike the commits, getting them can be slow (remote issue service).
     */
    const {data: issues, loading, error} = useQuery(
        gql`
            query ChangeLogIssues($from: Int!, $to: Int!) {
                scmChangeLog(from: $from, to: $to) {
                    issues {
                        issueServiceConfiguration {
                            serviceId
                        }
                        issues {
                            displayKey
                            summary
                            url
                            status {
                                name
                            }
                            updateTime
                            rawIssue
                        }
                    }
                }
            }
        `,
        {
            variables: {from, to},
            deps: [from, to],
            condition: !!from && !!to,
            dataFn: data => data.scmChangeLog?.issues,
        }
    )

    const issueServiceId = issues?.issueServiceConfiguration?.serviceId

    const templateRenderers = useTemplateRenderers()

    const [preferences, setPreferences] = useState({
        format: 'text',
        groups: [],
        exclude: '',
        altGroup: '',
    })

    useEffect(() => {
        const storedPreferences = localStorage.getItem('change-log-issues-export')
        if (storedPreferences) {
            setPreferences(JSON.parse(storedPreferences))
        }
    }, []);

    const savePreferences = (values) => {
        setPreferences(values)
        localStorage.setItem('change-log-issues-export', JSON.stringify(values))
    }

    const selectFormat = (id) => {
        const newPreferences = {
            ...preferences,
            format: id,
        }
        savePreferences(newPreferences)
    }

    const [items, setItems] = useState([])

    useEffect(() => {
        const items = []

        templateRenderers.forEach(renderer => {
            items.push({
                key: renderer.id,
                label: <Space>
                    {
                        renderer.id === preferences.format && <FaCheck/>
                    }
                    {renderer.name}
                </Space>,
                onClick: () => selectFormat(renderer.id)
            })
        })

        items.push({type: 'divider'})
        items.push({
            key: 'options',
            label: "Options...",
            icon: <FaTools/>,
            onClick: startIssueChangeLogExportRequestDialog,
        })

        setItems(items)

    }, [templateRenderers, preferences])

    const [exportedContent, setExportedContent] = useState('')
    const [exportCopied, setExportCopied] = useState(false)

    const {mutate: scmChangeLog, loading: exporting} = useMutation(
        gql`
            query ChangeLogExport(
                $from: Int!,
                $to: Int!,
                $format: String!,
                $grouping: String,
                $exclude: String,
                $altGroup: String,
            ) {
                scmChangeLog(from: $from, to: $to) {
                    export(
                        request: {
                            format: $format,
                            grouping: $grouping,
                            exclude: $exclude,
                            altGroup: $altGroup,
                        }
                    )
                }
            }
        `,
        {
            userNodeName: 'scmChangeLog',
            onSuccess: (userNode) => {
                const content = userNode.export
                setExportedContent(content)
                showExportedContent()
            }
        }
    )

    const [exportedContentShowing, setExportedContentShowing] = useState(false)

    const onExport = async () => {
        setExportedContent('')
        setExportCopied(false)

        const grouping = preferences.groups
            .map(({name, list}) => (
                `${name}=${
                    list.map(it => it.mapping).join(',')
                }`
            ))
            .join('|')

        await scmChangeLog({
            from,
            to,
            format: preferences.format,
            grouping,
            exclude: null, // TODO
            altGroup: null, // TODO
        })
    }

    const showExportedContent = () => {
        setExportedContentShowing(true)
    }

    const closeExportedContent = () => {
        setExportedContentShowing(false)
    }

    const onCopy = () => {
        if (exportedContent) {
            setExportCopied(copy(exportedContent))
        }
    }

    const issueChangeLogExportRequestDialog = useIssueChangeLogExportRequestDialog({
        onSuccess: (values) => {
            savePreferences(values)
        }
    })

    const startIssueChangeLogExportRequestDialog = () => {
        issueChangeLogExportRequestDialog.start(preferences)
    }

    return (
        <>
            <GridCell
                id={id}
                title="Issues"
                loading={loading}
                extra={
                    <>
                        <Dropdown.Button
                            type="primary"
                            trigger="click"
                            disabled={loading || exporting}
                            menu={{items}}
                            onClick={onExport}
                        >
                            <Space>
                                {exporting ? <Spin size="small"/> : <FaDownload/>}
                                Export
                            </Space>
                        </Dropdown.Button>
                    </>
                }
            >
                {
                    error &&
                    <Alert type="error" showIcon message={error}/>
                }
                {
                    issueServiceId &&
                    <Dynamic
                        path={`framework/issues/${issueServiceId}-issues`}
                        props={{issues: issues.issues}}
                    />
                }
            </GridCell>

            <Modal title="Exported change log"
                   open={exportedContentShowing}
                   disabled={true}
                   footer={() => (
                       <>
                           <Button
                               icon={exportCopied ? <FaCheck/> : <FaCopy/>}
                               onClick={onCopy}
                               disabled={exportCopied}
                           >
                               {
                                   exportCopied ? 'Copied' : 'Copy'
                               }
                           </Button>
                           <Button
                               id="close-exported-content"
                               onClick={closeExportedContent}
                               type="primary"
                           >
                               Close
                           </Button>
                       </>
                   )}
                   onCancel={closeExportedContent}
                   width="70%"
            >
                <Input.TextArea
                    id="exported-content"
                    placeholder="Exported content"
                    value={exportedContent}
                    rows={20}
                />
            </Modal>

            <IssueChangeLogExportRequestDialog issueChangeLogExportRequestDialog={issueChangeLogExportRequestDialog}/>
        </>
    )
}