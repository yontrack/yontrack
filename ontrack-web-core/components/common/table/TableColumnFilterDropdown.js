import {Button, Space} from "antd";
import {FaSearch} from "react-icons/fa";

export default function TableColumnFilterDropdown({confirm, clearFilters, children}) {

    const reset = () => {
        clearFilters()
        confirm()
    }

    return (
        <>
            <Space orientation="vertical" size={0}>
                <Space style={{padding: 8}}>
                    {children}
                </Space>
                <Space style={{padding: 8}}>
                    <Button
                        type="primary"
                        onClick={() => confirm()}
                        icon={<FaSearch/>}
                        size="small"
                        style={{width: 90}}
                    >
                        Search
                    </Button>
                    <Button onClick={reset} size="small" style={{width: 90}}>
                        Reset
                    </Button>
                </Space>
            </Space>
        </>
    )
}