import {Alert, Space} from "antd";

export default function ConnectionResult({connectionResult}) {
    return (
        <>
            {
                connectionResult && <Space className="ot-line">
                    {
                        connectionResult.type === 'ERROR' &&
                        <Alert
                            type="error"
                            title={connectionResult.message}
                            closable
                            style={{
                                marginTop: 16,
                                padding: 16,
                            }}
                        />
                    }
                    {
                        connectionResult.type === 'OK' &&
                        <Alert
                            type="success"
                            title="Connection OK"
                            closable
                            style={{
                                marginTop: 16,
                                padding: 16,
                            }}
                        />
                    }
                </Space>
            }
        </>
    )
}