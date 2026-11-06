const { execFileSync } = require("node:child_process");
const { readFileSync, writeFileSync } = require("node:fs");

const REGISTRY = "https://npm.madkarma.top";
// In dependency order: the MCP server uses the client, and the bot both.
const WORKSPACES = ["client", "mcp", "bot"];

// npm version cannot read the workspace: protocol, so the versions are written here and bun
// publishes the packages instead of @semantic-release/npm. Checking the login first stops a
// release without a registry token before it commits and tags anything.
exports.verifyConditions = (_, { cwd, env }) => {
    const registryEnv = { ...env, NPM_CONFIG_REGISTRY: REGISTRY };
    execFileSync("bun", ["pm", "whoami"], {
        cwd: `${cwd}/mcp`,
        env: registryEnv,
        stdio: "inherit",
    });
};

// The mod and all workspaces share one version.
exports.prepare = (_, { nextRelease, cwd, env }) => {
    const { version } = nextRelease;
    replaceInFile(`${cwd}/gradle.properties`, /^version=.*$/m, `version=${version}`);
    for (const workspace of WORKSPACES) {
        const manifest = `${cwd}/${workspace}/package.json`;
        replaceInFile(manifest, /"version": "[^"]*"/, `"version": "${version}"`);
    }

    const options = { cwd, env, stdio: "inherit" };
    // The lockfile records the workspace versions, and the build installs with a frozen lockfile.
    execFileSync("bun", ["install"], options);
    execFileSync("./gradlew", ["build"], options);
};

exports.publish = (_, { cwd, env }) => {
    for (const workspace of WORKSPACES) {
        execFileSync("bun", ["publish"], { cwd: `${cwd}/${workspace}`, env, stdio: "inherit" });
    }
    return { name: "@conduit packages", url: `${REGISTRY}/-/web/detail/@conduit/bot` };
};

function replaceInFile(path, pattern, replacement) {
    const content = readFileSync(path, "utf8");
    if (!pattern.test(content)) throw new Error(`No version found in ${path}`);
    writeFileSync(path, content.replace(pattern, replacement));
}
