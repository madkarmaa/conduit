module.exports = {
    branches: ["main"],
    plugins: [
        "@semantic-release/commit-analyzer",
        "@semantic-release/release-notes-generator",
        "./.github/scripts/release.cjs",
        [
            "@semantic-release/github",
            {
                assets: [
                    {
                        path: ["build/libs/conduit-*.jar", "!build/libs/*-sources.jar"],
                        label: "Mod jar",
                    },
                ],
            },
        ],
        [
            "@semantic-release/git",
            {
                assets: ["gradle.properties", "mcp/package.json", "bun.lock"],
                message: "chore(release): ${nextRelease.version} [skip ci]\n\n${nextRelease.notes}",
            },
        ],
    ],
};
