import { execFileSync } from "node:child_process";
import { openAsBlob, readFileSync, writeFileSync } from "node:fs";
import { basename } from "node:path";
import { AuthFeature, GenericModrinthClient } from "@modrinth/api-client";

// Modrinth names dependencies by project ID, not by slug.
const MODRINTH_DEPENDENCIES = [
    { project_id: "P7dR8mSH", dependency_type: "required" }, // Fabric API
    { project_id: "Ha28R6CL", dependency_type: "required" }, // Fabric Language Kotlin
];

// Servers need the mod, while players can join without it. Modrinth only takes this on version 3
// of its API, where each version carries it.
const MODRINTH_ENVIRONMENT = "server_only_client_optional";

const readProperty = (cwd, name) => {
    const content = readFileSync(`${cwd}/gradle.properties`, "utf8");
    const value = content.match(new RegExp(`^${name}=(.+)$`, "m"))?.[1];
    if (!value) throw new Error(`gradle.properties sets no ${name}`);
    return value;
};

const readModMetadata = (cwd) =>
    JSON.parse(readFileSync(`${cwd}/src/main/resources/fabric.mod.json`, "utf8"));

// The mod ID is also the slug of the Modrinth project and the start of the jar's name.
const readModId = (cwd) => readModMetadata(cwd).id;

// The README's relative paths only work inside the repository, so on Modrinth they point to the
// files on GitHub as they are at the release's tag.
const readDescription = (cwd, tag) => {
    const repository = readModMetadata(cwd).contact.sources;
    const files = `${repository}/blob/${tag}/`;
    const rawFiles = `${repository.replace("github.com", "raw.githubusercontent.com")}/${tag}/`;

    return readFileSync(`${cwd}/README.md`, "utf8")
        .replaceAll('src="./', `src="${rawFiles}`)
        .replaceAll("](./", `](${files}`);
};

const createModrinthClient = (env) =>
    new GenericModrinthClient({
        userAgent: "madkarmaa/conduit release",
        // Modrinth takes a personal access token as it is, without the "Bearer" prefix.
        features: [new AuthFeature({ token: env.MODRINTH_TOKEN, tokenPrefix: "" })],
    });

// The SKIP_MODRINTH_RELEASE repository variable turns the Modrinth steps off, such as while the
// project there is not set up yet. The GitHub release goes ahead without them.
const skipsModrinth = (env) => env.SKIP_MODRINTH_RELEASE === "true";

// Finding the project before the release starts keeps a missing token or project from leaving a
// release on GitHub that never reached Modrinth.
export const verifyConditions = async (_, { cwd, env, logger }) => {
    if (skipsModrinth(env)) {
        logger.log("SKIP_MODRINTH_RELEASE is true, so this release skips Modrinth");
        return;
    }
    if (!env.MODRINTH_TOKEN) throw new Error("MODRINTH_TOKEN is not set");

    const modId = readModId(cwd);
    const modrinth = createModrinthClient(env);
    await modrinth.labrinth.projects_v2.get(modId).catch((error) => {
        throw new Error(`MODRINTH_TOKEN cannot see a Modrinth project named ${modId}`, {
            cause: error,
        });
    });
};

// The release version goes into gradle.properties, so the jar carries it.
export const prepare = (_, { nextRelease, cwd, env }) => {
    const path = `${cwd}/gradle.properties`;
    const content = readFileSync(path, "utf8");
    const pattern = /^version=.*$/m;
    if (!pattern.test(content)) throw new Error(`No version found in ${path}`);

    writeFileSync(path, content.replace(pattern, `version=${nextRelease.version}`));
    execFileSync("./gradlew", ["build"], { cwd, env, stdio: "inherit" });
};

export const publish = async (_, { nextRelease, cwd, env, logger }) => {
    if (skipsModrinth(env)) return false;
    const modId = readModId(cwd);
    const minecraftVersion = readProperty(cwd, "minecraft_version");
    const versionName = `${nextRelease.version}+${minecraftVersion}`;
    const jarPath = `${cwd}/build/libs/${modId}-${versionName}.jar`;
    const modrinth = createModrinthClient(env);
    const project = await modrinth.labrinth.projects_v2.get(modId);

    const form = new FormData();
    form.append(
        "data",
        JSON.stringify({
            project_id: project.id,
            name: versionName,
            version_number: versionName,
            changelog: nextRelease.notes,
            dependencies: MODRINTH_DEPENDENCIES,
            game_versions: [minecraftVersion],
            loaders: ["fabric"],
            version_type: "release",
            featured: true,
            environment: MODRINTH_ENVIRONMENT,
            file_parts: ["jar"],
            primary_file: "jar",
        }),
    );
    form.append("jar", await openAsBlob(jarPath), basename(jarPath));

    // The client's createVersion uploads through XMLHttpRequest, which only browsers have. An empty
    // Content-Type lets the request set the multipart boundary itself.
    const version = await modrinth.request("/version", {
        api: "labrinth",
        version: 3,
        method: "POST",
        body: form,
        headers: { "Content-Type": "" },
    });
    await modrinth.labrinth.projects_v2.edit(project.id, {
        body: readDescription(cwd, nextRelease.gitTag),
    });

    const url = `https://modrinth.com/mod/${modId}/version/${version.id}`;
    logger.log(`Published ${versionName} to Modrinth: ${url}`);
    return { name: "Modrinth", url };
};
