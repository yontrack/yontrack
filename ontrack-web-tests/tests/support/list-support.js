import {expect} from "@playwright/test";

/**
 * Checks that exactly one item of the given list (an `ItemList`, or any `ul`) contains the text.
 */
export const checkListContainsItemText = async (list, item) => {
    const match = list.getByRole('listitem').filter({hasText: item})
    await expect(match).toHaveCount(1)
}
