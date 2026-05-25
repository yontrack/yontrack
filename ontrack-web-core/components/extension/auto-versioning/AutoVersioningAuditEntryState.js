import {Space, Tooltip, Typography} from "antd";
import {
    FaCalendar,
    FaCheck,
    FaCodeBranch,
    FaCog,
    FaFile,
    FaPause,
    FaPlay,
    FaRegClock,
    FaRocket,
    FaThumbsUp,
    FaTimes,
    FaTimesCircle,
    FaUpload,
    FaWindowClose
} from "react-icons/fa";

const statuses = {
    ERROR: {
        type: 'danger',
        icon: <FaTimes/>,
        text: "Error",
    },
    PROCESSING_ABORTED: {
        type: 'warning',
        icon: <FaRegClock/>,
        text: "Aborted",
    },
    PR_TIMEOUT: {
        type: 'danger',
        tooltip: "The PR was created but its checks timed out before it could be merged.",
        icon: <FaTimesCircle/>,
        text: "PR timed out",
    },
    PR_PROCESSED: {
        type: 'success',
        icon: <FaCheck/>,
        text: "PR processed",
    },
    PR_MERGED: {
        type: 'success',
        tooltip: "The PR has merged by Yontrack",
        icon: <FaCheck/>,
        text: "PR merged",
    },
    PR_CREATED: {
        type: 'success',
        tooltip: "The PR was created by Yontrack",
        icon: <FaCheck/>,
        text: "PR created",
    },
    PR_APPROVED: {
        type: 'success',
        tooltip: "The PR has created and approved by Yontrack",
        icon: <FaCheck/>,
        text: "PR approved",
    },
    CREATED: {
        type: 'secondary',
        tooltip: "Auto-versioning request registered",
        icon: <FaPlay/>,
        text: "Created",
    },
    RECEIVED: {
        type: 'secondary',
        tooltip: "Auto-versioning request dequeued",
        icon: <FaThumbsUp/>,
        text: "Received",
    },
    PROCESSING_START: {
        type: 'secondary',
        tooltip: "Auto-versioning processing started",
        icon: <FaCog/>,
        text: "Processing started",
    },
    THROTTLED: {
        type: 'warning',
        tooltip: "Auto-versioning request throttled",
        icon: <FaWindowClose/>,
        text: "Throttled",
    },
    PROCESSING_CREATING_BRANCH: {
        type: 'secondary',
        tooltip: "Target branch being created",
        icon: <FaCodeBranch/>,
        text: "Branch created",
    },
    PROCESSING_UPDATING_FILE: {
        type: 'secondary',
        tooltip: "Updating the files on the target branches",
        icon: <FaFile/>,
        text: "Updating files",
    },
    POST_PROCESSING_START: {
        type: 'secondary',
        tooltip: "Post-processing starts",
        icon: <FaCog/>,
        text: "Post-processing starts",
    },
    POST_PROCESSING_LAUNCHED: {
        type: 'secondary',
        tooltip: "Post-processing launched",
        icon: <FaRocket/>,
        text: "Post-processing launched",
    },
    POST_PROCESSING_END: {
        type: 'secondary',
        tooltip: "Post-processing ends",
        icon: <FaPause/>,
        text: "Post-processing ends",
    },
    PR_CREATING: {
        type: 'secondary',
        tooltip: "PR is being created",
        icon: <FaCodeBranch/>,
        text: "Creating PR",
    },
    PUSHING: {
        type: 'secondary',
        tooltip: "Pushing changes directly to the target branch",
        icon: <FaUpload/>,
        text: "Pushing",
    },
    PUSHED: {
        type: 'success',
        tooltip: "Changes pushed directly to the target branch",
        icon: <FaCheck/>,
        text: "Pushed",
    },
    SCHEDULED: {
        type: 'secondary',
        tooltip: "Auto-versioning request is scheduled for later processing",
        icon: <FaCalendar/>,
        text: "Scheduled",
    },
    PENDING_SCHEDULE: {
        type: 'secondary',
        tooltip: "Auto-versioning request is waiting for its scheduled time",
        icon: <FaCalendar/>,
        text: "Pending schedule",
    },
}

export default function AutoVersioningAuditEntryState({status, id, displayTooltip = true}) {
    const state = statuses[status.state]
    if (state) {
        return <>
            <Typography.Text type={state.type}>
                <Tooltip title={displayTooltip ? state.tooltip : undefined}>
                    <Space>
                        {state.icon}
                        <span data-testid={id}>{state.text}</span>
                    </Space>
                </Tooltip>
            </Typography.Text>
        </>
    } else {
        return `Unknown state: ${status.state}`
    }
}