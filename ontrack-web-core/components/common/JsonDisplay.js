import AceEditor from "react-ace";
import {useAceTheme} from "@components/theme/useAceTheme";

export default function JsonDisplay({value, width = "100%", height = "32em", showLineNumbers = false}) {
    return <AceEditor
        mode="json"
        theme={useAceTheme()}
        value={value}
        readOnly={true}
        showPrintMargin={false}
        width={width}
        height={height}
        setOptions={{
            showLineNumbers: showLineNumbers,
            useWorker: false,
        }}
    />
}