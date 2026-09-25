import SearchResultComponent from "@components/framework/search/SearchResultComponent";
import ProjectLink from "@components/projects/ProjectLink";

export default function Result({data}) {
    return <SearchResultComponent
        title={<ProjectLink project={data.project}/>}
        description=""
    />
}