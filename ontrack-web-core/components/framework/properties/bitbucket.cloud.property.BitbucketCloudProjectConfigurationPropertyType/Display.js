export default function Display({property}) {
    const {workspace, repository, repositoryUrl} = property.value
    return (
        <a href={repositoryUrl}>{workspace}/{repository}</a>
    )
}
