import {useGraphQLClient} from "@components/providers/ConnectionContextProvider";
import {useEffect, useState} from "react";
import LoadingInline from "@components/common/LoadingInline";
import {Divider, Space} from "antd";
import {FaBan, FaMagic} from "react-icons/fa";
import Link from "next/link";
import {autoVersioningAuditEntryUri} from "@components/common/Links";
import AutoVersioningAuditEntryPR from "@components/extension/auto-versioning/AutoVersioningAuditEntryPR";
import AutoVersioningAuditEntryState from "@components/extension/auto-versioning/AutoVersioningAuditEntryState";
import {gql} from "graphql-request";

export default function AutoVersioningAuditEntryLink({uuid}) {

    const client = useGraphQLClient()
    const [loading, setLoading] = useState(true)
    const [audit, setAudit] = useState()

    useEffect(() => {
        if (client && uuid) {
            setLoading(true)
            client.request(
                gql`
                    query AutoVersioningAuditEntry($uuid: String!) {
                        autoVersioningAuditEntries(filter: {uuid: $uuid}) {
                            pageItems {
                                mostRecentState {
                                    state
                                    data
                                }
                            }
                        }
                    }
                `,
                {uuid}
            ).then(data => {
                setAudit(data.autoVersioningAuditEntries.pageItems[0])
            }).finally(() => {
                setLoading(false)
            })
        }
    }, [client, uuid])

    return (
        <>
            {
                !uuid && <Space>
                    <FaBan/>
                    No AV process was scheduled
                </Space>
            }
            {
                uuid && <LoadingInline loading={loading} text="">
                    {
                        audit && <><Space>
                            <Link href={autoVersioningAuditEntryUri(uuid)}>
                                <Space>
                                    <FaMagic/>
                                    Audit
                                </Space>
                            </Link>
                            {
                                audit?.mostRecentState &&
                                <>
                                    <Divider orientation="vertical"/>
                                    <AutoVersioningAuditEntryPR
                                        entry={audit}
                                    />
                                    <Divider orientation="vertical"/>
                                    <AutoVersioningAuditEntryState
                                        status={audit.mostRecentState}
                                    />
                                </>
                            }
                        </Space>
                        </>
                    }
                </LoadingInline>
            }
        </>
    )
}