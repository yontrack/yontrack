import {Button, Popover, Spin} from "antd";

export default function InlineCommand({id, title, icon, onClick, href, className, loading}) {
    return (
        <>
            <Popover
                content={title}
            >
                {
                    !loading &&
                    <Button
                        data-testid={id}
                        className={className}
                        type="text"
                        icon={icon}
                        onClick={onClick}
                        href={href}
                    />
                }
                {
                    loading && <Spin size="small"/>
                }
            </Popover>
        </>
    )
}