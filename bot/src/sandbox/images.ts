import { spawn } from "node:child_process";
import { Err, Ok, type AsyncResult } from "results-ts";
import packageJson from "../../package.json" with { type: "json" };

// The agent image needs only Node.js and the CLI. Node.js also runs the supervisor and the relay.
// The slim image has no CA certificates, which CLIs that use the system's store need for HTTPS.
const DOCKERFILE = `FROM node:24-bookworm-slim
RUN apt-get update \\
    && apt-get install --yes --no-install-recommends ca-certificates \\
    && rm -rf /var/lib/apt/lists/*
ARG PACKAGE
RUN npm install --global "$PACKAGE" && npm cache clean --force
`;

export interface PresetImage {
    readonly name: string;
    readonly npmPackage: string;
}

// The tag includes the bot version, so a bot update builds new images with the latest CLI.
export function presetImage(preset: string, npmPackage: string): PresetImage {
    return { name: `conduit-bot-${preset}:${packageJson.version}`, npmPackage };
}

// Agents only run in containers, so the bot cannot work without docker.
export function checkDocker(): AsyncResult<undefined, string> {
    return docker(["version"], undefined).mapErr(
        (output) => `Running agents needs Docker, and docker version failed:\n${output}`,
    );
}

// Builds the image unless docker already has it.
export function ensureImage(
    image: PresetImage,
    onBuild: () => void,
): AsyncResult<undefined, string> {
    return docker(["image", "inspect", image.name], undefined).orElseAsync(() => {
        onBuild();

        const build = [
            "build",
            "--tag",
            image.name,
            "--build-arg",
            `PACKAGE=${image.npmPackage}`,
            "-",
        ];

        return docker(build, DOCKERFILE).mapErr(
            (output) => `Building ${image.name} failed:\n${output}`,
        );
    });
}

function docker(args: string[], input: string | undefined): AsyncResult<undefined, string> {
    return Ok(args)
        .mapAsync(
            (parts) =>
                new Promise<{ code: number | null; output: string }>((resolve) => {
                    const child = spawn("docker", parts, { stdio: ["pipe", "ignore", "pipe"] });
                    let output = "";

                    child.stderr
                        .setEncoding("utf8")
                        .on("data", (chunk: string) => (output += chunk));

                    child.on("error", (error) => (output += error.message));
                    child.on("close", (code) => resolve({ code, output }));

                    child.stdin.end(input);
                }),
        )
        .andThen(({ code, output }) =>
            code === 0 ? Ok(undefined) : Err(output.trim().slice(-4000)),
        );
}
