import {Popover, Space, Tag} from "antd";
import JiraIssueLink from "@components/extension/jira/JiraIssueLink";
import {FaLink} from "react-icons/fa";

export default function JiraIssueLinks({links = []}) {
    return (
        <>
            {
                links && links.length > 0 &&
                <Popover
                    title="Linked issues"
                    content={
                        <Space orientation="vertical">
                            {
                                links.map((link, index) => (
                                    <Space key={`link-${index}`}>
                                        <Tag>{link.link}</Tag>
                                        <JiraIssueLink issue={link}/>
                                    </Space>
                                ))
                            }
                        </Space>
                    }
                >
                    <FaLink className="ot-action"/>
                </Popover>
            }

            {/*<Space size={2}>*/}
            {/*    {*/}
            {/*        links.map((link, index) => (*/}
            {/*            <Space size={2} key={`link-${index}`}>*/}
            {/*                {link.link}*/}
            {/*                <JiraIssueLink issue={link}/>*/}
            {/*            </Space>*/}
            {/*        ))*/}
            {/*    }*/}
            {/*</Space>*/}
        </>
    )
}