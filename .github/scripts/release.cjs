const { execFileSync } = require("node:child_process");
const { readFileSync, writeFileSync } = require("node:fs");

const REGISTRY_PAGE = "https://npm.madkarma.top/-/web/detail/@conduit/mcp";

// npm version cannot read the workspace: protocol, so the versions are written here and bun
// publishes the MCP server instead of @semantic-release/npm.
exports.verifyConditions = (_, { env }) => {
    if (env.NPM_CONFIG_TOKEN) return;
    throw new Error("NPM_CONFIG_TOKEN is not set, so bun cannot publish @conduit/mcp.");
};

// The mod and the MCP server share one version.
exports.prepare = (_, { nextRelease, cwd, env }) => {
    const { version } = nextRelease;
    replaceInFile(`${cwd}/gradle.properties`, /^version=.*$/m, `version=${version}`);
    replaceInFile(`${cwd}/mcp/package.json`, /"version": "[^"]*"/, `"version": "${version}"`);

    const options = { cwd, env, stdio: "inherit" };
    // The lockfile records the workspace versions, and the build installs with a frozen lockfile.
    execFileSync("bun", ["install"], options);
    execFileSync("./gradlew", ["build"], options);
};

exports.publish = (_, { cwd, env }) => {
    execFileSync("bun", ["publish"], { cwd: `${cwd}/mcp`, env, stdio: "inherit" });
    return { name: "@conduit/mcp", url: REGISTRY_PAGE };
};

function replaceInFile(path, pattern, replacement) {
    const content = readFileSync(path, "utf8");
    if (!pattern.test(content)) throw new Error(`No version found in ${path}`);
    writeFileSync(path, content.replace(pattern, replacement));
}
