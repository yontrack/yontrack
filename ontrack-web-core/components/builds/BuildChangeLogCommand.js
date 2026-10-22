import {Command} from "@components/common/Commands";
import {FaCodeCompare} from "react-icons/fa6";
import {buildKnownName} from "@components/common/Titles";
import {scmChangeLogUri} from "@components/common/Links";

/**
 * Change log between the build and the build before it on its branch — shown only when
 * the branch can produce a change log and there is a previous build.
 */
export default function BuildChangeLogCommand({build}) {
    const previousBuild = build.previousBuild
    const changeLogs = build.branch?.scmBranchInfo?.changeLogs

    return (
        <>
            {
                changeLogs && previousBuild && <Command
                    icon={<FaCodeCompare/>}
                    href={scmChangeLogUri(previousBuild.id, build.id)}
                    text="Change log"
                    title={`Change log since ${buildKnownName(previousBuild)}`}
                    testId="build-change-log-command"
                />
            }
        </>
    )
}
