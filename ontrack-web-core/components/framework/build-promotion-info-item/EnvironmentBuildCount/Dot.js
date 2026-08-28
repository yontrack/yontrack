import {FaServer} from "react-icons/fa";
import {projectEnvironmentsUri} from "@components/extension/environments/EnvironmentsLinksUtils";
import Link from "next/link";
import {Badge, Popover} from "antd";
import {actionClassName} from "@components/common/ClassUtils";

export default function EnvironmentBuildCountBuildPromotionInfoItemDot({item, build, onChange}) {
    return (
        <>
            <Popover
                content={
                    `This build is deployed to ${item.count} environments.`
                }
            >
                <Link href={projectEnvironmentsUri(item.build.branch.project)}>
                    <Badge
                        overflowCount={10}
                        count={item.count}
                        showZero={true}
                        size="small"
                        color="green"
                    >
                        <FaServer
                            size={24}
                            color="var(--ot-text-strong)"
                            className={actionClassName(true, item.count === 0)}
                        />
                    </Badge>
                </Link>
            </Popover>
        </>
    )
}