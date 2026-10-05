import {useState} from "react";
import {gql} from "graphql-request";
import {Alert, Button, Empty, Popconfirm, Space, Typography} from "antd";
import {FaTrash, FaUpload} from "react-icons/fa";
import Table from "@components/common/table/Table";
import {callGraphQL, useQuery} from "@components/services/GraphQL";
import {getGraphQLErrors} from "@components/services/graphql-utils";
import {useRefresh} from "@components/common/RefreshUtils";
import {
    canDeleteEvidence,
    canUploadEvidence,
    evidenceStorageMessage,
} from "@components/extension/audit-trail/evidenceModel";
import {EvidenceReadActions, evidenceColumns} from "@components/extension/audit-trail/EvidenceCells";
import EvidencePreview from "@components/extension/audit-trail/EvidencePreview";
import EvidenceUploadDialog from "@components/extension/audit-trail/EvidenceUploadDialog";

export const gqlValidationRunEvidence = gql`
    query ValidationRunEvidence($id: Int!) {
        auditTrailStorageState
        validationRuns(id: $id) {
            id
            evidence {
                id
                fileName
                mediaType
                size
                sha256
                collectedAt
                collectedBy
                source {
                    tool
                    version
                    url
                }
                externalDigest
                deletedAt
                downloadUrl
            }
        }
    }
`

const gqlDeleteEvidence = gql`
    mutation DeleteEvidence($id: Int!) {
        deleteEvidence(input: {id: $id}) {
            errors {
                message
            }
        }
    }
`

const NO_EVIDENCE = []

/**
 * The evidence of a validation run, as the cell of its page shows it: the list, with a preview of
 * the allow-listed types, a download and a deletion for the users granted `evidence/delete` — and
 * an upload for the users granted `evidence/create`. When the evidence storage cannot be used, the
 * cell says so in place of the upload and of the list.
 *
 * @param run Validation run, with its `id` and its `authorizations`
 * @param evidence Evidence of the run, deleted ones included
 * @param storageState `AuditTrailStorageState`
 * @param loading Whether the evidence is being loaded
 * @param onChanged Called once an evidence was uploaded or deleted
 */
export function ValidationRunEvidenceContent({run, evidence, storageState, loading, onChanged}) {

    const [preview, setPreview] = useState(null)
    const [uploadOpen, setUploadOpen] = useState(false)
    const [error, setError] = useState(null)

    const storageMessage = evidenceStorageMessage(storageState)
    if (storageMessage) {
        return (
            <Alert
                type="warning"
                showIcon
                title={storageMessage}
                description="Evidence can be neither uploaded nor downloaded until it is fixed."
                data-testid="validation-run-evidence-storage"
            />
        )
    }

    const canUpload = canUploadEvidence(run)
    const canDelete = canDeleteEvidence(run)

    const onDelete = async (item) => {
        setError(null)
        try {
            const data = await callGraphQL({query: gqlDeleteEvidence, variables: {id: item.id}})
            const errors = getGraphQLErrors(data, 'deleteEvidence')
            if (errors.length > 0) {
                setError(errors[0])
            } else {
                onChanged()
            }
        } catch (ex) {
            setError(ex.message)
        }
    }

    const columns = [
        ...evidenceColumns(),
        {
            key: 'actions',
            title: 'Actions',
            render: (_, item) =>
                !item.deletedAt &&
                <Space size="small">
                    <EvidenceReadActions item={item} onPreview={setPreview}/>
                    {
                        canDelete &&
                        <Popconfirm
                            title={`Delete ${item.fileName}?`}
                            description="It stays listed, marked as deleted, and its deletion is written to the audit trail of the build."
                            okText="Delete"
                            okButtonProps={{danger: true}}
                            onConfirm={() => onDelete(item)}
                        >
                            <Button
                                size="small"
                                danger
                                icon={<FaTrash aria-hidden="true"/>}
                                aria-label={`Delete ${item.fileName}`}
                                title="Delete"
                            />
                        </Popconfirm>
                    }
                </Space>,
        },
    ]

    return (
        <Space orientation="vertical" className="ot-line">
            {
                canUpload &&
                <Button
                    icon={<FaUpload aria-hidden="true"/>}
                    onClick={() => setUploadOpen(true)}
                    data-testid="validation-run-evidence-upload"
                >
                    Upload evidence
                </Button>
            }
            {
                error &&
                <Alert
                    type="error"
                    showIcon
                    closable={{onClose: () => setError(null)}}
                    title={error}
                    data-testid="validation-run-evidence-error"
                />
            }
            <Table
                data-testid="validation-run-evidence"
                size="small"
                rowKey="id"
                loading={loading}
                columns={columns}
                dataSource={evidence}
                locale={{
                    emptyText: <Empty
                        image={Empty.PRESENTED_IMAGE_SIMPLE}
                        description="No evidence attached to this validation run"
                    />,
                }}
                pagination={{
                    pageSize: 20,
                    hideOnSinglePage: true,
                    showTotal: (total) => `${total} evidence`,
                }}
            />
            {
                preview &&
                <EvidencePreview evidence={preview} onClose={() => setPreview(null)}/>
            }
            {
                canUpload &&
                <EvidenceUploadDialog
                    run={run}
                    open={uploadOpen}
                    onClose={() => setUploadOpen(false)}
                    onUploaded={() => {
                        setUploadOpen(false)
                        onChanged()
                    }}
                />
            }
        </Space>
    )
}

/**
 * The Evidence cell of the validation run page: loads the evidence of the run and the state of the
 * evidence storage, and reloads them once an evidence is uploaded or deleted.
 *
 * @param run Validation run, with its `id` and its `authorizations`
 */
export default function ValidationRunEvidence({run}) {

    const [refreshState, refresh] = useRefresh()

    const {data, loading, finished, error} = useQuery(
        gqlValidationRunEvidence,
        {
            variables: {id: Number(run.id)},
            deps: [run.id, refreshState],
            condition: !!run.id,
        }
    )

    if (error) {
        return <Typography.Text type="danger">{error}</Typography.Text>
    }

    return (
        <ValidationRunEvidenceContent
            run={run}
            evidence={data?.validationRuns?.[0]?.evidence ?? NO_EVIDENCE}
            storageState={data?.auditTrailStorageState}
            loading={loading || !finished}
            onChanged={refresh}
        />
    )
}
