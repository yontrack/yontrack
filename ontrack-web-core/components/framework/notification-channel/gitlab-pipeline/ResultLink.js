import Link from "next/link";

export default function ResultLink({url, iid}) {
    return url ? <Link href={url}>Pipeline #{iid}</Link> : undefined
}
