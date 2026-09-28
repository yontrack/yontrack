import {FaBan, FaCheck, FaPlay, FaSpinner, FaTimesCircle} from "react-icons/fa";

const icons = {
    CANDIDATE: <FaSpinner color="blue"/>,
    RUNNING: <FaPlay color="blue"/>,
    CANCELLED: <FaBan color="gray"/>,
    DONE: <FaCheck color="green"/>,
    FAILED: <FaTimesCircle color="red"/>,
}

export default function SlotPipelineStatusIcon({status}) {
    return icons[status] ?? null
}
