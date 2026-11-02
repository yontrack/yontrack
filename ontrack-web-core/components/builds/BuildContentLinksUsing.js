import BuildContentLinks from "@components/builds/BuildContentLinks";

export default function BuildContentLinksUsing({build}) {
    return (
        <>
            <BuildContentLinks
                build={build}
                id="links-using"
                title="Depends on"
                fieldName="usingQualified"
            />
        </>
    )
}
