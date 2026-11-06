import type { Reply } from "./tool.ts";

// Responses carry every proto default, so compact drops empty values to keep the model's context
// small.
// Keys starting with "_" only name which proto3 optional fields are set.
export function compact(value: unknown): unknown {
    if (Array.isArray(value)) return value.map(compact);
    if (typeof value !== "object" || value === null) return value;

    const entries = Object.entries(value)
        .filter(([key]) => !key.startsWith("_"))
        .map(([key, field]) => [key, compact(field)] as const)
        .filter(([, field]) => !isEmpty(field));

    return Object.fromEntries(entries);
}

export function withoutKeys(value: Record<string, unknown>, keys: readonly string[]) {
    return removeKeys(value, keys) as Record<string, unknown>;
}

function removeKeys(value: unknown, keys: readonly string[]): unknown {
    if (Array.isArray(value)) return value.map((item) => removeKeys(item, keys));
    if (typeof value !== "object" || value === null) return value;

    const entries = Object.entries(value)
        .filter(([key]) => !keys.includes(key))
        .map(([key, field]) => [key, removeKeys(field, keys)] as const);
    return Object.fromEntries(entries);
}

function isEmpty(value: unknown): boolean {
    if (value === null || value === undefined || value === "") return true;
    if (Array.isArray(value)) return value.length === 0;
    return typeof value === "object" && Object.keys(value).length === 0;
}

export function json(value: unknown): Reply {
    const data = compact(value);
    const text = JSON.stringify(data) ?? "{}";
    return isRecord(data) ? { text, data } : { text };
}

export function done(text: string): Reply {
    return { text };
}

export function lines(header: string, rows: string[], data: Record<string, unknown>): Reply {
    return { text: [header, ...rows].join("\n"), data };
}

export function position(value: { x: number; y: number; z: number } | null): string {
    if (!value) return "?";
    return [value.x, value.y, value.z].map((axis) => round(axis)).join(" ");
}

export function round(value: number): number {
    return Math.round(value * 100) / 100;
}

function isRecord(value: unknown): value is Record<string, unknown> {
    return typeof value === "object" && value !== null && !Array.isArray(value);
}
