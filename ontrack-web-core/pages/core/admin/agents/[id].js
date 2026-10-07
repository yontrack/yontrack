import {useRouter} from "next/router";
import AgentView from "@components/core/admin/agents/AgentView";

export default function AgentPage() {
    const router = useRouter()
    const {id} = router.query
    return <AgentView id={id ? Number(id) : undefined} key={router.asPath}/>
}
