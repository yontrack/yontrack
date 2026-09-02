import dayjs from "dayjs";

// Default imports, not `import * as`: a namespace object is not callable, and `dayjs.extend`
// silently worked only because the bundler's interop handed one back. Under Jest it does not, and
// the module throws on load.
import utc from 'dayjs/plugin/utc';
import timezone from 'dayjs/plugin/timezone';

dayjs.extend(utc);
dayjs.extend(timezone);

export const weekDayFormat = "ddd, MMM DD, YYYY, HH:mm:ss"

export default function TimestampText({
                                          value,
                                          prefix = '',
                                          suffix = '',
                                          format = "YYYY MMM DD, HH:mm",
                                          empty = '',
                                      }) {

    if (value) {
        const localDateTime = dayjs.utc(value).local()
        return (
            <>
                {prefix && `${prefix} `}
                {localDateTime.format(format)}
                {suffix && ` ${suffix}`}
            </>
        )
    } else {
        return empty
    }
}