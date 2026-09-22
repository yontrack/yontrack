import {Descriptions, Popover, Space, Tooltip} from "antd";
import BuildRef from "@components/links/BuildRef";
import BuildPromotions from "@components/links/BuildPromotions";
import {useEffect, useState} from "react";
import CheckStatus from "@components/common/CheckStatus";
import {FaCaretRight} from "react-icons/fa";
import ChangelogButton from "@components/links/ChangelogButton";

export default function LatestLinkInfo({sourceBuild, latestOk, targetBuild, lastTargetBuild}) {

    const [items, setItems] = useState([])
    useEffect(() => {
        setItems([
            {
                key: "source",
                label: <Tooltip title="Latest known parent">Parent</Tooltip>,
                span: 12,
                children: <Space>
                    <BuildRef build={sourceBuild}/>
                    <BuildPromotions build={sourceBuild}/>
                </Space>
            },
            {
                key: "status",
                label: "Status",
                span: 12,
                children: <CheckStatus
                    value={latestOk}
                    text="Using latest"
                    noText="Not using latest"
                />
            },
            {
                key: "target",
                label: <Tooltip title="Latest known dependency">Dependency</Tooltip>,
                span: 12,
                children: <Space>
                    <BuildRef build={targetBuild}/>
                    <BuildPromotions build={targetBuild}/>
                </Space>
            },
        ])
    }, [sourceBuild, latestOk, targetBuild]);

    return (
        <>
            <Space orientation="vertical" className="ot-line">
                <Popover
                    title={
                        <Space>
                            <FaCaretRight/>
                            Latest build
                        </Space>
                    }
                    content={
                        <Descriptions
                            style={{width: '20em'}}
                            items={items}
                            size="small"
                        />
                    }
                >
                    <Space orientation="vertical" className="ot-line">
                        <CheckStatus
                            value={latestOk}
                            text="Using latest build"
                            noText="Not using latest build"
                        />
                    </Space>
                </Popover>
                {
                    !latestOk && <Space size="small">
                        (
                        <BuildRef build={targetBuild} displayTooltip={false} tooltipText="Current build"/>
                        <BuildPromotions build={targetBuild}/>
                        <ChangelogButton
                            targetBuild={targetBuild}
                            lastTargetBuild={lastTargetBuild}
                        />
                        )
                    </Space>
                }
            </Space>
        </>
    )
}