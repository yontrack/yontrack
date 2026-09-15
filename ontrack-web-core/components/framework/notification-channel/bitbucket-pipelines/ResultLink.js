import Link from "next/link";

export default function ResultLink({url, buildNumber}) {
    return url ? <Link href={url}>Pipeline #{buildNumber}</Link> : undefined
}
