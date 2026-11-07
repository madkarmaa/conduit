import { Err, Ok, type Result } from "results-ts";

// ${NAME} or ${NAME:-default}.
const REFERENCE = /\$\{(\w+)(?::-([^}]*))?\}/g;

// Replaces the variable references in every string of a parsed JSON value. A reference without a
// default to a variable that is not set is an error that names it.
export function expandVariables(
    value: unknown,
    env: Readonly<Record<string, string | undefined>>,
): Result<unknown, string> {
    const missing = new Set<string>();

    const expand = (item: unknown): unknown => {
        if (typeof item === "string") {
            return item.replace(REFERENCE, (_, name: string, fallback?: string) => {
                const variable = env[name];
                if (variable !== undefined) return variable;
                if (fallback === undefined) missing.add(name);
                return fallback ?? "";
            });
        }
        if (Array.isArray(item)) return item.map(expand);
        if (item !== null && typeof item === "object") {
            return Object.fromEntries(
                Object.entries(item).map(([key, entry]) => [key, expand(entry)]),
            );
        }
        return item;
    };

    const expanded = expand(value);
    const names = [...missing].join(", ");
    if (missing.size > 0)
        return Err(`Set ${missing.size === 1 ? "the variable" : "the variables"} ${names}`);
    return Ok(expanded);
}
