/**
 * Display string of a label, the way the UI renders it in a chip: `category:name`,
 * or `name` alone when the label has no category.
 *
 * It lives here rather than beside one page object because every page showing a
 * label chip - the admin page, the project page - locates it by that string.
 */
export const labelDisplay = ({category, name}) => category ? `${category}:${name}` : name
