// The agent starts this as its Conduit MCP server. It connects the agent's stdio to the supervisor,
// which forwards the messages to the real MCP server outside the container.
import { connect } from "node:net";
import { SOCKET_PATH } from "./protocol.ts";

const socket = connect(SOCKET_PATH);
process.stdin.pipe(socket);
socket.pipe(process.stdout);

socket.on("close", () => process.exit(0));
socket.on("error", (error) => {
    console.error(`Cannot reach the Conduit MCP server: ${error.message}`);
    process.exit(1);
});
