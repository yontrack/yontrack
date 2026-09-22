import MobileProjectScreen from "./ProjectScreen"

/**
 * One project on a phone, reached from the project list, from a favourite, or by
 * following a desktop `/project/[id]` link - which the redirect maps here.
 *
 * The screen itself is a client component; the page is only the route.
 */
export default async function MobileProjectPage(props) {
    const params = await props.params
    return <MobileProjectScreen id={params.id}/>
}
