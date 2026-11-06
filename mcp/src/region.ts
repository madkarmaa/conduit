import type { Reply } from "./tool.ts";

interface Region {
    min: { x: number; y: number; z: number } | null;
    size: { x: number; y: number; z: number } | null;
    palette: { serialized: string }[];
    blocks: number[];
}

// One character per palette entry; air gets "." so the shape of a build stands out.
const SYMBOLS = "#@%&*+=ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789$!?<>/|~^";
const AIR = new Set(["minecraft:air", "minecraft:cave_air", "minecraft:void_air"]);

export function summarizeRegion(region: Region): Reply {
    const counts = new Map<number, number>();
    for (const index of region.blocks) counts.set(index, (counts.get(index) ?? 0) + 1);

    const sorted = [...counts.entries()]
        .sort(([, first], [, second]) => second - first)
        .map(([index, count]) => [stateName(region, index), count] as const);
    const rows = sorted.map(([state, count]) => `${count} ${state}`);
    const data = { min: region.min, size: region.size, counts: Object.fromEntries(sorted) };
    return { text: [header(region), ...rows].join("\n"), data };
}

export function drawRegion(region: Region): Reply {
    const size = region.size ?? { x: 0, y: 0, z: 0 };
    const symbols = region.palette.map((state, index) =>
        AIR.has(baseId(state.serialized)) ? "." : (SYMBOLS[index] ?? "?"),
    );
    const legend = region.palette.map((state, index) => `${symbols[index]} ${state.serialized}`);

    const layers: string[] = [];
    for (let y = 0; y < size.y; y++) {
        const rows: string[] = [];
        for (let z = 0; z < size.z; z++) {
            const start = (y * size.z + z) * size.x;
            const row = region.blocks.slice(start, start + size.x);
            rows.push(row.map((index) => symbols[index] ?? "?").join(""));
        }
        layers.push(`y=${(region.min?.y ?? 0) + y}\n${rows.join("\n")}`);
    }

    const text = [header(region), "Rows run along z, columns along x.", ...legend, ...layers];
    return { text: text.join("\n"), data: { min: region.min, size: region.size } };
}

export function paletteFits(region: Region): boolean {
    return region.palette.length <= SYMBOLS.length;
}

function header(region: Region): string {
    const { min, size } = region;
    return `Region from ${min?.x} ${min?.y} ${min?.z}, size ${size?.x}x${size?.y}x${size?.z} (x, y, z)`;
}

function stateName(region: Region, index: number): string {
    return region.palette[index]?.serialized ?? "unknown";
}

function baseId(serialized: string): string {
    return serialized.split(/[[{]/)[0] ?? serialized;
}
