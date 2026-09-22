import {Button, Form, Modal, Space} from "antd";
import {useEffect, useState} from "react";
import FormErrors from "@components/form/FormErrors";
import {useMutation} from "@components/services/GraphQL";

export function useFormDialog(config) {
    const [open, setOpen] = useState(false)
    const [form] = Form.useForm()
    const [context, setContext] = useState(undefined)
    return {
        ...config,
        open,
        setOpen,
        form,
        context,
        start: (context) => {
            setContext(context)
            if (config.init) {
                config.init(form, context)
            }
            setOpen(true)
        },
    }
}

export default function FormDialog({
                                       id,
                                       dialog,
                                       onValuesChange,
                                       children,
                                       hasOk = true,
                                       submittable = true,
                                       header,
                                       width,
                                       height,
                                       extraButtons,
                                       okText
                                   }) {

    const form = dialog.form
    const [formErrors, setFormErrors] = useState([]);

    const bodyStyle = {}
    if (height) {
        bodyStyle.height = height
        bodyStyle.overflowY = 'auto'
        bodyStyle.overflowX = 'hidden'
    }

    const onCancel = () => {
        form.resetFields()
        dialog.setOpen(false)
    }

    const getActualQuery = () => {
        if (dialog.query) {
            if (typeof dialog.query === 'function') {
                return dialog.query(dialog.context)
            } else {
                return dialog.query
            }
        } else {
            return undefined
        }
    }

    const getActualUserNode = () => {
        if (dialog.userNode) {
            if (typeof dialog.userNode === 'function') {
                return dialog.userNode(dialog.context)
            } else {
                return dialog.userNode
            }
        } else {
            return undefined
        }
    }

    const actualQuery = getActualQuery()

    const formOnSuccess = (result) => {
        dialog.setOpen(false)
        form.resetFields()
        if (dialog.onSuccess) {
            dialog.onSuccess(result, dialog.context)
        }
    }

    const {mutate, loading, error} = useMutation(
        actualQuery,
        {
            userNodeName: getActualUserNode(),
            onSuccess: formOnSuccess,
        }
    )

    useEffect(() => {
        setFormErrors(error ? [error] : [])
    }, [error])

    const onSubmit = async () => {
        setFormErrors([])
        let values = await form.validateFields()
        if (dialog.prepareValues) {
            values = await dialog.prepareValues(values, dialog.context)
        }
        if (actualQuery) {
            await mutate(values)
        } else {
            formOnSuccess(values)
        }
    }

    return (
        <>
            <Modal
                open={dialog.open}
                closable={false}
                confirmLoading={loading}
                onCancel={onCancel}
                footer={null}
                width={width}
            >
                <Form
                    data-testid={id}
                    layout="vertical"
                    form={form}
                    onFinish={onSubmit}
                    onValuesChange={onValuesChange}
                >
                    <Space orientation="vertical" className="ot-line">
                        {
                            header &&
                            <div style={{width: '100%'}}>
                                {header}
                            </div>
                        }
                        <div style={bodyStyle}>
                            {children}
                        </div>
                        <Form.Item>
                            <Space style={{float: 'right'}}>
                                {
                                    extraButtons
                                }
                                <Button type="default" onClick={onCancel}>
                                    Cancel
                                </Button>
                                {
                                    hasOk &&
                                    <Button loading={loading} type="primary" htmlType="submit"
                                            disabled={loading || !submittable}>
                                        {okText ?? "OK"}
                                    </Button>
                                }
                            </Space>
                        </Form.Item>
                    </Space>
                </Form>
                <FormErrors errors={formErrors}/>
            </Modal>
        </>
    )
}