import Link from "next/link";
import {Typography} from "antd";
import {buildUri} from "@components/common/Links";

/** What is shown for a build whose name is not known. */
export const UNKNOWN_BUILD = 'build unknown'

/**
 * The name of the build of one end of a period of exposure, or of an entry of the history of a
 * finding, linked to the build while its run exists.
 *
 * @param run The run, `null` once purged
 * @param name The display name of its build, `null` when unknown
 */
export default function PeriodBuild({run, name}) {
    if (!name) {
        return <Typography.Text type="secondary">{UNKNOWN_BUILD}</Typography.Text>
    } else if (run?.build) {
        return <Link href={buildUri(run.build)}><Typography.Text code>{name}</Typography.Text></Link>
    } else {
        return <Typography.Text code>{name}</Typography.Text>
    }
}
