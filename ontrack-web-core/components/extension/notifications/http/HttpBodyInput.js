import {Input} from "antd";
import {useState} from "react";
import {formatHttpBody, parseHttpBody} from "@components/extension/notifications/http/httpBody";

/**
 * Edits the JSON body of an HTTP call as text, and gives the form its JSON value.
 *
 * The text is kept as typed: a body being written is not valid JSON at every keystroke.
 */
export default function HttpBodyInput({id, value, onChange}) {

    const [text, setText] = useState(() => formatHttpBody(value))

    const onTextChange = (e) => {
        const newText = e.target.value
        setText(newText)
        if (onChange) {
            onChange(parseHttpBody(newText))
        }
    }

    return (
        <Input.TextArea
            id={id}
            value={text}
            onChange={onTextChange}
            rows={8}
            style={{fontFamily: 'monospace'}}
            placeholder={'{\n  "version": "${build}"\n}'}
        />
    )
}
