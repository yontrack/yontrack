import BuildContentLinks from "@components/builds/BuildContentLinks";

export default function BuildContentLinksUsedBy({build}) {
    return (
        <>
            <BuildContentLinks
                build={build}
                id="links-usedby"
                title="Used by"
                fieldName="usedByQualified"
            />
        </>
    )
}
