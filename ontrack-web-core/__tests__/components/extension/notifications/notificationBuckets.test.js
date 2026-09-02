import {bucketNotificationTypes} from "@components/extension/notifications/notificationBuckets";

describe('bucketNotificationTypes', () => {

    it('counts nothing when there is nothing', () => {
        expect(bucketNotificationTypes([])).toEqual({success: 0, running: 0, error: 0})
    })

    it('tolerates a missing list', () => {
        expect(bucketNotificationTypes(undefined)).toEqual({success: 0, running: 0, error: 0})
    })

    it('counts OK as a success', () => {
        expect(bucketNotificationTypes(['OK', 'OK'])).toEqual({success: 2, running: 0, error: 0})
    })

    it('counts both in-flight types as running', () => {
        expect(bucketNotificationTypes(['ONGOING', 'ASYNC'])).toEqual({success: 0, running: 2, error: 0})
    })

    // Anything the frontend does not recognise is an error, not a silent drop:
    // a delivery whose outcome we cannot read is not one we should call fine.
    it('counts anything else as an error', () => {
        expect(bucketNotificationTypes(['ERROR', 'NOT_CONFIGURED', 'SOMETHING_NEW']))
            .toEqual({success: 0, running: 0, error: 3})
    })

    it('buckets a mixed list', () => {
        expect(bucketNotificationTypes(['OK', 'ASYNC', 'ERROR', 'OK']))
            .toEqual({success: 2, running: 1, error: 1})
    })
})
