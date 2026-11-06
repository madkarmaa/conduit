// Turns Minecraft's legacy formatting codes, such as "§cRed §lbold", into JSON text components.

const COLORS: Record<string, string> = {
    "0": "black",
    "1": "dark_blue",
    "2": "dark_green",
    "3": "dark_aqua",
    "4": "dark_red",
    "5": "dark_purple",
    "6": "gold",
    "7": "gray",
    "8": "dark_gray",
    "9": "blue",
    a: "green",
    b: "aqua",
    c: "red",
    d: "light_purple",
    e: "yellow",
    f: "white",
};

const STYLES: Record<string, Style> = {
    k: "obfuscated",
    l: "bold",
    m: "strikethrough",
    n: "underlined",
    o: "italic",
};

type Style = "obfuscated" | "bold" | "strikethrough" | "underlined" | "italic";

// Lists every code for the agent, such as "§c red, §l bold".
export const CODE_LIST: string = [
    ...Object.entries(COLORS).map(([code, color]) => `§${code} ${color.replace("_", " ")}`),
    ...Object.entries(STYLES).map(([code, style]) => `§${code} ${style}`),
    "§r reset",
].join(", ");

export interface TextComponent {
    text: string;
    color?: string;
    obfuscated?: boolean;
    bold?: boolean;
    strikethrough?: boolean;
    underlined?: boolean;
    italic?: boolean;
}

// This follows Java Edition. A color code also clears the styles before it, and §r clears everything.
// Unknown codes stay in the text, and a § at the very end is dropped.
export function toComponents(text: string): TextComponent[] {
    const components: TextComponent[] = [];
    let current: TextComponent = { text: "" };

    const startSegment = (format: Omit<TextComponent, "text">) => {
        if (current.text.length > 0) components.push(current);
        current = { ...format, text: "" };
    };

    for (let index = 0; index < text.length; index++) {
        const character = text[index] ?? "";
        const code = (text[index + 1] ?? "").toLowerCase();

        if (character !== "§") {
            current.text += character;
            continue;
        }
        if (index === text.length - 1) break;

        const color = COLORS[code];
        const style = STYLES[code];
        if (color) startSegment({ color });
        else if (style) startSegment({ ...withoutText(current), [style]: true });
        else if (code === "r") startSegment({});
        else {
            current.text += character;
            continue;
        }

        index++;
    }

    if (current.text.length > 0) components.push(current);
    return components;
}

function withoutText({ text: _, ...format }: TextComponent): Omit<TextComponent, "text"> {
    return format;
}
