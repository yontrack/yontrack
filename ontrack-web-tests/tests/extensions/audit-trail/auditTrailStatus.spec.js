import {expect} from "@playwright/test";
import {gql} from "graphql-request";
import {test} from "../../fixtures/connection";
import {login} from "../../core/login";
import {selectUserMenuItem} from "../../core/userMenu";
import {graphQLCall} from "@ontrack/graphql";

/**
 * The public key of the instance, as the API publishes it.
 */
const auditTrailKeys = async (ontrack) => {
    const data = await graphQLCall(
        ontrack.connection,
        gql`
            query AuditTrailKeys {
                auditTrailKeys {
                    keyId
                }
            }
        `
    )
    return data.auditTrailKeys
}

test('audit trail status page shows the licence, the evidence storage and the instance key', async ({page, ontrack}) => {
    await login(page, ontrack)
    // The administrator has the global settings: the page is in the system group
    await selectUserMenuItem(page, "System", "Audit trail status")
    await expect(page).toHaveURL(/\/extension\/audit-trail\/status$/)

    // The acceptance stack runs with the development licence and its own MinIO bucket
    await expect(page.getByTestId('audit-trail-licence')).toHaveText('Enabled')
    await expect(page.getByTestId('audit-trail-storage-state')).toHaveText('OK')
    await expect(page.getByTestId('audit-trail-storage-message')).toHaveCount(0)
    await expect(page.getByTestId('audit-trail-storage-bucket')).toHaveText('yontrack-audit-trail')
    await expect(page.getByTestId('audit-trail-storage-addressing')).toHaveText('Path-style, region us-east-1')

    // The instance key, as the API publishes it
    await expect(page.getByTestId('audit-trail-key-status')).toHaveText('Provisioned')
    const [key] = await auditTrailKeys(ontrack)
    expect(key).toBeDefined()
    await expect(page.getByTestId('audit-trail-key-id')).toContainText(key.keyId)
    await expect(page.getByTestId('audit-trail-public-key')).toContainText('-----BEGIN PUBLIC KEY-----')
})
