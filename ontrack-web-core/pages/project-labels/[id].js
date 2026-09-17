import {useRouter} from "next/router";
import ProjectLabelView from "@components/labels/ProjectLabelView";

export default function ProjectLabelPage() {
    const router = useRouter()
    const {id} = router.query

    return (
        <ProjectLabelView id={Number(id)} key={router.asPath}/>
    )
}
