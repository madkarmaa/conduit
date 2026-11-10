import { readFileSync } from "node:fs";

// Minecraft release ids have two or three numbers, such as 26.3 or 1.21.11, so the release name
// uses the id as it is, without adding a patch number.
const minecraftVersion = readFileSync(`${import.meta.dirname}/gradle.properties`, "utf8").match(
    /^minecraft_version=(.+)$/m,
)?.[1];
if (!minecraftVersion) throw new Error("gradle.properties sets no minecraft_version");

export default {
    branches: ["main"],
    plugins: [
        "@semantic-release/commit-analyzer",
        "@semantic-release/release-notes-generator",
        "./.github/scripts/release.mjs",
        [
            "@semantic-release/github",
            {
                releaseNameTemplate: `<%= nextRelease.version %>+${minecraftVersion}`,
                // Without a label, the release shows each asset by its file name.
                assets: [{ path: ["build/libs/conduit-*.jar", "!build/libs/*-sources.jar"] }],
            },
        ],
        [
            "@semantic-release/git",
            {
                assets: ["gradle.properties"],
                message: "chore(release): ${nextRelease.version} [skip ci]\n\n${nextRelease.notes}",
            },
        ],
    ],
};
