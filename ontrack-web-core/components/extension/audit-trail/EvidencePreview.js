import {useEffect, useState} from "react";
import {Alert, Button, Modal, Space, Spin, Typography} from "antd";
import {FaDownload} from "react-icons/fa";
import {evidenceDownloadUri} from "@components/common/Links";
import {PREVIEW_TEXT_MAX_LENGTH, previewKind, previewText} from "@components/extension/audit-trail/evidenceModel";

/**
 * Stops reading a download whose content is not needed.
 */
const cancelBody = (response) => {
    try {
        Promise.resolve(response.body?.cancel?.()).catch(() => {
        })
    } catch {
        // Nothing to cancel
    }
}

/**
 * Reads the error of a download which failed.
 */
const downloadError = async (response) => {
    try {
        const body = await response.json()
        return body?.error || body?.message || `HTTP ${response.status}`
    } catch {
        return `HTTP ${response.status}`
    }
}

/**
 * The content of an evidence, previewed in a modal.
 *
 * The `Content-Type` of the download decides: the server serves an allow-listed type only when
 * the content agrees with it, and anything else — HTML and SVG included — as
 * `application/octet-stream`, which is never previewed. What is previewed is never put in the page
 * as markup:
 *
 * - an image is shown by an `img`, from a `blob:` URL of its content
 * - JSON and plain text are shown as text
 * - a PDF is framed from the download route, which keeps the `Content-Security-Policy` of the
 *   server; the content read to decide is not read further
 *
 * @param evidence Evidence to preview
 * @param onClose Called when the modal is closed
 */
export default function EvidencePreview({evidence, onClose}) {

    const [preview, setPreview] = useState({status: 'loading'})

    useEffect(() => {
        let active = true
        let objectUrl = null
        const controller = new AbortController()
        const load = async () => {
            try {
                const response = await fetch(evidenceDownloadUri(evidence), {signal: controller.signal})
                if (!response.ok) {
                    const error = await downloadError(response)
                    if (active) setPreview({status: 'error', error})
                    return
                }
                const kind = previewKind(response.headers.get('content-type'))
                if (kind === 'image') {
                    const blob = await response.blob()
                    if (active) {
                        objectUrl = URL.createObjectURL(blob)
                        setPreview({status: 'image', url: objectUrl})
                    }
                } else if (kind === 'json' || kind === 'text') {
                    const content = await response.text()
                    if (active) setPreview({status: 'text', ...previewText(content, kind)})
                } else {
                    // A PDF is read by its frame; anything else is not previewed
                    cancelBody(response)
                    if (active) setPreview({status: kind === 'pdf' ? 'pdf' : 'refused'})
                }
            } catch (error) {
                if (active && error.name !== 'AbortError') {
                    setPreview({status: 'error', error: error.message})
                }
            }
        }
        // noinspection JSIgnoredPromiseFromCall
        load()
        return () => {
            active = false
            controller.abort()
            if (objectUrl) {
                URL.revokeObjectURL(objectUrl)
            }
        }
    }, [evidence])

    const fileName = evidence.fileName

    return (
        <Modal
            open={true}
            title={`Preview of ${fileName}`}
            onCancel={onClose}
            width="min(1000px, 95vw)"
            destroyOnHidden
            footer={
                <Space>
                    <Button
                        icon={<FaDownload aria-hidden="true"/>}
                        href={evidenceDownloadUri(evidence)}
                        download={fileName}
                        aria-label={`Download ${fileName}`}
                    >
                        Download
                    </Button>
                    <Button type="primary" onClick={onClose}>Close</Button>
                </Space>
            }
        >
            <div data-testid="evidence-preview" data-status={preview.status}>
                {
                    preview.status === 'loading' &&
                    <Spin aria-label={`Loading ${fileName}`}/>
                }
                {
                    preview.status === 'error' &&
                    <Alert
                        type="error"
                        showIcon
                        title={`${fileName} could not be read: ${preview.error}`}
                        data-testid="evidence-preview-error"
                    />
                }
                {
                    preview.status === 'refused' &&
                    <Alert
                        type="info"
                        showIcon
                        title="This evidence cannot be previewed."
                        description="Its content is not one of the types which can be shown in the page, or does not match its declared type: download it to open it."
                        data-testid="evidence-preview-refused"
                    />
                }
                {
                    preview.status === 'image' &&
                    // A blob: URL of the content, which next/image has nothing to optimize in
                    // eslint-disable-next-line @next/next/no-img-element
                    <img
                        src={preview.url}
                        alt={fileName}
                        style={{maxWidth: '100%', maxHeight: '70vh', display: 'block', margin: '0 auto'}}
                    />
                }
                {
                    preview.status === 'pdf' &&
                    <iframe
                        src={evidenceDownloadUri(evidence)}
                        title={`Preview of ${fileName}`}
                        style={{width: '100%', height: '70vh', border: 0}}
                    />
                }
                {
                    preview.status === 'text' &&
                    <Space orientation="vertical" className="ot-line">
                        {
                            preview.truncated &&
                            <Typography.Text type="secondary">
                                Only the first {PREVIEW_TEXT_MAX_LENGTH / 1024} KB are shown: download the evidence
                                to read all of it.
                            </Typography.Text>
                        }
                        <pre
                            data-testid="evidence-preview-text"
                            tabIndex={0}
                            aria-label={`Content of ${fileName}`}
                            style={{
                                maxHeight: '70vh',
                                overflow: 'auto',
                                whiteSpace: 'pre-wrap',
                                wordBreak: 'break-word',
                                margin: 0,
                            }}
                        >{preview.text}</pre>
                    </Space>
                }
            </div>
        </Modal>
    )
}
