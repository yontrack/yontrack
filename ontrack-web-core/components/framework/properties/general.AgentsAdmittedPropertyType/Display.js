import {Typography} from "antd";

/**
 * *Agents admitted* on a promotion level: whether a registered agent may promote a build to it.
 */
export default function Display({property}) {
    if (property.value?.admitted !== false) {
        return <Typography.Text data-testid="agents-admitted">
            Agents may promote to this level, if their owner may.
        </Typography.Text>
    } else {
        return <Typography.Text type="secondary" data-testid="agents-not-admitted">
            Agents may not promote to this level.
        </Typography.Text>
    }
}
