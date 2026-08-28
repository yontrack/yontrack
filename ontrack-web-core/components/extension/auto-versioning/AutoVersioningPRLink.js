import {FaCodeBranch} from "react-icons/fa";
import {Typography} from "antd";
import Link from "next/link";
import Columns from "@components/common/Columns";
import {AutoVersioningAuditEntryPRStatus} from "@components/extension/auto-versioning/AutoVersioningAuditEntryPRStatus";

export default function AutoVersioningPRLink({autoVersioningStatusMostRecentStateData, pullRequestStatus, size}) {
    return (
        <>
            {
                autoVersioningStatusMostRecentStateData?.prName &&
                autoVersioningStatusMostRecentStateData?.prLink &&
                <Columns size={size}>
                    <FaCodeBranch color="var(--ot-text-strong)"/>
                    <Typography.Text>PR</Typography.Text>
                    <Typography.Text>
                        <Link
                            href={autoVersioningStatusMostRecentStateData.prLink}>{autoVersioningStatusMostRecentStateData.prName}</Link>
                    </Typography.Text>
                    {
                        pullRequestStatus && pullRequestStatus.status &&
                        <AutoVersioningAuditEntryPRStatus prName={autoVersioningStatusMostRecentStateData.prName} status={pullRequestStatus.status}/>
                    }
                </Columns>
            }
        </>
    )
}