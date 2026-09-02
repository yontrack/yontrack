import {Typography} from "antd";
import Duration from "@components/common/Duration";

export default function RunInfoTime({info, mode = "complete"}) {
    // Truthiness, so that 0 counts as no run time. The Go CLI cannot express "not measured" and
    // sends 0 instead, and rows written before the server normalised that away still hold it;
    // either way "Ran in 0 second" is noise rather than a duration. Same check as
    // ValidationRunCell.
    const hasTime = Boolean(info?.runTime)

    return (
        <>
            {
                hasTime && mode === "complete" &&
                <Typography.Text>
                    Ran in <Duration
                    seconds={info.runTime}
                    displaySeconds={true}
                    displaySecondsInTooltip={false}
                />
                </Typography.Text>
            }
            {
                hasTime && mode === "minimal" &&
                <Typography.Text>
                    <Duration
                        seconds={info.runTime}
                        displaySeconds={true}
                        displaySecondsInTooltip={true}
                    />
                </Typography.Text>
            }
        </>
    )
}
