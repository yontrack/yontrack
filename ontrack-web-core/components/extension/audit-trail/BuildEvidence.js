import {useState} from "react";
import {Alert, Button, Empty, Space, theme, Tooltip, Typography} from "antd";
import {FaDownload, FaSearch} from "react-icons/fa";
import Link from "next/link";
import Table from "@components/common/table/Table";
import PageSection from "@components/common/PageSection";
import TableColumnFilterDropdownInput from "@components/common/table/TableColumnFilterDropdownInput";
import {buildEvidenceArchiveUri, validationRunUri} from "@components/common/Links";
import {
    evidenceFilterOptions,
    evidenceStorageMessage,
    filterEvidence,
} from "@components/extension/audit-trail/evidenceModel";
import {EvidenceReadActions, evidenceColumns} from "@components/extension/audit-trail/EvidenceCells";
import EvidencePreview from "@components/extension/audit-trail/EvidencePreview";

/**
 * Whether one of the filters of the table is on.
 */
const isFiltered = (tableFilters) => Object.values(tableFilters).some(value => value && value.length > 0)

/**
 * Download of the evidence archive of the build — disabled, saying why, when it cannot be.
 *
 * @param buildId ID of the build
 * @param activeCount Number of active evidence of the build
 * @param storageMessage Why the storage cannot be used, if it cannot
 */
const EvidenceArchiveButton = ({buildId, activeCount, storageMessage}) => {
    const reason = storageMessage ?? (activeCount === 0 ? 'No active evidence to download' : null)
    return (
        <Tooltip title={reason ?? 'Every active evidence, its manifest and the trail, as a ZIP'}>
            <Button
                size="small"
                icon={<FaDownload aria-hidden="true"/>}
                href={buildEvidenceArchiveUri({id: buildId})}
                download
                disabled={!!reason}
                data-testid="build-evidence-archive"
            >
                Download all ({activeCount})
            </Button>
        </Tooltip>
    )
}

/**
 * The Evidence section of the audit trail page of a build: the evidence of every validation run of
 * the build, deleted ones included, with their validation, their state and what the verification
 * found wrong with them — filtered on the page, previewed and downloaded, but neither uploaded nor
 * deleted, which belong to the validation run page.
 *
 * Every active evidence is downloaded at once as the evidence archive of the build, whatever the
 * filters.
 *
 * When the evidence storage cannot be used, the list still shows — its metadata and hashes come
 * from the database — but nothing can be previewed or downloaded.
 *
 * @param buildId ID of the build
 * @param evidence Evidence of the build, each with its `validationRun`
 * @param flags Flags of the verification, from `evidenceVerificationFlags`
 * @param storageState `AuditTrailStorageState`
 */
export default function BuildEvidence({buildId, evidence, flags, storageState}) {

    const {token} = theme.useToken()
    const [preview, setPreview] = useState(null)
    // Filters of the table, by the key of their column, as antd gives them - never persisted
    const [tableFilters, setTableFilters] = useState({})

    const storageMessage = evidenceStorageMessage(storageState)
    const options = evidenceFilterOptions(evidence)
    const filtered = filterEvidence(
        evidence,
        {
            text: tableFilters.fileName?.[0],
            mediaTypes: tableFilters.mediaType,
            validationStamps: tableFilters.validation,
            states: tableFilters.state,
        },
        flags,
    )
    const filtering = isFiltered(tableFilters)
    const activeCount = evidence.filter(item => !item.deletedAt).length

    const stateOptions = [
        {text: 'Active', value: 'active'},
        {text: 'Deleted', value: 'deleted'},
    ]
    if (flags.verified) {
        stateOptions.push({text: 'Fails the verification', value: 'failing'})
    }

    const [nameColumn, ...otherColumns] = evidenceColumns({
        stateColumn: true,
        flags: flags.byId,
        overrides: {
            fileName: {
                filterDropdown: (props) =>
                    <TableColumnFilterDropdownInput {...props} placeholder="Name or SHA-256"/>,
                filterIcon: (active) =>
                    <FaSearch
                        role="img"
                        aria-label="Filter by name or SHA-256"
                        style={{color: active ? token.colorPrimary : undefined}}
                    />,
                filteredValue: tableFilters.fileName ?? null,
            },
            state: {
                filters: stateOptions,
                filteredValue: tableFilters.state ?? null,
            },
            mediaType: {
                filters: options.mediaTypes.map(mediaType => ({text: mediaType, value: mediaType})),
                filteredValue: tableFilters.mediaType ?? null,
            },
        },
    })

    const columns = [
        nameColumn,
        {
            key: 'validation',
            title: 'Validation',
            render: (_, {validationRun}) =>
                <Link href={validationRunUri(validationRun)}>
                    {validationRun.validationStamp.name} #{validationRun.runOrder}
                </Link>,
            filters: options.validationStamps.map(name => ({text: name, value: name})),
            filteredValue: tableFilters.validation ?? null,
        },
        ...otherColumns,
    ]
    if (!storageMessage) {
        columns.push({
            key: 'actions',
            title: 'Actions',
            render: (_, item) =>
                !item.deletedAt &&
                <Space size="small">
                    <EvidenceReadActions item={item} onPreview={setPreview}/>
                </Space>,
        })
    }

    return (
        <PageSection
            id="audit-trail-evidence"
            title={`Evidence (${evidence.length})`}
            extra={<EvidenceArchiveButton buildId={buildId} activeCount={activeCount} storageMessage={storageMessage}/>}
        >
            <Space orientation="vertical" className="ot-line">
                {
                    storageMessage &&
                    <Alert
                        type="warning"
                        showIcon
                        title={storageMessage}
                        description="The evidence can be neither previewed nor downloaded until it is fixed."
                        data-testid="build-evidence-storage"
                    />
                }
                <Table
                    data-testid="build-evidence"
                    size="small"
                    rowKey="id"
                    columns={columns}
                    dataSource={filtered}
                    onChange={(_pagination, filters) => setTableFilters(filters)}
                    locale={{
                        emptyText: <Empty
                            image={Empty.PRESENTED_IMAGE_SIMPLE}
                            description={
                                filtering ?
                                    "No evidence matches the filters" :
                                    "No evidence attached to any validation of this build"
                            }
                        />,
                    }}
                    pagination={{
                        pageSize: 20,
                        hideOnSinglePage: true,
                    }}
                    footer={
                        filtering ?
                            () => <Typography.Text type="secondary" data-testid="build-evidence-filtered">
                                {filtered.length} of {evidence.length} evidence
                            </Typography.Text> :
                            undefined
                    }
                />
            </Space>
            {
                preview &&
                <EvidencePreview evidence={preview} onClose={() => setPreview(null)}/>
            }
        </PageSection>
    )
}
