import FormDialog, {useFormDialog} from "@components/form/FormDialog";
import {Form, Space, Spin, Typography, Upload} from "antd";
import {FaPlus} from "react-icons/fa";
import {useGraphQLClient} from "@components/providers/ConnectionContextProvider";
import {useEffect, useState} from "react";
import FormErrors from "@components/form/FormErrors";
import {formatFileSize, getBase64} from "@components/common/FileUtils";

export const useChangeImageDialog = ({
                                         query,
                                         queryUserNode,
                                         imageCallback,
                                     }) => {

    const client = useGraphQLClient()

    const [imageContainer, setImageContainer] = useState()
    const [image, setImage] = useState()

    return useFormDialog({
        init: (form, {id}) => {
            client.request(
                query,
                {id}
            ).then(data => {
                setImageContainer(data[queryUserNode])
            })
        },
        onSuccess: (values, {id}) => {
            const file = image
            if (file?.status === 'done') {
                getBase64(file.originFileObj, false, (data) => {
                    imageCallback(data, id)
                })
            }
        },
        imageContainer,
        setImage,
    })
}

export default function ChangeImageDialog({changeImageDialog, renderer}) {

    const [loading, setLoading] = useState(false)
    const [imageUrl, setImageUrl] = useState()
    const [fileSize, setFileSize] = useState('')
    const [fileType, setFileType] = useState('')

    const [submittable, setSubmittable] = useState(false)

    const onChange = (info) => {
        if (info.file.status === 'uploading') {
            setLoading(true)
        } else if (info.file.status === 'done') {
            const size = info.file.size
            const type = info.file.type

            setFileType(type)
            setFileSize(size)

            // Get this url from response in real world.
            getBase64(info.file.originFileObj, true, (url) => {
                setLoading(false)
                setImageUrl(url)
            })

            // Setting the field value
            changeImageDialog.setImage(info.file)
        }
    }

    const formatSize = () => {
        return formatFileSize(fileSize)
    }

    const [formErrors, setFormErrors] = useState([])

    useEffect(() => {
        const errors = []
        if (fileSize && fileSize >= 16 * 1024) {
            errors.push("File exceeds 16 KB.")
        }
        if (fileType && fileType !== 'image/png') {
            errors.push("Only PNG files are supported.")
        }
        setSubmittable(fileSize && fileType && errors.length === 0)
        setFormErrors(errors)
    }, [fileSize, fileType]);

    return (
        <>
            <FormDialog dialog={changeImageDialog} submittable={submittable}>
                <Form.Item
                    name="image"
                    label={
                        changeImageDialog.imageContainer && renderer && renderer(changeImageDialog.imageContainer)
                    }
                >
                    <Space className="ot-line">
                        <Upload
                            listType="picture-card"
                            maxCount={1}
                            showUploadList={false}
                            onChange={onChange}
                            style={{width: '100%'}}
                        >
                            <Space>
                                {
                                    imageUrl ?
                                        (
                                            <img
                                                src={imageUrl}
                                                alt="Image"
                                                style={{
                                                    width: '100%',
                                                }}
                                            />
                                        ) :
                                        <Space orientation="vertical">
                                            {
                                                loading ? <Spin size="small"/> : <FaPlus/>
                                            }
                                            Upload
                                        </Space>
                                }
                            </Space>
                        </Upload>
                        <Space orientation="vertical">
                            {
                                fileSize &&
                                <Typography.Text>File size: {formatSize()}</Typography.Text>
                            }
                            {
                                fileType &&
                                <Typography.Text>File type: {fileType}</Typography.Text>
                            }
                        </Space>
                    </Space>
                </Form.Item>
                <Form.Item>
                    <FormErrors errors={formErrors}/>
                </Form.Item>
            </FormDialog>
        </>
    )
}