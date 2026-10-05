import {Button, Space, Tag, Typography} from "antd";
import {FaDownload, FaEye, FaTimesCircle} from "react-icons/fa";
import TimestampText from "@components/common/TimestampText";
import {evidenceDownloadUri} from "@components/common/Links";
import {actorText} from "@components/extension/audit-trail/auditTrailModel";
import {evidenceSize, evidenceSourceUrl, isPreviewOffered} from "@components/extension/audit-trail/evidenceModel";

/**
 * The cells of a table of evidence, shared by the validation run page and the audit trail page of
 * a build.
 */

/**
 * Text of each flag of the verification.
 */
const FLAG_TEXTS = {
    missing: 'Missing',
    altered: 'Altered',
}

/**
 * What the verification found wrong with an evidence: an icon and a text, never the colour alone.
 */
const EvidenceFlag = ({flag}) =>
    <Tag
        color="error"
        icon={<FaTimesCircle aria-hidden="true"/>}
        data-testid="evidence-flag"
        title={flag === 'missing' ? 'The content of the evidence is absent from the storage' : 'The content of the evidence no longer has its recorded SHA-256'}
    >
        <span style={{marginLeft: 4}}>{FLAG_TEXTS[flag]}</span>
    </Tag>

/**
 * When an evidence was deleted.
 */
const EvidenceDeletion = ({deletedAt}) =>
    <span>
        <Tag>Deleted</Tag>
        <TimestampText value={deletedAt}/>
    </span>

/**
 * Name of an evidence, struck through when it is deleted — with its deletion, unless a State
 * column shows it.
 *
 * @param item Evidence
 * @param withState Whether a State column shows the deletion
 */
const EvidenceNameCell = ({item, withState}) =>
    item.deletedAt ?
        <Space orientation="vertical" size={0}>
            <Typography.Text delete type="secondary">{item.fileName}</Typography.Text>
            {!withState && <EvidenceDeletion deletedAt={item.deletedAt}/>}
        </Space> :
        <Typography.Text strong>{item.fileName}</Typography.Text>

/**
 * State of an evidence: active or deleted, and what the verification found wrong with it, if
 * anything.
 *
 * @param item Evidence
 * @param flag `missing`, `altered`, or nothing
 */
const EvidenceStateCell = ({item, flag}) =>
    <Space orientation="vertical" size={4}>
        {
            item.deletedAt ?
                <EvidenceDeletion deletedAt={item.deletedAt}/> :
                <Typography.Text type="secondary">Active</Typography.Text>
        }
        {flag && <EvidenceFlag flag={flag}/>}
    </Space>

/**
 * Where an evidence comes from, as its client said it.
 */
const EvidenceSourceCell = ({source}) => {
    if (!source) {
        return <Typography.Text type="secondary">-</Typography.Text>
    }
    const tool = [source.tool, source.version].filter(it => !!it).join(' ')
    const url = evidenceSourceUrl(source.url)
    return (
        <Space orientation="vertical" size={0}>
            {tool && <Typography.Text>{tool}</Typography.Text>}
            {
                url &&
                <Typography.Link href={url} target="_blank" rel="noopener noreferrer" style={{wordBreak: 'break-all'}}>
                    {url}
                </Typography.Link>
            }
        </Space>
    )
}

/**
 * Columns describing an evidence: name, state when asked for, type, size, SHA-256, collection and
 * source.
 *
 * @param stateColumn Whether to show the state of the evidence in a column of its own, rather than
 * the deletion under the name
 * @param flags Flags of the verification by evidence ID, shown in the State column — see
 * `evidenceVerificationFlags`
 * @param overrides Extra antd props of a column, by its key — like its filter
 */
export const evidenceColumns = ({stateColumn = false, flags, overrides = {}} = {}) => [
    {
        key: 'fileName',
        title: 'Name',
        render: (_, item) => <EvidenceNameCell item={item} withState={stateColumn}/>,
    },
    stateColumn && {
        key: 'state',
        title: 'State',
        render: (_, item) => <EvidenceStateCell item={item} flag={flags?.get(item.id)}/>,
    },
    {
        key: 'mediaType',
        title: 'Type',
        render: (_, {mediaType}) => <Typography.Text code>{mediaType}</Typography.Text>,
    },
    {
        key: 'size',
        title: 'Size',
        render: (_, {size}) => <span title={`${size} bytes`}>{evidenceSize(size)}</span>,
    },
    {
        key: 'sha256',
        title: 'SHA-256',
        width: '22em',
        render: (_, {sha256}) =>
            <Typography.Text
                code
                copyable={{text: sha256, tooltips: ['Copy the SHA-256', 'Copied']}}
                style={{wordBreak: 'break-all', fontSize: '0.85em'}}
            >{sha256}</Typography.Text>,
    },
    {
        key: 'collected',
        title: 'Collected',
        render: (_, {collectedAt, collectedBy}) =>
            <Space orientation="vertical" size={0}>
                <TimestampText value={collectedAt}/>
                <Typography.Text type="secondary">{actorText(collectedBy)}</Typography.Text>
            </Space>,
    },
    {
        key: 'source',
        title: 'Source',
        render: (_, {source}) => <EvidenceSourceCell source={source}/>,
    },
].filter(column => !!column).map(column => ({...column, ...overrides[column.key]}))

/**
 * The preview and the download of an evidence which is not deleted.
 *
 * @param item Evidence
 * @param onPreview Called with the evidence to preview
 */
export const EvidenceReadActions = ({item, onPreview}) =>
    <>
        {
            isPreviewOffered(item) &&
            <Button
                size="small"
                icon={<FaEye aria-hidden="true"/>}
                aria-label={`Preview ${item.fileName}`}
                title="Preview"
                onClick={() => onPreview(item)}
            />
        }
        {
            item.downloadUrl &&
            <Button
                size="small"
                icon={<FaDownload aria-hidden="true"/>}
                href={evidenceDownloadUri(item)}
                download={item.fileName}
                aria-label={`Download ${item.fileName}`}
                title="Download"
            />
        }
    </>
