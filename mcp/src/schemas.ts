import { z } from "zod";

export const dimension = z
    .string()
    .optional()
    .describe('Dimension id, e.g. "minecraft:the_nether". Defaults to the overworld.');

export const blockPos = z.object({ x: z.int(), y: z.int(), z: z.int() });

export const vec3 = z.object({ x: z.number(), y: z.number(), z: z.number() });

export const rotation = z
    .object({ yaw: z.number(), pitch: z.number() })
    .describe("Degrees. Yaw 0 faces south and pitch -90 faces up.");

export const uuid = z.string().describe("Entity UUID.");

export const player = z.string().describe("Player name or UUID.");

export const snbt = z.string().describe('SNBT, e.g. {CustomName:"Bob",NoAI:1b}.');

export const item = z.object({
    id: z.string().describe('Item id, e.g. "minecraft:diamond_sword".'),
    count: z.int().min(1).default(1),
    components: z
        .string()
        .optional()
        .describe('Data component patch as SNBT, e.g. {"minecraft:custom_name":"Excalibur"}.'),
});

export const text = z
    .string()
    .describe("Plain text, or a JSON text component when it starts with { or [.");

export function toText(value: string) {
    const trimmed = value.trimStart();
    return trimmed.startsWith("{") || trimmed.startsWith("[") ? { json: value } : { plain: value };
}

export const limit = z.int().min(1).max(1000).default(50).describe("Maximum results to return.");

export const blockState = z
    .string()
    .describe('Block state in command syntax, e.g. "minecraft:oak_stairs[facing=east]".');

export const noUpdates = z
    .boolean()
    .default(false)
    .describe("Skip neighbor updates and physics, like structure placement.");
