import {useRef, useState} from "react";
import {Alert, Button, Divider, Input, Popover, Radio, Space, Spin} from "antd";
import {gql} from "graphql-request";
import {callGraphQL, useQuery} from "@components/services/GraphQL";
import {useRefData} from "@components/providers/RefDataProvider";
import {isAuthorized} from "@components/common/authorizations";
import ValidationRunHistoryDialog, {
    useValidationRunHistoryDialog
} from "@components/validationRuns/ValidationRunHistoryDialog";

const FIXED = 'FIXED'

/**
 * The next statuses offered for a run in `statusId`, FIXED first when it is one of them: it is
 * the change the popover exists for, and it should not move around from one status to the next.
 *
 * @param validationRunStatuses `refData.validationRunStatuses`
 * @param statusId Current status of the run
 */
export function quickTransitionStatuses(validationRunStatuses, statusId) {
    const next = validationRunStatuses.getAccessibleStatuses(statusId).filter(it => it)
    return [
        ...next.filter(it => it.id === FIXED),
        ...next.filter(it => it.id !== FIXED),
    ]
}

const RUN_QUERY = gql`
    query ValidationRunQuickTransition($id: Int!) {
        validationRuns(id: $id) {
            id
            lastStatus {
                statusID {
                    id
                    name
                }
            }
            authorizations {
                name
                action
                authorized
            }
        }
    }
`

const CHANGE_STATUS = gql`
    mutation QuickChangeValidationRunStatus(
        $runId: Int!,
        $statusId: String!,
        $description: String,
    ) {
        changeValidationRunStatus(input: {
            validationRunId: $runId,
            validationRunStatusId: $statusId,
            description: $description,
        }) {
            errors {
                message
            }
        }
    }
`

/**
 * Content of the popover. Mounted only while the popover is open, so that the run is loaded
 * when it opens and the popover acts on its *current* status, whatever the host loaded earlier.
 */
function QuickTransitionContent({run, onChanged, onHistory}) {

    const {validationRunStatuses} = useRefData()

    const {data: currentRun, loading, error: loadingError} = useQuery(RUN_QUERY, {
        variables: {id: Number(run.id)},
        deps: [run.id],
        dataFn: data => data.validationRuns[0],
    })

    const [changing, setChanging] = useState(false)
    const [error, setError] = useState()
    const [withComment, setWithComment] = useState(false)
    const [commentStatusId, setCommentStatusId] = useState()
    const [comment, setComment] = useState('')

    const statuses = currentRun && isAuthorized(currentRun, 'validation_run', 'status_change') ?
        quickTransitionStatuses(validationRunStatuses, currentRun.lastStatus.statusID.id) :
        []

    const changeStatus = async (statusId, description) => {
        setChanging(true)
        setError(undefined)
        try {
            const data = await callGraphQL({
                query: CHANGE_STATUS,
                variables: {
                    runId: Number(run.id),
                    statusId,
                    description: description || null,
                },
            })
            // No payload at all when the session has expired and the user is being signed out
            const payload = data?.changeValidationRunStatus
            const errors = payload?.errors
            if (errors && errors.length > 0) {
                setError(errors[0].message)
            } else if (payload) {
                onChanged()
            }
        } catch (ex) {
            setError(ex.message)
        } finally {
            setChanging(false)
        }
    }

    // 'none' when the run cannot change status from here, then only its history is offered
    const mode = statuses.length === 0 ? 'none' : (withComment ? 'comment' : 'quick')

    return (
        <Space
            data-testid={`validation-run-quick-transition-${run.id}`}
            direction="vertical"
            style={{minWidth: '14em', maxWidth: '22em'}}
        >
            {loading && <Spin size="small"/>}
            {loadingError && <Alert type="error" showIcon message={loadingError}/>}
            {
                mode === 'quick' &&
                <Space wrap>
                    {
                        statuses.map((status, index) =>
                            <Button
                                key={status.id}
                                type={index === 0 ? 'primary' : 'default'}
                                size="small"
                                disabled={changing}
                                autoFocus={index === 0}
                                onClick={() => changeStatus(status.id)}
                            >
                                {status.name}
                            </Button>
                        )
                    }
                </Space>
            }
            {
                mode === 'comment' &&
                <Space direction="vertical" style={{width: '100%'}}>
                    <Radio.Group
                        size="small"
                        optionType="button"
                        value={commentStatusId}
                        onChange={e => setCommentStatusId(e.target.value)}
                        options={statuses.map(status => ({label: status.name, value: status.id}))}
                    />
                    <Input.TextArea
                        aria-label="Comment"
                        placeholder="Comment"
                        autoSize={{minRows: 2, maxRows: 6}}
                        value={comment}
                        onChange={e => setComment(e.target.value)}
                    />
                    <Button
                        type="primary"
                        size="small"
                        disabled={!commentStatusId || changing}
                        onClick={() => changeStatus(commentStatusId, comment)}
                    >
                        Confirm
                    </Button>
                </Space>
            }
            {error && <Alert type="error" showIcon message={error}/>}
            {(mode !== 'none' || loadingError) && <Divider style={{margin: 0}}/>}
            <Space wrap>
                {
                    mode === 'quick' &&
                    <Button size="small" type="text" onClick={() => setWithComment(true)}>
                        With comment…
                    </Button>
                }
                {
                    mode === 'comment' &&
                    <Button size="small" type="text" onClick={() => setWithComment(false)}>
                        Back
                    </Button>
                }
                <Button size="small" type="text" onClick={onHistory}>
                    History…
                </Button>
            </Space>
        </Space>
    )
}

/**
 * Clicking `children` - the status of a validation run - opens a popover to move the run to one
 * of its next statuses in one click, to add a comment while doing so, or to open its history.
 *
 * @param run Validation run, only its `id` is used
 * @param label Accessible name of the trigger, for when `children` alone does not say which run it is
 * @param onChange Called after the status of the run has changed
 * @param children Trigger of the popover, or a function `({open}) => trigger`, so that the
 * trigger can silence its own hover popover while this one is open
 */
export default function ValidationRunQuickTransition({run, label, onChange, children}) {

    const [open, setOpen] = useState(false)
    const triggerRef = useRef()

    const historyDialog = useValidationRunHistoryDialog()

    const close = () => {
        setOpen(false)
        triggerRef.current?.focus()
    }

    const onChanged = () => {
        close()
        if (onChange) onChange()
    }

    const onHistory = () => {
        close()
        historyDialog.start({id: run.id})
    }

    return (
        <>
            <Popover
                trigger="click"
                placement="bottom"
                open={open}
                onOpenChange={setOpen}
                destroyTooltipOnHide={true}
                content={
                    <div onKeyDown={e => {
                        if (e.key === 'Escape') close()
                    }}>
                        <QuickTransitionContent run={run} onChanged={onChanged} onHistory={onHistory}/>
                    </div>
                }
            >
                <span
                    ref={triggerRef}
                    role="button"
                    tabIndex={0}
                    aria-label={label}
                    aria-haspopup="dialog"
                    aria-expanded={open}
                    className="ot-action"
                    style={{display: 'inline-block'}}
                    onKeyDown={e => {
                        if (e.key === 'Enter' || e.key === ' ') {
                            e.preventDefault()
                            setOpen(!open)
                        } else if (e.key === 'Escape') {
                            setOpen(false)
                        }
                    }}
                >
                    {typeof children === 'function' ? children({open}) : children}
                </span>
            </Popover>
            <ValidationRunHistoryDialog dialog={historyDialog} onChange={onChange}/>
        </>
    )
}
