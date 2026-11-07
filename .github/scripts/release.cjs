const { execFileSync } = require("node:child_process");
const { readFileSync, writeFileSync } = require("node:fs");

// The release version goes into gradle.properties, so the jar carries it.
exports.prepare = (_, { nextRelease, cwd, env }) => {
    const path = `${cwd}/gradle.properties`;
    const content = readFileSync(path, "utf8");
    const pattern = /^version=.*$/m;
    if (!pattern.test(content)) throw new Error(`No version found in ${path}`);

    writeFileSync(path, content.replace(pattern, `version=${nextRelease.version}`));
    execFileSync("./gradlew", ["build"], { cwd, env, stdio: "inherit" });
};
