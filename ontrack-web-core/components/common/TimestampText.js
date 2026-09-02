import {Popover} from "antd";
import dayjs from "dayjs";

// Default imports, not `import * as`: a namespace object is not callable, and `dayjs.extend`
// silently worked only because the bundler's interop handed one back. Under Jest it does not, and
// the module throws on load.
import utc from 'dayjs/plugin/utc';
import timezone from 'dayjs/plugin/timezone';
import relativeTime from 'dayjs/plugin/relativeTime';

dayjs.extend(utc);
dayjs.extend(timezone);
dayjs.extend(relativeTime);

export const weekDayFormat = "ddd, MMM DD, YYYY, HH:mm:ss"

/**
 * A timestamp, rendered in the reader's own time zone.
 *
 * `relative` swaps the absolute format for an age - "3 days ago" - and puts the absolute time in a
 * tooltip. A relative age is what a scanning reader wants ("is this branch alive?"); the absolute
 * time is what they want once they have stopped scanning, so it stays one hover away rather than
 * being dropped. This lives here so no caller has to bootstrap dayjs a second time to get it.
 */
export default function TimestampText({
                                          value,
                                          prefix = '',
                                          suffix = '',
                                          format = "YYYY MMM DD, HH:mm",
                                          relative = false,
                                          empty = '',
                                      }) {

    if (!value) return empty

    const localDateTime = dayjs.utc(value).local()

    if (relative) {
        return (
            <Popover content={localDateTime.format(weekDayFormat)}>
                <span>
                    {prefix && `${prefix} `}
                    {localDateTime.fromNow()}
                    {suffix && ` ${suffix}`}
                </span>
            </Popover>
        )
    }

    return (
        <>
            {prefix && `${prefix} `}
            {localDateTime.format(format)}
            {suffix && ` ${suffix}`}
        </>
    )
}